package com.whereyouat.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Player entity for Where you at (LibGDX Edition).
 *
 * Implements:
 * - 4-direction walk cycles (up1..13, down1..13, left1..8, right1..8)
 * - Idle looking-around cycle (idle0..8)
 * - Obstacle and tree trunk collision
 * - Water wading and drowning mechanics with depth clipping and foam
 * - Step cadence and vegetation interaction callbacks
 * - Health, lives, damage, and regeneration systems
 */
public class Player extends Entity {

    // Listener interface for decoupled audio and particle/footprint triggers
    public interface PlayerStepListener {
        void onStep(float footX, float footY, double angle, boolean isLeftFoot, int footCol, int footRow);
        void onBushRustle();
    }

    private PlayerStepListener stepListener;

    // Sprite textures and regions
    private final List<Texture> textures = new ArrayList<>();
    private final TextureRegion[] downWalk = new TextureRegion[13];
    private final TextureRegion[] upWalk   = new TextureRegion[13];
    private final TextureRegion[] leftWalk = new TextureRegion[8];
    private final TextureRegion[] rightWalk= new TextureRegion[8];
    private final TextureRegion[] idleAnim = new TextureRegion[9];

    // Footstep pacing
    private long lastStepMs = 0;
    private boolean isLeftFootStep = true;
    private static final long STEP_INTERVAL_MS = 400;
    private static final long STEP_INTERVAL_SHALLOW_WATER_MS = 640;
    private static final long STEP_INTERVAL_DEEP_WATER_MS = 950;

    // Vegetation state
    private boolean wasInBush = false;
    private boolean wasInTallGrass = false;

    // Movement state
    public boolean moving = false;

    // Life and health system
    public int maxLives = 3;
    public int lives = 3;
    public int maxHealth = 100;
    public int health = 100;
    public boolean dead = false;

    private int healCooldown = 0;
    private int regenTick = 0;

    // Drowning mechanic
    private int drownCounter = 0;
    public static final int DROWN_TICKS_DEEP = 180; // 3 seconds at 60fps

    // Invincibility
    private int invincibleCounter = 0;
    public static final int INVINCIBLE_DURATION = 120; // 2 seconds at 60fps

    // Movement speeds (pixels per second)
    private static final float BASE_SPEED = 240f;
    private static final float BUSH_SPEED = 180f;
    private static final float SHALLOW_SPEED = 120f;
    private static final float DEEP_SPEED = 60f;

    // Foot hitbox offsets (relative to worldX, worldY)
    public static final float HIT_OFFSET_X = 14f;
    public static final float HIT_OFFSET_Y = 34f;
    public static final float HIT_W = 20f;
    public static final float HIT_H = 12f;

    public Player() {
        super(0, 0, BASE_SPEED);
        loadSprites();
    }

    public void setStepListener(PlayerStepListener listener) {
        this.stepListener = listener;
    }

    private TextureRegion loadRegion(String path) {
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        return tr;
    }

    private void loadSprites() {
        for (int i = 1; i <= 13; i++) {
            downWalk[i - 1] = loadRegion("player/down" + i + ".png");
            upWalk[i - 1]   = loadRegion("player/up" + i + ".png");
        }
        for (int i = 1; i <= 8; i++) {
            leftWalk[i - 1]  = loadRegion("player/left" + i + ".png");
            rightWalk[i - 1] = loadRegion("player/right" + i + ".png");
        }
        for (int i = 0; i <= 8; i++) {
            idleAnim[i] = loadRegion("player/idle" + i + ".png");
        }
    }

    public void initSpawn(MapGenerator mapGen) {
        lives = maxLives;
        health = maxHealth;
        dead = false;
        drownCounter = 0;
        invincibleCounter = 0;
        direction = "down";

        java.util.Random rand = new java.util.Random(mapGen.seed);
        int startCol = 40 + rand.nextInt(mapGen.mapCols - 80);
        int startRow = 40 + rand.nextInt(mapGen.mapRows - 80);

        for (int r = 0; r < 50; r++) {
            for (int dc = -r; dc <= r; dc++) {
                for (int dr = -r; dr <= r; dr++) {
                    int c = startCol + dc;
                    int row = startRow + dr;
                    if (c >= 10 && c < mapGen.mapCols - 10 && row >= 10 && row < mapGen.mapRows - 10) {
                        if (!mapGen.isDeepWater[c][row] && !mapGen.isShallowWater[c][row] && mapGen.mapTreeNum[c][row] == 0) {
                            worldX = c * TileManager.TILE_SIZE;
                            worldY = row * TileManager.TILE_SIZE;
                            return;
                        }
                    }
                }
            }
        }
        worldX = 256 * TileManager.TILE_SIZE;
        worldY = 256 * TileManager.TILE_SIZE;
    }

    /**
     * Respawns the player at a safe random land position on the map.
     */
    public void randomSpawn(MapGenerator mapGen) {
        health = maxHealth;
        dead = false;
        drownCounter = 0;
        healCooldown = 0;
        regenTick = 0;
        invincibleCounter = INVINCIBLE_DURATION;
        direction = "down";
        speed = BASE_SPEED;
        moving = false;

        java.util.Random rand = new java.util.Random();
        for (int attempts = 0; attempts < 500; attempts++) {
            int col = 35 + rand.nextInt(mapGen.mapCols - 70);
            int row = 35 + rand.nextInt(mapGen.mapRows - 70);
            if (!mapGen.isDeepWater[col][row] && !mapGen.isShallowWater[col][row]
                    && mapGen.mapTileNum[col][row] != MapGenerator.TILE_STONE) {
                int treeVal = mapGen.mapTreeNum[col][row];
                if (((treeVal < 12 || treeVal > 22)
                        && (treeVal < MapGenerator.TILE_WILLOW1 || treeVal > MapGenerator.TILE_MANGROVE))
                        && treeVal != MapGenerator.TILE_ROCK1
                        && treeVal != MapGenerator.TILE_SNOWY_ROCK1
                        && treeVal != MapGenerator.TILE_SANDSTONE_ROCK
                        && treeVal != MapGenerator.TILE_LOG1 && treeVal != MapGenerator.TILE_LOG2) {
                    worldX = col * TileManager.TILE_SIZE;
                    worldY = row * TileManager.TILE_SIZE;
                    return;
                }
            }
        }
        worldX = 256 * TileManager.TILE_SIZE;
        worldY = 256 * TileManager.TILE_SIZE;
    }

    @Override
    public void update(float delta) {
        // Concrete method called when MapGenerator context is not provided
    }

    /**
     * Main tick update with world map collision, water depth, and foliage reaction.
     */
    public void update(float delta, MapGenerator mapGen, TileManager tileM) {
        if (dead) return;

        // Current tile under player feet
        int footCol = (int) ((worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int footRow = (int) ((worldY + HIT_OFFSET_Y + HIT_H / 2f) / TileManager.TILE_SIZE);
        int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);

        boolean inBush = tileM.isPlayerInBush(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);

        // Adjust speed according to terrain
        if (waterLevel == 2) {
            speed = DEEP_SPEED;
        } else if (waterLevel == 1) {
            speed = SHALLOW_SPEED;
        } else if (inBush) {
            speed = BUSH_SPEED;
        } else {
            speed = BASE_SPEED;
        }

        // Deep water drowning
        if (waterLevel == 2 && invincibleCounter == 0) {
            drownCounter++;
            if (drownCounter > 10) {
                damage(1);
            }
        } else {
            if (drownCounter > 0) {
                drownCounter = Math.max(0, drownCounter - 3);
            }
        }

        // Natural health regeneration
        if (health > 0 && health < maxHealth && !dead) {
            if (healCooldown > 0) {
                healCooldown--;
            } else {
                regenTick++;
                if (regenTick >= 90) {
                    health = Math.min(maxHealth, health + 1);
                    regenTick = 0;
                }
            }
        } else {
            regenTick = 0;
        }

        if (invincibleCounter > 0) {
            invincibleCounter--;
        }

        // Read movement input
        float inputX = 0f;
        float inputY = 0f;
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP))    inputY -= 1f;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN))  inputY += 1f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT))  inputX -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) inputX += 1f;

        if (inputX != 0f || inputY != 0f) {
            moving = true;

            if (inputY < 0f) {
                direction = "up";
            } else if (inputY > 0f) {
                direction = "down";
            } else if (inputX < 0f) {
                direction = "left";
            } else if (inputX > 0f) {
                direction = "right";
            }

            // Diagonal normalization
            float len = (float) Math.hypot(inputX, inputY);
            float moveX = (inputX / len) * speed * delta;
            float moveY = (inputY / len) * speed * delta;

            float prevX = worldX;
            float prevY = worldY;

            // X axis movement with collision check
            if (inputX != 0f) {
                float targetX = worldX + moveX;
                float hx = targetX + HIT_OFFSET_X;
                float hy = worldY + HIT_OFFSET_Y;
                if (!tileM.collidesWithTree(mapGen, hx, hy, HIT_W, HIT_H)) {
                    worldX = targetX;
                }
            }

            // Y axis movement with collision check
            if (inputY != 0f) {
                float targetY = worldY + moveY;
                float hx = worldX + HIT_OFFSET_X;
                float hy = targetY + HIT_OFFSET_Y;
                if (!tileM.collidesWithTree(mapGen, hx, hy, HIT_W, HIT_H)) {
                    worldY = targetY;
                }
            }

            // Keep within map boundaries
            int margin = 30;
            float minBoundX = margin * TileManager.TILE_SIZE;
            float minBoundY = margin * TileManager.TILE_SIZE;
            float maxBoundX = (mapGen.mapCols - margin) * TileManager.TILE_SIZE - TileManager.TILE_SIZE;
            float maxBoundY = (mapGen.mapRows - margin) * TileManager.TILE_SIZE - TileManager.TILE_SIZE;

            worldX = Math.max(minBoundX, Math.min(maxBoundX, worldX));
            worldY = Math.max(minBoundY, Math.min(maxBoundY, worldY));

            float movedX = worldX - prevX;
            float movedY = worldY - prevY;

            if (movedX != 0f || movedY != 0f) {
                long nowMs = System.currentTimeMillis();
                long stepInterval = STEP_INTERVAL_MS;
                if (waterLevel == 2) {
                    stepInterval = STEP_INTERVAL_DEEP_WATER_MS;
                } else if (waterLevel == 1) {
                    stepInterval = STEP_INTERVAL_SHALLOW_WATER_MS;
                }

                if (nowMs - lastStepMs >= stepInterval) {
                    lastStepMs = nowMs;
                    triggerStep(movedX, movedY, waterLevel);
                }

                // Vegetation transitions
                boolean nowInBush = tileM.isPlayerInBush(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);
                boolean nowInTallGrass = tileM.isPlayerInTallGrass(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);

                if ((!wasInBush && nowInBush) || (wasInBush && !nowInBush) || (!wasInTallGrass && nowInTallGrass)) {
                    if (stepListener != null) {
                        stepListener.onBushRustle();
                    }
                }
                wasInBush = nowInBush;
                wasInTallGrass = nowInTallGrass;
            }

            // Trigger bush sway
            tileM.checkBushInteraction(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H, moveX, moveY);

            // Walk animation
            spriteCounter += delta * 60f;
            if (direction.equals("up") || direction.equals("down")) {
                if (spriteCounter >= 2.5f) {
                    spriteNum++;
                    if (spriteNum > 13) spriteNum = 1;
                    spriteCounter = 0;
                }
            } else {
                if (spriteCounter >= 5f) {
                    spriteNum++;
                    if (spriteNum > 8) spriteNum = 1;
                    spriteCounter = 0;
                }
            }

        } else {
            // Idle state
            if (moving) {
                spriteNum = 1;
                spriteCounter = 0;
            }
            moving = false;
            lastStepMs = 0;

            if (direction.equals("down")) {
                spriteCounter += delta * 60f;
                if (spriteCounter >= 60f) {
                    spriteNum++;
                    if (spriteNum > 9) spriteNum = 1;
                    spriteCounter = 0;
                }
            } else {
                spriteNum = 1;
                spriteCounter = 0;
            }
        }
    }

    private void triggerStep(float moveDx, float moveDy, int waterLevel) {
        float footCenterX = worldX + HIT_OFFSET_X + HIT_W / 2f;
        float footCenterY = worldY + HIT_OFFSET_Y + HIT_H / 2f;

        float len = (float) Math.hypot(moveDx, moveDy);
        float perpX = 0f;
        float perpY = 0f;
        if (len > 0.0001f) {
            perpX = -moveDy / len;
            perpY = moveDx / len;
        }

        float footSideOffset = isLeftFootStep ? -4.5f : 4.5f;
        float footX = footCenterX + perpX * footSideOffset;
        float footY = footCenterY + perpY * footSideOffset;

        double angle = Math.atan2(moveDy, moveDx);
        int footCol = (int) (footX / TileManager.TILE_SIZE);
        int footRow = (int) (footY / TileManager.TILE_SIZE);

        if (stepListener != null) {
            stepListener.onStep(footX, footY, angle + Math.PI / 2.0, isLeftFootStep, footCol, footRow);
        }

        isLeftFootStep = !isLeftFootStep;
    }

    public void damage(int amount) {
        if (invincibleCounter > 0 || dead) return;
        health -= amount;
        healCooldown = 300;
        if (health <= 0) {
            health = 0;
            loseLife();
        }
    }

    public void heal(int amount) {
        if (dead) return;
        health = Math.min(maxHealth, health + amount);
    }

    public void loseLife() {
        lives--;
        drownCounter = 0;
        healCooldown = 0;
        regenTick = 0;
        if (lives <= 0) {
            lives = 0;
            health = 0;
            dead = true;
        } else {
            health = maxHealth;
            invincibleCounter = INVINCIBLE_DURATION;
        }
    }

    public float getDrownProgress() {
        if (drownCounter == 0) return 0f;
        return (float) drownCounter / DROWN_TICKS_DEEP;
    }

    public float getHealthRatio() {
        if (maxHealth <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) health / maxHealth));
    }

    public TextureRegion getCurrentSprite() {
        switch (direction) {
            case "up":
                int uIdx = Math.max(0, Math.min(12, spriteNum - 1));
                return upWalk[uIdx];
            case "left":
                int lIdx = Math.max(0, Math.min(7, spriteNum - 1));
                return leftWalk[lIdx];
            case "right":
                int rIdx = Math.max(0, Math.min(7, spriteNum - 1));
                return rightWalk[rIdx];
            case "down":
            default:
                if (!moving) {
                    int iIdx = Math.max(0, Math.min(8, spriteNum - 1));
                    return idleAnim[iIdx];
                } else {
                    int dIdx = Math.max(0, Math.min(12, spriteNum - 1));
                    return downWalk[dIdx];
                }
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        draw(batch, 0, null);
    }

    /**
     * Returns how many world-pixels the sprite should shift DOWN when in water.
     * Mirrors the original yShift logic from v0.3.
     */
    public int getWaterYShift(int waterLevel) {
        if (waterLevel == 1) return 4;
        if (waterLevel == 2) return 4 + (int) (getDrownProgress() * 8);
        return 0;
    }

    /**
     * Returns how many screen-pixels to clip from the BOTTOM of the sprite for water submersion.
     * Mirrors the original cutOff logic from v0.3 (tileSize = 48).
     */
    public int getWaterCutOff(int waterLevel) {
        if (waterLevel == 1) return 16;
        if (waterLevel == 2) return 16 + (int) (getDrownProgress() * 16);
        return 0;
    }

    /**
     * Draws the full player sprite at the given world position (no clipping applied here).
     * Water submersion clipping is handled by the caller (GameScreen) via GL scissor test,
     * matching the original g2.clipRect approach from v0.3.
     */
    public void draw(SpriteBatch batch, int waterLevel, TextureRegion foamRegion) {
        TextureRegion current = getCurrentSprite();
        if (current == null) return;

        // Invincibility blink
        if (invincibleCounter > 0 && (invincibleCounter / 10) % 2 == 0) {
            batch.setColor(1f, 1f, 1f, 0.4f);
        } else {
            batch.setColor(1f, 1f, 1f, 1f);
        }

        // yShift sinks the sprite slightly into water so feet meet the waterline
        float drawY = worldY + getWaterYShift(waterLevel);

        // Draw full sprite — clipping of the submerged portion is done in GameScreen with GL scissor
        batch.draw(current, worldX, drawY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);

        // Foam overlay at the waist line (positioned at: drawY + tileSize - cutOff - 3)
        if (waterLevel > 0 && foamRegion != null) {
            int cutOff = getWaterCutOff(waterLevel);
            float foamY = drawY + TileManager.TILE_SIZE - cutOff - 3f;
            batch.setColor(1f, 1f, 1f, 0.9f);
            batch.draw(foamRegion, worldX, foamY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void dispose() {
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
    }
}
