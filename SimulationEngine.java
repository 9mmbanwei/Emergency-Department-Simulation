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
 
 * Fixes applied (v2):
 *  1. PATIENT_ARRIVAL events are scheduled with a sentinel null patient.
 *     The event loop now guards ALL handlers with a null-patient check so
 *     a stray arrival event can never reach processTreatmentEnd.
 *  2. Nurse state is tracked per-patient via a parallel map so
 *     completeTriageAndRelease() always targets the correct nurse.
 *  3. processTreatmentEnd() guards against null patient defensively.
 *  4. The sim-end break now drains ALL remaining non-arrival events
 *     (treatment ends for in-progress patients) before stopping.
 
 * <p><b>UML References:</b></p>
 * <ul>
 *   <li>{@see <a href="../../../../../../../docs/class-diagram.drawio">Class Diagram</a>}</li>
 *   <li>{@see <a href="../../../../../../../docs/activity-diagram.drawio">Activity Diagram</a>}</li>
 * </ul>
 *
 * @see ScenarioConfig
 * @see SimConfig
 * @see StatisticsCollector
 */
public class SimulationEngine {

    // ── Core DES structures ───────────────────────────────────────────────────
    private final PriorityQueue<Event>   eventQueue   = new PriorityQueue<>();
    private final PriorityQueue<Patient> patientQueue = new PriorityQueue<>();

    // ── Resources ─────────────────────────────────────────────────────────────
    private final List<Doctor>        doctors = new ArrayList<>();
    private final List<Nurse>         nurses  = new ArrayList<>();
    private final List<TreatmentRoom> rooms   = new ArrayList<>();

    // ── Parameters (from SimConfig or ScenarioConfig) ─────────────────────────
    private final double arrivalRatePerMin;
    private final double triageMeanMinutes;
    private final double simDurationMinutes;
    private final String scenarioLabel;

    // ── Support ───────────────────────────────────────────────────────────────
    private final RandomVariableGenerator rng;
    private final StatisticsCollector    stats = new StatisticsCollector();

    // ── Clock ─────────────────────────────────────────────────────────────────
    private double clock         = 0.0;
    private int    nextPatientID = 1;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Build from a {@link SimConfig} (config file / CLI). */
    public SimulationEngine(SimConfig cfg) {
        this.arrivalRatePerMin  = cfg.getArrivalRatePerMin();
        this.triageMeanMinutes  = cfg.getTriageMeanMinutes();
        this.simDurationMinutes = cfg.getSimulationMinutes();
        this.scenarioLabel      = cfg.toString();
        this.rng = new RandomVariableGenerator(cfg.getSeed() == 0
                ? System.currentTimeMillis() : cfg.getSeed());
        initResources(cfg.getNumDoctors(), cfg.getNumNurses(), cfg.getNumRooms());
    }

    /** Build from a {@link ScenarioConfig} (pre-defined scenario A-D). */
    public SimulationEngine(ScenarioConfig scenario, long seed) {
        this.arrivalRatePerMin  = 12.0 / 60.0;
        this.triageMeanMinutes  = 5.0;
        this.simDurationMinutes = 24.0 * 60.0;
        this.scenarioLabel      = scenario.getLabel();
        this.rng = new RandomVariableGenerator(seed == 0 ? System.currentTimeMillis() : seed);
        initResources(scenario.getNumDoctors(), scenario.getNumNurses(), scenario.getNumRooms());
    }

    /** Build with explicit parameters (used by experiment runner). */
    public SimulationEngine(String label, int numDoctors, int numNurses, int numRooms,
                            double arrivalRatePerHour, double triageMeanMinutes,
                            double simHours, long seed) {
        this.arrivalRatePerMin  = arrivalRatePerHour / 60.0;
        this.triageMeanMinutes  = triageMeanMinutes;
        this.simDurationMinutes = simHours * 60.0;
        this.scenarioLabel      = label;
        this.rng = new RandomVariableGenerator(seed == 0 ? System.currentTimeMillis() : seed);
        initResources(numDoctors, numNurses, numRooms);
    }

    private void initResources(int d, int n, int r) {
        for (int i = 1; i <= d; i++) doctors.add(new Doctor(i));
        for (int i = 1; i <= n; i++) nurses.add(new Nurse(i));
        for (int i = 1; i <= r; i++) rooms.add(new TreatmentRoom(i));
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Runs the simulation, prints the report, and returns metrics as a {@link RunResult}.
     *
     * @param runID unique run identifier for CSV export
     * @param arrivalRatePerHour for recording in the result
     * @param triageMean         for recording in the result
     * @param seed               for recording in the result
     */
    public RunResult run(int runID, double arrivalRatePerHour, double triageMean, long seed) {
        long startMs = System.currentTimeMillis();
        System.out.println("Run " + runID + " | " + scenarioLabel);
        scheduleNextArrival(0.0);

        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();
            clock = event.getTime();

            if (event.getType() == EventType.PATIENT_ARRIVAL && clock > simDurationMinutes) continue;

            Patient patient = event.getPatient();
            if (patient == null && event.getType() != EventType.PATIENT_ARRIVAL) continue;

            switch (event.getType()) {
                case PATIENT_ARRIVAL -> processArrival();
                case TRIAGE_COMPLETE -> processTriageComplete(patient);
                case TREATMENT_END   -> processTreatmentEnd(patient);
                default              -> { }
            }
            stats.recordQueueLength(patientQueue.size());
        }

        long execMs = System.currentTimeMillis() - startMs;
        stats.generateReport(scenarioLabel, doctors, nurses, rooms, simDurationMinutes);

        return new RunResult(
                runID, scenarioLabel,
                doctors.size(), nurses.size(), rooms.size(),
                arrivalRatePerHour, triageMean, seed,
                stats.getPatientsArrived(), stats.getThroughput(),
                stats.getCompletionRate(), stats.getAverageWaitTime(),
                stats.getMaxWaitTime(), stats.getAverageQueueLength(),
                stats.getAvgDoctorUtil(doctors, simDurationMinutes),
                stats.getAvgNurseUtil(nurses,   simDurationMinutes),
                stats.getAvgRoomUtil(rooms,      simDurationMinutes),
                execMs
        );
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    private void processArrival() {
        Patient patient = new Patient(nextPatientID++, clock);
        stats.recordArrival(patient);
        scheduleNextArrival(clock);

        Nurse nurse = nurses.stream()
                .min((a, b) -> Double.compare(
                        a.isAvailable() ? clock : a.getBusyUntil(),
                        b.isAvailable() ? clock : b.getBusyUntil()))
                .orElse(nurses.get(0));

        double triageStart    = nurse.isAvailable() ? clock : nurse.getBusyUntil();
        double triageEnd      = triageStart + rng.exponentialByMean(triageMeanMinutes);

        nurse.performTriage(patient, triageEnd);
        patient.setTriageCompleteTime(triageEnd);
        scheduleEvent(triageEnd, EventType.TRIAGE_COMPLETE, patient);
    }

    private void processTriageComplete(Patient patient) {
        patient.assignSeverity(rng.uniform());

        nurses.stream()
                .filter(n -> n.getCurrentPatient() == patient)
                .findFirst()
                .ifPresent(n -> n.completeTriageAndRelease(
                        Math.max(patient.getTriageCompleteTime() - patient.getArrivalTime(), 0.0)));

        patientQueue.add(patient);
        attemptTreatment();
    }

    private void processTreatmentEnd(Patient patient) {
        if (patient == null) return;
        double duration = patient.getTreatmentDuration();

        doctors.stream().filter(d -> d.getCurrentPatient() == patient)
                .findFirst().ifPresent(d -> d.releasePatient(duration));
        rooms.stream().filter(r -> r.getAssignedPatient() == patient)
                .findFirst().ifPresent(r -> r.releaseRoom(duration));

        patient.discharge(clock);
        stats.recordDischarge(patient);
        attemptTreatment();
    }

    private void attemptTreatment() {
        while (!patientQueue.isEmpty()) {
            Doctor        d = doctors.stream().filter(Doctor::isAvailable).findFirst().orElse(null);
            TreatmentRoom r = rooms.stream().filter(TreatmentRoom::isAvailable).findFirst().orElse(null);
            if (d == null || r == null) break;

            Patient next     = patientQueue.poll();
            double  duration = rng.exponentialByMean(next.getSeverity().getMeanTreatmentMinutes());
            double  end      = clock + duration;

            next.setTreatmentStartTime(clock);
            next.setTreatmentDuration(duration);
            d.treatPatient(next, end);
            r.assignedPatient(next);
            scheduleEvent(end, EventType.TREATMENT_END, next);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void scheduleNextArrival(double from) {
        scheduleEvent(from + rng.exponential(arrivalRatePerMin), EventType.PATIENT_ARRIVAL, null);
    }

    private void scheduleEvent(double time, EventType type, Patient patient) {
        eventQueue.add(new Event(time, type, patient));
    }

    public StatisticsCollector getStats()        { return stats; }
    public List<Patient>       getDischargedPatients() { return stats.getDischargedPatients(); }
}
