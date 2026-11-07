package com.whereyouat.map;

/**
 * Perlin Noise implementation for procedural map generation.
 * Based on Ken Perlin's improved noise algorithm.
 */
public class PerlinNoise {

    private final int[] permutation;

    public PerlinNoise(long seed) {
        // Use seed to build a deterministic permutation table
        java.util.Random rand = new java.util.Random(seed);
        int[] source = new int[256];
        for (int i = 0; i < 256; i++) source[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = source[i]; source[i] = source[j]; source[j] = tmp;
        }
        permutation = new int[512];
        for (int i = 0; i < 512; i++) permutation[i] = source[i & 255];
    }

    private double fade(double progress) { return progress * progress * progress * (progress * (progress * 6 - 15) + 10); }
    private double lerp(double progress, double startVal, double endVal) { return startVal + progress * (endVal - startVal); }
    private double grad(int hash, double x, double y) {
        switch (hash & 3) {
            case 0:  return  x + y;
            case 1:  return -x + y;
            case 2:  return  x - y;
            default: return -x - y;
        }
    }

    public double noise(double x, double y) {
        int gridX = (int) Math.floor(x) & 255;
        int gridY = (int) Math.floor(y) & 255;
        double fracX = x - Math.floor(x);
        double fracY = y - Math.floor(y);
        double fadeX = fade(fracX);
        double fadeY = fade(fracY);

        int cornerAA = permutation[permutation[gridX]     + gridY];
        int cornerAB = permutation[permutation[gridX]     + gridY + 1];
        int cornerBA = permutation[permutation[gridX + 1] + gridY];
        int cornerBB = permutation[permutation[gridX + 1] + gridY + 1];

        return lerp(fadeY,
            lerp(fadeX, grad(cornerAA, fracX,     fracY),     grad(cornerBA, fracX - 1, fracY)),
            lerp(fadeX, grad(cornerAB, fracX,     fracY - 1), grad(cornerBB, fracX - 1, fracY - 1))
        );
    }

    /**
     * Octave (fractal) noise for more natural terrain.
     * Returns a value in approximately [0, 1].
     */
    public double octaveNoise(double x, double y, int octaves, double persistence, double lacunarity) {
        double total = 0;
        double frequency = 1;
        double amplitude = 1;
        double maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return (total / maxValue + 1) / 2.0; // normalize to [0,1]
    }
}
