// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.xray;

import ffx.potential.bonded.Atom;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnisotropicBFactorRestraintTest {
  private static final double WEIGHT = 0.3;
  private static final double[] U1 = {.18,.23,.31,.017,-.012,.011};
  private static final double[] U2 = {.29,.35,.42,-.008,.015,-.009};

  private Atom atom(int index, double[] u) {
    Atom a = new Atom(index,"C",' ',new double[]{index,0,0},"ALA",1,'A',1,20,"A");
    a.setAnisou(u.clone());
    a.setAnisouGradient(new double[6]);
    return a;
  }

  private double pair(Atom a, Atom b, double weight, boolean gradient) {
    return XRayEnergy.anisotropicBFactorRestraint(a,b,weight,gradient,new double[6],new double[6],new double[6]);
  }

  private double determinant(double[] u) {
    return u[0]*u[1]*u[2] + 2*u[3]*u[4]*u[5]
        - u[0]*u[5]*u[5] - u[1]*u[4]*u[4] - u[2]*u[3]*u[3];
  }

  @Test public void unequalTensorsHaveExpectedPositivePenalty() {
    Atom a=atom(1,U1),b=atom(2,U2);
    double d=determinant(U1)-determinant(U2);
    double expected=Math.pow(8*Math.PI*Math.PI,3)*WEIGHT*d*d;
    double actual=pair(a,b,WEIGHT,false);
    assertTrue("Unequal determinants must have a nonzero restraint",actual>0);
    assertEquals(expected,actual,expected*1e-12);
  }

  @Test public void equalTensorsHaveZeroPenaltyAndGradient() {
    Atom a=atom(1,U1),b=atom(2,U1);
    assertEquals(0,pair(a,b,WEIGHT,true),0);
    assertArrayEquals(new double[6],a.getAnisouGradient(null),0);
    assertArrayEquals(new double[6],b.getAnisouGradient(null),0);
  }

  @Test public void allTwelveGradientComponentsMatchFiniteDifferences() {
    Atom a=atom(1,U1),b=atom(2,U2);
    pair(a,b,WEIGHT,true);
    double[][] gradients={a.getAnisouGradient(null),b.getAnisouGradient(null)};
    Atom[] atoms={a,b};double[][] original={U1,U2};double h=1e-6;
    for(int i=0;i<2;i++)for(int k=0;k<6;k++){
      double[] u=original[i].clone();u[k]+=h;atoms[i].setAnisou(u);
      double plus=pair(a,b,WEIGHT,false);
      u=original[i].clone();u[k]-=h;atoms[i].setAnisou(u);
      double minus=pair(a,b,WEIGHT,false);
      atoms[i].setAnisou(original[i].clone());
      double numerical=(plus-minus)/(2*h);
      assertEquals("atom "+i+" component "+k,numerical,gradients[i][k],Math.max(1e-5,Math.abs(numerical)*1e-7));
    }
    assertTrue("Unequal tensors need a restoring gradient",Math.abs(gradients[0][0])>1e-6);
  }

  @Test public void swappingAtomsPreservesEnergyAndSwapsGradient() {
    Atom a=atom(1,U1),b=atom(2,U2),c=atom(3,U2),d=atom(4,U1);
    assertEquals(pair(a,b,WEIGHT,true),
        pair(c,d,WEIGHT,true),1e-10);
    assertArrayEquals(a.getAnisouGradient(null),d.getAnisouGradient(null),1e-9);
    assertArrayEquals(b.getAnisouGradient(null),c.getAnisouGradient(null),1e-9);
  }

  @Test public void energyOnlyDoesNotMutateTensorsOrGradientBuffers() {
    Atom a=atom(1,U1),b=atom(2,U2);
    double[] g={1,2,3,4,5,6};a.setAnisouGradient(g.clone());b.setAnisouGradient(g.clone());
    pair(a,b,WEIGHT,false);
    assertArrayEquals(U1,a.getAnisou(null),0);assertArrayEquals(U2,b.getAnisou(null),0);
    assertArrayEquals(g,a.getAnisouGradient(null),0);assertArrayEquals(g,b.getAnisouGradient(null),0);
  }
}
