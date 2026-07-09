package edsim.stats;

/**
 * Captures the complete results of a single simulation run for CSV export.
 *
 * <p>One {@code RunResult} is produced per run and written as a row in
 * {@code results/simulation_runs.csv}.</p>
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
    public final int    patientsDischarged;
    public final double completionRatePct;
    public final double avgWaitTimeMin;
    public final double maxWaitTimeMin;
    public final double avgQueueLength;

    // ── Resource metrics ──────────────────────────────────────────────────────
    public final double avgDoctorUtilPct;
    public final double avgNurseUtilPct;
    public final double avgRoomUtilPct;

    // ── Execution metrics ─────────────────────────────────────────────────────
    public final long   executionTimeMs;

    // ── Success criteria ──────────────────────────────────────────────────────
    public final boolean passWaitTime;
    public final boolean passQueueLength;
    public final boolean passCompletionRate;
    public final boolean passDoctorUtil;

    public RunResult(int runID, String scenarioLabel,
                     int numDoctors, int numNurses, int numRooms,
                     double arrivalRatePerHour, double triageMeanMinutes, long seed,
                     int patientsArrived, int patientsDischarged,
                     double completionRatePct, double avgWaitTimeMin,
                     double maxWaitTimeMin, double avgQueueLength,
                     double avgDoctorUtilPct, double avgNurseUtilPct, double avgRoomUtilPct,
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
        this.avgWaitTimeMin      = avgWaitTimeMin;
        this.maxWaitTimeMin      = maxWaitTimeMin;
        this.avgQueueLength      = avgQueueLength;
        this.avgDoctorUtilPct    = avgDoctorUtilPct;
        this.avgNurseUtilPct     = avgNurseUtilPct;
        this.avgRoomUtilPct      = avgRoomUtilPct;
        this.executionTimeMs     = executionTimeMs;
        this.passWaitTime        = avgWaitTimeMin    < 30.0;
        this.passQueueLength     = avgQueueLength    < 10.0;
        this.passCompletionRate  = completionRatePct >= 95.0;
        this.passDoctorUtil      = avgDoctorUtilPct  >= 70.0 && avgDoctorUtilPct <= 90.0;
    }

    /** Returns a CSV row matching the header from {@link CsvExporter#CSV_HEADER}. */
    public String toCsvRow() {
        return String.join(",",
                str(runID), quote(scenarioLabel),
                str(numDoctors), str(numNurses), str(numRooms),
                fmt(arrivalRatePerHour), fmt(triageMeanMinutes), str(seed),
                str(patientsArrived), str(patientsDischarged),
                fmt(completionRatePct), fmt(avgWaitTimeMin), fmt(maxWaitTimeMin),
                fmt(avgQueueLength), fmt(avgDoctorUtilPct), fmt(avgNurseUtilPct),
                fmt(avgRoomUtilPct), str(executionTimeMs),
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