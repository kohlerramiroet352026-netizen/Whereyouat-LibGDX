package com.whereyouat.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/**
 * LightingEngine for LibGDX.
 * High-performance GPU-accelerated 2D dynamic lighting system.
 * Uses a FrameBuffer lightmap with multiplicative blending (GL_DST_COLOR, GL_ZERO),
 * smooth radial player illumination aura for nighttime, and ambient DayNightCycle integration.
 */
public class LightingEngine {

    private FrameBuffer lightFbo;
    private TextureRegion lightFboRegion;
    private Texture lightCircleTex;
    private int width;
    private int height;

    public LightingEngine(int initialWidth, int initialHeight) {
        generateLightCircle();
        resize(initialWidth, initialHeight);
    }

    private void generateLightCircle() {
        int size = 256;
        Pixmap pixmap = new Pixmap(size, size, Format.RGBA8888);
        float center = size / 2f;
        float radius = size / 2f;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float deltaX = x - center;
                float deltaY = y - center;
                float distance = (float) Math.hypot(deltaX, deltaY);
                float falloff = Math.max(0f, 1f - distance / radius);
                // Cubic Hermite smoothstep curve
                falloff = falloff * falloff * (3f - 2f * falloff);
                pixmap.drawPixel(x, y, Color.rgba8888(1f, 0.90f, 0.70f, falloff));
            }
        }

        lightCircleTex = new Texture(pixmap);
        lightCircleTex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pixmap.dispose();
    }

    public final void resize(int newWidth, int newHeight) {
        if (newWidth <= 0) newWidth = 768;
        if (newHeight <= 0) newHeight = 576;

        if (lightFbo != null && this.width == newWidth && this.height == newHeight) {
            return;
        }

        this.width = newWidth;
        this.height = newHeight;

        if (lightFbo != null) {
            lightFbo.dispose();
        }

        lightFbo = new FrameBuffer(Format.RGBA8888, width, height, false);
        Texture fboTexture = lightFbo.getColorBufferTexture();
        fboTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        lightFboRegion = new TextureRegion(fboTexture);
        lightFboRegion.flip(false, true); // OpenGL FBOs have inverted Y
    }

    /**
     * Renders the lightmap into the FBO.
     */
    public void renderLightmap(SpriteBatch batch, DayNightCycle cycle, float playerScreenX, float playerScreenY) {
        if (lightFbo == null) return;

        lightFbo.begin();

        float ambientRed = cycle.getAmbientR() * cycle.getAmbientIntensity();
        float ambientGreen = cycle.getAmbientG() * cycle.getAmbientIntensity();
        float ambientBlue = cycle.getAmbientB() * cycle.getAmbientIntensity();

        Gdx.gl.glClearColor(ambientRed, ambientGreen, ambientBlue, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Player nighttime aura
        float nightFactor = 1f - cycle.getAmbientIntensity();
        if (nightFactor > 0.08f) {
            batch.begin();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // Additive blending
            batch.setColor(1f, 0.88f, 0.65f, Math.min(1f, nightFactor * 0.95f));
            float lightSize = 340f;
            batch.draw(lightCircleTex, playerScreenX - lightSize / 2f, playerScreenY - lightSize / 2f, lightSize, lightSize);
            batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        lightFbo.end();
    }

    /**
     * Multiplies the lightmap onto the rendered world scene.
     */
    public void drawLightmap(SpriteBatch batch, float minX, float minY, float viewportWidth, float viewportHeight) {
        if (lightFboRegion == null) return;

        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO); // Hardware multiplicative blend
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(lightFboRegion, minX, minY, viewportWidth, viewportHeight);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // Restore normal blend
    }

    public void dispose() {
        if (lightFbo != null) {
            lightFbo.dispose();
            lightFbo = null;
        }
        if (lightCircleTex != null) {
            lightCircleTex.dispose();
            lightCircleTex = null;
        }
    }
}
