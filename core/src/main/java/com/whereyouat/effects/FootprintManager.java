package com.whereyouat.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * FootprintManager for LibGDX.
 * Renders directional footstep impressions on snow and sand with smooth alpha fade.
 */
public class FootprintManager {

    public static class Footprint {
        public float worldX;
        public float worldY;
        public float angleDegrees;
        public boolean isLeftFoot;
        public float life;
        public static final float MAX_LIFE = 1.83f; // ~110 frames at 60fps

        public Footprint(float worldX, float worldY, float angleDegrees, boolean isLeftFoot) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.angleDegrees = angleDegrees;
            this.isLeftFoot = isLeftFoot;
            this.life = MAX_LIFE;
        }

        public float getAlpha() {
            if (life > 0.66f) {
                return 0.80f;
            } else if (life > 0f) {
                return 0.80f * (life / 0.66f);
            }
            return 0f;
        }
    }

    private Texture footprintTexture;
    private TextureRegion footprintRegion;
    private final List<Footprint> footprints = new ArrayList<>();

    public static final float DRAW_WIDTH = 12f;
    public static final float DRAW_HEIGHT = 12f;

    public FootprintManager() {
        loadSprite();
    }

    private void loadSprite() {
        if (Gdx.files.internal("player/footprint.png").exists()) {
            footprintTexture = new Texture(Gdx.files.internal("player/footprint.png"));
        } else if (Gdx.files.internal("player/footstep.png").exists()) {
            footprintTexture = new Texture(Gdx.files.internal("player/footstep.png"));
        }
        if (footprintTexture != null) {
            footprintTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            footprintRegion = new TextureRegion(footprintTexture);
            footprintRegion.flip(false, true);
        }
    }

    public void addFootprint(float worldX, float worldY, double angleRadians, boolean isLeftFoot) {
        float minDistanceSq = 12f * 12f;
        for (Footprint fp : footprints) {
            float dx = fp.worldX - worldX;
            float dy = fp.worldY - worldY;
            if (dx * dx + dy * dy < minDistanceSq) {
                return;
            }
        }

        if (footprints.size() > 150) {
            footprints.remove(0);
        }
        float deg = (float) Math.toDegrees(angleRadians);
        footprints.add(new Footprint(worldX, worldY, deg, isLeftFoot));
    }

    public void update(float delta) {
        if (footprints.isEmpty()) return;
        Iterator<Footprint> it = footprints.iterator();
        while (it.hasNext()) {
            Footprint fp = it.next();
            fp.life -= delta;
            if (fp.life <= 0f) {
                it.remove();
            }
        }
    }

    public void draw(SpriteBatch batch, float minX, float minY, float maxX, float maxY) {
        if (footprintRegion == null || footprints.isEmpty()) return;

        float halfW = DRAW_WIDTH / 2f;
        float halfH = DRAW_HEIGHT / 2f;

        for (Footprint fp : footprints) {
            if (fp.worldX + halfW < minX || fp.worldX - halfW > maxX ||
                fp.worldY + halfH < minY || fp.worldY - halfH > maxY) {
                continue;
            }

            float alpha = fp.getAlpha();
            if (alpha <= 0.01f) continue;

            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(footprintRegion,
                       fp.worldX - halfW, fp.worldY - halfH,
                       halfW, halfH,
                       DRAW_WIDTH, DRAW_HEIGHT,
                       1f, 1f,
                       fp.angleDegrees);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void clear() {
        footprints.clear();
    }

    public void dispose() {
        if (footprintTexture != null) {
            footprintTexture.dispose();
            footprintTexture = null;
        }
        footprints.clear();
    }
}
