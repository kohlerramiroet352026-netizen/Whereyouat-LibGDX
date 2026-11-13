package com.whereyouat.lighting;

public class DayNightCycle {
    public static final int TICKS_PER_SEC = 60;
    public static final int DAY_MINUTES   = 15;
    public static final int NIGHT_MINUTES = 9;
    public static final int TOTAL_MINUTES = DAY_MINUTES + NIGHT_MINUTES;

    public static final long DAY_TICKS   = (long) DAY_MINUTES   * 60 * TICKS_PER_SEC;
    public static final long NIGHT_TICKS = (long) NIGHT_MINUTES * 60 * TICKS_PER_SEC;
    public static final long TOTAL_TICKS = (long) TOTAL_MINUTES * 60 * TICKS_PER_SEC;

    public static final long DEFAULT_TIME_TICKS = 2 * 60 * TICKS_PER_SEC + 30 * TICKS_PER_SEC;
    private long timeTicks = DEFAULT_TIME_TICKS;

    private int timeMultiplier = 4;

    private float ambientR = 1.0f;
    private float ambientG = 1.0f;
    private float ambientB = 1.0f;
    private float ambientIntensity = 1.0f;

    private float shadowDirX = 0.0f;
    private float shadowDirY = 0.4f;
    private float shadowLength = 12.0f;
    private float shadowOpacity = 0.25f;

    private String phaseName = "DÍA";
    private int clockHour = 8;
    private int clockMinute = 0;

    private static class Keyframe {
        float minute;
        float r, g, b;
        float intensity;
        float shadowDirX;
        float shadowDirY;
        float shadowLen;
        float shadowAlpha;
        String phase;

        Keyframe(float minute, int red255, int green255, int blue255, float intensity,
                 float shadowDirX, float shadowDirY, float shadowLen, float shadowAlpha, String phase) {
            this.minute = minute;
            this.r = red255 / 255.0f;
            this.g = green255 / 255.0f;
            this.b = blue255 / 255.0f;
            this.intensity = intensity;
            this.shadowDirX = shadowDirX;
            this.shadowDirY = shadowDirY;
            this.shadowLen = shadowLen;
            this.shadowAlpha = shadowAlpha;
            this.phase = phase;
        }
    }

    private static final Keyframe[] KEYFRAMES = new Keyframe[] {
        new Keyframe(0.0f,  125, 100, 150, 0.35f, -0.95f, 0.25f, 44.0f, 0.58f, "AMANECER"),
        new Keyframe(1.2f,  255, 215, 160, 0.82f, -0.90f, 0.35f, 36.0f, 0.54f, "MAÑANA"),

        new Keyframe(3.5f,  255, 248, 235, 0.95f, -0.55f, 0.45f, 22.0f, 0.50f, "DÍA"),

        new Keyframe(7.5f,  255, 255, 250, 1.00f,  0.00f, 0.40f, 14.0f, 0.48f, "MEDIODÍA"),

        new Keyframe(11.5f, 255, 240, 215, 0.92f,  0.50f, 0.40f, 22.0f, 0.52f, "DÍA"),
        new Keyframe(13.0f, 255, 210, 150, 0.80f,  0.85f, 0.35f, 34.0f, 0.62f, "TARDE"),

        new Keyframe(14.0f, 255, 140,  50, 0.62f,  0.98f, 0.20f, 48.0f, 0.75f, "ATARDECER"),
        new Keyframe(14.8f, 195,  75,  65, 0.42f,  0.95f, 0.25f, 52.0f, 0.78f, "ATARDECER"),

        new Keyframe(15.8f, 110,  85, 145, 0.44f,  0.80f, 0.35f, 38.0f, 0.52f, "ANOCHECER"),

        new Keyframe(17.0f,  78, 105, 160, 0.40f, -0.30f, 0.45f, 20.0f, 0.42f, "NOCHE"),
        new Keyframe(20.0f,  72,  98, 155, 0.38f, -0.20f, 0.40f, 16.0f, 0.40f, "NOCHE"),

        new Keyframe(22.8f,  95, 115, 165, 0.42f, -0.80f, 0.30f, 32.0f, 0.44f, "MADRUGADA"),

        new Keyframe(24.0f, 125, 100, 150, 0.35f, -0.95f, 0.25f, 44.0f, 0.58f, "AMANECER")
    };

    public DayNightCycle() {
        evaluate();
    }

    public void update() {
        timeTicks = (timeTicks + timeMultiplier) % TOTAL_TICKS;
        evaluate();
    }

    private void evaluate() {
        float currentMinute = (timeTicks / (float) TICKS_PER_SEC) / 60.0f;

        Keyframe startFrame = KEYFRAMES[0];
        Keyframe endFrame = KEYFRAMES[KEYFRAMES.length - 1];

        for (int i = 0; i < KEYFRAMES.length - 1; i++) {
            if (currentMinute >= KEYFRAMES[i].minute && currentMinute <= KEYFRAMES[i + 1].minute) {
                startFrame = KEYFRAMES[i];
                endFrame = KEYFRAMES[i + 1];
                break;
            }
        }

        float span = endFrame.minute - startFrame.minute;
        float progress = (span <= 0.0001f) ? 0f : (currentMinute - startFrame.minute) / span;
        float smoothProgress = (float) ((1.0 - Math.cos(progress * Math.PI)) * 0.5);

        ambientR = lerp(startFrame.r, endFrame.r, smoothProgress);
        ambientG = lerp(startFrame.g, endFrame.g, smoothProgress);
        ambientB = lerp(startFrame.b, endFrame.b, smoothProgress);
        ambientIntensity = lerp(startFrame.intensity, endFrame.intensity, smoothProgress);

        shadowDirX = lerp(startFrame.shadowDirX, endFrame.shadowDirX, smoothProgress);
        shadowDirY = lerp(startFrame.shadowDirY, endFrame.shadowDirY, smoothProgress);
        shadowLength = lerp(startFrame.shadowLen, endFrame.shadowLen, smoothProgress);
        shadowOpacity = lerp(startFrame.shadowAlpha, endFrame.shadowAlpha, smoothProgress);

        phaseName = (progress < 0.5f) ? startFrame.phase : endFrame.phase;

        float virtualHour = 6.0f + currentMinute;
        if (virtualHour >= 24.0f) virtualHour -= 24.0f;
        clockHour = (int) virtualHour;
        clockMinute = (int) ((virtualHour - clockHour) * 60);
    }

    private static float lerp(float startValue, float endValue, float progress) {
        return startValue + (endValue - startValue) * progress;
    }

    public float getAmbientR() { return ambientR; }
    public float getAmbientG() { return ambientG; }
    public float getAmbientB() { return ambientB; }
    public float getAmbientIntensity() { return ambientIntensity; }

    public float getShadowDirX() { return shadowDirX; }
    public float getShadowDirY() { return shadowDirY; }
    public float getShadowLength() { return shadowLength; }
    public float getShadowOpacity() { return shadowOpacity; }

    public String getPhaseName() { return phaseName; }

    public String getClockString() {
        String amPm = (clockHour >= 12) ? "PM" : "AM";
        int displayHour = clockHour % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format("%02d:%02d %s · %s", displayHour, clockMinute, amPm, phaseName);
    }

    public boolean isNight() {
        return timeTicks >= DAY_TICKS;
    }

    public int getTimeMultiplier() {
        return timeMultiplier;
    }

    public void setTimeMultiplier(int mult) {
        this.timeMultiplier = Math.max(1, mult);
    }

    public float getSunDirX() {
        return -shadowDirX;
    }

    public float getSunDirY() {
        return -shadowDirY;
    }

    public void advanceMinutes(float minutes) {
        timeTicks = (long) ((timeTicks + minutes * 60 * TICKS_PER_SEC) % TOTAL_TICKS);
        evaluate();
    }

    public long getTimeTicks() {
        return timeTicks;
    }

    public void setTimeTicks(long ticks) {
        this.timeTicks = (ticks % TOTAL_TICKS + TOTAL_TICKS) % TOTAL_TICKS;
        evaluate();
    }

    public void resetToDefault() {
        setTimeTicks(DEFAULT_TIME_TICKS);
    }
}
