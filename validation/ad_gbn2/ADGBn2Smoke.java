package ffx.potential;

import ffx.potential.bonded.Atom;
import ffx.potential.bonded.Bond;
import ffx.potential.parameters.AtomType;
import ffx.potential.parameters.ForceField;
import ffx.numerics.optimization.LBFGS;
import ffx.xray.refine.RefinementModel;
import ffx.xray.refine.RefinementMode;
import ffx.xray.RefinementEnergy;
import ffx.realspace.RealSpaceData;
import ffx.realspace.RealSpaceEnergy;
import ffx.realspace.parsers.RealSpaceFile;
import edu.rit.pj.ParallelTeam;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.commons.configuration2.CompositeConfiguration;
import java.nio.file.*;
import java.util.*;

/** Real GPU integration check; run only inside a Slurm allocation. */
public final class ADGBn2Smoke {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    String toolkit = args[1];
    List<String> lines = Files.readAllLines(Path.of(toolkit, "AD_singlepoint_implicit/examples/water.xyz"));
    Atom[] atoms = new Atom[3];
    for (int i = 0; i < 3; i++) {
      String[] fields = lines.get(i+2).trim().split("\\s+");
      int z = fields[0].equals("O") ? 8 : 1;
      AtomType type = new AtomType(z,z,fields[0],"smoke water",z,z==8 ? 15.999 : 1.008,z==8 ? 2 : 1);
      atoms[i] = new Atom(i+1, fields[0]+(i+1), type,
          new double[]{Double.parseDouble(fields[1]),Double.parseDouble(fields[2]),Double.parseDouble(fields[3])});
    }
    new Bond(atoms[0],atoms[1]);
    new Bond(atoms[0],atoms[2]);
    CompositeConfiguration config = new CompositeConfiguration();
    ForceField ff = new ForceField(config);
    ff.addProperty("AD_PYTHON", "/home/shuzidong/miniconda3/envs/AD/bin/python");
    ff.addProperty("AD_WORKER", root.resolve("python/ad_gbn2_worker.py").toString());
    ff.addProperty("AD_TOOLKIT",toolkit);
    ff.addProperty("AD_PARAMETERS",Path.of(toolkit,"AD_singlepoint_implicit/examples/water_parameters.json").toString());
    ff.addProperty("AD_CHARGE","0");
    ADGBn2Potential p = new ADGBn2Potential(atoms,ff);
    double unit = AlphaDynamicsClient.EV_TO_KCAL_MOL;
    try {
      double[] x = p.getCoordinates(null), initial=x.clone(), g=new double[9];
      double energy = p.energyAndGradient(x,g);
      List<String> ref=Files.readAllLines(root.resolve("validation/ad_gbn2/direct_reference.txt"));
      double expected=Double.parseDouble(ref.get(0))*unit;
      require(Math.abs(energy-expected)<1e-5,"direct energy mismatch");
      String[] forces=ref.get(1).split(" ");
      double maxBridge=0;
      for (int i=0;i<9;i++) maxBridge=Math.max(maxBridge,Math.abs(g[i]+Double.parseDouble(forces[i])*unit));
      require(maxBridge<1e-4,"bridge gradient mismatch "+maxBridge);
      MolecularAssembly assembly = new MolecularAssembly("water") {
        @Override public Atom[] getAtomArray() { return atoms.clone(); }
        @Override public Atom[] getActiveAtomArray() { return Arrays.stream(atoms).filter(Atom::isActive).toArray(Atom[]::new); }
      };
      assembly.setForceField(ff);
      RefinementModel model = new RefinementModel(RefinementMode.COORDINATES, new MolecularAssembly[]{assembly});
      double[] aggregated = new double[9];
      model.addAssemblyGradient(0, aggregated);
      for (int i=0;i<9;i++) require(Math.abs(aggregated[i]-g[i])<1e-12,"refinement gradient mapping");
      double[] scaling = new double[9], scaled = initial.clone(), scaledGradient = new double[9];
      Arrays.fill(scaling, 3.0);
      for (int i=0;i<9;i++) scaled[i] *= scaling[i];
      double[] savedScaled = scaled.clone();
      p.setScaling(scaling);
      require(Math.abs(p.energyAndGradient(scaled,scaledGradient)-energy)<1e-7,"scaled energy");
      for (int i=0;i<9;i++) {
        require(scaled[i]==savedScaled[i],"input coordinate mutation");
        require(Math.abs(scaledGradient[i]*scaling[i]-g[i])<1e-7,"scaled gradient");
      }
      p.setScaling(null);
      System.out.println("ADGBN2_SCALING_AND_REFINEMENT_MAPPING PASS");
      double fdError=0,h=1e-3;
      for (int i=0;i<9;i++) {
        double[] plus=initial.clone(),minus=initial.clone(); plus[i]+=h; minus[i]-=h;
        double numerical=(p.energy(plus)-p.energy(minus))/(2*h);
        fdError=Math.max(fdError,Math.abs(numerical-g[i]));
      }
      require(fdError<0.07,"finite difference error "+fdError);
      p.setCoordinates(initial);
      double start=p.energyAndGradient(initial,g);
      double[] opt=initial.clone();
      int status=LBFGS.minimize(9,5,opt,start,g,0.05,30,p,null);
      double end=p.energyAndGradient(opt,g);
      require(end<start-1e-3,"LBFGS did not lower energy");
      System.out.printf(Locale.ROOT,"ADGBN2_SMOKE PASS energy_kcal_mol=%.12f bridge_gradient_error=%.8g fd_error=%.8g initial=%.12f final=%.12f lbfgs_status=%d%n",energy,maxBridge,fdError,start,end,status);
      p.setCoordinates(initial);
    } finally { p.destroy(); }
    atoms[0].setActive(false);
    p=new ADGBn2Potential(atoms,ff);
    try {
      double[] x=p.getCoordinates(null),g=new double[6];
      double frozenX=atoms[0].getX();
      require(p.getNumberOfVariables()==6,"frozen variable count");
      x[0]+=0.001;p.energyAndGradient(x,g);
      require(atoms[0].getX()==frozenX,"frozen atom moved");
      System.out.println("ADGBN2_FROZEN PASS");
    } finally { p.destroy(); }
    atoms[0].setActive(true);
    testCombinedRefinement(root, atoms, ff);
  }
  private static void testCombinedRefinement(Path root, Atom[] atoms, ForceField ff) throws Exception {
    int dim = 16;
    ByteBuffer map = ByteBuffer.allocate(1024+4*dim*dim*dim).order(ByteOrder.LITTLE_ENDIAN);
    map.putInt(0,dim);map.putInt(4,dim);map.putInt(8,dim);map.putInt(12,2);
    for (int i=0;i<3;i++) { map.putInt(28+4*i,dim);map.putFloat(40+4*i,dim);map.putFloat(52+4*i,90);map.putInt(64+4*i,i+1); }
    map.putInt(88,1);map.putInt(208,0x2050414d);map.putInt(212,0x4444);
    map.position(1024);
    for(int z=0;z<dim;z++) for(int y=0;y<dim;y++) for(int x=0;x<dim;x++)
      map.putFloat((float)(Math.sin(2*Math.PI*x/dim)+0.5*Math.cos(2*Math.PI*y/dim)+0.2*Math.sin(2*Math.PI*z/dim)));
    Path mapFile=root.resolve("validation/ad_gbn2/synthetic.map");Files.write(mapFile,map.array());
    double chemical;double[] chemicalGradient=new double[9];
    ADGBn2Potential isolated=new ADGBn2Potential(atoms,ff);
    try { chemical=isolated.energyAndGradient(isolated.getCoordinates(null),chemicalGradient); }
    finally { isolated.destroy(); }
    ff.addProperty("AD_GBN2","true");
    MolecularAssembly assembly=new MolecularAssembly("water-refinement") {
      @Override public Atom[] getAtomArray(){return atoms.clone();}
      @Override public Atom[] getActiveAtomArray(){return atoms.clone();}
    };
    assembly.setForceField(ff);
    CompositeConfiguration props=ff.getProperties();props.setProperty("xweight",0.3);
    ParallelTeam team=new ParallelTeam(1);
    RealSpaceData data=new RealSpaceData(assembly,props,team,new RealSpaceFile(mapFile.toString()));
    RefinementEnergy combined=null;
    try {
      RealSpaceEnergy target=new RealSpaceEnergy(data);
      double[] x=target.getCoordinates(null),targetGradient=new double[9],gradient=new double[9];
      double mapEnergy=target.energyAndGradient(x,targetGradient);
      combined=new RefinementEnergy(data);
      double energy=combined.energyAndGradient(x,gradient), max=0;
      require(Math.abs(energy-chemical-0.3*mapEnergy)<1e-6,"combined refinement energy");
      for(int i=0;i<9;i++) max=Math.max(max,Math.abs(gradient[i]-chemicalGradient[i]-0.3*targetGradient[i]));
      require(max<1e-6,"combined refinement gradient "+max);
      double fd=0,h=1e-3;
      for(int i=0;i<9;i++) {
        double[] plus=x.clone(),minus=x.clone();plus[i]+=h;minus[i]-=h;
        fd=Math.max(fd,Math.abs((combined.energy(plus)-combined.energy(minus))/(2*h)-gradient[i]));
      }
      require(fd<0.07,"combined finite difference "+fd);
      System.out.printf(Locale.ROOT,"ADGBN2_COMBINED_REFINEMENT PASS chemical=%.12f map=%.12f total=%.12f gradient_error=%.8g fd_error=%.8g%n",chemical,mapEnergy,energy,max,fd);
    } finally { if(combined!=null) combined.destroy();else data.destroy(); }
  }
  private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
