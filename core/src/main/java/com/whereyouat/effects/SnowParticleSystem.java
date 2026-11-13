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

//particulas de nieve y hojas
public class SnowParticleSystem {

    private static final int   MAX_FLAKES     = 50;
    private static final float MIN_SPEED      = 20f;
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

    //particula de nieve
    private static class Snowflake {
        float screenX, screenY;
        float speed, drift, alpha;
        float wobbleOffset, wobbleSpeed, wobbleAmp;
        float time;
    }

    //particula de hoja
    private static class LeafParticle {
        float worldX, worldY;
        float velocityX, velocityY;
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

    //iniciar particulas
    public SnowParticleSystem(long seed) {
        this.rand = new Random(seed + 55555);
        loadTextures();

        for (int i = 0; i < MAX_FLAKES; i++) {
            flakes.add(createFlake(true));
        }
    }

    //cargar texturas
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

    //crear copo nieve
    private Snowflake createFlake(boolean randomY) {
        Snowflake flake = new Snowflake();
        flake.screenX = rand.nextFloat() * 768f;
        flake.screenY = randomY ? rand.nextFloat() * 576f : -10f;
        flake.speed = MIN_SPEED + rand.nextFloat() * (MAX_SPEED - MIN_SPEED);
        flake.drift = MIN_DRIFT + rand.nextFloat() * (MAX_DRIFT - MIN_DRIFT);
        flake.alpha = 0.5f + rand.nextFloat() * 0.5f;
        flake.wobbleOffset = rand.nextFloat() * (float) (Math.PI * 2);
        flake.wobbleSpeed  = 1.0f + rand.nextFloat() * 1.5f;
        flake.wobbleAmp    = 12f + rand.nextFloat() * 24f;
        flake.time = 0f;
        return flake;
    }

    //actualizar nieve
    public void update(float delta, boolean inSnowBiome, float blendFactor, float viewportWidth, float viewportHeight) {
        updateLeaves(delta);

        targetIntensity = inSnowBiome ? Math.min(1f, blendFactor * 1.5f) : 0f;
        float step = delta / 1.5f;
        if (intensity < targetIntensity) {
            intensity = Math.min(targetIntensity, intensity + step);
        } else if (intensity > targetIntensity) {
            intensity = Math.max(targetIntensity, intensity - step);
        }

        if (intensity < 0.01f) return;

        int activeCount = Math.max(1, (int) (intensity * MAX_FLAKES));
        for (int i = 0; i < flakes.size(); i++) {
            Snowflake flake = flakes.get(i);
            if (i >= activeCount) {
                flake.screenY = -20f;
                continue;
            }
            flake.time += delta;
            float wobble = flake.wobbleAmp * (float) Math.sin(flake.wobbleOffset + flake.time * flake.wobbleSpeed);
            flake.screenX += (flake.drift + wobble) * delta;
            flake.screenY += flake.speed * delta;

            if (flake.screenY > viewportHeight + 10f) {
                flake.screenY = -15f;
                flake.screenX = rand.nextFloat() * viewportWidth;
            }
            if (flake.screenX < -15f) flake.screenX = viewportWidth + 10f;
            if (flake.screenX > viewportWidth + 15f) flake.screenX = -10f;
        }
    }

    //generar hojas pisadas
    public void spawnLeafParticles(int tileCol, int tileRow, int tileSize) {
        int count = 4 + rand.nextInt(2);
        float centerX = tileCol * tileSize + tileSize / 2f;
        float groundLevel = tileRow * tileSize + tileSize - 4f;

        for (int i = 0; i < count; i++) {
            if (leaves.size() >= MAX_LEAVES) {
                leaves.remove(0);
            }
            LeafParticle leaf = new LeafParticle();
            boolean onLeft = (i % 2 == 0);
            float sideOffset = 12f + rand.nextFloat() * 14f;
            leaf.worldX = centerX + (onLeft ? -sideOffset : sideOffset);
            leaf.worldY = (tileRow * tileSize + 10f) + rand.nextFloat() * 16f;

            leaf.velocityX = (onLeft ? -1f : 1f) * (24f + rand.nextFloat() * 48f);
            leaf.velocityY = -48f - rand.nextFloat() * 60f;

            leaf.targetFallSpeed = 48f + rand.nextFloat() * 30f;
            leaf.swayPhase = rand.nextFloat() * (float) (Math.PI * 2);
            leaf.swayFreq = 3.0f + rand.nextFloat() * 2.0f;
            leaf.swayAmp = 60f + rand.nextFloat() * 40f;
            leaf.groundY = groundLevel + (rand.nextFloat() * 8f - 4f);
            leaf.groundTimer = 2.0f + rand.nextFloat() * 1.0f;
            leaf.facingRight = (leaf.velocityX >= 0);

            leaves.add(leaf);
        }
    }

    //generar hojas viento
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
            LeafParticle leaf = new LeafParticle();
            leaf.worldX = spawnX + (rand.nextFloat() * 24f - 12f);
            leaf.worldY = spawnY + (rand.nextFloat() * 16f - 8f);

            float baseWindSpeed = 132f + rand.nextFloat() * 90f;
            leaf.velocityX = windDirX * baseWindSpeed + (rand.nextFloat() * 24f - 12f);
            leaf.velocityY = -15f - rand.nextFloat() * 21f;
            leaf.windDir = windDirX;

            leaf.targetFallSpeed = 30f + rand.nextFloat() * 18f;
            leaf.swayPhase = rand.nextFloat() * (float) (Math.PI * 2);
            leaf.swayFreq = 2.7f + rand.nextFloat() * 1.8f;
            leaf.swayAmp = 90f + rand.nextFloat() * 42f;
            leaf.groundY = groundY + (rand.nextFloat() * 8f - 4f);
            leaf.groundTimer = 2.5f + rand.nextFloat() * 1.0f;
            leaf.facingRight = (leaf.velocityX >= 0);

            leaves.add(leaf);
        }
    }

    //actualizar hojas
    private void updateLeaves(float delta) {
        if (leaves.isEmpty()) return;
        Iterator<LeafParticle> iterator = leaves.iterator();
        while (iterator.hasNext()) {
            LeafParticle leaf = iterator.next();
            if (!leaf.onGround) {
                leaf.velocityY += (leaf.targetFallSpeed - leaf.velocityY) * 3.6f * delta;
                leaf.velocityX = leaf.velocityX * (1f - 0.9f * delta) + (leaf.windDir * 2.0f * delta);

                leaf.swayPhase += leaf.swayFreq * delta;
                float swayDelta = (float) Math.cos(leaf.swayPhase) * leaf.swayAmp;

                float totalDeltaX = leaf.velocityX + swayDelta;
                leaf.worldX += totalDeltaX * delta;
                leaf.worldY += leaf.velocityY * delta;

                if (totalDeltaX > 1f) {
                    leaf.facingRight = true;
                } else if (totalDeltaX < -1f) {
                    leaf.facingRight = false;
                }

                if (leaf.worldY >= leaf.groundY) {
                    leaf.worldY = leaf.groundY;
                    leaf.onGround = true;
                }
            } else {
                leaf.groundTimer -= delta;
                if (leaf.groundTimer <= 0f) {
                    iterator.remove();
                } else if (leaf.groundTimer < 0.75f) {
                    leaf.alpha = leaf.groundTimer / 0.75f;
                }
            }
        }
    }

    //dibujar hojas
    public void drawWorldLeaves(SpriteBatch batch, float minX, float minY, float maxX, float maxY) {
        if (leaves.isEmpty() || leafRight == null) return;

        for (LeafParticle leaf : leaves) {
            if (leaf.worldX < minX - 16 || leaf.worldX > maxX + 16 ||
                leaf.worldY < minY - 16 || leaf.worldY > maxY + 16) {
                continue;
            }
            TextureRegion region = leaf.facingRight ? leafRight : leafLeft;
            batch.setColor(1f, 1f, 1f, leaf.alpha);
            batch.draw(region, leaf.worldX - 4f, leaf.worldY - 4f, 8f, 8f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    //dibujar nieve pantalla
    public void drawScreenSnow(SpriteBatch batch, float camMinX, float camMinY) {
        if (intensity < 0.01f || snowRegion == null) return;

        int activeCount = Math.max(1, (int) (intensity * MAX_FLAKES));
        for (int i = 0; i < activeCount && i < flakes.size(); i++) {
            Snowflake flake = flakes.get(i);
            if (flake.screenY < -8f) continue;

            float drawAlpha = flake.alpha * intensity;
            batch.setColor(1f, 1f, 1f, drawAlpha);
            batch.draw(snowRegion, camMinX + flake.screenX, camMinY + flake.screenY, 5f, 5f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    //liberar memoria
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
