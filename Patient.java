package edsim.entities;

import edsim.enums.SeverityLevel;

/**
 * Represents a patient moving through the emergency department.
 *
 * Implements Comparable so patients can be ordered inside a PriorityQueue:
 *   - Higher severity (lower code number) comes first.
 *   - Ties broken by arrival time (FCFS within same severity group).
 */
public class Patient implements Comparable<Patient> {

    // ── Identity ─────────────────────────────────────────────────────────────
    private final int patientID;

    // ── Timing (minutes from simulation start) ────────────────────────────────
    private final double arrivalTime;
    private double       triageCompleteTime;   // when nurse finishes triage
    private double       treatmentStartTime;   // when doctor begins treatment
    private double       treatmentEndTime;     // when patient is discharged
    private double       treatmentDuration;    // sampled from Exponential dist.

    // ── Classification ────────────────────────────────────────────────────────
    private SeverityLevel severity;

    // ── State flag ────────────────────────────────────────────────────────────
    private boolean discharged = false;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Patient(int patientID, double arrivalTime) {
        this.patientID   = patientID;
        this.arrivalTime = arrivalTime;
    }

    // ── Business logic ────────────────────────────────────────────────────────

    /**
     * Assigns a severity level to the patient (called during triage).
     *
     * @param rand uniform random value in [0, 1) used for inverse-transform sampling
     */
    public void assignSeverity(double rand) {
        this.severity = SeverityLevel.assign(rand);
    }

    /**
     * Calculates how long the patient waited before treatment began.
     * Wait time = time treatment started − time patient arrived.
     *
     * @return wait time in minutes, or 0 if treatment has not yet started
     */
    public double calculateWaitTime() {
        if (treatmentStartTime <= 0) return 0.0;
        return treatmentStartTime - arrivalTime;
    }

    /**
     * Marks the patient as discharged and records the end time.
     *
     * @param endTime simulation clock time at discharge
     */
    public void discharge(double endTime) {
        this.treatmentEndTime = endTime;
        this.discharged       = true;
    }

    // ── Comparable (priority queue ordering) ─────────────────────────────────

    /**
     * Patients with lower severity code (more critical) come first.
     * Within the same severity, earlier arrivals come first (FCFS).
     */
    @Override
    public int compareTo(Patient other) {
        int severityCompare = Integer.compare(this.severity.getCode(), other.severity.getCode());
        if (severityCompare != 0) return severityCompare;
        return Double.compare(this.arrivalTime, other.arrivalTime);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int           getPatientID()            { return patientID; }
    public double        getArrivalTime()           { return arrivalTime; }
    public SeverityLevel getSeverity()              { return severity; }
    public boolean       isDischarged()             { return discharged; }
    public double        getTreatmentDuration()     { return treatmentDuration; }
    public double        getTriageCompleteTime()    { return triageCompleteTime; }
    public double        getTreatmentStartTime()    { return treatmentStartTime; }
    public double        getTreatmentEndTime()      { return treatmentEndTime; }

    public void setSeverity(SeverityLevel severity)             { this.severity             = severity; }
    public void setTriageCompleteTime(double triageCompleteTime){ this.triageCompleteTime   = triageCompleteTime; }
    public void setTreatmentStartTime(double treatmentStartTime){ this.treatmentStartTime   = treatmentStartTime; }
    public void setTreatmentDuration(double treatmentDuration)  { this.treatmentDuration    = treatmentDuration; }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
            "Patient[id=%d, severity=%s, arrival=%.2f, wait=%.2f min, discharged=%b]",
            patientID, severity, arrivalTime, calculateWaitTime(), discharged
        );
    }
}
