package edsim.engine;

import java.util.Random;

/**
 * Utility class for generating random variates used throughout the simulation.
 *
 * All distributions are derived from a single seeded Random instance so
 * results are reproducible across runs when the same seed is used.
 */
public class RandomVariableGenerator {

    private final Random rng;

    public RandomVariableGenerator(long seed) {
        this.rng = new Random(seed);
    }

    public RandomVariableGenerator() {
        this.rng = new Random();
    }

    /**
     * Exponential distribution via inverse-transform: X = -ln(U) / lambda
     *
     * @param rate lambda (events per unit time)
     * @return sampled inter-event time
     */
    public double exponential(double rate) {
        return -Math.log(1.0 - rng.nextDouble()) / rate;
    }

    /**
     * Convenience: exponential parameterised by mean instead of rate.
     *
     * @param mean average value (= 1/lambda)
     * @return sampled value
     */
    public double exponentialByMean(double mean) {
        return exponential(1.0 / mean);
    }

    /**
     * Uniform [0, 1) — used for probability comparisons (e.g. severity assignment).
     */
    public double uniform() {
        return rng.nextDouble();
    }
}