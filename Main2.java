package edsim;

import edsim.engine.ScenarioConfig;
import edsim.engine.SimConfig;
import edsim.engine.SimulationEngine;
import edsim.stats.CSVExporter;
import edsim.stats.RunResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the Emergency Department Resource Optimization Simulation.
 *
 * <p><b>UML References:</b></p>
 * <ul>
 *   <li>{@see <a href="../../docs/class-diagram.drawio">Class Diagram (Draw.io)</a>}</li>
 *   <li>{@see <a href="../../docs/activity-diagram.drawio">Activity Diagram (Draw.io)</a>}</li>
 * </ul>
 *
 * <p><b>Run modes (set in config.properties or --runMode):</b></p>
 * <ul>
 *   <li>{@code single}     — one run using config file parameters</li>
 *   <li>{@code all}        — four pre-defined scenarios A–D</li>
 *   <li>{@code experiment} — full 10-run matrix varying 3+ parameters</li>
 * </ul>
 *
 * <p><b>CLI examples:</b></p>
 * <pre>
 *   java -cp out Main
 *   java -cp out Main --runMode single --doctors 6 --nurses 4 --arrivalRate 15
 *   java -cp out Main --config myconfig.properties
 * </pre>
 */
public class Main2 {

    public static void main(String[] args) {
        SimConfig cfg = SimConfig.load(args);
        System.out.println("[Config] " + cfg);

        CSVExporter     exporter = new CSVExporter(cfg.getOutputDir());
        List<RunResult> results  = new ArrayList<>();

        switch (cfg.getRunMode()) {
            case "single"     -> results.add(runSingle(cfg));
            case "all"        -> results.addAll(runAllScenarios(cfg));
            case "experiment" -> results.addAll(runExperiment(cfg, exporter));
            default -> {
                System.err.println("[Error] Unknown runMode: " + cfg.getRunMode());
                System.exit(1);
            }
        }

        exporter.exportRunSummary(results);
        System.out.println("\nAll runs complete. Results in: " + cfg.getOutputDir() + "/");
    }

    // ── Run modes ─────────────────────────────────────────────────────────────

    /** Single run using parameters from config/CLI. */
    private static RunResult runSingle(SimConfig cfg) {
        SimulationEngine engine = new SimulationEngine(cfg);
        return engine.run(1, cfg.getArrivalRatePerHour(), cfg.getTriageMeanMinutes(), cfg.getSeed());
    }

    /** Four pre-defined scenarios A–D. */
    private static List<RunResult> runAllScenarios(SimConfig cfg) {
        List<RunResult> results = new ArrayList<>();
        ScenarioConfig[] scenarios = {
                ScenarioConfig.scenarioA(),
                ScenarioConfig.scenarioB(),
                ScenarioConfig.scenarioC(),
                ScenarioConfig.scenarioD()
        };
        int runID = 1;
        for (ScenarioConfig sc : scenarios) {
            SimulationEngine engine = new SimulationEngine(sc, cfg.getSeed());
            results.add(engine.run(runID++, 12.0, 5.0, cfg.getSeed()));
        }
        return results;
    }

    /**
     * 10-run experiment matrix varying 3 parameters:
     *   1. Number of doctors    (4, 5, 6)
     *   2. Patient arrival rate (10, 12, 15 patients/hr)
     *   3. Triage mean duration (3, 5, 8 minutes)
     *
     * Produces ≥10 distinct runs for the milestone-3 PDF report.
     */
    private static List<RunResult> runExperiment(SimConfig cfg, CSVExporter exporter) {
        List<RunResult> results = new ArrayList<>();

        // Each row: { label, doctors, nurses, rooms, arrivalRate/hr, triageMean/min, seed }
        Object[][] matrix = {
                // ── Vary doctors (arrival=12, triage=5) ──────────────────────────
                { "Run01 | Baseline (4 doc, λ=12, triage=5)",  4, 3, 8,  12.0, 5.0, 42L },
                { "Run02 | +1 Doctor (5 doc, λ=12, triage=5)", 5, 3, 8,  12.0, 5.0, 42L },
                { "Run03 | +2 Doctor (6 doc, λ=12, triage=5)", 6, 3, 8,  12.0, 5.0, 42L },

                // ── Vary arrival rate (doctors=4, triage=5) ───────────────────────
                { "Run04 | Low arrival   (4 doc, λ=10, triage=5)", 4, 3, 8, 10.0, 5.0, 42L },
                { "Run05 | High arrival  (4 doc, λ=15, triage=5)", 4, 3, 8, 15.0, 5.0, 42L },
                { "Run06 | Very high arr (4 doc, λ=18, triage=5)", 4, 3, 8, 18.0, 5.0, 42L },

                // ── Vary triage mean (doctors=4, arrival=12) ──────────────────────
                { "Run07 | Fast triage  (4 doc, λ=12, triage=3)", 4, 3, 8, 12.0, 3.0, 42L },
                { "Run08 | Slow triage  (4 doc, λ=12, triage=8)", 4, 3, 8, 12.0, 8.0, 42L },

                // ── Combined stress scenarios ──────────────────────────────────────
                { "Run09 | Max stress   (4 doc, λ=18, triage=8)", 4, 3, 8, 18.0, 8.0, 42L },
                { "Run10 | Optimal      (6 doc, λ=12, triage=3)", 6, 4, 10, 12.0, 3.0, 42L },

                // ── Bonus: different seed for reproducibility check ────────────────
                { "Run11 | Baseline seed=99 (4 doc, λ=12, triage=5)", 4, 3, 8, 12.0, 5.0, 99L },
                { "Run12 | Scenario D (5 doc, 4 nur, λ=12, triage=5)", 5, 4, 10, 12.0, 5.0, 42L },
        };

        for (int i = 0; i < matrix.length; i++) {
            Object[] row  = matrix[i];
            String label  = (String) row[0];
            int    docs   = (int)    row[1];
            int    nurs   = (int)    row[2];
            int    rms    = (int)    row[3];
            double lambda = (double) row[4];
            double triage = (double) row[5];
            long   seed   = (long)   row[6];

            SimulationEngine engine = new SimulationEngine(
                    label, docs, nurs, rms, lambda, triage, 24.0, seed);
            RunResult result = engine.run(i + 1, lambda, triage, seed);
            results.add(result);

            // Export per-run patient CSV
            exporter.exportPatientData(i + 1, engine.getDischargedPatients());
        }

        return results;
    }
}