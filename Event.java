package edsim.engine;

import edsim.entities.Patient;


/**
 * Represents a single discrete event in the simulation.
 * Events are ordered by time so the engine always processes the next
 * chronological event first.
 */
public class Event implements Comparable<Event> {

    private final double    time;       // simulation clock time this event fires
    private final EventType type;  // FIX: was `static` — shared across ALL Event
                                   // instances, corrupting getType() for every
                                   // event still sitting in the queue.
    private final Patient   patient;    // patient associated with this event



    public Event(double time, EventType type, Patient patient) {
        this.time    = time;
        this.type    = type;
        this.patient = patient;
    }



    @Override
    public int compareTo(Event other) {
        return Double.compare(this.time, other.time);
    }

    public double getTime()    { return time; }
    public EventType getType()    { return type; } //QUICK FIX this was static
    public Patient   getPatient() { return patient; }

    @Override
    public String toString() {
        return String.format("Event[t=%.2f, type=%s, patient=%d]",
                time, type, patient != null ? patient.getPatientID() : -1);
    }
}
