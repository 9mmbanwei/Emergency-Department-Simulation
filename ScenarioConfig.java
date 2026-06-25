package edsim.engine;

/**
 * Defines a staffing configuration scenario.
 * Matches the four scenarios in the project specification (Table 3).
 */
public class ScenarioConfig {

    private final String label;
    private final int    numDoctors;
    private final int    numNurses;
    private final int    numRooms;

    public ScenarioConfig(String label, int numDoctors, int numNurses, int numRooms) {
        this.label      = label;
        this.numDoctors = numDoctors;
        this.numNurses  = numNurses;
        this.numRooms   = numRooms;
    }

    // ── Pre-built scenarios from Table 3 ──────────────────────────────────────

    public static ScenarioConfig scenarioA() { return new ScenarioConfig("Scenario A (Baseline)", 4, 3, 8);  }
    public static ScenarioConfig scenarioB() { return new ScenarioConfig("Scenario B",            5, 3, 8);  }
    public static ScenarioConfig scenarioC() { return new ScenarioConfig("Scenario C",            4, 4, 8);  }
    public static ScenarioConfig scenarioD() { return new ScenarioConfig("Scenario D",            5, 4, 10); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getLabel()      { return label; }
    public int    getNumDoctors() { return numDoctors; }
    public int    getNumNurses()  { return numNurses; }
    public int    getNumRooms()   { return numRooms; }

    @Override
    public String toString() {
        return String.format("%s  [Doctors=%d, Nurses=%d, Rooms=%d]",
                label, numDoctors, numNurses, numRooms);
    }
}