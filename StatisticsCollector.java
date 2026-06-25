package edsim.stats;

import edsim.entities.Doctor;
import edsim.entities.Nurse;
import edsim.entities.Patient;
import edsim.entities.TreatmentRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects and summarises simulation metrics across a single run.
 *
 * Metrics tracked:
 *   Patient  – wait times, throughput, completion rate
 *   Doctor   – utilisation per doctor
 *   Nurse    – utilisation per nurse
 *   Room     – utilisation per room
 *   Queue    – length samples used to compute average queue length
 */
public class StatisticsCollector {

    // ── Patient records ───────────────────────────────────────────────────────
    private final List<Patient> allPatients      = new ArrayList<>();
    private final List<Patient> dischargedPatients = new ArrayList<>();

    // ── Queue length samples (recorded at each event) ─────────────────────────
    private final List<Integer> queueLengthSamples = new ArrayList<>();

    // ── Totals ────────────────────────────────────────────────────────────────
    private int patientsArrived = 0;

    // ── Constructor ───────────────────────────────────────────────────────────
    public StatisticsCollector() {}

    // ── Recording methods ─────────────────────────────────────────────────────

    public void recordArrival(Patient p) {
        allPatients.add(p);
        patientsArrived++;
    }

    public void recordDischarge(Patient p) {
        dischargedPatients.add(p);
    }

    public void recordQueueLength(int length) {
        queueLengthSamples.add(length);
    }

    // ── Computed metrics ──────────────────────────────────────────────────────

    public double getAverageWaitTime() {
        return dischargedPatients.stream()
                .mapToDouble(Patient::calculateWaitTime)
                .average()
                .orElse(0.0);
    }

    public double getMaxWaitTime() {
        return dischargedPatients.stream()
                .mapToDouble(Patient::calculateWaitTime)
                .max()
                .orElse(0.0);
    }

    public double getAverageQueueLength() {
        return queueLengthSamples.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    public int getThroughput() {
        return dischargedPatients.size();
    }

    public double getCompletionRate() {
        return patientsArrived > 0
                ? (double) dischargedPatients.size() / patientsArrived * 100.0
                : 0.0;
    }

    // ── Report generation ─────────────────────────────────────────────────────

    /**
     * Prints a formatted summary report to stdout.
     *
     * @param scenarioLabel   e.g. "Scenario A (Baseline)"
     * @param doctors         list of doctors from the simulation
     * @param nurses          list of nurses from the simulation
     * @param rooms           list of treatment rooms from the simulation
     * @param simDurationMins total simulation duration in minutes
     */
    public void generateReport(String scenarioLabel,
                               List<Doctor> doctors,
                               List<Nurse>  nurses,
                               List<TreatmentRoom> rooms,
                               double simDurationMins) {

        System.out.println("=".repeat(60));
        System.out.println("  SIMULATION REPORT — " + scenarioLabel);
        System.out.println("=".repeat(60));

        // ── Patient metrics ───────────────────────────────────────────────────
        System.out.println("\n--- Patient Metrics ---");
        System.out.printf("  Patients arrived      : %d%n", patientsArrived);
        System.out.printf("  Patients discharged   : %d%n", getThroughput());
        System.out.printf("  Completion rate       : %.1f%%%n", getCompletionRate());
        System.out.printf("  Avg wait time         : %.2f min%n", getAverageWaitTime());
        System.out.printf("  Max wait time         : %.2f min%n", getMaxWaitTime());
        System.out.printf("  Avg queue length      : %.2f patients%n", getAverageQueueLength());

        // ── Success criteria evaluation ───────────────────────────────────────
        System.out.println("\n--- Success Criteria ---");
        checkCriterion("Avg wait < 30 min",       getAverageWaitTime()  < 30.0);
        checkCriterion("Avg queue < 10 patients",  getAverageQueueLength() < 10.0);
        checkCriterion("Completion rate >= 95%",   getCompletionRate()   >= 95.0);

        // ── Doctor utilisation ────────────────────────────────────────────────
        System.out.println("\n--- Doctor Utilisation ---");
        double totalDocUtil = 0;
        for (Doctor d : doctors) {
            double util = d.getUtilizationRate(simDurationMins) * 100.0;
            totalDocUtil += util;
            System.out.printf("  Doctor %-2d : %5.1f%%  (treated %d patients)%n",
                    d.getDoctorID(), util, d.getPatientsTreated());
        }
        double avgDocUtil = doctors.isEmpty() ? 0 : totalDocUtil / doctors.size();
        System.out.printf("  Avg doctor util       : %.1f%%%n", avgDocUtil);
        checkCriterion("Doctor util 70–90%", avgDocUtil >= 70.0 && avgDocUtil <= 90.0);

        // ── Nurse utilisation ─────────────────────────────────────────────────
        System.out.println("\n--- Nurse Utilisation ---");
        for (Nurse n : nurses) {
            double util = n.getUtilizationRate(simDurationMins) * 100.0;
            System.out.printf("  Nurse  %-2d : %5.1f%%  (triaged %d patients)%n",
                    n.getNurseID(), util, n.getPatientsTriaged());
        }

        // ── Room utilisation ──────────────────────────────────────────────────
        System.out.println("\n--- Room Utilisation ---");
        for (TreatmentRoom r : rooms) {
            double util = r.getUtilizationRate(simDurationMins) * 100.0;
            System.out.printf("  Room   %-2d : %5.1f%%  (assignments %d)%n",
                    r.getRoomID(), util, r.getTotalAssignments());
        }

        System.out.println("=".repeat(60));
        System.out.println();
    }

    private void checkCriterion(String label, boolean passed) {
        System.out.printf("  [%s] %s%n", passed ? "PASS" : "FAIL", label);
    }
}