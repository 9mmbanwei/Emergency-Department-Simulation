package edsim.engine;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads simulation parameters from {@code config.properties} and applies
 * any command-line overrides.
 *
 * <p><b>CLI usage:</b></p>
 * <pre>
 *   java -cp out Main [options]
 *
 *   --config &lt;path&gt;          path to config file (default: config.properties)
 *   --arrivalRate &lt;double&gt;   patients per hour
 *   --doctors &lt;int&gt;          number of doctors
 *   --nurses &lt;int&gt;           number of nurses
 *   --rooms &lt;int&gt;            number of treatment rooms
 *   --triageMean &lt;double&gt;    mean triage duration in minutes
 *   --hours &lt;double&gt;         simulation duration in hours
 *   --seed &lt;long&gt;            RNG seed (0 = random)
 *   --outputDir &lt;path&gt;       directory for CSV output
 *   --runMode &lt;mode&gt;         single | all | experiment
 * </pre>
 */
public class SimConfig {

    // ── Defaults (match project spec) ─────────────────────────────────────────
    private double arrivalRatePerHour = 12.0;
    private double simulationHours    = 24.0;
    private int    numDoctors         = 4;
    private int    numNurses          = 3;
    private int    numRooms           = 8;
    private double triageMeanMinutes  = 5.0;
    private long   seed               = 42L;
    private String outputDir          = "results";
    private String runMode            = "experiment";

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Loads config from file then applies CLI overrides.
     *
     * @param args command-line arguments passed to {@code main()}
     * @return fully resolved {@link SimConfig}
     */
    public static SimConfig load(String[] args) {
        SimConfig cfg = new SimConfig();

        // 1. Determine config file path from CLI (--config flag) or default
        String configPath = "config.properties";
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--config")) configPath = args[i + 1];
        }

        // 2. Load from file
        cfg.loadFromFile(configPath);

        // 3. Apply CLI overrides (take precedence over file)
        cfg.applyArgs(args);

        return cfg;
    }

    // ── File loading ──────────────────────────────────────────────────────────

    private void loadFromFile(String path) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
            arrivalRatePerHour = doubleOrDefault(props, "arrivalRatePerHour", arrivalRatePerHour);
            simulationHours    = doubleOrDefault(props, "simulationHours",    simulationHours);
            numDoctors         = intOrDefault   (props, "numDoctors",         numDoctors);
            numNurses          = intOrDefault   (props, "numNurses",          numNurses);
            numRooms           = intOrDefault   (props, "numRooms",           numRooms);
            triageMeanMinutes  = doubleOrDefault(props, "triageMeanMinutes",  triageMeanMinutes);
            seed               = longOrDefault  (props, "seed",               seed);
            outputDir          = props.getProperty("outputDir",  outputDir);
            runMode            = props.getProperty("runMode",    runMode);
            System.out.println("[Config] Loaded from: " + path);
        } catch (IOException e) {
            System.out.println("[Config] " + path + " not found — using defaults.");
        }
    }

    // ── CLI override parsing ──────────────────────────────────────────────────

    private void applyArgs(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--arrivalRate" -> arrivalRatePerHour = Double.parseDouble(args[i + 1]);
                case "--doctors"     -> numDoctors         = Integer.parseInt(args[i + 1]);
                case "--nurses"      -> numNurses          = Integer.parseInt(args[i + 1]);
                case "--rooms"       -> numRooms           = Integer.parseInt(args[i + 1]);
                case "--triageMean"  -> triageMeanMinutes  = Double.parseDouble(args[i + 1]);
                case "--hours"       -> simulationHours    = Double.parseDouble(args[i + 1]);
                case "--seed"        -> seed               = Long.parseLong(args[i + 1]);
                case "--outputDir"   -> outputDir          = args[i + 1];
                case "--runMode"     -> runMode            = args[i + 1];
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double doubleOrDefault(Properties p, String key, double def) {
        String v = p.getProperty(key); return v != null ? Double.parseDouble(v.trim()) : def;
    }
    private int intOrDefault(Properties p, String key, int def) {
        String v = p.getProperty(key); return v != null ? Integer.parseInt(v.trim()) : def;
    }
    private long longOrDefault(Properties p, String key, long def) {
        String v = p.getProperty(key); return v != null ? Long.parseLong(v.trim()) : def;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public double getArrivalRatePerHour() { return arrivalRatePerHour; }
    public double getArrivalRatePerMin()  { return arrivalRatePerHour / 60.0; }
    public double getSimulationHours()    { return simulationHours; }
    public double getSimulationMinutes()  { return simulationHours * 60.0; }
    public int    getNumDoctors()         { return numDoctors; }
    public int    getNumNurses()          { return numNurses; }
    public int    getNumRooms()           { return numRooms; }
    public double getTriageMeanMinutes()  { return triageMeanMinutes; }
    public long   getSeed()               { return seed; }
    public String getOutputDir()          { return outputDir; }
    public String getRunMode()            { return runMode; }

    @Override
    public String toString() {
        return String.format(
                "SimConfig[arrivalRate=%.1f/hr, doctors=%d, nurses=%d, rooms=%d, " +
                        "triageMean=%.1fmin, hours=%.0f, seed=%d, mode=%s]",
                arrivalRatePerHour, numDoctors, numNurses, numRooms,
                triageMeanMinutes, simulationHours, seed, runMode);
    }
}