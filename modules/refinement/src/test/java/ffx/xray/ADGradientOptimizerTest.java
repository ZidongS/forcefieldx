// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.xray;

import ffx.numerics.OptimizationInterface;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ADGradientOptimizerTest {
  private OptimizationInterface target(String mode) {
    return (OptimizationInterface) Proxy.newProxyInstance(OptimizationInterface.class.getClassLoader(),
        new Class<?>[]{OptimizationInterface.class}, (proxy, method, args) -> {
          if (method.getName().equals("getScaling")) return mode.equals("scaled") ? new double[]{1,1,1} : null;
          if (method.getName().equals("energyAndGradient")) {
            double[] x=(double[])args[0],g=(double[])args[1];
            if(mode.equals("nonfinite")&&x[0]!=1){Arrays.fill(g,Double.NaN);return Double.NaN;}
            if(mode.equals("flat")){Arrays.fill(g,1);return 0.;}
            double e=0;for(int i=0;i<3;i++){g[i]=100*x[i];e+=50*x[i]*x[i];}
            return Math.rint((1e8+e)/5)*5;
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }
  @Test public void quantizedEnergyQuadraticConverges() {
    double[] x={1,-.8,.3};var result=ADGradientOptimizer.minimize(target("normal"),x,150,1e-4,.03);
    assertTrue(result.converged());assertTrue(result.gradientRms()<1e-4);
    for(double v:x)assertEquals(0,v,1e-6);
  }
  @Test public void nonfiniteTrialRestoresAcceptedState() {
    double[] x={1,0,0};var result=ADGradientOptimizer.minimize(target("nonfinite"),x,5,.001,.03);
    assertFalse(result.converged());assertEquals("line_search_stalled",result.reason());
    assertArrayEquals(new double[]{1,0,0},x,0);
  }
  @Test public void flatEnergyWithNonzeroGradientIsNotConvergence() {
    var result=ADGradientOptimizer.minimize(target("flat"),new double[]{0,0,0},5,.001,.03);
    assertFalse(result.converged());assertEquals(5,result.iterations());
  }
  @Test public void listenerCanTerminateWithoutMoving() {
    double[] x={1,0,0};int[] updates={0};
    var result=ADGradientOptimizer.minimize(target("normal"),x,10,.001,.03,3,
        (i,m,n,g,r,e,d,a,info)->{updates[0]++;return false;});
    assertEquals("terminated",result.reason());assertFalse(result.converged());
    assertEquals(1,updates[0]);assertArrayEquals(new double[]{1,0,0},x,0);
  }
  @Test(expected=IllegalArgumentException.class) public void rejectsScaledTarget() {
    ADGradientOptimizer.minimize(target("scaled"),new double[]{0,0,0},5,.001,.03);
  }
  @Test(expected=IllegalArgumentException.class) public void rejectsInfiniteTolerance() {
    ADGradientOptimizer.minimize(target("normal"),new double[]{0,0,0},5,Double.POSITIVE_INFINITY,.03);
  }
}
