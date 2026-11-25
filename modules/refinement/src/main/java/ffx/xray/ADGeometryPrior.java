// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.xray;

import ffx.potential.bonded.Atom;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Optional monomer-library prior (heavy atoms by default), separate from AD+GBn2.
 * The worker returns a dimensionless squared-z penalty; weight converts it to kcal/mol.
 */
public final class ADGeometryPrior implements AutoCloseable {
  private final Process process;
  private final BufferedWriter input;
  private final BufferedReader output;
  private final ExecutorService reader;
  private final int n;
  private final double weight;
  private boolean closed;

  public ADGeometryPrior(String python, String worker, String referencePdb, Atom[] atoms,
      double weight, boolean nonbonded) throws IOException {
    this(python, worker, referencePdb, atoms, weight, nonbonded, false);
  }

  /** Optionally include interpreted hydrogen restraints; the original constructor uses heavy atoms. */
  public ADGeometryPrior(String python, String worker, String referencePdb, Atom[] atoms,
      double weight, boolean nonbonded, boolean hydrogens) throws IOException {
    if (atoms == null || atoms.length == 0 || python == null || python.isBlank()
        || worker == null || worker.isBlank() || referencePdb == null || referencePdb.isBlank()
        || !Double.isFinite(weight) || weight <= 0) {
      throw new IllegalArgumentException("Geometry prior requires atoms, worker, reference and a positive finite weight");
    }
    n = atoms.length * 3;
    this.weight = weight;
    List<String> command = new ArrayList<>(List.of(python, "-u", worker, "--pdb", referencePdb));
    if (nonbonded) command.add("--nonbonded");
    if (hydrogens) command.add("--hydrogens");
    ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
    builder.environment().put("OMP_NUM_THREADS", "1");
    builder.environment().put("OPENBLAS_NUM_THREADS", "1");
    process = builder.start();
    input = process.outputWriter();
    output = process.inputReader();
    reader = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "ad-geometry-reader");
      thread.setDaemon(true);
      return thread;
    });
    try {
      if (!readLine().equals("GEOMETRY 1 READY " + atoms.length)) {
        throw new IOException("Geometry atom-count/protocol mismatch");
      }
      input.write("INIT " + atoms.length + "\n");
      for (Atom atom : atoms) {
        if (atom == null || !atom.isActive()) throw new IllegalArgumentException("All geometry atoms must be active");
        input.write(atom.getAtomicNumber() + " " + atom.getName() + " " + atom.getX()
            + " " + atom.getY() + " " + atom.getZ() + "\n");
      }
      input.flush();
      if (!readLine().equals("INITIALIZED")) throw new IOException("Geometry reference validation failed");
    } catch (IOException | RuntimeException error) {
      close();
      throw error;
    }
  }

  private String readLine() throws IOException {
    Future<String> future = reader.submit(output::readLine);
    try {
      String line = future.get(120, TimeUnit.SECONDS);
      if (line == null) throw new EOFException("Geometry worker exited");
      return line;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new IOException("Interrupted geometry worker", error);
    } catch (ExecutionException | TimeoutException error) {
      future.cancel(true);
      throw new IOException("Geometry worker failed or timed out", error);
    }
  }

  /** Return only the weighted additional prior, without evaluating AD or experimental data. */
  public synchronized double energy(double[] coordinates) {
    return evaluate(coordinates, null);
  }

  /** Return the prior energy and overwrite gradient with its Cartesian gradient. */
  public synchronized double energyAndGradient(double[] coordinates, double[] gradient) {
    if (gradient == null || gradient.length != n) throw new IllegalArgumentException("Geometry gradient length mismatch");
    return evaluate(coordinates, gradient);
  }

  private double evaluate(double[] coordinates, double[] gradient) {
    if (closed) throw new IllegalStateException("Geometry prior is closed");
    if (coordinates == null || coordinates.length != n) throw new IllegalArgumentException("Geometry coordinate length mismatch");
    for (double v : coordinates) if (!Double.isFinite(v)) throw new IllegalArgumentException("Nonfinite geometry coordinate");
    try {
      input.write("EVAL " + n / 3 + "\n");
      for (int i = 0; i < n; i += 3) {
        input.write(coordinates[i] + " " + coordinates[i + 1] + " " + coordinates[i + 2] + "\n");
      }
      input.flush();
      String[] fields = readLine().split(" ");
      if (fields.length != n + 2 || !fields[0].equals("RESULT")) throw new IOException("Invalid geometry response");
      double energy = Double.parseDouble(fields[1]);
      if (!Double.isFinite(energy)) throw new IOException("Nonfinite geometry energy");
      double[] values = new double[n];
      for (int i = 0; i < n; i++) {
        values[i] = weight * Double.parseDouble(fields[i + 2]);
        if (!Double.isFinite(values[i])) throw new IOException("Nonfinite geometry gradient");
      }
      if (gradient != null) System.arraycopy(values, 0, gradient, 0, n);
      if (!Double.isFinite(weight * energy)) throw new IOException("Nonfinite weighted geometry energy");
      return weight * energy;
    } catch (IOException | NumberFormatException error) {
      close();
      throw new IllegalStateException("AD geometry prior failed; no fallback applied", error);
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    try { input.write("QUIT\n"); input.flush(); } catch (IOException ignored) { }
    process.destroy();
    try { if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly(); }
    catch (InterruptedException error) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
    reader.shutdownNow();
    try { input.close(); } catch (IOException ignored) { }
    try { output.close(); } catch (IOException ignored) { }
  }
}
