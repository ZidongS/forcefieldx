// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.xray;

import ffx.numerics.OptimizationInterface;
import ffx.numerics.optimization.OptimizationListener;
import ffx.numerics.optimization.LineSearch.LineSearchResult;
import java.util.ArrayList;
import java.util.List;

/** L-BFGS with bounded directional-gradient search for quantized ML energies. */
public final class ADGradientOptimizer {
  private ADGradientOptimizer() {}
  public record Result(int iterations, int evaluations, double energy, double gradientRms,
                       boolean converged, String reason) {}

  /** Coordinates are unscaled Angstroms; tolerance is never relaxed on stalled searches. */
  public static Result minimize(OptimizationInterface potential, double[] x, int maxIterations,
                                double tolerance, double maxAtomStep) {
    return minimize(potential, x, maxIterations, tolerance, maxAtomStep, 7, null);
  }

  /** Bounded Cartesian optimization with an optional progress/termination listener. */
  public static Result minimize(OptimizationInterface potential, double[] x, int maxIterations,
      double tolerance, double maxAtomStep, int memory, OptimizationListener listener) {
    if (memory < 0) throw new IllegalArgumentException("Negative L-BFGS memory");
    if (potential == null || potential.getScaling() != null || x == null || x.length == 0 || x.length % 3 != 0 || maxIterations < 1 || !(tolerance > 0)
        || !(maxAtomStep > 0) || !Double.isFinite(tolerance) || !Double.isFinite(maxAtomStep)) throw new IllegalArgumentException("Invalid optimizer settings");
    for (double coordinate : x) if (!Double.isFinite(coordinate)) throw new IllegalArgumentException("Nonfinite initial coordinate");
    int n=x.length, evaluations=1;
    double[] g=new double[n];double e=potential.energyAndGradient(x,g);
    if (!finite(e,g)) throw new ArithmeticException("Nonfinite initial target");
    List<double[]> ss=new ArrayList<>(),ys=new ArrayList<>();
    double rms=rms(g);
    if (listener != null && !listener.optimizationUpdate(0, memory, evaluations, rms,
        0, e, 0, 0, null)) return new Result(0, evaluations, e, rms, false, "terminated");
    for(int iter=0;iter<maxIterations;iter++) {
      if(rms<=tolerance)return new Result(iter,evaluations,e,rms,true,"gradient_tolerance");
      double[] p=memory==0?g.clone():direction(g,ss,ys);
      if(memory==0)for(int j=0;j<n;j++)p[j]=-p[j];double slope=dot(g,p);
      if(!Double.isFinite(slope)||slope>=-1e-12*dot(g,g)) {
        ss.clear();ys.clear();for(int j=0;j<n;j++)p[j]=-g[j];slope=-dot(g,g);
      }
      double largest=0;for(int j=0;j<n;j+=3)largest=Math.max(largest,Math.sqrt(p[j]*p[j]+p[j+1]*p[j+1]+p[j+2]*p[j+2]));
      double alpha=Math.min(1.0,maxAtomStep/largest);
      double[] trial=new double[n],ng=new double[n];double ne=Double.NaN;boolean accepted=false;
      for(int search=0;search<12;search++) {
        for(int j=0;j<n;j++)trial[j]=x[j]+alpha*p[j];
        try {
          ne=potential.energyAndGradient(trial,ng);evaluations++;
        } catch (RuntimeException | Error failure) {
          try { potential.energyAndGradient(x,g); }
          catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
          throw failure;
        }
        double newSlope=dot(ng,p);
        // Accept by the directional derivative, without subtracting quantized total energies.
        // Allow a small derivative overshoot near the directional minimum.
        // At the displacement cap a negative derivative can still indicate descent.
        if(finite(ne,ng)&&newSlope<=0.1*Math.abs(slope)) {accepted=true;break;}
        alpha*=0.5;
      }
      if(!accepted) {
        // Restore both coordinates and potential state to the last accepted point.
        e=potential.energyAndGradient(x,g);evaluations++;
        if (!finite(e,g)) throw new ArithmeticException("Nonfinite restored target");
        return new Result(iter,evaluations,e,rms(g),false,"line_search_stalled");
      }
      double[] s=new double[n],y=new double[n];
      for(int j=0;j<n;j++){s[j]=trial[j]-x[j];y[j]=ng[j]-g[j];}
      double sy=dot(s,y);
      if(memory>0&&sy>1e-8*Math.sqrt(dot(s,s)*dot(y,y))&&sy>1e-12){
        if(ss.size()==memory){ss.remove(0);ys.remove(0);}ss.add(s);ys.add(y);
      } else {ss.clear();ys.clear();}
      double oldEnergy=e;
      System.arraycopy(trial,0,x,0,n);System.arraycopy(ng,0,g,0,n);e=ne;rms=rms(g);
      if (listener != null && !listener.optimizationUpdate(iter+1, memory, evaluations,
          rms, Math.sqrt(dot(s,s)/n), e, oldEnergy-e, 0, LineSearchResult.Success)) {
        return new Result(iter+1,evaluations,e,rms,rms<=tolerance,"terminated");
      }
    }
    return new Result(maxIterations,evaluations,e,rms,rms<=tolerance,rms<=tolerance?"gradient_tolerance":"iteration_limit");
  }
  private static double[] direction(double[] g,List<double[]> ss,List<double[]> ys){
    double[] q=g.clone();int m=ss.size();double[] a=new double[m],rho=new double[m];
    for(int i=m-1;i>=0;i--){rho[i]=1/dot(ss.get(i),ys.get(i));a[i]=rho[i]*dot(ss.get(i),q);axpy(q,ys.get(i),-a[i]);}
    double scale=m==0?0.002:dot(ss.get(m-1),ys.get(m-1))/dot(ys.get(m-1),ys.get(m-1));
    scale=Math.max(1e-6,Math.min(.1,scale));for(int j=0;j<q.length;j++)q[j]*=scale;
    for(int i=0;i<m;i++){double b=rho[i]*dot(ys.get(i),q);axpy(q,ss.get(i),a[i]-b);}
    for(int j=0;j<q.length;j++)q[j]=-q[j];return q;
  }
  private static void axpy(double[] x,double[] y,double a){for(int j=0;j<x.length;j++)x[j]+=a*y[j];}
  private static double dot(double[] x,double[] y){double d=0;for(int j=0;j<x.length;j++)d+=x[j]*y[j];return d;}
  private static double rms(double[] g){return Math.sqrt(dot(g,g)/g.length);}
  private static boolean finite(double e,double[] g){if(!Double.isFinite(e))return false;for(double v:g)if(!Double.isFinite(v))return false;return true;}
}
