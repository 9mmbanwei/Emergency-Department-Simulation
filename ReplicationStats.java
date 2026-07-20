package edsim.stats;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes replication statistics (mean, standard deviation, 95% confidence
 * interval) across multiple seeded runs of the same scenario, and writes a
 * scenario-level summary CSV.
 *
 * <p>M4 requires reporting parameter effects with confidence intervals rather
 * than single-seed point estimates. A scenario here means "same doctors /
 * nurses / rooms / arrival rate / triage mean, different seed" — i.e. a set
 * of {@link RunResult}s that only differ by {@code seed}.</p>
 *
 * <p>The half-width of the CI is computed as {@code t(0.025, n-1) * s / sqrt(n)}
 * using Student's t critical values (two-tailed, alpha = 0.05). A small
 * lookup table covers typical replication counts (n = 2..30); for larger n
 * the normal approximation (1.96) is used.</p>
 */
public class ReplicationStats {

    /** Summary statistics for one metric across replications of one scenario. */
    public static class MetricSummary {
        public final double mean;
        public final double stdDev;
        public final double halfWidth95;
        public final int    n;

        public MetricSummary(double mean, double stdDev, double halfWidth95, int n) {
            this.mean = mean;
            this.stdDev = stdDev;
            this.halfWidth95 = halfWidth95;
            this.n = n;
        }

        public double lowerCI() { return mean - halfWidth95; }
        public double upperCI() { return mean + halfWidth95; }
    }

    /** Two-tailed 95% Student's t critical values indexed by degrees of freedom (n-1). */
    private static final double[] T_TABLE = {
            // df=1..30
            12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042
    };

    private static double tCritical(int df) {
        if (df <= 0) return Double.NaN;
        if (df <= T_TABLE.length) return T_TABLE[df - 1];
        return 1.960; // normal approximation for large df
    }

    /** Computes mean / stdDev / 95% CI half-width for an array of replicated values. */
    public static MetricSummary summarize(double[] values) {
        int n = values.length;
        if (n == 0) return new MetricSummary(0, 0, 0, 0);
        double mean = 0;
        for (double v : values) mean += v;
        mean /= n;

        if (n == 1) return new MetricSummary(mean, 0.0, 0.0, 1);

        double sumSqDiff = 0;
        for (double v : values) sumSqDiff += (v - mean) * (v - mean);
        double variance = sumSqDiff / (n - 1);   // sample variance
        double stdDev   = Math.sqrt(variance);

        double t = tCritical(n - 1);
        double halfWidth = t * stdDev / Math.sqrt(n);

        return new MetricSummary(mean, stdDev, halfWidth, n);
    }

    /** Extracts the avgWaitTimeMin values from a list of RunResults. */
    public static double[] waitTimes(List<RunResult> runs) {
        return runs.stream().mapToDouble(r -> r.avgWaitTimeMin).toArray();
    }
    public static double[] queueLengths(List<RunResult> runs) {
        return runs.stream().mapToDouble(r -> r.avgQueueLength).toArray();
    }
    public static double[] doctorUtils(List<RunResult> runs) {
        return runs.stream().mapToDouble(r -> r.avgDoctorUtilPct).toArray();
    }
    public static double[] completionWithinWindow(List<RunResult> runs) {
        return runs.stream().mapToDouble(r -> r.completionRateWithinWindowPct).toArray();
    }
    public static double[] waitCritical(List<RunResult> runs) {
        return runs.stream().mapToDouble(r -> r.avgWaitCritical).toArray();
    }
    public static double[] waitMinor(List<RunResult> runs) {
        return runs.stream().mapToDouble(r -> r.avgWaitMinor).toArray();
    }

    /**
     * Writes one row per scenario summarizing mean +/- 95% CI for the key
     * metrics, given a map of scenario label -> list of replicated RunResults
     * (all sharing the same parameters, differing only by seed).
     */
    public static void exportScenarioSummary(String outputDir, Map<String, List<RunResult>> scenarioRuns) {
        String path = outputDir + "/scenario_replication_summary.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("scenarioLabel,numDoctors,numNurses,numRooms,arrivalRatePerHour,triageMeanMinutes,replications," +
                    "meanWaitMin,waitCI95Lower,waitCI95Upper," +
                    "meanQueueLength,queueCI95Lower,queueCI95Upper," +
                    "meanDoctorUtilPct,utilCI95Lower,utilCI95Upper," +
                    "meanCompletionWithinWindowPct,completionCI95Lower,completionCI95Upper," +
                    "meanWaitCriticalMin,waitCriticalCI95Lower,waitCriticalCI95Upper," +
                    "meanWaitMinorMin,waitMinorCI95Lower,waitMinorCI95Upper");

            // Sort for stable, readable output
            Map<String, List<RunResult>> sorted = new TreeMap<>(scenarioRuns);
            for (Map.Entry<String, List<RunResult>> entry : sorted.entrySet()) {
                List<RunResult> runs = entry.getValue();
                if (runs.isEmpty()) continue;
                RunResult first = runs.get(0);

                MetricSummary wait  = summarize(waitTimes(runs));
                MetricSummary queue = summarize(queueLengths(runs));
                MetricSummary util  = summarize(doctorUtils(runs));
                MetricSummary comp  = summarize(completionWithinWindow(runs));
                MetricSummary waitC = summarize(waitCritical(runs));
                MetricSummary waitM = summarize(waitMinor(runs));

                pw.println(String.join(",",
                        quote(entry.getKey()),
                        String.valueOf(first.numDoctors), String.valueOf(first.numNurses), String.valueOf(first.numRooms),
                        fmt(first.arrivalRatePerHour), fmt(first.triageMeanMinutes), String.valueOf(runs.size()),
                        fmt(wait.mean), fmt(wait.lowerCI()), fmt(wait.upperCI()),
                        fmt(queue.mean), fmt(queue.lowerCI()), fmt(queue.upperCI()),
                        fmt(util.mean), fmt(util.lowerCI()), fmt(util.upperCI()),
                        fmt(comp.mean), fmt(comp.lowerCI()), fmt(comp.upperCI()),
                        fmt(waitC.mean), fmt(waitC.lowerCI()), fmt(waitC.upperCI()),
                        fmt(waitM.mean), fmt(waitM.lowerCI()), fmt(waitM.upperCI())
                ));
            }
            System.out.println("[CSV] Scenario replication summary written to: " + path);
        } catch (IOException e) {
            System.err.println("[CSV] Failed to write scenario summary: " + e.getMessage());
        }
    }

    private static String quote(String s) { return "\"" + s + "\""; }
    private static String fmt(double v)   { return String.format("%.4f", v); }
}