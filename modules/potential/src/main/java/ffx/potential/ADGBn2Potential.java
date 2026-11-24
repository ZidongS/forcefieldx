// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.potential;

import ffx.numerics.Constraint;
import ffx.numerics.Potential;
import ffx.potential.bonded.Atom;
import ffx.potential.parameters.ForceField;
import java.util.Collections;
import java.util.List;

/** AD vacuum plus GBn2 mechanical embedding for coordinate refinement. */
public final class ADGBn2Potential implements Potential {
  private final Atom[] atoms;
  private final Atom[] activeAtoms;
  private final AlphaDynamicsClient client;
  private final double[] mass;
  private final double[] velocity;
  private final double[] acceleration;
  private final double[] previousAcceleration;
  private double[] scaling;
  private double totalEnergy;
  private boolean closed;

  public ADGBn2Potential(Atom[] atoms, ForceField forceField) {
    this.atoms = atoms.clone();
    this.activeAtoms = java.util.Arrays.stream(atoms).filter(Atom::isActive).toArray(Atom[]::new);
    if (activeAtoms.length == 0) throw new IllegalArgumentException("AD+GBn2 requires active atoms");
    mass = new double[activeAtoms.length * 3];
    velocity = new double[mass.length];
    acceleration = new double[mass.length];
    previousAcceleration = new double[mass.length];
    for (int i = 0; i < activeAtoms.length; i++) {
      double m = activeAtoms[i].getMass();
      mass[3*i] = mass[3*i+1] = mass[3*i+2] = m;
    }
    client = new AlphaDynamicsClient(forceField);
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("AD+GBn2 potential is closed");
    int active = 0;
    for (Atom atom : atoms) {
      if (atom.isActive()) {
        if (active >= activeAtoms.length || activeAtoms[active++] != atom)
          throw new IllegalStateException("Active atom selection changed; recreate the AD potential");
      }
    }
    if (active != activeAtoms.length)
      throw new IllegalStateException("Active atom selection changed; recreate the AD potential");
  }

  @Override public double energy(double[] x) { return energy(x, false); }

  @Override public double energy(double[] x, boolean verbose) {
    setCoordinates(unscale(x));
    totalEnergy = client.evaluate(atoms, false);
    if (verbose) System.err.printf("AD+GBn2: %.8f kcal/mol%n", totalEnergy);
    return totalEnergy;
  }

  @Override public double energyAndGradient(double[] x, double[] g) {
    if (g == null || g.length < getNumberOfVariables()) {
      throw new IllegalArgumentException("Gradient array is too small");
    }
    java.util.Arrays.fill(g, 0.0);
    setCoordinates(unscale(x));
    totalEnergy = client.evaluate(atoms, true);
    double[] xyzGradient = new double[3];
    for (int i = 0; i < activeAtoms.length; i++) {
      activeAtoms[i].getXYZGradient(xyzGradient);
      for (int j = 0; j < 3; j++) {
        int k = 3 * i + j;
        g[k] = scaling == null ? xyzGradient[j] : xyzGradient[j] / scaling[k];
      }
    }
    return totalEnergy;
  }

  @Override public double[] getCoordinates(double[] x) {
    int n = getNumberOfVariables();
    if (x == null || x.length < n) x = new double[n];
    int j = 0;
    for (Atom atom : activeAtoms) {
      x[j++] = atom.getX(); x[j++] = atom.getY(); x[j++] = atom.getZ();
    }
    return x;
  }

  @Override public void setCoordinates(double[] x) {
    ensureOpen();
    if (x == null || x.length < getNumberOfVariables()) {
      throw new IllegalArgumentException("Coordinate array is too small");
    }
    int j = 0;
    for (Atom atom : activeAtoms) {
      double a = x[j++], b = x[j++], c = x[j++];
      if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c))
        throw new IllegalArgumentException("Nonfinite coordinate");
      atom.moveTo(a, b, c);
    }
  }

  @Override public int getNumberOfVariables() { return activeAtoms.length * 3; }
  @Override public double[] getScaling() { return scaling == null ? null : scaling.clone(); }
  @Override public void setScaling(double[] scaling) {
    if (scaling == null) { this.scaling = null; return; }
    if (scaling.length != getNumberOfVariables()) throw new IllegalArgumentException("Scaling length mismatch");
    for (double v : scaling) if (!Double.isFinite(v) || v <= 0.0) throw new IllegalArgumentException("Scaling values must be finite and positive");
    this.scaling = scaling.clone();
  }
  @Override public double getTotalEnergy() { return totalEnergy; }
  @Override public double[] getMass() { return mass.clone(); }
  @Override public double[] getVelocity(double[] x) { return copy(velocity, x); }
  @Override public double[] getAcceleration(double[] x) { return copy(acceleration, x); }
  @Override public double[] getPreviousAcceleration(double[] x) { return copy(previousAcceleration, x); }
  @Override public void setVelocity(double[] x) { assign(velocity, x); }
  @Override public void setAcceleration(double[] x) { assign(acceleration, x); }
  @Override public void setPreviousAcceleration(double[] x) { assign(previousAcceleration, x); }
  @Override public VARIABLE_TYPE[] getVariableTypes() {
    VARIABLE_TYPE[] types = new VARIABLE_TYPE[getNumberOfVariables()];
    for (int i = 0; i < types.length; i += 3) {
      types[i] = VARIABLE_TYPE.X; types[i+1] = VARIABLE_TYPE.Y; types[i+2] = VARIABLE_TYPE.Z;
    }
    return types;
  }
  @Override public STATE getEnergyTermState() { return STATE.BOTH; }
  @Override public void setEnergyTermState(STATE state) {
    if (state != STATE.BOTH) throw new IllegalArgumentException("AD+GBn2 has no RESPA split");
  }
  @Override public List<Constraint> getConstraints() { return Collections.emptyList(); }
  @Override public boolean destroy() { close(); return true; }

  private static double[] copy(double[] src, double[] dst) {
    if (dst == null || dst.length < src.length) dst = new double[src.length];
    System.arraycopy(src, 0, dst, 0, src.length); return dst;
  }
  private static void assign(double[] dst, double[] src) {
    if (src == null || src.length < dst.length) throw new IllegalArgumentException("Array too small");
    System.arraycopy(src, 0, dst, 0, dst.length);
  }
  private double[] unscale(double[] x) {
    if (x == null || x.length < getNumberOfVariables()) throw new IllegalArgumentException("Coordinate array is too small");
    if (scaling == null) return x;
    double[] physical = x.clone();
    for (int i = 0; i < getNumberOfVariables(); i++) physical[i] /= scaling[i];
    return physical;
  }
  private void close() { if (!closed) { closed = true; client.close(); } }
}
