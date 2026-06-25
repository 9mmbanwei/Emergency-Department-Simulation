package edsim.engine;

import edsim.entities.Doctor;
import edsim.entities.Nurse;
import edsim.entities.Patient;
import edsim.entities.TreatmentRoom;
import edsim.enums.SeverityLevel;
import edsim.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Discrete-Event Simulation engine for the Emergency Department.
 *
 * Event loop:
 *   1. PATIENT_ARRIVAL  → schedule next arrival + TRIAGE_COMPLETE
 *   2. TRIAGE_COMPLETE  → assign severity, enter priority queue,
 *                         attempt to start treatment immediately
 *   3. TREATMENT_START  → record start time (fired internally by triage logic)
 *   4. TREATMENT_END    → discharge patient, release doctor + room,
 *                         attempt to serve next queued patient
 *   5. PATIENT_DISCHARGE→ recorded by stats collector
 *
 * Parameters (from spec):
 *   Arrival rate λ = 12 patients/hour = 0.2 patients/min
 *   Triage mean    = 5 minutes  (Exponential)
 *   Treatment mean = per SeverityLevel (Exponential)
 *   Sim duration   = 24 hours  = 1440 minutes
 */
public class SimulationEngine {

    // ── Simulation constants ──────────────────────────────────────────────────
    public static final double ARRIVAL_RATE_PER_MIN = 12.0 / 60.0; // λ = 0.2 /min
    public static final double TRIAGE_MEAN_MINUTES  = 5.0;
    public static final double SIM_DURATION_HOURS   = 24.0;
    public static final double SIM_DURATION_MINUTES = SIM_DURATION_HOURS * 60.0;

    // ── Core DES data structures ──────────────────────────────────────────────
    /** Global event queue — always processes the earliest event next. */
    private final PriorityQueue<Event>   eventQueue   = new PriorityQueue<>();

    /** Priority treatment queue — sorted by severity then FCFS. */
    private final PriorityQueue<Patient> patientQueue = new PriorityQueue<>();

    // ── Resources ─────────────────────────────────────────────────────────────
    private final List<Doctor>        doctors = new ArrayList<>();
    private final List<Nurse>         nurses  = new ArrayList<>();
    private final List<TreatmentRoom> rooms   = new ArrayList<>();

    // ── Support objects ───────────────────────────────────────────────────────
    private final RandomVariableGenerator rng;
    private final StatisticsCollector    stats;
    private final ScenarioConfig         config;

    // ── State ─────────────────────────────────────────────────────────────────
    private double clock        = 0.0;
    private int    nextPatientID = 1;

    // ── Constructor ───────────────────────────────────────────────────────────
    public SimulationEngine(ScenarioConfig config, long seed) {
        this.config = config;
        this.rng    = new RandomVariableGenerator(seed);
        this.stats  = new StatisticsCollector();

        // Initialise resources per scenario
        for (int i = 1; i <= config.getNumDoctors(); i++) doctors.add(new Doctor(i));
        for (int i = 1; i <= config.getNumNurses();  i++) nurses.add(new Nurse(i));
        for (int i = 1; i <= config.getNumRooms();   i++) rooms.add(new TreatmentRoom(i));
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Runs the simulation for the configured duration and prints a report.
     */
    public void run() {
        System.out.println("Starting: " + config);

        // Seed the event queue with the first patient arrival
        scheduleNextArrival(0.0);

        // ── Main event loop ───────────────────────────────────────────────────
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();
            clock = event.getTime();

            // Stop processing new arrivals after simulation end,
            // but finish events already in the queue for in-progress patients.
            if (clock > SIM_DURATION_MINUTES && Event.getType() == EventType.PATIENT_ARRIVAL) {
                break;
            }

            switch (Event.getType()) {
                case PATIENT_ARRIVAL   -> processArrival(event);
                case TRIAGE_COMPLETE   -> processTriageComplete(event);
                case TREATMENT_END     -> processTreatmentEnd(event);
                default                -> { /* TREATMENT_START / DISCHARGE handled inline */ }
            }

            // Sample queue length after every event for statistics
            stats.recordQueueLength(patientQueue.size());
        }

        // Generate final report
        stats.generateReport(config.getLabel(), doctors, nurses, rooms, SIM_DURATION_MINUTES);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * PATIENT_ARRIVAL: create patient, schedule next arrival and triage completion.
     */
    private void processArrival(Event event) {
        Patient patient = new Patient(nextPatientID++, clock);
        stats.recordArrival(patient);

        // Schedule the next patient arrival (Poisson process → exponential inter-arrivals)
        scheduleNextArrival(clock);

        // Find an available nurse; if none free, triage is delayed until one is
        Nurse nurse = findAvailableNurse();
        double triageDuration = rng.exponentialByMean(TRIAGE_MEAN_MINUTES);

        if (nurse != null) {
            // Nurse available immediately
            double triageEnd = clock + triageDuration;
            nurse.performTriage(patient, triageEnd);
            patient.setTriageCompleteTime(triageEnd);
            scheduleEvent(triageEnd, EventType.TRIAGE_COMPLETE, patient);
        } else {
            // No nurse free — find the earliest finishing nurse and queue behind them
            double earliestFree = nurses.stream()
                    .mapToDouble(Nurse::getBusyUntil)
                    .min()
                    .orElse(clock);
            double triageEnd = earliestFree + triageDuration;

            // Assign to that nurse (simplification: pick the earliest-free nurse)
            Nurse soonestNurse = nurses.stream()
                    .min((a, b) -> Double.compare(a.getBusyUntil(), b.getBusyUntil()))
                    .orElse(nurses.get(0));

            // We schedule a future triage completion; nurse state will be updated
            // when triage_complete fires (nurse may have freed by then).
            patient.setTriageCompleteTime(triageEnd);
            scheduleEvent(triageEnd, EventType.TRIAGE_COMPLETE, patient);
        }
    }

    /**
     * TRIAGE_COMPLETE: assign severity, place in priority queue, attempt treatment.
     */
    private void processTriageComplete(Event event) {
        Patient patient = event.getPatient();
        patient.assignSeverity(rng.uniform());

        // Free up a nurse
        Nurse nurse = findLeastBusyNurse();
        if (nurse != null && !nurse.isAvailable()) {
            double triageDuration = patient.getTriageCompleteTime() - patient.getArrivalTime();
            nurse.completeTriageAndRelease(Math.max(triageDuration, TRIAGE_MEAN_MINUTES));
        }

        // Enter the non-preemptive priority queue
        patientQueue.add(patient);

        // Immediately attempt to assign a doctor + room
        attemptTreatment();
    }

    /**
     * TREATMENT_END: discharge patient, release resources, serve next in queue.
     */
    private void processTreatmentEnd(Event event) {
        Patient patient = event.getPatient();

        // Release doctor
        Doctor doctor = findDoctorTreating(patient);
        if (doctor != null) {
            doctor.releasePatient(patient.getTreatmentDuration());
        }

        // Release room
        TreatmentRoom room = findRoomOccupiedBy(patient);
        if (room != null) {
            room.releaseRoom(patient.getTreatmentDuration());
        }

        // Discharge
        patient.discharge(clock);
        stats.recordDischarge(patient);

        // Try to serve the next patient in the priority queue
        attemptTreatment();
    }

    // ── Resource allocation ───────────────────────────────────────────────────

    /**
     * Attempts to pull the highest-priority patient from the queue and start
     * treatment if both a doctor and a room are available.
     */
    private void attemptTreatment() {
        while (!patientQueue.isEmpty()) {
            Doctor        doctor = findAvailableDoctor();
            TreatmentRoom room   = findAvailableRoom();

            if (doctor == null || room == null) break; // no capacity

            Patient patient = patientQueue.poll();

            // Sample treatment duration from the patient's severity distribution
            double treatDuration = rng.exponentialByMean(
                    patient.getSeverity().getMeanTreatmentMinutes()
            );

            double treatEnd = clock + treatDuration;

            // Update patient
            patient.setTreatmentStartTime(clock);
            patient.setTreatmentDuration(treatDuration);

            // Assign resources
            doctor.treatPatient(patient, treatEnd);
            room.assignedPatient(patient);

            // Schedule treatment completion
            scheduleEvent(treatEnd, EventType.TREATMENT_END, patient);
        }
    }

    // ── Scheduling helpers ────────────────────────────────────────────────────

    private void scheduleNextArrival(double fromTime) {
        double interArrival = rng.exponential(ARRIVAL_RATE_PER_MIN);
        scheduleEvent(fromTime + interArrival, EventType.PATIENT_ARRIVAL, null);
    }

    private void scheduleEvent(double time, EventType type, Patient patient) {
        eventQueue.add(new Event(time, type, patient));
    }

    // ── Resource finders ──────────────────────────────────────────────────────

    private Nurse findAvailableNurse() {
        return nurses.stream().filter(Nurse::isAvailable).findFirst().orElse(null);
    }

    private Nurse findLeastBusyNurse() {
        return nurses.stream()
                .min((a, b) -> Double.compare(a.getBusyUntil(), b.getBusyUntil()))
                .orElse(null);
    }

    private Doctor findAvailableDoctor() {
        return doctors.stream().filter(Doctor::isAvailable).findFirst().orElse(null);
    }

    private TreatmentRoom findAvailableRoom() {
        return rooms.stream().filter(TreatmentRoom::isAvailable).findFirst().orElse(null);
    }

    private Doctor findDoctorTreating(Patient patient) {
        return doctors.stream()
                .filter(d -> d.getCurrentPatient() == patient)
                .findFirst().orElse(null);
    }

    private TreatmentRoom findRoomOccupiedBy(Patient patient) {
        return rooms.stream()
                .filter(r -> r.getAssignedPatient() == patient)
                .findFirst().orElse(null);
    }

    // ── Getters (for multi-run comparisons) ───────────────────────────────────
    public StatisticsCollector getStats()  { return stats; }
    public ScenarioConfig      getConfig() { return config; }
}