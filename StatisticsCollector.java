package edsim.stats;

import edsim.entities.Doctor;
import edsim.entities.Nurse;
import edsim.entities.Patient;
import edsim.entities.TreatmentRoom;
import edsim.enums.SeverityLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Collects and reports simulation metrics for a single run.
 *
 * <p><b>M4 changes:</b></p>
 * <ul>
 *   <li>Per-severity wait-time breakdown (mean, count) so the global average
 *       does not mask what the severity-priority queue does to CRITICAL vs
 *       MINOR patients.</li>
 *   <li>Within-window completion tracking: distinguishes patients discharged
 *       before the nominal simulation window ends from those only discharged
 *       because the engine drains the backlog past that window.</li>
 * </ul>
 *
 * @see edsim.engine.SimulationEngine
 */
public class StatisticsCollector {

    private final List<Patient> allPatients        = new ArrayList<>();
    private final List<Patient> dischargedPatients = new ArrayList<>();
    private final List<Integer> queueLengthSamples = new ArrayList<>();
    private int patientsArrived = 0;

    public void recordArrival(Patient p)      { allPatients.add(p); patientsArrived++; }
    public void recordDischarge(Patient p)    { dischargedPatients.add(p); }
    public void recordQueueLength(int length) { queueLengthSamples.add(length); }

    public double getAverageWaitTime() {
        return dischargedPatients.stream().mapToDouble(Patient::calculateWaitTime).average().orElse(0.0);
    }
    public double getMaxWaitTime() {
        return dischargedPatients.stream().mapToDouble(Patient::calculateWaitTime).max().orElse(0.0);
    }
    public double getAverageQueueLength() {
        return queueLengthSamples.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
    public int    getThroughput()      { return dischargedPatients.size(); }
    public int    getPatientsArrived() { return patientsArrived; }

    /**
     * Completion rate including patients discharged after the nominal window
     * during backlog drain. This is the "100% every run" number from M3 —
     * useful for confirming no patient is lost, but not a measure of
     * within-window service performance.
     */
    public double getCompletionRate()  {
        return patientsArrived > 0 ? (double) dischargedPatients.size() / patientsArrived * 100.0 : 0.0;
    }

    /**
     * Number of patients actually discharged before {@code windowMinutes}
     * (the nominal simulation duration), i.e. NOT counting backlog-drain
     * discharges that occur after the window nominally closed.
     */
    public int getDischargedWithinWindow(double windowMinutes) {
        return (int) dischargedPatients.stream()
                .filter(p -> p.getTreatmentEndTime() <= windowMinutes)
                .count();
    }

    /**
     * Completion rate measured against the nominal service window only.
     * This is the metric to use when judging whether staffing meets demand
     * during the intended operating period — {@link #getCompletionRate()}
     * will read 100% even for badly understaffed configurations because it
     * counts backlog-drain discharges.
     */
    public double getCompletionRateWithinWindow(double windowMinutes) {
        return patientsArrived > 0
                ? (double) getDischargedWithinWindow(windowMinutes) / patientsArrived * 100.0
                : 0.0;
    }

    /** Returns an unmodifiable view of discharged patients (for CSV export). */
    public List<Patient> getDischargedPatients() {
        return Collections.unmodifiableList(dischargedPatients);
    }

    // ── Resource utilization ──────────────────────────────────────────────────
    // NOTE (M4 fix): callers should pass the ACTUAL elapsed simulated time
    // (the simulation clock at the moment the event queue drained), not the
    // fixed nominal window. Under backlog-draining overload the engine keeps
    // running past the nominal window, so dividing accumulated busy time by
    // the fixed window produced utilization figures above 100% in M3. See
    // SimulationEngine.run() for where actualElapsedMinutes is computed.

    public double getAvgDoctorUtil(List<Doctor> doctors, double actualElapsedMinutes) {
        return doctors.stream().mapToDouble(d -> d.getUtilizationRate(actualElapsedMinutes) * 100).average().orElse(0.0);
    }
    public double getAvgNurseUtil(List<Nurse> nurses, double actualElapsedMinutes) {
        return nurses.stream().mapToDouble(n -> n.getUtilizationRate(actualElapsedMinutes) * 100).average().orElse(0.0);
    }
    public double getAvgRoomUtil(List<TreatmentRoom> rooms, double actualElapsedMinutes) {
        return rooms.stream().mapToDouble(r -> r.getUtilizationRate(actualElapsedMinutes) * 100).average().orElse(0.0);
    }

    // ── Per-severity wait time breakdown ──────────────────────────────────────

    /**
     * Mean wait time (minutes) for discharged patients of a given severity.
     * Returns 0.0 if no patients of that severity were discharged.
     */
    public double getAvgWaitTimeBySeverity(SeverityLevel level) {
        return dischargedPatients.stream()
                .filter(p -> p.getSeverity() == level)
                .mapToDouble(Patient::calculateWaitTime)
                .average().orElse(0.0);
    }

    /** Count of discharged patients at a given severity (denominator for the above). */
    public int getCountBySeverity(SeverityLevel level) {
        return (int) dischargedPatients.stream().filter(p -> p.getSeverity() == level).count();
    }

    /** Convenience: full per-severity mean-wait map, in severity order. */
    public Map<SeverityLevel, Double> getAvgWaitBySeverityMap() {
        Map<SeverityLevel, Double> map = new EnumMap<>(SeverityLevel.class);
        for (SeverityLevel level : SeverityLevel.values()) {
            map.put(level, getAvgWaitTimeBySeverity(level));
        }
        return map;
    }

    /** Prints a formatted summary report and evaluates success criteria. */
    public void generateReport(String scenarioLabel, List<Doctor> doctors,
                               List<Nurse> nurses, List<TreatmentRoom> rooms,
                               double simDurationMins, double actualElapsedMinutes) {
        System.out.println("=".repeat(60));
        System.out.println("  SIMULATION REPORT — " + scenarioLabel);
        System.out.println("=".repeat(60));

        System.out.println("\n--- Patient Metrics ---");
        System.out.printf("  Patients arrived              : %d%n", patientsArrived);
        System.out.printf("  Patients discharged (total)   : %d%n", getThroughput());
        System.out.printf("  Completion rate (incl. drain) : %.1f%%%n", getCompletionRate());
        System.out.printf("  Discharged within window      : %d%n", getDischargedWithinWindow(simDurationMins));
        System.out.printf("  Completion rate (within window): %.1f%%%n", getCompletionRateWithinWindow(simDurationMins));
        System.out.printf("  Avg wait time                 : %.2f min%n", getAverageWaitTime());
        System.out.printf("  Max wait time                 : %.2f min%n", getMaxWaitTime());
        System.out.printf("  Avg queue length               : %.2f patients%n", getAverageQueueLength());
        System.out.printf("  Actual elapsed sim time        : %.1f min (nominal window %.1f min)%n",
                actualElapsedMinutes, simDurationMins);

        System.out.println("\n--- Wait Time by Severity (min, discharged only) ---");
        for (SeverityLevel level : SeverityLevel.values()) {
            System.out.printf("  %-9s: avg=%7.2f  n=%d%n",
                    level, getAvgWaitTimeBySeverity(level), getCountBySeverity(level));
        }

        System.out.println("\n--- Success Criteria ---");
        checkCriterion("Avg wait < 30 min",              getAverageWaitTime()    <  30.0);
        checkCriterion("Avg queue < 10 patients",         getAverageQueueLength() <  10.0);
        checkCriterion("Completion rate (window) >= 95%", getCompletionRateWithinWindow(simDurationMins) >= 95.0);

        System.out.println("\n--- Doctor Utilisation (vs. actual elapsed time) ---");
        double total = 0;
        for (Doctor d : doctors) {
            double u = d.getUtilizationRate(actualElapsedMinutes) * 100;
            total += u;
            System.out.printf("  Doctor %-2d : %5.1f%%  (treated %d)%n",
                    d.getDoctorID(), u, d.getPatientsTreated());
        }
        double avg = doctors.isEmpty() ? 0 : total / doctors.size();
        System.out.printf("  Avg       : %5.1f%%%n", avg);
        checkCriterion("Doctor util 70-90%", avg >= 70.0 && avg <= 90.0);

        System.out.println("\n--- Nurse Utilisation ---");
        for (Nurse n : nurses)
            System.out.printf("  Nurse  %-2d : %5.1f%%  (triaged %d)%n",
                    n.getNurseID(), n.getUtilizationRate(actualElapsedMinutes) * 100, n.getPatientsTriaged());

        System.out.println("\n--- Room Utilisation ---");
        for (TreatmentRoom r : rooms)
            System.out.printf("  Room   %-2d : %5.1f%%  (assignments %d)%n",
                    r.getRoomID(), r.getUtilizationRate(actualElapsedMinutes) * 100, r.getTotalAssignments());

        System.out.println("=".repeat(60));
        System.out.println();
    }

    private void checkCriterion(String label, boolean passed) {
        System.out.printf("  [%s] %s%n", passed ? "PASS" : "FAIL", label);
    }
}
