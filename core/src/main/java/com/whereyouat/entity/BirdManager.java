package com.whereyouat.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Bird.BirdState;
import com.whereyouat.entity.Bird.PerchType;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * BirdManager for LibGDX.
 * Handles procedural ambient bird spawning, perched and flying AI states,
 * player/foliage spook reactions, parabolic hopping, and dual-depth rendering.
 */
public class BirdManager {

    private static final int   MAX_BIRDS          = 6;
    private static final int   MAX_GRASS_BIRDS    = 1;
    private static final float SPAWN_INTERVAL_SEC = 3.0f; // ~180 ticks
    private static final int   SPAWN_RING_TILES   = 14;
    private static final float FLEE_RADIUS_PX     = 160f;
    private static final float FLY_SPEED_PX_SEC   = 276f;
    private static final float CULL_MARGIN_PX     = 90f;
    private static final int   SPRITE_SIZE        = 13;
    private static final int   DRAW_SIZE          = SPRITE_SIZE * 3; // 39 px
    private static final float DESPAWN_DIST_TILES = 32f;

    private Random rng;
    private final List<Bird> birds = new ArrayList<>();
    private final List<Texture> textures = new ArrayList<>();
    private final TextureRegion[][] frames = new TextureRegion[3][2];

    private float spawnTimer = 0.6f;

    public BirdManager(long seed) {
        this.rng = new Random(seed ^ 0xB1D_CAF3L);
        loadSprites();
    }

    private void loadSprites() {
        for (int i = 1; i <= 3; i++) {
            String path = "critters/bird" + i + ".png";
            if (Gdx.files.internal(path).exists()) {
                Texture tex = new Texture(Gdx.files.internal(path));
                tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                textures.add(tex);
                if (tex.getHeight() >= SPRITE_SIZE * 2) {
                    frames[i - 1][0] = new TextureRegion(tex, 0, 0, SPRITE_SIZE, SPRITE_SIZE);
                    frames[i - 1][1] = new TextureRegion(tex, 0, SPRITE_SIZE, SPRITE_SIZE, SPRITE_SIZE);
                } else {
                    frames[i - 1][0] = new TextureRegion(tex);
                    frames[i - 1][1] = new TextureRegion(tex);
                }
                frames[i - 1][0].flip(false, true);
                frames[i - 1][1].flip(false, true);
            }
        }
    }

    public void initForWorld(long seed, MapGenerator mapGen, Player player) {
        this.rng = new Random(seed ^ 0xB1D_CAF3L);
        birds.clear();
        spawnTimer = 0.6f;
        if (player != null && mapGen != null) {
            spawnBatch(mapGen, player.worldX, player.worldY, 2 + rng.nextInt(2));
        }
    }

    public void update(float delta, MapGenerator mapGen, TileManager tileM, Player player) {
        if (mapGen == null || player == null) return;

        float px = player.worldX + TileManager.TILE_SIZE / 2f;
        float py = player.worldY + TileManager.TILE_SIZE / 2f;

        Iterator<Bird> it = birds.iterator();
        while (it.hasNext()) {
            Bird b = it.next();
            if (b.state == BirdState.PERCHED) {
                float distPx = (float) Math.hypot(b.worldX - px, b.worldY - py);
                if (distPx > DESPAWN_DIST_TILES * TileManager.TILE_SIZE) {
                    it.remove();
                    continue;
                }

                // Spooked by swaying foliage
                long key = (((long) b.perchCol) << 32) | (b.perchRow & 0xFFFFFFFFL);
                if ((b.perchType == PerchType.TREE_TOP || b.perchType == PerchType.BUSH_TOP)
                        && tileM.swayingBushes.containsKey(key)) {
                    float awayDx = (rng.nextFloat() - 0.5f);
                    float awayDy = -0.7f + (rng.nextFloat() - 0.5f) * 0.5f;
                    startFleeing(b, awayDx, awayDy);
                    continue;
                }

                // Spooked by player approach
                float triggerX = (b.perchType == PerchType.TREE_TOP)
                        ? (b.perchCol * TileManager.TILE_SIZE + TileManager.TILE_SIZE)
                        : b.worldX;
                float triggerY = (b.perchType == PerchType.TREE_TOP)
                        ? (b.perchRow * TileManager.TILE_SIZE + TileManager.TILE_SIZE)
                        : b.worldY;

                float distDx = triggerX - px;
                float distDy = triggerY - py;
                if (distDx * distDx + distDy * distDy < FLEE_RADIUS_PX * FLEE_RADIUS_PX) {
                    float awayDx = b.worldX - px;
                    float awayDy = b.worldY - py;
                    startFleeing(b, awayDx, awayDy);
                    continue;
                }

                // Ambient state
                if (b.perchType == PerchType.GRASS) {
                    updateGroundBirdAI(b, delta, mapGen);
                } else {
                    b.idleFidgetTimer--;
                    if (b.idleFidgetTimer <= 0) {
                        b.idleFidgetTimer = 120 + rng.nextInt(180);
                        b.facingLeft = !b.facingLeft;
                    }
                }

            } else { // FLYING
                b.worldX += b.velX * delta;
                b.worldY += b.velY * delta;

                b.flapTick++;
                if (b.flapTick >= 7) {
                    b.flapTick = 0;
                    b.flapFrame = 1 - b.flapFrame;
                }

                if (b.velX > 5f) {
                    b.facingLeft = false;
                } else if (b.velX < -5f) {
                    b.facingLeft = true;
                }

                // Cull once far outside active gameplay area
                float maxBoundX = mapGen.mapCols * TileManager.TILE_SIZE;
                float maxBoundY = mapGen.mapRows * TileManager.TILE_SIZE;
                if (b.worldX < -CULL_MARGIN_PX || b.worldX > maxBoundX + CULL_MARGIN_PX ||
                    b.worldY < -CULL_MARGIN_PX || b.worldY > maxBoundY + CULL_MARGIN_PX) {
                    it.remove();
                }
            }
        }

        // Periodic ambient spawn
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawnTimer = SPAWN_INTERVAL_SEC;
            trySpawn(mapGen, player.worldX, player.worldY);
        }
    }

    private void updateGroundBirdAI(Bird b, float delta, MapGenerator mapGen) {
        if (b.isHopping) {
            b.hopTick++;
            float t = (float) b.hopTick / b.hopDuration;
            if (t >= 1f) {
                b.worldX = b.hopTargetX;
                b.groundBaseY = b.hopTargetY;
                b.worldY = b.groundBaseY;
                b.isHopping = false;
                b.perchCol = (int) (b.worldX / TileManager.TILE_SIZE);
                b.perchRow = (int) (b.groundBaseY / TileManager.TILE_SIZE);

                if (b.hopsRemaining > 0) {
                    b.hopsRemaining--;
                    b.aiTimer = 6 + rng.nextInt(6);
                } else {
                    b.aiTimer = 45 + rng.nextInt(85);
                }
            } else {
                b.worldX = b.hopStartX + (b.hopTargetX - b.hopStartX) * t;
                float currentBaseY = b.hopStartY + (b.hopTargetY - b.hopStartY) * t;
                float arc = (float) Math.sin(t * Math.PI) * b.hopHeight;
                b.worldY = currentBaseY - arc;
            }
        } else {
            b.aiTimer--;
            if (b.aiTimer <= 0) {
                int roll = rng.nextInt(100);
                if (roll < 55) {
                    if (rng.nextFloat() < 0.25f) {
                        b.facingLeft = !b.facingLeft;
                    }

                    float hopDistX = (b.facingLeft ? -1f : 1f) * (10f + rng.nextFloat() * 8f);
                    float hopDistY = (rng.nextFloat() - 0.5f) * 6f;

                    float targetX = b.worldX + hopDistX;
                    float targetY = b.groundBaseY + hopDistY;

                    int targetCol = (int) (targetX / TileManager.TILE_SIZE);
                    int targetRow = (int) (targetY / TileManager.TILE_SIZE);

                    if (isWalkableGrass(mapGen, targetCol, targetRow)) {
                        b.isHopping = true;
                        b.hopStartX = b.worldX;
                        b.hopStartY = b.groundBaseY;
                        b.hopTargetX = targetX;
                        b.hopTargetY = targetY;
                        b.hopHeight = 5f + rng.nextFloat() * 4f;
                        b.hopDuration = 11 + rng.nextInt(4);
                        b.hopTick = 0;

                        if (b.hopsRemaining == 0 && rng.nextFloat() < 0.60f) {
                            b.hopsRemaining = 1 + rng.nextInt(2);
                        }
                    } else {
                        b.facingLeft = !b.facingLeft;
                        b.aiTimer = 25 + rng.nextInt(35);
                    }
                } else if (roll < 80) {
                    b.facingLeft = !b.facingLeft;
                    b.aiTimer = 35 + rng.nextInt(55);
                } else {
                    b.aiTimer = 45 + rng.nextInt(70);
                }
            }
        }
    }

    private boolean isWalkableGrass(MapGenerator map, int col, int row) {
        if (col < 1 || col >= map.mapCols - 1 || row < 1 || row >= map.mapRows - 1) return false;
        if (map.isDeepWater[col][row] || map.isShallowWater[col][row]) return false;
        if (map.isSnowBiome[col][row] || map.isForestBiome[col][row]) return false;

        int groundIdx = map.mapTileNum[col][row];
        int treeIdx   = map.mapTreeNum[col][row];

        if (groundIdx == MapGenerator.TILE_SAND1 || groundIdx == MapGenerator.TILE_SAND2
                || groundIdx == 24 || groundIdx == 25 || groundIdx == 26) {
            return false;
        }

        return (groundIdx >= 0 && groundIdx <= 3 && treeIdx == 0);
    }

    private void startFleeing(Bird b, float awayDx, float awayDy) {
        b.state = BirdState.FLYING;
        b.isHopping = false;
        b.hopsRemaining = 0;

        double angle = Math.atan2(awayDy, awayDx);
        angle += (rng.nextDouble() - 0.5) * Math.toRadians(90.0);
        float len = (float) Math.sqrt(awayDx * awayDx + awayDy * awayDy);
        if (len < 0.001f) {
            angle = rng.nextDouble() * Math.PI * 2;
        }

        b.velX = (float) (Math.cos(angle) * FLY_SPEED_PX_SEC);
        b.velY = (float) (Math.sin(angle) * FLY_SPEED_PX_SEC);

        b.facingLeft = (b.velX < 0);
        b.flapFrame = 1;
        b.flapTick = 0;
    }

    private void trySpawn(MapGenerator map, float playerX, float playerY) {
        if (birds.size() >= MAX_BIRDS) return;
        spawnBatch(map, playerX, playerY, 1);
    }

    private void spawnBatch(MapGenerator map, float playerX, float playerY, int count) {
        int camCol = (int) (playerX / TileManager.TILE_SIZE);
        int camRow = (int) (playerY / TileManager.TILE_SIZE);

        int visMargin = 10;
        int r = SPAWN_RING_TILES;

        int outerMinCol = Math.max(1, camCol - r);
        int outerMaxCol = Math.min(map.mapCols - 2, camCol + r);
        int outerMinRow = Math.max(1, camRow - r);
        int outerMaxRow = Math.min(map.mapRows - 2, camRow + r);

        List<int[]> foliageCandidates = new ArrayList<>();
        List<int[]> grassCandidates = new ArrayList<>();

        int currentGrassBirds = 0;
        for (Bird b : birds) {
            if (b.perchType == PerchType.GRASS && b.state == BirdState.PERCHED) {
                currentGrassBirds++;
            }
        }

        for (int row = outerMinRow; row <= outerMaxRow; row++) {
            for (int col = outerMinCol; col <= outerMaxCol; col++) {
                if (Math.abs(col - camCol) <= visMargin && Math.abs(row - camRow) <= visMargin) {
                    continue; // Skip visible area
                }

                if (tileOccupied(col, row)) continue;

                PerchType pt = getPerchType(map, col, row);
                if (pt == null) continue;

                if (pt == PerchType.TREE_TOP || pt == PerchType.BUSH_TOP) {
                    foliageCandidates.add(new int[]{ col, row, pt.ordinal() });
                } else if (pt == PerchType.GRASS && currentGrassBirds < MAX_GRASS_BIRDS) {
                    grassCandidates.add(new int[]{ col, row, pt.ordinal() });
                }
            }
        }

        int toAdd = Math.min(count, MAX_BIRDS - birds.size());
        for (int i = 0; i < toAdd; i++) {
            int[] cand = null;
            boolean pickFoliage = rng.nextFloat() < 0.85f;
            if (pickFoliage && !foliageCandidates.isEmpty()) {
                cand = foliageCandidates.remove(rng.nextInt(foliageCandidates.size()));
            } else if (!grassCandidates.isEmpty() && currentGrassBirds < MAX_GRASS_BIRDS) {
                cand = grassCandidates.remove(rng.nextInt(grassCandidates.size()));
                currentGrassBirds++;
            } else if (!foliageCandidates.isEmpty()) {
                cand = foliageCandidates.remove(rng.nextInt(foliageCandidates.size()));
            }

            if (cand == null) break;

            int col = cand[0];
            int row = cand[1];
            PerchType pt = PerchType.values()[cand[2]];

            float wx = perchWorldX(col, pt);
            float wy = perchWorldY(row, pt);
            int spriteIndex = rng.nextInt(3);
            boolean facingLeft = rng.nextBoolean();

            birds.add(new Bird(wx, wy, spriteIndex, pt, col, row, facingLeft));
        }
    }

    private boolean tileOccupied(int col, int row) {
        for (Bird b : birds) {
            if (b.state == BirdState.PERCHED && b.perchCol == col && b.perchRow == row) {
                return true;
            }
        }
        return false;
    }

    private PerchType getPerchType(MapGenerator map, int col, int row) {
        if (map.isDeepWater[col][row] || map.isShallowWater[col][row]) return null;

        int groundIdx = map.mapTileNum[col][row];
        int treeIdx   = map.mapTreeNum[col][row];
        boolean isSnow    = map.isSnowBiome[col][row];
        boolean isForest  = map.isForestBiome[col][row];

        if (groundIdx == MapGenerator.TILE_SAND1 || groundIdx == MapGenerator.TILE_SAND2
                || groundIdx == 24 || groundIdx == 25 || groundIdx == 26) {
            return null;
        }

        if (isForest && treeIdx >= MapGenerator.TILE_TREE1 && treeIdx < MapGenerator.TILE_TREE4) {
            return PerchType.TREE_TOP;
        }

        if (!isSnow && !isForest && treeIdx >= MapGenerator.TILE_BUSH1 && treeIdx <= MapGenerator.TILE_BUSH5_LARGE) {
            return PerchType.BUSH_TOP;
        }

        if (!isSnow && !isForest && groundIdx >= 0 && groundIdx <= 3 && treeIdx == 0) {
            long hash = (long) col * 0x9E3779B97F4A7C15L ^ (long) row * 0x517CC1B727220A95L ^ map.seed;
            if ((hash & 0x1FL) != 0) return null;
            return PerchType.GRASS;
        }

        return null;
    }

    private float perchWorldX(int col, PerchType pt) {
        switch (pt) {
            case TREE_TOP:
                return col * TileManager.TILE_SIZE + TileManager.TILE_SIZE + (rng.nextFloat() - 0.5f) * 6f;
            case BUSH_TOP:
                return col * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f + (rng.nextFloat() - 0.5f) * 6f;
            case GRASS:
            default:
                return col * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f + (rng.nextFloat() - 0.5f) * 12f;
        }
    }

    private float perchWorldY(int row, PerchType pt) {
        int tileY = row * TileManager.TILE_SIZE;
        switch (pt) {
            case TREE_TOP:
                int treeTopY = tileY - TileManager.TILE_SIZE * 2;
                return treeTopY + 12f - DRAW_SIZE / 2f;
            case BUSH_TOP:
                return tileY + 6f - DRAW_SIZE / 2f;
            case GRASS:
            default:
                return tileY + TileManager.TILE_SIZE * 0.55f;
        }
    }

    public void drawBehindPlayer(SpriteBatch batch, float minX, float minY, float maxX, float maxY) {
        for (Bird b : birds) {
            if (b.state == BirdState.PERCHED && b.perchType == PerchType.GRASS) {
                drawBird(batch, b, minX, minY, maxX, maxY);
            }
        }
    }

    public void drawInFrontOfPlayer(SpriteBatch batch, float minX, float minY, float maxX, float maxY) {
        for (Bird b : birds) {
            if (b.state == BirdState.FLYING ||
               (b.state == BirdState.PERCHED && (b.perchType == PerchType.TREE_TOP || b.perchType == PerchType.BUSH_TOP))) {
                drawBird(batch, b, minX, minY, maxX, maxY);
            }
        }
    }

    private void drawBird(SpriteBatch batch, Bird b, float minX, float minY, float maxX, float maxY) {
        int sIdx = Math.max(0, Math.min(2, b.spriteIndex));
        int fIdx = (b.state == BirdState.PERCHED) ? 0 : b.flapFrame;
        TextureRegion tr = frames[sIdx][fIdx];
        if (tr == null) return;

        float drawX = b.worldX - DRAW_SIZE / 2f;
        float drawY = b.worldY - DRAW_SIZE / 2f;

        if (fIdx == 1) {
            drawY -= (TileManager.TILE_SIZE / 16f); // 3px vertical flap lift
        }

        if (drawX + DRAW_SIZE < minX || drawX > maxX || drawY + DRAW_SIZE < minY || drawY > maxY) {
            return;
        }

        float scaleX = b.facingLeft ? 1f : -1f;
        batch.draw(tr,
                   drawX, drawY,
                   DRAW_SIZE / 2f, DRAW_SIZE / 2f,
                   DRAW_SIZE, DRAW_SIZE,
                   scaleX, 1f,
                   0f);
    }

    public void scatterAll() {
        for (Bird b : birds) {
            if (b.state == BirdState.PERCHED) {
                float dx = (rng.nextFloat() - 0.5f) * 2f;
                float dy = (rng.nextFloat() - 0.5f) * 2f;
                startFleeing(b, dx, dy);
            }
        }
    }

    public void clear() {
        birds.clear();
        spawnTimer = 0.6f;
    }

    public void dispose() {
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
        birds.clear();
    }
}
