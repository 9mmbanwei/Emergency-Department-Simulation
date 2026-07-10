package edsim.entities;

/**
 * Represents a triage nurse in the emergency department.
 *
 * Nurses perform triage on arriving patients before they enter the priority
 * queue. Each nurse can handle one patient at a time.
 *
 * <p><b>Scheduling model:</b> the engine assigns each arriving patient to
 * whichever nurse becomes free <i>soonest</i> (see
 * {@code SimulationEngine.processArrival()}), computing that nurse's next
 * triage interval analytically as {@code [max(clock, busyUntil), ... ]}.
 * This means a nurse can legitimately receive a new reservation while still
 * "occupied" by an earlier patient — that is not an error, it's the queueing
 * model. {@link #performTriage} must therefore accept forward reservations
 * rather than requiring the nurse be idle at the moment of assignment.</p>
 */
public class Nurse {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final int nurseID;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean available;

    /** The patient currently (or most recently) assigned for triage. */
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
     *
     * FIX: previously threw {@code IllegalStateException} whenever the nurse
     * was already occupied. That conflicted with the engine's forward-looking
     * scheduling model (assigning to the soonest-available nurse based on
     * {@code busyUntil}, ahead of that nurse's current patient actually
     * finishing). A reservation is now always accepted; {@code busyUntil} is
     * only ever extended forward because the engine always computes the new
     * interval starting at {@code max(clock, busyUntil)}.
     *
     * @param patient        the patient to triage
     * @param completionTime simulation clock time when triage will finish
     */
    public void performTriage(Patient patient, double completionTime) {
        this.currentPatient = patient;
        this.busyUntil      = completionTime;
        this.available      = false;
    }

    /**
     * Releases the nurse after triage is complete.
     * Updates utilisation statistics.
     *
     * FIX: only flips {@code available} back to true if this release
     * corresponds to the nurse's most recent reservation (i.e. no later
     * patient has already been queued onto this nurse). Otherwise the nurse
     * is still logically occupied by a subsequent reservation and must stay
     * marked busy.
     *
     * @param triageDuration how long (minutes) the triage took
     * @param completionTime the completion time this release corresponds to
     * @return the patient who was just triaged
     */
    public Patient completeTriageAndRelease(double triageDuration, double completionTime) {
        Patient finished = this.currentPatient;
        this.patientsTriaged++;
        this.totalBusyTime += triageDuration;
        if (Double.compare(completionTime, this.busyUntil) == 0) {
            this.currentPatient = null;
            this.available      = true;
        }
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
