package com.whereyouat.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SnowParticleSystem for LibGDX.
 * Renders atmospheric snow precipitation in snow biomes and falling leaves from shaken foliage.
 */
public class SnowParticleSystem {

    private static final int   MAX_FLAKES     = 50;
    private static final float MIN_SPEED      = 20f;  // px/s
    private static final float MAX_SPEED      = 60f;
    private static final float MIN_DRIFT      = -18f;
    private static final float MAX_DRIFT      = 36f;

    private final Random rand;
    private final List<Snowflake> flakes = new ArrayList<>();
    private final List<LeafParticle> leaves = new ArrayList<>();
    private static final int MAX_LEAVES = 140;

    private Texture snowTexture;
    private Texture leafTexture;
    private TextureRegion snowRegion;
    private TextureRegion leafRight;
    private TextureRegion leafLeft;

    private float intensity = 0f;
    private float targetIntensity = 0f;

    private static class Snowflake {
        float screenX, screenY;
        float speed, drift, alpha;
        float wobbleOffset, wobbleSpeed, wobbleAmp;
        float time;
    }

    private static class LeafParticle {
        float worldX, worldY;
        float vx, vy;
        float targetFallSpeed;
        float swayPhase;
        float swayFreq;
        float swayAmp;
        float groundY;
        float windDir = 1.0f;
        float groundTimer;
        float alpha = 1.0f;
        boolean onGround = false;
        boolean facingRight = true;
    }

    public SnowParticleSystem(long seed) {
        this.rand = new Random(seed + 55555);
        loadTextures();

        for (int i = 0; i < MAX_FLAKES; i++) {
            flakes.add(createFlake(true));
        }
    }

    private void loadTextures() {
        if (Gdx.files.internal("particles/snow_particle.png").exists()) {
            snowTexture = new Texture(Gdx.files.internal("particles/snow_particle.png"));
            snowTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            snowRegion = new TextureRegion(snowTexture);
            snowRegion.flip(false, true);
        }
        if (Gdx.files.internal("particles/leaf_particle.png").exists()) {
            leafTexture = new Texture(Gdx.files.internal("particles/leaf_particle.png"));
            leafTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            leafRight = new TextureRegion(leafTexture);
            leafRight.flip(false, true);
            leafLeft = new TextureRegion(leafTexture);
            leafLeft.flip(true, true);
        }
    }

    private Snowflake createFlake(boolean randomY) {
        Snowflake f = new Snowflake();
        f.screenX = rand.nextFloat() * 768f;
        f.screenY = randomY ? rand.nextFloat() * 576f : -10f;
        f.speed = MIN_SPEED + rand.nextFloat() * (MAX_SPEED - MIN_SPEED);
        f.drift = MIN_DRIFT + rand.nextFloat() * (MAX_DRIFT - MIN_DRIFT);
        f.alpha = 0.5f + rand.nextFloat() * 0.5f;
        f.wobbleOffset = rand.nextFloat() * (float) (Math.PI * 2);
        f.wobbleSpeed  = 1.0f + rand.nextFloat() * 1.5f;
        f.wobbleAmp    = 12f + rand.nextFloat() * 24f;
        f.time = 0f;
        return f;
    }

    public void update(float delta, boolean inSnowBiome, float blendFactor, float viewW, float viewH) {
        updateLeaves(delta);

        targetIntensity = inSnowBiome ? Math.min(1f, blendFactor * 1.5f) : 0f;
        float step = delta / 1.5f; // ~1.5s fade
        if (intensity < targetIntensity) {
            intensity = Math.min(targetIntensity, intensity + step);
        } else if (intensity > targetIntensity) {
            intensity = Math.max(targetIntensity, intensity - step);
        }

        if (intensity < 0.01f) return;

        int activeCount = Math.max(1, (int) (intensity * MAX_FLAKES));
        for (int i = 0; i < flakes.size(); i++) {
            Snowflake f = flakes.get(i);
            if (i >= activeCount) {
                f.screenY = -20f;
                continue;
            }
            f.time += delta;
            float wobble = f.wobbleAmp * (float) Math.sin(f.wobbleOffset + f.time * f.wobbleSpeed);
            f.screenX += (f.drift + wobble) * delta;
            f.screenY += f.speed * delta;

            if (f.screenY > viewH + 10f) {
                f.screenY = -15f;
                f.screenX = rand.nextFloat() * viewW;
            }
            if (f.screenX < -15f) f.screenX = viewW + 10f;
            if (f.screenX > viewW + 15f) f.screenX = -10f;
        }
    }

    public void spawnLeafParticles(int tc, int tr, int tileSize) {
        int count = 4 + rand.nextInt(2);
        float centerX = tc * tileSize + tileSize / 2f;
        float groundLevel = tr * tileSize + tileSize - 4f;

        for (int i = 0; i < count; i++) {
            if (leaves.size() >= MAX_LEAVES) {
                leaves.remove(0);
            }
            LeafParticle lp = new LeafParticle();
            boolean onLeft = (i % 2 == 0);
            float sideOffset = 12f + rand.nextFloat() * 14f;
            lp.worldX = centerX + (onLeft ? -sideOffset : sideOffset);
            lp.worldY = (tr * tileSize + 10f) + rand.nextFloat() * 16f;

            lp.vx = (onLeft ? -1f : 1f) * (24f + rand.nextFloat() * 48f);
            lp.vy = -48f - rand.nextFloat() * 60f;

            lp.targetFallSpeed = 48f + rand.nextFloat() * 30f;
            lp.swayPhase = rand.nextFloat() * (float) (Math.PI * 2);
            lp.swayFreq = 3.0f + rand.nextFloat() * 2.0f;
            lp.swayAmp = 60f + rand.nextFloat() * 40f;
            lp.groundY = groundLevel + (rand.nextFloat() * 8f - 4f);
            lp.groundTimer = 2.0f + rand.nextFloat() * 1.0f;
            lp.facingRight = (lp.vx >= 0);

            leaves.add(lp);
        }
    }


    /**
     * Spawns wind-blown leaves from tree canopies or bush tops during wind gusts.
     * Leaves carry strong horizontal momentum from wind direction.
     */
    public void spawnWindLeaves(float spawnX, float spawnY, float groundY, int count, float windDirX) {
        for (int i = 0; i < count; i++) {
            if (leaves.size() >= MAX_LEAVES) {
                boolean removed = false;
                for (int j = 0; j < leaves.size(); j++) {
                    if (leaves.get(j).onGround) {
                        leaves.remove(j);
                        removed = true;
                        break;
                    }
                }
                if (!removed) continue;
            }
            LeafParticle lp = new LeafParticle();
            lp.worldX = spawnX + (rand.nextFloat() * 24f - 12f);
            lp.worldY = spawnY + (rand.nextFloat() * 16f - 8f);

            float baseWindSpeed = 132f + rand.nextFloat() * 90f; // pixels/sec
            lp.vx = windDirX * baseWindSpeed + (rand.nextFloat() * 24f - 12f);
            lp.vy = -15f - rand.nextFloat() * 21f;
            lp.windDir = windDirX;

            lp.targetFallSpeed = 30f + rand.nextFloat() * 18f;
            lp.swayPhase = rand.nextFloat() * (float) (Math.PI * 2);
            lp.swayFreq = 2.7f + rand.nextFloat() * 1.8f;
            lp.swayAmp = 90f + rand.nextFloat() * 42f;
            lp.groundY = groundY + (rand.nextFloat() * 8f - 4f);
            lp.groundTimer = 2.5f + rand.nextFloat() * 1.0f;
            lp.facingRight = (lp.vx >= 0);

            leaves.add(lp);
        }
    }

    private void updateLeaves(float delta) {
        if (leaves.isEmpty()) return;
        Iterator<LeafParticle> it = leaves.iterator();
        while (it.hasNext()) {
            LeafParticle lp = it.next();
            if (!lp.onGround) {
                lp.vy += (lp.targetFallSpeed - lp.vy) * 3.6f * delta;
                lp.vx = lp.vx * (1f - 0.9f * delta) + (lp.windDir * 2.0f * delta);

                lp.swayPhase += lp.swayFreq * delta;
                float swayDelta = (float) Math.cos(lp.swayPhase) * lp.swayAmp;

                float totalDx = lp.vx + swayDelta;
                lp.worldX += totalDx * delta;
                lp.worldY += lp.vy * delta;

                if (totalDx > 1f) {
                    lp.facingRight = true;
                } else if (totalDx < -1f) {
                    lp.facingRight = false;
                }

                if (lp.worldY >= lp.groundY) {
                    lp.worldY = lp.groundY;
                    lp.onGround = true;
                }
            } else {
                lp.groundTimer -= delta;
                if (lp.groundTimer <= 0f) {
                    it.remove();
                } else if (lp.groundTimer < 0.75f) {
                    lp.alpha = lp.groundTimer / 0.75f;
                }
            }
        }
    }

    public void drawWorldLeaves(SpriteBatch batch, float minX, float minY, float maxX, float maxY) {
        if (leaves.isEmpty() || leafRight == null) return;

        for (LeafParticle lp : leaves) {
            if (lp.worldX < minX - 16 || lp.worldX > maxX + 16 ||
                lp.worldY < minY - 16 || lp.worldY > maxY + 16) {
                continue;
            }
            TextureRegion reg = lp.facingRight ? leafRight : leafLeft;
            batch.setColor(1f, 1f, 1f, lp.alpha);
            batch.draw(reg, lp.worldX - 4f, lp.worldY - 4f, 8f, 8f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void drawScreenSnow(SpriteBatch batch, float camMinX, float camMinY) {
        if (intensity < 0.01f || snowRegion == null) return;

        int activeCount = Math.max(1, (int) (intensity * MAX_FLAKES));
        for (int i = 0; i < activeCount && i < flakes.size(); i++) {
            Snowflake f = flakes.get(i);
            if (f.screenY < -8f) continue;

            float drawAlpha = f.alpha * intensity;
            batch.setColor(1f, 1f, 1f, drawAlpha);
            // Drawn relative to camera frustum top-left
            batch.draw(snowRegion, camMinX + f.screenX, camMinY + f.screenY, 5f, 5f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void dispose() {
        if (snowTexture != null) {
            snowTexture.dispose();
            snowTexture = null;
        }
        if (leafTexture != null) {
            leafTexture.dispose();
            leafTexture = null;
        }
        leaves.clear();
        flakes.clear();
    }
}
