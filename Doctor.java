package edsim.entities;

/**
 * Represents a physician in the emergency department.
 *
 * Doctors treat patients after triage. Each doctor can treat one patient at a
 * time and requires a TreatmentRoom to be assigned alongside them.
 */
public class Doctor {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final int doctorID;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean available;

    /** Patient currently under treatment (null when idle). */
    private Patient currentPatient;

    /** Simulation clock time when this doctor will be free again. */
    private double busyUntil;

    // ── Statistics ────────────────────────────────────────────────────────────
    private int    patientsTreated;
    private double totalBusyTime;   // accumulated minutes spent treating patients

    // ── Constructor ───────────────────────────────────────────────────────────
    public Doctor(int doctorID) {
        this.doctorID  = doctorID;
        this.available = true;
        this.busyUntil = 0.0;
    }

    // ── Business logic ────────────────────────────────────────────────────────

    /**
     * Assigns a patient to this doctor for treatment.
     *
     * @param patient        the patient to treat
     * @param completionTime simulation clock time when treatment will end
     */
    public void treatPatient(Patient patient, double completionTime) {
        if (!available) {
            throw new IllegalStateException(
                "Doctor " + doctorID + " is already treating a patient."
            );
        }
        this.currentPatient = patient;
        this.busyUntil      = completionTime;
        this.available      = false;
    }

    /**
     * Releases the doctor after treatment is complete.
     * Updates utilisation statistics.
     *
     * @param treatmentDuration how long (minutes) the treatment took
     * @return the patient who was just treated
     */
    public Patient releasePatient(double treatmentDuration) {
        Patient finished    = this.currentPatient;
        this.currentPatient = null;
        this.available      = true;
        this.busyUntil      = 0.0;
        this.patientsTreated++;
        this.totalBusyTime  += treatmentDuration;
        return finished;
    }

    /**
     * Returns true if this doctor is free at or before {@code currentTime}.
     *
     * @param currentTime current simulation clock
     */
    public boolean isAvailableAt(double currentTime) {
        return available || busyUntil <= currentTime;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getDoctorID()        { return doctorID; }
    public boolean isAvailable()        { return available; }
    public Patient getCurrentPatient()  { return currentPatient; }
    public double  getBusyUntil()       { return busyUntil; }
    public int     getPatientsTreated() { return patientsTreated; }
    public double  getTotalBusyTime()   { return totalBusyTime; }

    /**
     * Utilisation rate over the simulation period.
     *
     * @param simulationDuration total simulation time in minutes
     * @return fraction of time the doctor was busy (0.0 – 1.0)
     */
    public double getUtilizationRate(double simulationDuration) {
        return (simulationDuration > 0) ? totalBusyTime / simulationDuration : 0.0;
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
            "Doctor[id=%d, available=%b, treated=%d, utilization=%.1f%%]",
            doctorID, available, patientsTreated,
            getUtilizationRate(1) * 100   // placeholder; pass real duration at report time
        );
    }
}
