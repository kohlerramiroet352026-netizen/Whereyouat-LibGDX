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

        float playerCenterX = player.worldX + TileManager.TILE_SIZE / 2f;
        float playerCenterY = player.worldY + TileManager.TILE_SIZE / 2f;

        Iterator<Bird> iterator = birds.iterator();
        while (iterator.hasNext()) {
            Bird bird = iterator.next();
            if (bird.state == BirdState.PERCHED) {
                float distPx = (float) Math.hypot(bird.worldX - playerCenterX, bird.worldY - playerCenterY);
                if (distPx > DESPAWN_DIST_TILES * TileManager.TILE_SIZE) {
                    iterator.remove();
                    continue;
                }

                // Spooked by swaying foliage
                long foliageKey = (((long) bird.perchCol) << 32) | (bird.perchRow & 0xFFFFFFFFL);
                if ((bird.perchType == PerchType.TREE_TOP || bird.perchType == PerchType.BUSH_TOP)
                        && tileM.swayingBushes.containsKey(foliageKey)) {
                    float fleeDirectionX = (rng.nextFloat() - 0.5f);
                    float fleeDirectionY = -0.7f + (rng.nextFloat() - 0.5f) * 0.5f;
                    startFleeing(bird, fleeDirectionX, fleeDirectionY);
                    continue;
                }

                // Spooked by player approach
                float triggerX = (bird.perchType == PerchType.TREE_TOP)
                        ? (bird.perchCol * TileManager.TILE_SIZE + TileManager.TILE_SIZE)
                        : bird.worldX;
                float triggerY = (bird.perchType == PerchType.TREE_TOP)
                        ? (bird.perchRow * TileManager.TILE_SIZE + TileManager.TILE_SIZE)
                        : bird.worldY;

                float distanceX = triggerX - playerCenterX;
                float distanceY = triggerY - playerCenterY;
                if (distanceX * distanceX + distanceY * distanceY < FLEE_RADIUS_PX * FLEE_RADIUS_PX) {
                    float fleeDirectionX = bird.worldX - playerCenterX;
                    float fleeDirectionY = bird.worldY - playerCenterY;
                    startFleeing(bird, fleeDirectionX, fleeDirectionY);
                    continue;
                }

                // Ambient state
                if (bird.perchType == PerchType.GRASS) {
                    updateGroundBirdAI(bird, delta, mapGen);
                } else {
                    bird.idleFidgetTimer--;
                    if (bird.idleFidgetTimer <= 0) {
                        bird.idleFidgetTimer = 120 + rng.nextInt(180);
                        bird.facingLeft = !bird.facingLeft;
                    }
                }

            } else { // FLYING
                bird.worldX += bird.velX * delta;
                bird.worldY += bird.velY * delta;

                bird.flapTick++;
                if (bird.flapTick >= 7) {
                    bird.flapTick = 0;
                    bird.flapFrame = 1 - bird.flapFrame;
                }

                if (bird.velX > 5f) {
                    bird.facingLeft = false;
                } else if (bird.velX < -5f) {
                    bird.facingLeft = true;
                }

                // Cull once far outside active gameplay area
                float maxBoundX = mapGen.mapCols * TileManager.TILE_SIZE;
                float maxBoundY = mapGen.mapRows * TileManager.TILE_SIZE;
                if (bird.worldX < -CULL_MARGIN_PX || bird.worldX > maxBoundX + CULL_MARGIN_PX ||
                    bird.worldY < -CULL_MARGIN_PX || bird.worldY > maxBoundY + CULL_MARGIN_PX) {
                    iterator.remove();
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

    private void updateGroundBirdAI(Bird bird, float delta, MapGenerator mapGen) {
        if (bird.isHopping) {
            bird.hopTick++;
            float progress = (float) bird.hopTick / bird.hopDuration;
            if (progress >= 1f) {
                bird.worldX = bird.hopTargetX;
                bird.groundBaseY = bird.hopTargetY;
                bird.worldY = bird.groundBaseY;
                bird.isHopping = false;
                bird.perchCol = (int) (bird.worldX / TileManager.TILE_SIZE);
                bird.perchRow = (int) (bird.groundBaseY / TileManager.TILE_SIZE);

                if (bird.hopsRemaining > 0) {
                    bird.hopsRemaining--;
                    bird.aiTimer = 6 + rng.nextInt(6);
                } else {
                    bird.aiTimer = 45 + rng.nextInt(85);
                }
            } else {
                bird.worldX = bird.hopStartX + (bird.hopTargetX - bird.hopStartX) * progress;
                float currentBaseY = bird.hopStartY + (bird.hopTargetY - bird.hopStartY) * progress;
                float arc = (float) Math.sin(progress * Math.PI) * bird.hopHeight;
                bird.worldY = currentBaseY - arc;
            }
        } else {
            bird.aiTimer--;
            if (bird.aiTimer <= 0) {
                int randomRoll = rng.nextInt(100);
                if (randomRoll < 55) {
                    if (rng.nextFloat() < 0.25f) {
                        bird.facingLeft = !bird.facingLeft;
                    }

                    float hopDistX = (bird.facingLeft ? -1f : 1f) * (10f + rng.nextFloat() * 8f);
                    float hopDistY = (rng.nextFloat() - 0.5f) * 6f;

                    float targetX = bird.worldX + hopDistX;
                    float targetY = bird.groundBaseY + hopDistY;

                    int targetCol = (int) (targetX / TileManager.TILE_SIZE);
                    int targetRow = (int) (targetY / TileManager.TILE_SIZE);

                    if (isWalkableGrass(mapGen, targetCol, targetRow)) {
                        bird.isHopping = true;
                        bird.hopStartX = bird.worldX;
                        bird.hopStartY = bird.groundBaseY;
                        bird.hopTargetX = targetX;
                        bird.hopTargetY = targetY;
                        bird.hopHeight = 5f + rng.nextFloat() * 4f;
                        bird.hopDuration = 11 + rng.nextInt(4);
                        bird.hopTick = 0;

                        if (bird.hopsRemaining == 0 && rng.nextFloat() < 0.60f) {
                            bird.hopsRemaining = 1 + rng.nextInt(2);
                        }
                    } else {
                        bird.facingLeft = !bird.facingLeft;
                        bird.aiTimer = 25 + rng.nextInt(35);
                    }
                } else if (randomRoll < 80) {
                    bird.facingLeft = !bird.facingLeft;
                    bird.aiTimer = 35 + rng.nextInt(55);
                } else {
                    bird.aiTimer = 45 + rng.nextInt(70);
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

    private void startFleeing(Bird bird, float fleeDirectionX, float fleeDirectionY) {
        bird.state = BirdState.FLYING;
        bird.isHopping = false;
        bird.hopsRemaining = 0;

        double angle = Math.atan2(fleeDirectionY, fleeDirectionX);
        angle += (rng.nextDouble() - 0.5) * Math.toRadians(90.0);
        float directionLength = (float) Math.sqrt(fleeDirectionX * fleeDirectionX + fleeDirectionY * fleeDirectionY);
        if (directionLength < 0.001f) {
            angle = rng.nextDouble() * Math.PI * 2;
        }

        bird.velX = (float) (Math.cos(angle) * FLY_SPEED_PX_SEC);
        bird.velY = (float) (Math.sin(angle) * FLY_SPEED_PX_SEC);

        bird.facingLeft = (bird.velX < 0);
        bird.flapFrame = 1;
        bird.flapTick = 0;
    }

    private void trySpawn(MapGenerator map, float playerX, float playerY) {
        if (birds.size() >= MAX_BIRDS) return;
        spawnBatch(map, playerX, playerY, 1);
    }

    private void spawnBatch(MapGenerator map, float playerX, float playerY, int count) {
        int camCol = (int) (playerX / TileManager.TILE_SIZE);
        int camRow = (int) (playerY / TileManager.TILE_SIZE);

        int visibleMargin = 10;
        int spawnRadius = SPAWN_RING_TILES;

        int outerMinCol = Math.max(1, camCol - spawnRadius);
        int outerMaxCol = Math.min(map.mapCols - 2, camCol + spawnRadius);
        int outerMinRow = Math.max(1, camRow - spawnRadius);
        int outerMaxRow = Math.min(map.mapRows - 2, camRow + spawnRadius);

        List<int[]> foliageCandidates = new ArrayList<>();
        List<int[]> grassCandidates = new ArrayList<>();

        int currentGrassBirds = 0;
        for (Bird bird : birds) {
            if (bird.perchType == PerchType.GRASS && bird.state == BirdState.PERCHED) {
                currentGrassBirds++;
            }
        }

        for (int row = outerMinRow; row <= outerMaxRow; row++) {
            for (int col = outerMinCol; col <= outerMaxCol; col++) {
                if (Math.abs(col - camCol) <= visibleMargin && Math.abs(row - camRow) <= visibleMargin) {
                    continue; // Skip visible area
                }

                if (tileOccupied(col, row)) continue;

                PerchType perchType = getPerchType(map, col, row);
                if (perchType == null) continue;

                if (perchType == PerchType.TREE_TOP || perchType == PerchType.BUSH_TOP) {
                    foliageCandidates.add(new int[]{ col, row, perchType.ordinal() });
                } else if (perchType == PerchType.GRASS && currentGrassBirds < MAX_GRASS_BIRDS) {
                    grassCandidates.add(new int[]{ col, row, perchType.ordinal() });
                }
            }
        }

        int toAdd = Math.min(count, MAX_BIRDS - birds.size());
        for (int i = 0; i < toAdd; i++) {
            int[] candidate = null;
            boolean pickFoliage = rng.nextFloat() < 0.85f;
            if (pickFoliage && !foliageCandidates.isEmpty()) {
                candidate = foliageCandidates.remove(rng.nextInt(foliageCandidates.size()));
            } else if (!grassCandidates.isEmpty() && currentGrassBirds < MAX_GRASS_BIRDS) {
                candidate = grassCandidates.remove(rng.nextInt(grassCandidates.size()));
                currentGrassBirds++;
            } else if (!foliageCandidates.isEmpty()) {
                candidate = foliageCandidates.remove(rng.nextInt(foliageCandidates.size()));
            }

            if (candidate == null) break;

            int col = candidate[0];
            int row = candidate[1];
            PerchType perchType = PerchType.values()[candidate[2]];

            float worldSpawnX = perchWorldX(col, perchType);
            float worldSpawnY = perchWorldY(row, perchType);
            int spriteIndex = rng.nextInt(3);
            boolean facingLeft = rng.nextBoolean();

            birds.add(new Bird(worldSpawnX, worldSpawnY, spriteIndex, perchType, col, row, facingLeft));
        }
    }

    private boolean tileOccupied(int col, int row) {
        for (Bird bird : birds) {
            if (bird.state == BirdState.PERCHED && bird.perchCol == col && bird.perchRow == row) {
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

    private float perchWorldX(int col, PerchType perchType) {
        switch (perchType) {
            case TREE_TOP:
                return col * TileManager.TILE_SIZE + TileManager.TILE_SIZE + (rng.nextFloat() - 0.5f) * 6f;
            case BUSH_TOP:
                return col * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f + (rng.nextFloat() - 0.5f) * 6f;
            case GRASS:
            default:
                return col * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f + (rng.nextFloat() - 0.5f) * 12f;
        }
    }

    private float perchWorldY(int row, PerchType perchType) {
        int tileY = row * TileManager.TILE_SIZE;
        switch (perchType) {
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
        for (Bird bird : birds) {
            if (bird.state == BirdState.PERCHED && bird.perchType == PerchType.GRASS) {
                drawBird(batch, bird, minX, minY, maxX, maxY);
            }
        }
    }

    public void drawInFrontOfPlayer(SpriteBatch batch, float minX, float minY, float maxX, float maxY) {
        for (Bird bird : birds) {
            if (bird.state == BirdState.FLYING ||
               (bird.state == BirdState.PERCHED && (bird.perchType == PerchType.TREE_TOP || bird.perchType == PerchType.BUSH_TOP))) {
                drawBird(batch, bird, minX, minY, maxX, maxY);
            }
        }
    }

    private void drawBird(SpriteBatch batch, Bird bird, float minX, float minY, float maxX, float maxY) {
        int spriteIndex = Math.max(0, Math.min(2, bird.spriteIndex));
        int flapIndex = (bird.state == BirdState.PERCHED) ? 0 : bird.flapFrame;
        TextureRegion birdFrame = frames[spriteIndex][flapIndex];
        if (birdFrame == null) return;

        float drawX = bird.worldX - DRAW_SIZE / 2f;
        float drawY = bird.worldY - DRAW_SIZE / 2f;

        if (flapIndex == 1) {
            drawY -= (TileManager.TILE_SIZE / 16f); // 3px vertical flap lift
        }

        if (drawX + DRAW_SIZE < minX || drawX > maxX || drawY + DRAW_SIZE < minY || drawY > maxY) {
            return;
        }

        float scaleX = bird.facingLeft ? 1f : -1f;
        batch.draw(birdFrame,
                   drawX, drawY,
                   DRAW_SIZE / 2f, DRAW_SIZE / 2f,
                   DRAW_SIZE, DRAW_SIZE,
                   scaleX, 1f,
                   0f);
    }

    public void scatterAll() {
        for (Bird bird : birds) {
            if (bird.state == BirdState.PERCHED) {
                float randomDeltaX = (rng.nextFloat() - 0.5f) * 2f;
                float randomDeltaY = (rng.nextFloat() - 0.5f) * 2f;
                startFleeing(bird, randomDeltaX, randomDeltaY);
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
