package ffx.potential;

import edu.rit.pj.ParallelTeam;
import ffx.crystal.Crystal;
import ffx.numerics.optimization.LBFGS;
import ffx.potential.bonded.Atom;
import ffx.potential.parameters.AtomType;
import ffx.potential.parameters.VDWType;
import ffx.potential.parameters.ForceField;
import ffx.realspace.RealSpaceData;
import ffx.realspace.parsers.RealSpaceFile;
import ffx.xray.*;
import ffx.xray.parsers.DiffractionFile;
import ffx.xray.refine.RefinementMode;
import ffx.xray.solvent.SolventModel;
import org.apache.commons.configuration2.CompositeConfiguration;
import java.nio.file.*;
import java.util.*;

/** Coordinate-only FFX refinement preserving the prepared GB atom identity. */
public final class FFXRefine71Retry {
  public static void main(String[] args) {
    try { run(args);System.exit(0); }
    catch(Throwable error){error.printStackTrace();System.exit(1);}
  }
  private static void run(String[] args) throws Throwable {
    Path ffx=Path.of(args[0]), prep=Path.of(args[1]), dataFile=Path.of(args[2]), out=Path.of(args[3]);
    String group=args[4];int iterations=Integer.parseInt(args[5]);
    Files.createDirectories(out);
    List<String> pdb=Files.readAllLines(prep.resolve("prepared.pdb"));
    List<String> records=pdb.stream().filter(l->l.startsWith("ATOM  ")||l.startsWith("HETATM")).toList();
    Atom[] atoms=new Atom[records.size()];
    for(int i=0;i<atoms.length;i++) {
      String l=String.format("%-80s",records.get(i));
      String symbol=l.substring(76,78).trim();
      int z=atomicNumber(symbol);double mass=mass(z);
      atoms[i]=new Atom(i+1,l.substring(12,16).trim(),l.charAt(16),new double[]{number(l,30,38),number(l,38,46),number(l,46,54)},l.substring(17,20).trim(),Integer.parseInt(l.substring(22,26).trim()),l.charAt(21),number(l,54,60),number(l,60,66),l.substring(72,76).trim());
      atoms[i].setAtomType(new AtomType(z,z,symbol,"AD element identity",z,mass,0));
      // Bondi radii (diameters in FFX): diffraction support and solvent mask only.
      // Zero well depth: the chemical target remains exclusively AD+GBn2.
      atoms[i].setVDWType(new VDWType(z,2.0*bondiRadius(z),0.0,1.0));
      atoms[i].setActive(true);
    }
    Map<Integer,Atom> serials=new HashMap<>();
    for(int i=0;i<atoms.length;i++)serials.put(Integer.parseInt(records.get(i).substring(6,11).trim()),atoms[i]);
    for(String l:pdb)if(l.startsWith("ANISOU")){
      Atom atom=serials.get(Integer.parseInt(l.substring(6,11).trim()));
      if(atom!=null){double[] u=new double[6];for(int j=0;j<6;j++)u[j]=number(l,28+7*j,35+7*j)*1e-4;atom.setAnisou(u);}
    }
    CompositeConfiguration props=new CompositeConfiguration();
    props.setProperty("riding-hydrogen-bfactor",false);
    double targetWeight=Double.parseDouble(System.getProperty("ffx71.weight","0.05"));
    props.setProperty("data-weight",targetWeight);props.setProperty("xweight",targetWeight);
    props.setProperty("solvent-grid-search",false);
    props.setProperty("scattering-buffer",4.0);
    props.setProperty("sampling",Double.parseDouble(System.getProperty("ffx71.sampling","0.6")));
    for(String l:pdb) if(l.startsWith("CRYST1")) {
      l=String.format("%-80s",l);
      String[] keys={"a-axis","b-axis","c-axis","alpha","beta","gamma"};
      int[] starts={6,15,24,33,40,47},ends={15,24,33,40,47,54};
      for(int j=0;j<6;j++)props.setProperty(keys[j],number(l,starts[j],ends[j]));
      props.setProperty("spacegroup",l.substring(55,66).trim());break;
    }
    Crystal crystal=Crystal.checkProperties(props);
    if(crystal==null)throw new IllegalArgumentException("Missing crystal");
    ForceField ff=new ForceField(props);
    ff.addProperty("AD_GBN2","true");
    ff.addProperty("AD_PYTHON","/home/shuzidong/miniconda3/envs/AD/bin/python");
    ff.addProperty("AD_WORKER",ffx.resolve("python/ad_gbn2_worker.py").toString());
    ff.addProperty("AD_TOOLKIT","/home/shuzidong/workfile/alphadynamics/zidong_analysis_toolkit");
    ff.addProperty("AD_PARAMETERS",prep.resolve("parameters.json").toString());
    ff.addProperty("AD_CHARGE",Files.readString(prep.resolve("charge.txt")).trim());
    ParallelTeam team=new ParallelTeam(1);
    MolecularAssembly assembly=new MolecularAssembly("AD benchmark") {
      @Override public Atom[] getAtomArray(){return atoms.clone();}
      @Override public Atom[] getActiveAtomArray(){return atoms.clone();}
      @Override public List<Atom> getAtomList(){return Arrays.asList(atoms);}
      @Override public Crystal getCrystal(){return crystal;}
      @Override public ParallelTeam getParallelTeam(){return team;}
    };
    assembly.setForceField(ff);assembly.setFile(prep.resolve("prepared.pdb").toFile());
    DataContainer data;
    if(group.equals("cryoem")) {
      data=new RealSpaceData(assembly,props,team,new RealSpaceFile(dataFile.toString()));
    } else {
      DiffractionData diffraction=new DiffractionData(new MolecularAssembly[]{assembly},props,SolventModel.valueOf(System.getProperty("ffx71.solvent","POLYNOMIAL")),new DiffractionFile(dataFile.toString())) {
        @Override public String printEnergyUpdate(){return printOptimizationUpdate();}
      };
      diffraction.getRefinementModel().setRefinementMode(RefinementMode.COORDINATES);
      diffraction.scaleBulkFit();diffraction.printStats();
      Files.writeString(out.resolve("initial_data_fit.txt"),diffraction.printOptimizationUpdate());
      data=diffraction;
    }
    RefinementEnergy energy=null;Throwable pendingFailure=null;
    try {
      energy=new RefinementEnergy(data);
      double[] x=energy.getCoordinates(null), initial=x.clone(),g=new double[x.length];
      System.out.println("FFX71_STAGE initial_gradient nvars="+x.length);
      double start=energy.energyAndGradient(x,g);
      double grms=0;for(double v:g){if(!Double.isFinite(v))throw new ArithmeticException("Nonfinite gradient");grms+=v*v;}
      // Check the descent direction of the full target; avoids cancellation in a random direction.
      double[] plus=x.clone(),minus=x.clone(),direction=new double[x.length];double h=0.001;
      double analytic=0, maxGradient=0;for(double v:g)maxGradient=Math.max(maxGradient,Math.abs(v));
      for(int i=0;i<x.length;i++){direction[i]=g[i]/Math.max(1.0,maxGradient);plus[i]+=h*direction[i];minus[i]-=h*direction[i];analytic+=g[i]*direction[i];}
      double numerical=Double.NaN,error=Double.POSITIVE_INFINITY;
      // Try successively smaller steps: AD neighbor lists can change at a finite step,
      // while very small displacements reach the precision limit of the exported model.
      for(double step:new double[]{0.001,0.0003,0.0001,0.003,0.01}){
        for(int j=0;j<x.length;j++){plus[j]=initial[j]+step*direction[j];minus[j]=initial[j]-step*direction[j];}
        double candidate=(energy.energy(plus)-energy.energy(minus))/(2*step);
        double candidateError=Math.abs(candidate-analytic);
        System.out.printf(Locale.ROOT,"FFX71_FD h=%g analytic=%g numerical=%g error=%g%n",step,analytic,candidate,candidateError);
        if(candidateError<error){numerical=candidate;error=candidateError;h=step;}
        if(error<=0.15+0.02*Math.abs(analytic))break;
      }
      if(error>0.15+0.02*Math.abs(analytic)){
        data.setWeight(0.0);energy.setCoordinates(initial);double[] cg=new double[x.length];double ce=energy.energyAndGradient(initial.clone(),cg);double ca=0;for(int j=0;j<cg.length;j++)ca+=cg[j]*direction[j];
        for(double step:new double[]{0.03,0.01,0.003,0.001,0.0003,0.0001,0.00003}){double[] xp=initial.clone(),xm=initial.clone();for(int j=0;j<xp.length;j++){xp[j]+=step*direction[j];xm[j]-=step*direction[j];} double cn=(energy.energy(xp.clone())-energy.energy(xm.clone()))/(2*step);data.setWeight(targetWeight);double tn=(energy.energy(xp.clone())-energy.energy(xm.clone()))/(2*step);double dn=(energy.getDataEnergy().energy(xp.clone())-energy.getDataEnergy().energy(xm.clone()))/(2*step);System.out.printf("FD_DATA_DIRECT=%g%n",dn);data.setWeight(0.0);System.out.printf(Locale.ROOT,"FD_DIAGNOSTIC h=%g chemical_analytic=%g chemical_numeric=%g data_analytic=%g data_numeric=%g%n",step,ca,cn,analytic-ca,tn-cn);}
        throw new AssertionError("Directional derivative mismatch "+error+" numerical "+numerical+" analytic "+analytic);}
      energy.setCoordinates(initial);
      double weight=targetWeight;
      data.setWeight(weight);
      start=energy.energyAndGradient(x,g);
      int status=LBFGS.minimize(x.length,0,x,start,g,1.0,iterations,energy,null);
      double end=energy.energyAndGradient(x,g),rms=0;
      for(int i=0;i<x.length;i++)rms+=(x[i]-initial[i])*(x[i]-initial[i]);
      if(!Double.isFinite(end)||end>start+1e-5||status<0)throw new AssertionError("Refinement failed: "+status+" start "+start+" end "+end);
      writePdb(pdb,atoms,out.resolve("refined.pdb"));
      String fit="";
      if(data instanceof DiffractionData diffraction){diffraction.scaleBulkFit();diffraction.printStats();fit=diffraction.printOptimizationUpdate();Files.writeString(out.resolve("final_data_fit.txt"),fit);}
      String result=String.format(Locale.ROOT,"{\"state\":\"refined\",\"natoms\":%d,\"initial_energy_kcal_mol\":%.12f,\"final_energy_kcal_mol\":%.12f,\"initial_grms\":%.8g,\"directional_fd_error\":%.8g,\"lbfgs_status\":%d,\"rms_displacement_A\":%.8g,\"max_iterations\":%d,\"data_weight\":%.4f}%n",atoms.length,start,end,Math.sqrt(grms/g.length),error,status,Math.sqrt(rms/atoms.length),iterations,weight);
      double finalGrms=0;for(double v:g)finalGrms+=v*v;
      result=result.stripTrailing();result=result.substring(0,result.length()-1)+String.format(Locale.ROOT,",\"final_grms\":%.8g,\"gradient_tolerance\":1.0,\"converged\":%s,\"directional_analytic\":%.8g,\"directional_numerical\":%.8g,\"fd_step_A\":%.8g}%n",Math.sqrt(finalGrms/g.length),status==0,analytic,numerical,h);
      result=result.stripTrailing();result=result.substring(0,result.length()-1)+",\"optimizer\":\"steepest_descent\",\"lbfgs_memory\":0}\n";
      Files.writeString(out.resolve("refinement.json"),result);System.out.println("FFX71_PASS "+result);
    } catch(Throwable failure){pendingFailure=failure;} finally {
      if(pendingFailure==null){
        if(energy!=null)energy.destroy();
        else if(data instanceof RealSpaceData real)real.destroy();
        else if(data instanceof DiffractionData diffraction)diffraction.destroy();
        team.shutdown();
      }
    }
    if(pendingFailure!=null)throw pendingFailure;
  }
  static double number(String l,int a,int b){return Double.parseDouble(l.substring(a,b).trim());}
  static int atomicNumber(String s){return switch(s.trim().toUpperCase()){case "H","D"->1;case "C"->6;case "N"->7;case "O"->8;case "F"->9;case "P"->15;case "S"->16;case "CL"->17;case "SE"->34;case "ZN"->30;case "MG"->12;case "CA"->20;case "FE"->26;default->throw new IllegalArgumentException("Unsupported element "+s);};}
  static double bondiRadius(int z){return switch(z){case 1->1.20;case 6->1.70;case 7->1.55;case 8->1.52;case 9->1.47;case 15->1.80;case 16->1.80;case 17->1.75;case 34->1.90;case 30->1.39;case 12->1.73;case 20->2.31;case 26->2.0;default->throw new IllegalArgumentException("No mask radius for "+z);};}
  static double mass(int z){return switch(z){case 1->1.008;case 6->12.011;case 7->14.007;case 8->15.999;case 9->18.998;case 15->30.974;case 16->32.06;case 17->35.45;case 34->78.971;default->2.0*z;};}
  static void writePdb(List<String> lines,Atom[] atoms,Path out)throws Exception {
    List<String> result=new ArrayList<>();int i=0;
    for(String original:lines){
      if(original.startsWith("ATOM  ")||original.startsWith("HETATM")){
        String l=String.format("%-80s",original);Atom a=atoms[i++];
        result.add(l.substring(0,30)+String.format(Locale.ROOT,"%8.3f%8.3f%8.3f",a.getX(),a.getY(),a.getZ())+l.substring(54));
      }else result.add(original);
    }Files.write(out,result);
  }
}
