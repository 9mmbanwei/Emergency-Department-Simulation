package edsim.enums;

/**
 * Represents the five-level triage severity classification.
 * Lower ordinal = higher priority (CRITICAL treated first).
 */
public enum SeverityLevel {

    CRITICAL(1, 60.0, 0.05),
    HIGH    (2, 45.0, 0.10),
    MODERATE(3, 30.0, 0.25),
    LOW     (4, 20.0, 0.35),
    MINOR   (5, 15.0, 0.25);

    /** Numeric severity code (1 = most severe). */
    private final int code;

    /** Mean treatment duration in minutes (Exponential distribution). */
    private final double meanTreatmentMinutes;

    /** Probability of a patient receiving this severity level at triage. */
    private final double arrivalProbability;

    SeverityLevel(int code, double meanTreatmentMinutes, double arrivalProbability) {
        this.code                 = code;
        this.meanTreatmentMinutes = meanTreatmentMinutes;
        this.arrivalProbability   = arrivalProbability;
    }

    public int    getCode()                 { return code; }
    public double getMeanTreatmentMinutes() { return meanTreatmentMinutes; }
    public double getArrivalProbability()   { return arrivalProbability; }

    /**
     * Randomly assigns a severity level based on the defined probability
     * distribution using inverse-transform sampling.
     *
     * @param rand a value uniformly drawn from [0, 1)
     * @return the assigned SeverityLevel
     */
    public static SeverityLevel assign(double rand) {
        double cumulative = 0.0;
        for (SeverityLevel level : values()) {
            cumulative += level.arrivalProbability;
            if (rand < cumulative) {
                return level;
            }
        }
        return MINOR; // fallback (floating-point edge case)
    }
}
