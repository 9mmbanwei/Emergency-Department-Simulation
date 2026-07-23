package edsim;

import edsim.engine.ScenarioConfig;
import edsim.engine.SimConfig;
import edsim.engine.SimulationEngine;
import edsim.stats.CSVExporter;
import edsim.stats.ReplicationStats;
import edsim.stats.RunResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the Emergency Department Resource Optimization Simulation.
 *
 * <p><b>Run modes (set in config.properties or --runMode):</b></p>
 * <ul>
 *   <li>{@code single}      — one run using config file parameters</li>
 *   <li>{@code all}         — four pre-defined scenarios A–D</li>
 *   <li>{@code experiment}  — M3's 13-run single-seed matrix (kept for continuity)</li>
 *   <li>{@code replication} — M4: each scenario run across multiple seeds,
 *       producing mean + 95% CI per metric in
 *       {@code results/scenario_replication_summary.csv}</li>
 *   <li>{@code sensitivity} — M4 revision: re-runs the M3 one-at-a-time
 *       parameter sweep (baseline, doctors, arrival rate, triage mean) across
 *       the same {@link #REPLICATION_SEEDS} 8-seed set used by {@code
 *       replication}, so the Section 2 sensitivity ratios carry 95% CIs
 *       instead of resting on the single seed=42 point estimates. Output in
 *       {@code results/sensitivity_replication_summary.csv}. Added in
 *       response to M4 feedback that the sensitivity study inherited noise
 *       from single-seed rows near the &rho;&asymp;1 high-variance region.</li>
 * </ul>
 *
 * <p><b>M4 scenario set</b> ({@link #buildM4Scenarios()}) covers three
 * categories required by the milestone:</p>
 * <ul>
 *   <li><b>Staffing-load scenarios</b> (understaffed / marginal / adequate) —
 *       demonstrates the queueing-stability threshold (rho ~ lambda*E[S]/c)
 *       with replicated confidence intervals instead of single-run point
 *       estimates.</li>
 *   <li><b>Degeneracy tests</b> — near-zero arrival rate (system should stay
 *       essentially empty) and a single-doctor extreme-overload case (system
 *       should degrade predictably, not error out).</li>
 *   <li><b>Continuity test</b> — a fine sweep of doctor count around the
 *       theoretical rho=1 threshold, to confirm wait time responds smoothly
 *       (no discontinuities/artifacts) as capacity crosses the boundary.</li>
 * </ul>
 */
public class Main {

    /** Number of seeded replications per scenario for the M4 CI analysis. */
    private static final int REPLICATIONS = 8;

    /** Seeds used for replication — fixed list so results are reproducible. */
    private static final long[] REPLICATION_SEEDS = {
            11L, 22L, 33L, 44L, 55L, 66L, 77L, 88L
    };

    public static void main(String[] args) {
        SimConfig cfg = SimConfig.load(args);
        System.out.println("[Config] " + cfg);

        CSVExporter     exporter = new CSVExporter(cfg.getOutputDir());
        List<RunResult> results  = new ArrayList<>();

        switch (cfg.getRunMode()) {
            case "single"      -> results.add(runSingle(cfg));
            case "all"         -> results.addAll(runAllScenarios(cfg));
            case "experiment"  -> results.addAll(runExperiment(cfg, exporter));
            case "replication" -> results.addAll(runReplicationStudy(cfg, exporter));
            case "sensitivity" -> results.addAll(runSensitivityReplication(cfg, exporter));
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
     * M3's 13-run single-seed experiment matrix, kept unchanged so the M3
     * results remain reproducible as a baseline / regression check.
     */
    private static List<RunResult> runExperiment(SimConfig cfg, CSVExporter exporter) {
        List<RunResult> results = new ArrayList<>();

        Object[][] matrix = {
                { "Run01 | Baseline (4 doc, λ=12, triage=5)",  4, 3, 8,  12.0, 5.0, 42L },
                { "Run02 | +1 Doctor (5 doc, λ=12, triage=5)", 5, 3, 8,  12.0, 5.0, 42L },
                { "Run03 | +2 Doctor (6 doc, λ=12, triage=5)", 6, 3, 8,  12.0, 5.0, 42L },
                { "Run04 | Low arrival   (4 doc, λ=10, triage=5)", 4, 3, 8, 10.0, 5.0, 42L },
                { "Run05 | High arrival  (4 doc, λ=15, triage=5)", 4, 3, 8, 15.0, 5.0, 42L },
                { "Run06 | Very high arr (4 doc, λ=18, triage=5)", 4, 3, 8, 18.0, 5.0, 42L },
                { "Run07 | Fast triage  (4 doc, λ=12, triage=3)", 4, 3, 8, 12.0, 3.0, 42L },
                { "Run08 | Mid triage  (4 doc, λ=12, triage=5.5)", 4, 3, 8, 12.0, 5.5, 42L },
                { "Run09 | Slow triage  (4 doc, λ=12, triage=8)", 4, 3, 8, 12.0, 8.0, 42L },
                { "Run10 | Max stress   (4 doc, λ=18, triage=8)", 4, 3, 8, 18.0, 8.0, 42L },
                { "Run11 | Optimal      (6 doc, λ=12, triage=3)", 6, 4, 10, 12.0, 3.0, 42L },
                { "Run12 | Baseline seed=99 (4 doc, λ=12, triage=5)", 4, 3, 8, 12.0, 5.0, 99L },
                { "Run13 | Scenario D (5 doc, 4 nur, λ=12, triage=5)", 5, 4, 10, 12.0, 5.0, 42L },
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
            exporter.exportPatientData(i + 1, engine.getDischargedPatients());
        }

        return results;
    }

    /**
     * M4 replication study: runs each defined scenario across
     * {@link #REPLICATION_SEEDS} and exports both the raw per-run CSV
     * ({@code simulation_runs.csv}, all runs from all scenarios/seeds) and a
     * scenario-level summary with mean + 95% CI
     * ({@code scenario_replication_summary.csv}).
     */
    private static List<RunResult> runReplicationStudy(SimConfig cfg, CSVExporter exporter) {
        List<RunResult> allResults = new ArrayList<>();
        Map<String, List<RunResult>> byScenario = new LinkedHashMap<>();

        List<ScenarioSpec> scenarios = buildM4Scenarios();

        int runID = 1;
        for (ScenarioSpec spec : scenarios) {
            List<RunResult> replications = new ArrayList<>();
            for (long seed : REPLICATION_SEEDS) {
                SimulationEngine engine = new SimulationEngine(
                        spec.label, spec.doctors, spec.nurses, spec.rooms,
                        spec.arrivalRatePerHour, spec.triageMeanMinutes, spec.simHours, seed);
                RunResult result = engine.run(runID++, spec.arrivalRatePerHour, spec.triageMeanMinutes, seed);
                replications.add(result);
                allResults.add(result);
                exporter.exportPatientData(result.runID, engine.getDischargedPatients());
            }
            byScenario.put(spec.label, replications);
        }

        ReplicationStats.exportScenarioSummary(exporter.getOutputDir(), byScenario);
        return allResults;
    }

    /**
     * M4 revision (post-feedback): re-runs the M3 one-at-a-time parameter
     * sweep — the same nine configurations behind Section 2's Table 1/Table 2
     * ({@link #buildSensitivitySweepConfigs()}) — across the same 8-seed
     * {@link #REPLICATION_SEEDS} set used by {@link #runReplicationStudy}
     * for the scenario study, instead of the single seed=42 used in M3/the
     * original M4 draft. This lets the sensitivity ratios in the revised
     * Section 2.1 carry 95% CIs, so rows sitting near the &rho;&asymp;1
     * high-variance region (Doctors 4&rarr;5, Arrival rate 12&rarr;15) no
     * longer rest on noisy single-run point estimates.
     */
    private static List<RunResult> runSensitivityReplication(SimConfig cfg, CSVExporter exporter) {
        List<RunResult> allResults = new ArrayList<>();
        Map<String, List<RunResult>> byConfig = new LinkedHashMap<>();

        List<ScenarioSpec> configs = buildSensitivitySweepConfigs();

        int runID = 1;
        for (ScenarioSpec spec : configs) {
            List<RunResult> replications = new ArrayList<>();
            for (long seed : REPLICATION_SEEDS) {
                SimulationEngine engine = new SimulationEngine(
                        spec.label, spec.doctors, spec.nurses, spec.rooms,
                        spec.arrivalRatePerHour, spec.triageMeanMinutes, spec.simHours, seed);
                RunResult result = engine.run(runID++, spec.arrivalRatePerHour, spec.triageMeanMinutes, seed);
                replications.add(result);
                allResults.add(result);
            }
            byConfig.put(spec.label, replications);
        }

        ReplicationStats.exportScenarioSummary(
                exporter.getOutputDir(), byConfig, "sensitivity_replication_summary.csv");
        return allResults;
    }

    /**
     * The nine one-at-a-time parameter-sweep configurations from the M3
     * matrix / M4 Table 1 (baseline, doctors 4&rarr;5&rarr;6, arrival rate
     * 12&rarr;10/15/18, triage mean 5&rarr;3/5.5/8), each held at the common
     * baseline (4 doctors, 3 nurses, 8 rooms, &lambda;=12/hr, triage mean =
     * 5 min) except for the single parameter varied in that row. Labels are
     * ordered to match Table 1's Run 01-09 numbering.
     */
    private static List<ScenarioSpec> buildSensitivitySweepConfigs() {
        List<ScenarioSpec> configs = new ArrayList<>();
        configs.add(new ScenarioSpec("Sens01_Baseline_4doc",     4, 3, 8, 12.0, 5.0, 24.0));
        configs.add(new ScenarioSpec("Sens02_Doctors_5doc",      5, 3, 8, 12.0, 5.0, 24.0));
        configs.add(new ScenarioSpec("Sens03_Doctors_6doc",      6, 3, 8, 12.0, 5.0, 24.0));
        configs.add(new ScenarioSpec("Sens04_ArrivalRate_10hr",  4, 3, 8, 10.0, 5.0, 24.0));
        configs.add(new ScenarioSpec("Sens05_ArrivalRate_15hr",  4, 3, 8, 15.0, 5.0, 24.0));
        configs.add(new ScenarioSpec("Sens06_ArrivalRate_18hr",  4, 3, 8, 18.0, 5.0, 24.0));
        configs.add(new ScenarioSpec("Sens07_TriageMean_3min",   4, 3, 8, 12.0, 3.0, 24.0));
        configs.add(new ScenarioSpec("Sens08_TriageMean_5p5min", 4, 3, 8, 12.0, 5.5, 24.0));
        configs.add(new ScenarioSpec("Sens09_TriageMean_8min",   4, 3, 8, 12.0, 8.0, 24.0));
        return configs;
    }

    /**
     * Defines the M4 scenario set: staffing-load scenarios (understaffed /
     * marginal / adequate), degeneracy tests, and a continuity sweep.
     * Each entry is replicated across {@link #REPLICATION_SEEDS} seeds by
     * {@link #runReplicationStudy}.
     */
    private static List<ScenarioSpec> buildM4Scenarios() {
        List<ScenarioSpec> scenarios = new ArrayList<>();

        // ── Category 1: staffing-load scenarios (rho below/near/above 1) ──────
        // E[S] = 25.75 min (from configured severity mix), lambda=12/hr=0.2/min
        // c=4 -> rho≈1.29 (unstable)  c=5 -> rho≈1.03 (marginal)  c=6 -> rho≈0.86 (stable)
        scenarios.add(new ScenarioSpec("LoadA_Understaffed_4doc",  4, 3, 8,  12.0, 5.0, 24.0));
        scenarios.add(new ScenarioSpec("LoadB_Marginal_5doc",      5, 3, 8,  12.0, 5.0, 24.0));
        scenarios.add(new ScenarioSpec("LoadC_Adequate_6doc",      6, 4, 10, 12.0, 5.0, 24.0));

        // ── Category 2: degeneracy tests ───────────────────────────────────────
        // Near-zero arrivals: system should stay essentially empty, near-0%
        // utilization, near-0 wait — validates the model doesn't produce
        // spurious congestion when there's no demand.
        scenarios.add(new ScenarioSpec("Degen_NearZeroArrival_4doc", 4, 3, 8, 0.5, 5.0, 24.0));

        // Single-doctor extreme overload: should degrade predictably (very
        // high wait, queue grows) rather than crash or produce nonsensical
        // output — validates robustness at the boundary.
        scenarios.add(new ScenarioSpec("Degen_SingleDoctor_Overload", 1, 3, 8, 12.0, 5.0, 24.0));

        // Very high doctor count: utilization should approach 0%, not go
        // negative or undefined — validates the opposite boundary.
        scenarios.add(new ScenarioSpec("Degen_Overprovisioned_20doc", 20, 6, 20, 12.0, 5.0, 24.0));

        // ── Category 3: continuity sweep around rho=1 ──────────────────────────
        // Finer-grained doctor sweep than the M3 4/5/6 matrix, to confirm
        // wait time responds smoothly (no artifacts) as capacity crosses the
        // theoretical instability threshold rather than jumping discontinuously.
        for (int docs = 4; docs <= 8; docs++) {
            scenarios.add(new ScenarioSpec("Continuity_" + docs + "doc", docs, 4, 10, 12.0, 5.0, 24.0));
        }

        return scenarios;
    }

    /** Simple holder for a scenario definition used by the replication study. */
    private static class ScenarioSpec {
        final String label;
        final int    doctors, nurses, rooms;
        final double arrivalRatePerHour, triageMeanMinutes, simHours;

        ScenarioSpec(String label, int doctors, int nurses, int rooms,
                     double arrivalRatePerHour, double triageMeanMinutes, double simHours) {
            this.label = label;
            this.doctors = doctors;
            this.nurses = nurses;
            this.rooms = rooms;
            this.arrivalRatePerHour = arrivalRatePerHour;
            this.triageMeanMinutes = triageMeanMinutes;
            this.simHours = simHours;
        }
    }
}
