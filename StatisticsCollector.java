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
 *
 * @see <a href="../../../../../../../docs/class-diagram.drawio">Class Diagram (Draw.io)</a>
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
    public double getCompletionRate()  {
        return patientsArrived > 0 ? (double) dischargedPatients.size() / patientsArrived * 100.0 : 0.0;
    }

    /** Returns an unmodifiable view of discharged patients (for CSV export). */
    public List<Patient> getDischargedPatients() {
        return Collections.unmodifiableList(dischargedPatients);
    }

    public double getAvgDoctorUtil(List<Doctor> doctors, double simMins) {
        return doctors.stream().mapToDouble(d -> d.getUtilizationRate(simMins) * 100).average().orElse(0.0);
    }
    public double getAvgNurseUtil(List<Nurse> nurses, double simMins) {
        return nurses.stream().mapToDouble(n -> n.getUtilizationRate(simMins) * 100).average().orElse(0.0);
    }
    public double getAvgRoomUtil(List<TreatmentRoom> rooms, double simMins) {
        return rooms.stream().mapToDouble(r -> r.getUtilizationRate(simMins) * 100).average().orElse(0.0);
    }

    /** Prints a formatted summary report and evaluates success criteria. */
    public void generateReport(String scenarioLabel, List<Doctor> doctors,
                               List<Nurse> nurses, List<TreatmentRoom> rooms,
                               double simDurationMins) {
        System.out.println("=".repeat(60));
        System.out.println("  SIMULATION REPORT — " + scenarioLabel);
        System.out.println("=".repeat(60));

        System.out.println("\n--- Patient Metrics ---");
        System.out.printf("  Patients arrived      : %d%n", patientsArrived);
        System.out.printf("  Patients discharged   : %d%n", getThroughput());
        System.out.printf("  Completion rate       : %.1f%%%n", getCompletionRate());
        System.out.printf("  Avg wait time         : %.2f min%n", getAverageWaitTime());
        System.out.printf("  Max wait time         : %.2f min%n", getMaxWaitTime());
        System.out.printf("  Avg queue length      : %.2f patients%n", getAverageQueueLength());

        System.out.println("\n--- Success Criteria ---");
        checkCriterion("Avg wait < 30 min",       getAverageWaitTime()    <  30.0);
        checkCriterion("Avg queue < 10 patients",  getAverageQueueLength() <  10.0);
        checkCriterion("Completion rate >= 95%",   getCompletionRate()     >= 95.0);

        System.out.println("\n--- Doctor Utilisation ---");
        double total = 0;
        for (Doctor d : doctors) {
            double u = d.getUtilizationRate(simDurationMins) * 100;
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
                n.getNurseID(), n.getUtilizationRate(simDurationMins) * 100, n.getPatientsTriaged());

        System.out.println("\n--- Room Utilisation ---");
        for (TreatmentRoom r : rooms)
            System.out.printf("  Room   %-2d : %5.1f%%  (assignments %d)%n",
                r.getRoomID(), r.getUtilizationRate(simDurationMins) * 100, r.getTotalAssignments());

        System.out.println("=".repeat(60));
        System.out.println();
    }

    private void checkCriterion(String label, boolean passed) {
        System.out.printf("  [%s] %s%n", passed ? "PASS" : "FAIL", label);
    }
}
