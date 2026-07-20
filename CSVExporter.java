package edsim.stats;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Exports simulation run results to CSV files in the configured output directory.
 *
 * <p>Files produced:</p>
 * <ul>
 *   <li>{@code simulation_runs.csv}    — one row per run, all metrics (M4: now
 *       includes within-window completion, per-severity wait, and actual
 *       elapsed time alongside the nominal window)</li>
 *   <li>{@code patient_data_run<N>.csv} — per-patient wait/severity data</li>
 *   <li>{@code scenario_replication_summary.csv} — mean + 95% CI across
 *       replications per scenario (M4, written by {@link ReplicationStats})</li>
 * </ul>
 */
public class CSVExporter {

    public static final String CSV_HEADER =
            "runID,scenarioLabel," +
                    "numDoctors,numNurses,numRooms," +
                    "arrivalRatePerHour,triageMeanMinutes,seed," +
                    "patientsArrived,patientsDischargedTotal,completionRatePctTotal," +
                    "patientsDischargedWithinWindow,completionRatePctWithinWindow," +
                    "avgWaitTimeMin,maxWaitTimeMin,avgQueueLength," +
                    "avgWaitCriticalMin,avgWaitHighMin,avgWaitModerateMin,avgWaitLowMin,avgWaitMinorMin," +
                    "avgDoctorUtilPct,avgNurseUtilPct,avgRoomUtilPct," +
                    "actualElapsedMinutes,nominalWindowMinutes," +
                    "executionTimeMs," +
                    "passWaitTime,passQueueLength,passCompletionRate,passDoctorUtil";

    private final String outputDir;

    public CSVExporter(String outputDir) {
        this.outputDir = outputDir;
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            System.err.println("[CSV] Could not create output directory: " + outputDir);
        }
    }

    public String getOutputDir() { return outputDir; }

    /**
     * Writes all run results to {@code simulation_runs.csv}.
     *
     * @param results list of completed run results
     */
    public void exportRunSummary(List<RunResult> results) {
        String path = outputDir + "/simulation_runs.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println(CSV_HEADER);
            for (RunResult r : results) {
                pw.println(r.toCsvRow());
            }
            System.out.println("[CSV] Run summary written to: " + path);
        } catch (IOException e) {
            System.err.println("[CSV] Failed to write run summary: " + e.getMessage());
        }
    }

    /**
     * Writes per-patient data for a single run to {@code patient_data_run<N>.csv}.
     *
     * @param runID    the run identifier
     * @param patients list of patients from this run (discharged only)
     */
    public void exportPatientData(int runID, List<edsim.entities.Patient> patients) {
        String path = outputDir + "/patient_data_run" + runID + ".csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("patientID,severity,arrivalTime,triageCompleteTime," +
                    "treatmentStartTime,treatmentEndTime,waitTimeMin,treatmentDurationMin");
            for (edsim.entities.Patient p : patients) {
                pw.printf("%d,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        p.getPatientID(),
                        p.getSeverity(),
                        p.getArrivalTime(),
                        p.getTriageCompleteTime(),
                        p.getTreatmentStartTime(),
                        p.getTreatmentEndTime(),
                        p.calculateWaitTime(),
                        p.getTreatmentDuration()
                );
            }
            System.out.println("[CSV] Patient data written to: " + path);
        } catch (IOException e) {
            System.err.println("[CSV] Failed to write patient data: " + e.getMessage());
        }
    }
}
