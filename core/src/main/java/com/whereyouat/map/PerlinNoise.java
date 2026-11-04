package com.whereyouat.map;

/**
 * Perlin Noise implementation for procedural map generation.
 * Based on Ken Perlin's improved noise algorithm.
 */
public class PerlinNoise {

    private final int[] p;

    public PerlinNoise(long seed) {
        // Use seed to build a deterministic permutation table
        java.util.Random rand = new java.util.Random(seed);
        int[] source = new int[256];
        for (int i = 0; i < 256; i++) source[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = source[i]; source[i] = source[j]; source[j] = tmp;
        }
        p = new int[512];
        for (int i = 0; i < 512; i++) p[i] = source[i & 255];
    }

    private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private double lerp(double t, double a, double b) { return a + t * (b - a); }
    private double grad(int hash, double x, double y) {
        switch (hash & 3) {
            case 0:  return  x + y;
            case 1:  return -x + y;
            case 2:  return  x - y;
            default: return -x - y;
        }
    }

    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);

        int aa = p[p[xi]     + yi];
        int ab = p[p[xi]     + yi + 1];
        int ba = p[p[xi + 1] + yi];
        int bb = p[p[xi + 1] + yi + 1];

        return lerp(v,
            lerp(u, grad(aa, xf,     yf),     grad(ba, xf - 1, yf)),
            lerp(u, grad(ab, xf,     yf - 1), grad(bb, xf - 1, yf - 1))
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
