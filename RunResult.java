package edsim.stats;

/**
 * Captures the complete results of a single simulation run for CSV export.
 *
 * <p>One {@code RunResult} is produced per run and written as a row in
 * {@code results/simulation_runs.csv}.</p>
 *
 * <p><b>M4 changes:</b> added {@code actualElapsedMinutes} (denominator now
 * used for utilization instead of the fixed nominal window — see
 * {@code SimulationEngine.run()}), within-window completion metrics, and a
 * per-severity mean wait time breakdown so CRITICAL-patient wait is visible
 * even when the global average looks acceptable.</p>
 */
public class RunResult {

    // ── Run identity ──────────────────────────────────────────────────────────
    public final int    runID;
    public final String scenarioLabel;

    // ── Parameters (the 3+ varied parameters) ────────────────────────────────
    public final int    numDoctors;
    public final int    numNurses;
    public final int    numRooms;
    public final double arrivalRatePerHour;
    public final double triageMeanMinutes;
    public final long   seed;

    // ── Patient metrics ───────────────────────────────────────────────────────
    public final int    patientsArrived;
    public final int    patientsDischarged;          // total, incl. backlog drain
    public final double completionRatePct;            // total, incl. backlog drain
    public final int    patientsDischargedWithinWindow;
    public final double completionRateWithinWindowPct; // nominal-window only
    public final double avgWaitTimeMin;
    public final double maxWaitTimeMin;
    public final double avgQueueLength;

    // ── Per-severity wait time (minutes, discharged patients only) ───────────
    public final double avgWaitCritical;
    public final double avgWaitHigh;
    public final double avgWaitModerate;
    public final double avgWaitLow;
    public final double avgWaitMinor;

    // ── Resource metrics (now vs. actual elapsed time, not fixed window) ─────
    public final double avgDoctorUtilPct;
    public final double avgNurseUtilPct;
    public final double avgRoomUtilPct;
    public final double actualElapsedMinutes;
    public final double nominalWindowMinutes;

    // ── Execution metrics ─────────────────────────────────────────────────────
    public final long   executionTimeMs;

    // ── Success criteria ──────────────────────────────────────────────────────
    public final boolean passWaitTime;
    public final boolean passQueueLength;
    public final boolean passCompletionRate;   // now evaluated within-window
    public final boolean passDoctorUtil;

    public RunResult(int runID, String scenarioLabel,
                     int numDoctors, int numNurses, int numRooms,
                     double arrivalRatePerHour, double triageMeanMinutes, long seed,
                     int patientsArrived, int patientsDischarged,
                     double completionRatePct,
                     int patientsDischargedWithinWindow, double completionRateWithinWindowPct,
                     double avgWaitTimeMin, double maxWaitTimeMin, double avgQueueLength,
                     double avgWaitCritical, double avgWaitHigh, double avgWaitModerate,
                     double avgWaitLow, double avgWaitMinor,
                     double avgDoctorUtilPct, double avgNurseUtilPct, double avgRoomUtilPct,
                     double actualElapsedMinutes, double nominalWindowMinutes,
                     long executionTimeMs) {
        this.runID               = runID;
        this.scenarioLabel       = scenarioLabel;
        this.numDoctors          = numDoctors;
        this.numNurses           = numNurses;
        this.numRooms            = numRooms;
        this.arrivalRatePerHour  = arrivalRatePerHour;
        this.triageMeanMinutes   = triageMeanMinutes;
        this.seed                = seed;
        this.patientsArrived     = patientsArrived;
        this.patientsDischarged  = patientsDischarged;
        this.completionRatePct   = completionRatePct;
        this.patientsDischargedWithinWindow  = patientsDischargedWithinWindow;
        this.completionRateWithinWindowPct   = completionRateWithinWindowPct;
        this.avgWaitTimeMin      = avgWaitTimeMin;
        this.maxWaitTimeMin      = maxWaitTimeMin;
        this.avgQueueLength      = avgQueueLength;
        this.avgWaitCritical     = avgWaitCritical;
        this.avgWaitHigh         = avgWaitHigh;
        this.avgWaitModerate     = avgWaitModerate;
        this.avgWaitLow          = avgWaitLow;
        this.avgWaitMinor        = avgWaitMinor;
        this.avgDoctorUtilPct    = avgDoctorUtilPct;
        this.avgNurseUtilPct     = avgNurseUtilPct;
        this.avgRoomUtilPct      = avgRoomUtilPct;
        this.actualElapsedMinutes = actualElapsedMinutes;
        this.nominalWindowMinutes = nominalWindowMinutes;
        this.executionTimeMs     = executionTimeMs;
        this.passWaitTime        = avgWaitTimeMin    < 30.0;
        this.passQueueLength     = avgQueueLength    < 10.0;
        this.passCompletionRate  = completionRateWithinWindowPct >= 95.0;
        this.passDoctorUtil      = avgDoctorUtilPct  >= 70.0 && avgDoctorUtilPct <= 90.0;
    }

    /** Returns a CSV row matching the header from {@link CSVExporter#CSV_HEADER}. */
    public String toCsvRow() {
        return String.join(",",
                str(runID), quote(scenarioLabel),
                str(numDoctors), str(numNurses), str(numRooms),
                fmt(arrivalRatePerHour), fmt(triageMeanMinutes), str(seed),
                str(patientsArrived), str(patientsDischarged), fmt(completionRatePct),
                str(patientsDischargedWithinWindow), fmt(completionRateWithinWindowPct),
                fmt(avgWaitTimeMin), fmt(maxWaitTimeMin), fmt(avgQueueLength),
                fmt(avgWaitCritical), fmt(avgWaitHigh), fmt(avgWaitModerate),
                fmt(avgWaitLow), fmt(avgWaitMinor),
                fmt(avgDoctorUtilPct), fmt(avgNurseUtilPct), fmt(avgRoomUtilPct),
                fmt(actualElapsedMinutes), fmt(nominalWindowMinutes),
                str(executionTimeMs),
                pass(passWaitTime), pass(passQueueLength),
                pass(passCompletionRate), pass(passDoctorUtil)
        );
    }

    private String str(long v)    { return String.valueOf(v); }
    private String str(int v)     { return String.valueOf(v); }
    private String fmt(double v)  { return String.format("%.4f", v); }
    private String quote(String s){ return "\"" + s + "\""; }
    private String pass(boolean b){ return b ? "PASS" : "FAIL"; }
}
