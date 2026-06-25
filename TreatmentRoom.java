package edsim.entities;

/**
 * Represents a treatment room in the emergency department.
 *
 * A treatment room must be available alongside a doctor before a patient
 * can begin treatment. Rooms are identical (no specialisation).
 */
public class TreatmentRoom {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final int roomID;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean occupied;

    /** The patient currently occupying this room (null if empty). */
    private Patient assignedPatient;

    // ── Statistics ────────────────────────────────────────────────────────────
    private int    totalAssignments;
    private double totalOccupiedTime; // accumulated minutes this room was in use

    // ── Constructor ───────────────────────────────────────────────────────────
    public TreatmentRoom(int roomID) {
        this.roomID   = roomID;
        this.occupied = false;
    }

    // ── Business logic ────────────────────────────────────────────────────────

    /**
     * Assigns a patient to this room, marking it as occupied.
     *
     * @param patient the patient being placed in this room
     */
    public void assignedPatient(Patient patient) {
        if (occupied) {
            throw new IllegalStateException(
                "Room " + roomID + " is already occupied."
            );
        }
        this.assignedPatient = patient;
        this.occupied        = true;
        this.totalAssignments++;
    }

    /**
     * Releases the room once treatment is complete.
     *
     * @param occupancyDuration how long (minutes) the room was in use
     * @return the patient who was just discharged from this room
     */
    public Patient releaseRoom(double occupancyDuration) {
        Patient finished      = this.assignedPatient;
        this.assignedPatient  = null;
        this.occupied         = false;
        this.totalOccupiedTime += occupancyDuration;
        return finished;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getRoomID()           { return roomID; }
    public boolean isOccupied()          { return occupied; }
    public boolean isAvailable()         { return !occupied; }
    public Patient getAssignedPatient()  { return assignedPatient; }
    public int     getTotalAssignments() { return totalAssignments; }
    public double  getTotalOccupiedTime(){ return totalOccupiedTime; }

    /**
     * Utilisation rate over the simulation period.
     *
     * @param simulationDuration total simulation time in minutes
     * @return fraction of time the room was occupied (0.0 – 1.0)
     */
    public double getUtilizationRate(double simulationDuration) {
        return (simulationDuration > 0) ? totalOccupiedTime / simulationDuration : 0.0;
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
            "TreatmentRoom[id=%d, occupied=%b, assignments=%d]",
            roomID, occupied, totalAssignments
        );
    }
}
