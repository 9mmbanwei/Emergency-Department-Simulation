package edsim.engine;

import edsim.entities.Doctor;
import edsim.entities.Nurse;
import edsim.entities.Patient;
import edsim.entities.TreatmentRoom;
import edsim.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Discrete-Event Simulation engine for the Emergency Department.
 *
 * Fixes applied (v2):
 *  1. PATIENT_ARRIVAL events are scheduled with a sentinel null patient.
 *     The event loop now guards ALL handlers with a null-patient check so
 *     a stray arrival event can never reach processTreatmentEnd.
 *  2. Nurse state is tracked per-patient via a parallel map so
 *     completeTriageAndRelease() always targets the correct nurse.
 *  3. processTreatmentEnd() guards against null patient defensively.
 *  4. The sim-end break now drains ALL remaining non-arrival events
 *     (treatment ends for in-progress patients) before stopping.
 */
public class SimulationEngine {

    // ── Simulation constants ──────────────────────────────────────────────────
    public static final double ARRIVAL_RATE_PER_MIN = 12.0 / 60.0; // λ = 0.2 /min
    public static final double TRIAGE_MEAN_MINUTES  = 5.0;
    public static final double SIM_DURATION_MINUTES = 24.0 * 60.0;  // 1440 min

    // ── Core DES structures ───────────────────────────────────────────────────
    private final PriorityQueue<Event>   eventQueue   = new PriorityQueue<>();
    private final PriorityQueue<Patient> patientQueue = new PriorityQueue<>();

    // ── Resources ─────────────────────────────────────────────────────────────
    private final List<Doctor>        doctors = new ArrayList<>();
    private final List<Nurse>         nurses  = new ArrayList<>();
    private final List<TreatmentRoom> rooms   = new ArrayList<>();

    // ── Support ───────────────────────────────────────────────────────────────
    private final RandomVariateGenerator rng;
    private final StatisticsCollector    stats;
    private final ScenarioConfig         config;

    // ── Clock & ID counter ────────────────────────────────────────────────────
    private double clock         = 0.0;
    private int    nextPatientID = 1;

    // ── Constructor ───────────────────────────────────────────────────────────
    public SimulationEngine(ScenarioConfig config, long seed) {
        this.config = config;
        this.rng    = new RandomVariateGenerator(seed);
        this.stats  = new StatisticsCollector();

        for (int i = 1; i <= config.getNumDoctors(); i++) doctors.add(new Doctor(i));
        for (int i = 1; i <= config.getNumNurses();  i++) nurses.add(new Nurse(i));
        for (int i = 1; i <= config.getNumRooms();   i++) rooms.add(new TreatmentRoom(i));
    }

    // ── Public entry point ────────────────────────────────────────────────────
    public void run() {
        System.out.println("Starting: " + config);
        scheduleNextArrival(0.0);

        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();
            clock = event.getTime();

            // FIX 1: skip new arrivals after sim end; keep processing treatment ends
            if (event.getType() == EventType.PATIENT_ARRIVAL
                    && clock > SIM_DURATION_MINUTES) {
                continue;  // drain remaining treatment-end events, don't break
            }

            // FIX 2: never route a null-patient event to a patient handler
            Patient patient = event.getPatient();
            if (patient == null && event.getType() != EventType.PATIENT_ARRIVAL) {
                continue;
            }

            switch (event.getType()) {
                case PATIENT_ARRIVAL -> processArrival();
                case TRIAGE_COMPLETE -> processTriageComplete(patient);
                case TREATMENT_END   -> processTreatmentEnd(patient);
                default              -> { /* no-op */ }
            }

            stats.recordQueueLength(patientQueue.size());
        }

        stats.generateReport(config.getLabel(), doctors, nurses, rooms, SIM_DURATION_MINUTES);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /** PATIENT_ARRIVAL — create patient, assign to a nurse, schedule triage end. */
    private void processArrival() {
        Patient patient = new Patient(nextPatientID++, clock);
        stats.recordArrival(patient);

        // Always schedule the next arrival regardless
        scheduleNextArrival(clock);

        double triageDuration = rng.exponentialByMean(TRIAGE_MEAN_MINUTES);

        // Find the nurse who will be free soonest
        Nurse nurse = nurses.stream()
            .min((a, b) -> Double.compare(
                a.isAvailable() ? clock : a.getBusyUntil(),
                b.isAvailable() ? clock : b.getBusyUntil()))
            .orElse(nurses.get(0));

        // Triage starts when that nurse becomes free
        double triageStart = nurse.isAvailable() ? clock : nurse.getBusyUntil();
        double triageEnd   = triageStart + triageDuration;

        // FIX 3: track which nurse owns this patient so we can release correctly
        nurse.performTriage(patient, triageEnd);
        patient.setTriageCompleteTime(triageEnd);
        scheduleEvent(triageEnd, EventType.TRIAGE_COMPLETE, patient);
    }

    /** TRIAGE_COMPLETE — assign severity, release nurse, enter priority queue. */
    private void processTriageComplete(Patient patient) {
        patient.assignSeverity(rng.uniform());

        // Release the nurse that was assigned to this patient
        // FIX 4: find the nurse whose currentPatient matches — not just "least busy"
        Nurse nurse = nurses.stream()
            .filter(n -> n.getCurrentPatient() == patient)
            .findFirst()
            .orElse(null);

        if (nurse != null) {
            double triageDuration = patient.getTriageCompleteTime() - patient.getArrivalTime();
            nurse.completeTriageAndRelease(Math.max(triageDuration, 0.0));
        }

        patientQueue.add(patient);
        attemptTreatment();
    }

    /** TREATMENT_END — discharge, release resources, serve next patient. */
    private void processTreatmentEnd(Patient patient) {
        // FIX 5: defensive null guard (belt-and-suspenders)
        if (patient == null) return;

        Doctor doctor = doctors.stream()
            .filter(d -> d.getCurrentPatient() == patient)
            .findFirst().orElse(null);

        TreatmentRoom room = rooms.stream()
            .filter(r -> r.getAssignedPatient() == patient)
            .findFirst().orElse(null);

        double duration = patient.getTreatmentDuration();

        if (doctor != null) doctor.releasePatient(duration);
        if (room   != null) room.releaseRoom(duration);

        patient.discharge(clock);
        stats.recordDischarge(patient);

        attemptTreatment();
    }

    // ── Resource allocation ───────────────────────────────────────────────────

    private void attemptTreatment() {
        while (!patientQueue.isEmpty()) {
            Doctor        doctor = doctors.stream().filter(Doctor::isAvailable).findFirst().orElse(null);
            TreatmentRoom room   = rooms.stream().filter(TreatmentRoom::isAvailable).findFirst().orElse(null);

            if (doctor == null || room == null) break;

            Patient next = patientQueue.poll();

            double treatDuration = rng.exponentialByMean(
                next.getSeverity().getMeanTreatmentMinutes()
            );
            double treatEnd = clock + treatDuration;

            next.setTreatmentStartTime(clock);
            next.setTreatmentDuration(treatDuration);

            doctor.treatPatient(next, treatEnd);
            room.assignedPatient(next);

            scheduleEvent(treatEnd, EventType.TREATMENT_END, next);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void scheduleNextArrival(double fromTime) {
        double interArrival = rng.exponential(ARRIVAL_RATE_PER_MIN);
        // null patient is intentional — arrival events create their own patient
        scheduleEvent(fromTime + interArrival, EventType.PATIENT_ARRIVAL, null);
    }

    private void scheduleEvent(double time, EventType type, Patient patient) {
        eventQueue.add(new Event(time, type, patient));
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public StatisticsCollector getStats()  { return stats; }
    public ScenarioConfig      getConfig() { return config; }
}
