import edsim.engine.ScenarioConfig;
import edsim.engine.SimulationEngine;

/**
 * Entry point for the Emergency Department Resource Optimization Simulation.
 *
 * Runs all four staffing scenarios defined in the project spec (Table 3)
 * and prints a comparative report for each.
 *
 * Usage:
 *   javac -d out src/main/java/edsim/**\/*.java src/main/java/Main.java
 *   java  -cp out Main
 */
public class Main {

    // Reproducible seed — change to 0 for a different random run each time
    private static final long SEED = 42L;

    public static void main(String[] args) {

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   Emergency Department Resource Optimization Simulation  ║");
        System.out.println("║   24-hour run  |  λ = 12 patients/hr  |  Seed = "+SEED+" ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        ScenarioConfig[] scenarios = {
                ScenarioConfig.scenarioA(),
                ScenarioConfig.scenarioB(),
                ScenarioConfig.scenarioC(),
                ScenarioConfig.scenarioD()
        };

        for (ScenarioConfig scenario : scenarios) {
            SimulationEngine engine = new SimulationEngine(scenario, SEED);
            engine.run();
        }

        System.out.println("All scenarios complete.");
    }
}