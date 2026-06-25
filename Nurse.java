package edsim.entities;

/**
 * Represents a triage nurse in the emergency department.
 *
 * Nurses perform triage on arriving patients before they enter the priority
 * queue. Each nurse can handle one patient at a time.
 */
public class Nurse {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final int nurseID;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean available;

    /** The patient currently being triaged (null if idle). */
    private Patient currentPatient;

    /** Simulation clock time when this nurse will finish and become free. */
    private double busyUntil;

    // ── Statistics ────────────────────────────────────────────────────────────
    private int    patientsTriaged;
    private double totalBusyTime;   // accumulated minutes spent on triage

    // ── Constructor ───────────────────────────────────────────────────────────
    public Nurse(int nurseID) {
        this.nurseID   = nurseID;
        this.available = true;
        this.busyUntil = 0.0;
    }

    // ── Business logic ────────────────────────────────────────────────────────

    /**
     * Assigns a patient to this nurse for triage.
     * The nurse becomes unavailable until {@code completionTime}.
     *
     * @param patient        the patient to triage
     * @param completionTime simulation clock time when triage will finish
     */
    public void performTriage(Patient patient, double completionTime) {
        if (!available) {
            throw new IllegalStateException(
                "Nurse " + nurseID + " is already occupied."
            );
        }
        this.currentPatient = patient;
        this.busyUntil      = completionTime;
        this.available      = false;
    }

    /**
     * Releases the nurse after triage is complete.
     * Updates utilisation statistics.
     *
     * @param triageDuration how long (minutes) the triage took
     * @return the patient who was just triaged
     */
    public Patient completeTriageAndRelease(double triageDuration) {
        Patient finished    = this.currentPatient;
        this.currentPatient = null;
        this.available      = true;
        this.busyUntil      = 0.0;
        this.patientsTriaged++;
        this.totalBusyTime += triageDuration;
        return finished;
    }

    /**
     * Checks whether this nurse will be free by the given simulation time.
     *
     * @param currentTime current simulation clock
     * @return true if available or will become available at/before currentTime
     */
    public boolean isAvailableAt(double currentTime) {
        return available || busyUntil <= currentTime;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getNurseID()        { return nurseID; }
    public boolean isAvailable()       { return available; }
    public Patient getCurrentPatient() { return currentPatient; }
    public double  getBusyUntil()      { return busyUntil; }
    public int     getPatientsTriaged(){ return patientsTriaged; }
    public double  getTotalBusyTime()  { return totalBusyTime; }

    /**
     * Utilisation rate over a simulation period.
     *
     * @param simulationDuration total simulation time in minutes
     * @return fraction of time the nurse was busy (0.0 – 1.0)
     */
    public double getUtilizationRate(double simulationDuration) {
        return (simulationDuration > 0) ? totalBusyTime / simulationDuration : 0.0;
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
            "Nurse[id=%d, available=%b, triaged=%d]",
            nurseID, available, patientsTriaged
        );
    }
}
