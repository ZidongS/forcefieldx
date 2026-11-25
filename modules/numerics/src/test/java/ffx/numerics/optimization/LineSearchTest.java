// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.numerics.optimization;

import ffx.numerics.OptimizationInterface;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression checks for bounded line search and restoration of rejected states. */
public class LineSearchTest {
  private static OptimizationInterface target(boolean nonfinite) {
    return (OptimizationInterface) Proxy.newProxyInstance(OptimizationInterface.class.getClassLoader(),
        new Class<?>[]{OptimizationInterface.class}, (proxy, method, args) -> {
          if (method.getName().equals("energyAndGradient")) {
            double[] x=(double[])args[0],g=(double[])args[1];
            Arrays.fill(g,nonfinite&&x[0]!=0?Double.NaN:1.0);
            return nonfinite&&x[0]!=0?Double.NaN:0.0;
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }
  @Test(timeout=2000)
  public void flatEnergyCannotRecurseIndefinitely() { checkRestore(false); }
  @Test(timeout=2000)
  public void nonfiniteTrialRestoresCoordinatesAndGradient() { checkRestore(true); }
  private void checkRestore(boolean nonfinite) {
    double[] x={0},g={1};int[] evaluations={0};var info=new LineSearch.LineSearchResult[1];
    double e=new LineSearch(1).search(1,x,0,g,new double[]{-1},new double[1],1,
        info,evaluations,target(nonfinite));
    assertEquals(0,e,0);assertArrayEquals(new double[]{0},x,0);
    assertArrayEquals(new double[]{1},g,0);
    assertEquals(LineSearch.LineSearchResult.BadIntpln,info[0]);
    assertTrue(evaluations[0]<=102);
  }
}
