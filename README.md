# Emergency-Department-Simulation
The goal of this project is to develop a discrete-event simulation of an emergency department that models patient flow, prioritizing classification, and resource allocation. The simulation will allow experimentation with different staffing and resource configurations to find strategies that reduce patient wait times and improve system efficiency.

# ED Simulation — Build & Run Instructions

## Prerequisites
- Java Development Kit (JDK) 17 or higher
- IntelliJ IDEA (recommended) or any terminal

## IntelliJ IDEA

1. Open IntelliJ → **File > Open** → select the `EDSimulation` folder
2. Mark `src/main/java` as the **Sources Root**
   - Right-click the folder → Mark Directory As → Sources Root
3. Run `Main.java` (right-click → Run 'Main.main()')

---

## Project Structure

```
EDSimulation/
└── src/main/java/
    ├── Main.java                        ← Entry point (runs all 4 scenarios)
    ├── edsim/
    │   ├── enums/
    │   │   └── SeverityLevel.java       ← 5-level triage enum + probability table
    │   ├── entities/
    │   │   ├── Patient.java             ← Patient entity + Comparable (priority queue)
    │   │   ├── Doctor.java              ← Doctor entity + utilisation tracking
    │   │   ├── Nurse.java               ← Nurse entity + triage logic
    │   │   └── TreatmentRoom.java       ← Room entity + occupancy tracking
    │   ├── engine/
    │   │   ├── EventType.java           ← DES event types enum
    │   │   ├── Event.java               ← Event class (Comparable by time)
    │   │   ├── RandomVariateGenerator.java ← Exponential + uniform sampling
    │   │   ├── ScenarioConfig.java      ← Scenarios A–D from Table 3
    │   │   └── SimulationEngine.java    ← Main DES event loop
    │   └── stats/
    │       └── StatisticsCollector.java ← Metrics + report generation
```

---

## Expected Output (per example scenario)

```
============================================================
  SIMULATION REPORT — Scenario A (Baseline)
============================================================

--- Patient Metrics ---
  Patients arrived      : 288
  Patients discharged   : 275
  Completion rate       : 95.5%
  Avg wait time         : 18.4 min
  Max wait time         : 61.2 min
  Avg queue length      : 3.2 patients

--- Success Criteria ---
  [PASS] Avg wait < 30 min
  [PASS] Avg queue < 10 patients
  [PASS] Completion rate >= 95%

--- Doctor Utilisation --
  Doctor 1  :  82.3%  (treated 71 patients)

---

**## Configuration**

---

# ============================================================
#  Emergency Department Simulation — Configuration File
#  CS4632 | Mbah Tichuck Mbanwei
#
#  All parameters can also be overridden via CLI arguments:
#  java -cp out Main --arrivalRate 15 --doctors 5 --nurses 4
# ============================================================
 
# ── Arrival process ───────────────────────────────────────────
# Average patient arrivals per hour (Poisson process)
arrivalRatePerHour=12
 
# ── Simulation duration ───────────────────────────────────────
# How many hours to simulate
simulationHours=24
 
# ── Staffing (baseline) ───────────────────────────────────────
numDoctors=4
numNurses=3
numRooms=8
 
# ── Triage ────────────────────────────────────────────────────
# Mean triage duration in minutes (Exponential distribution)
triageMeanMinutes=5
 
# ── Random seed ───────────────────────────────────────────────
# Use 0 for a different result each run
seed=42
 
# ── Output ────────────────────────────────────────────────────
# Directory where CSV results are written (relative to working dir)
outputDir=results
 
# ── Run mode ──────────────────────────────────────────────────
# single  → run one scenario using the parameters above
# all     → run all four pre-defined scenarios (A-D)
# experiment → run the full 10-run experiment matrix
runMode=experiment
  ...
  [PASS] Doctor util 70–90%
  ```
