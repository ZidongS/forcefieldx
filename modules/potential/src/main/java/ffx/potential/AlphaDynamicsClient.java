// SPDX-License-Identifier: GPL-3.0-or-later
package ffx.potential;

import ffx.potential.bonded.Atom;
import ffx.potential.parameters.ForceField;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Persistent, process-isolated AD+GBn2 evaluator with full AUX charge response. */
public final class AlphaDynamicsClient implements AutoCloseable {
  // Exact SI eV and thermochemical kcal, per mole.
  static final double EV_TO_KCAL_MOL = 1.602176634e-19 * 6.02214076e23 / 4184.0;
  private final Process process;
  private final BufferedReader input;
  private final BufferedWriter output;
  private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "AD-GBn2-protocol"); t.setDaemon(true); return t;
  });
  private final long timeout;
  private boolean closed;

  public AlphaDynamicsClient(ForceField ff) {
    timeout = ff.getInteger("AD_TIMEOUT", 1800);
    if (timeout <= 0) throw new IllegalArgumentException("AD_TIMEOUT must be positive");
    List<String> command = new ArrayList<>();
    Collections.addAll(command, required(ff, "AD_PYTHON"), "-u", required(ff, "AD_WORKER"),
        "--toolkit", required(ff, "AD_TOOLKIT"));
    String params = ff.getString("AD_PARAMETERS", null);
    String topology = ff.getString("AD_PRMTOP", null);
    if ((params == null) == (topology == null)) {
      throw new IllegalArgumentException("Supply exactly one of AD_PARAMETERS and AD_PRMTOP");
    }
    Collections.addAll(command, params == null ? "--prmtop" : "--parameters", params == null ? topology : params);
    String charge = ff.getString("AD_CHARGE", null);
    if (charge != null) Collections.addAll(command, "--charge", charge);
    Collections.addAll(command, "--platform", ff.getString("AD_GB_PLATFORM", "CUDA"));
    Process started = null;
    try {
      ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
      builder.environment().put("PYTHONNOUSERSITE", "1");
      process = builder.start();
      started = process;
      input = process.inputReader(StandardCharsets.UTF_8);
      output = process.outputWriter(StandardCharsets.UTF_8);
      if (!"ADGBN2 1 READY".equals(readLine())) throw new IOException("Invalid worker handshake");
    } catch (Exception e) {
      if (started != null) started.destroyForcibly();
      reader.shutdownNow();
      throw new IllegalStateException("Could not initialize AD+GBn2 worker", e);
    }
  }

  private static String required(ForceField ff, String key) {
    String value = ff.getString(key, null);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }

  private String readLine() throws Exception {
    Future<String> future = reader.submit(input::readLine);
    try {
      String line = future.get(timeout, TimeUnit.SECONDS);
      if (line == null) throw new EOFException("AD worker exited; inspect stderr");
      return line;
    } catch (Exception e) {
      future.cancel(true);
      process.destroyForcibly();
      throw e;
    }
  }

  public synchronized double evaluate(Atom[] atoms, boolean gradient) {
    if (closed) throw new IllegalStateException("AD worker is closed");
    try {
      output.write("EVAL " + atoms.length + "\n");
      for (Atom atom : atoms) {
        String symbol = element(atom.getAtomicNumber());
        String name = atom.getName();
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
          throw new IllegalArgumentException("Atom name must be one nonempty token");
        }
        double x = atom.getX(), y = atom.getY(), z = atom.getZ();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
          throw new IllegalArgumentException("Nonfinite atomic coordinate");
        }
        output.write(symbol + " " + name + " " + x + " " + y + " " + z + "\n");
      }
      output.flush();
      String[] fields = readLine().trim().split("\\s+");
      if (fields.length != 2 + 3 * atoms.length || !fields[0].equals("RESULT")) {
        throw new IOException("Invalid AD result length or tag");
      }
      double[] values = new double[fields.length - 1];
      for (int i = 0; i < values.length; i++) {
        values[i] = Double.parseDouble(fields[i + 1]);
        if (!Double.isFinite(values[i])) throw new IOException("Nonfinite AD result");
      }
      if (gradient) {
        for (int i = 0; i < atoms.length; i++) {
          atoms[i].setXYZGradient(-values[1 + 3*i] * EV_TO_KCAL_MOL,
              -values[2 + 3*i] * EV_TO_KCAL_MOL, -values[3 + 3*i] * EV_TO_KCAL_MOL);
        }
      }
      return values[0] * EV_TO_KCAL_MOL;
    } catch (Exception e) {
      close();
      throw new IllegalStateException("AD+GBn2 evaluation failed; no classical fallback", e);
    }
  }

  private static String element(int z) {
    String[] elements = {"", "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I"};
    if (z < 1 || z >= elements.length) throw new IllegalArgumentException("Unsupported atomic number " + z);
    return elements[z];
  }

  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    try { output.write("QUIT\n"); output.flush(); } catch (IOException ignored) { }
    try { if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
    try { output.close(); input.close(); } catch (IOException ignored) { }
    reader.shutdownNow();
  }
}
