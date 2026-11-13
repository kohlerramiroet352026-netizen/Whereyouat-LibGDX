package com.whereyouat.map;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.effects.SnowParticleSystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FoliageSwayManager {
    private static final int TILE_SIZE = TileManager.TILE_SIZE;

    public final Map<Long, TileManager.BushSway> swayingBushes = new HashMap<>();

    private int windGustTimer = 480;
    private boolean gustActive = false;
    private int gustTicks = 0;
    private int gustDuration = 420;
    private float gustDirX = 1.0f;
    private float gustStrength = 0.0f;

    public boolean isWindGustActive() {
        return gustActive && gustStrength > 0.15f;
    }

    public float getWindGustDirX() {
        return gustDirX;
    }

    public boolean isFoliageSwaying(int col, int row) {
        long key = (((long) col) << 32) | (row & 0xFFFFFFFFL);
        TileManager.BushSway bushSway = swayingBushes.get(key);
        if (bushSway == null) return false;
        return Math.abs(bushSway.topShift) > 0.35f || (gustActive && gustStrength > 0.25f);
    }

    public void update(float delta, MapGenerator map, SnowParticleSystem snowPS) {
        updateBushSway();
        updateWindGust(map, snowPS);
    }

    private void updateBushSway() {
        if (swayingBushes.isEmpty()) return;
        Iterator<Map.Entry<Long, TileManager.BushSway>> iterator = swayingBushes.entrySet().iterator();
        while (iterator.hasNext()) {
            TileManager.BushSway bushSway = iterator.next().getValue();
            if (bushSway.cooldown > 0) bushSway.cooldown--;
            if (!gustActive || gustStrength < 0.05f) {
                bushSway.velocity += (0f - bushSway.topShift) * 0.09f;
                bushSway.velocity *= 0.88f;
                bushSway.topShift += bushSway.velocity;
                if (Math.abs(bushSway.topShift) < 0.05f && Math.abs(bushSway.velocity) < 0.05f && bushSway.cooldown <= 0) {
                    iterator.remove();
                }
            }
        }
    }

    private void updateWindGust(MapGenerator map, SnowParticleSystem snowPS) {
        if (!gustActive) {
            windGustTimer--;
            if (windGustTimer <= 0) {
                gustActive = true;
                gustTicks = 0;
                gustDuration = 540 + (int) (Math.random() * 240);
                gustDirX = Math.random() < 0.5 ? 1.0f : -1.0f;
            }
            return;
        }

        gustTicks++;
        if (gustTicks < 120) {
            gustStrength = gustTicks / 120.0f;
        } else if (gustTicks > gustDuration - 120) {
            gustStrength = Math.max(0.0f, (gustDuration - gustTicks) / 120.0f);
        } else {
            gustStrength = 1.0f;
        }

        if (map != null && gustStrength > 0.02f) {
            boolean peakWind = (gustStrength > 0.55f && gustTicks % 48 == 0);
            for (Map.Entry<Long, TileManager.BushSway> entry : swayingBushes.entrySet()) {
                long key = entry.getKey();
                int worldCol = (int) (key >>> 32);
                int worldRow = (int) (key & 0xFFFFFFFFL);
                TileManager.BushSway bushSway = entry.getValue();

                if (worldCol < 0 || worldCol >= map.mapCols || worldRow < 0 || worldRow >= map.mapRows) continue;

                int treeIdx = map.mapTreeNum[worldCol][worldRow];
                boolean isTree = (treeIdx >= 12 && treeIdx <= 22)
                        || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_MANGROVE);
                boolean isBush = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || (treeIdx == 39);
                boolean isGrassDecoration = (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_CATTAIL);

                if (!isTree && !isBush && !isGrassDecoration) continue;

                float spatialPhase = (worldCol * 0.73f) + (worldRow * 0.57f) + (float) Math.sin(worldCol * 1.15f + worldRow * 0.85f) * 1.8f;
                float osc = (float) Math.sin((gustTicks * 0.065f) + spatialPhase);
                float swayAmount = gustDirX * (0.55f + 0.45f * osc) * gustStrength;

                if (isTree) {
                    float targetShift = swayAmount * 6.8f;
                    bushSway.topShift += (targetShift - bushSway.topShift) * 0.10f;
                    if (peakWind && snowPS != null && Math.random() < 0.08) {
                        snowPS.spawnWindLeaves(worldCol * TILE_SIZE + TILE_SIZE, worldRow * TILE_SIZE - (TILE_SIZE * 0.5f),
                                worldRow * TILE_SIZE + TILE_SIZE - 4f, 1, gustDirX);
                    }
                } else if (isBush) {
                    float targetShift = swayAmount * 3.8f;
                    bushSway.topShift += (targetShift - bushSway.topShift) * 0.12f;
                    if (peakWind && snowPS != null && Math.random() < 0.02) {
                        snowPS.spawnWindLeaves(worldCol * TILE_SIZE + TILE_SIZE / 2.0f, worldRow * TILE_SIZE + TILE_SIZE * 0.3f,
                                worldRow * TILE_SIZE + TILE_SIZE - 4f, 1, gustDirX);
                    }
                } else if (isGrassDecoration) {
                    float targetShift = swayAmount * 3.0f;
                    bushSway.topShift += (targetShift - bushSway.topShift) * 0.14f;
                }
            }
        }

        if (gustTicks >= gustDuration) {
            gustActive = false;
            windGustTimer = 700 + (int) (Math.random() * 700);
        }
    }

    public void drawSheared(SpriteBatch batch, TextureRegion region, float drawX, float drawY,
                            float drawWidth, float drawHeight, float baseY, float topShift) {
        float topOffset = -topShift;
        float uMin = region.getU(), vMin = region.getV(), uMax = region.getU2(), vMax = region.getV2();
        float topLeftX = drawX + topOffset, topLeftY = drawY;
        float bottomLeftX = drawX, bottomLeftY = baseY;
        float bottomRightX = drawX + drawWidth, bottomRightY = baseY;
        float topRightX = drawX + drawWidth + topOffset, topRightY = drawY;
        float packedColor = batch.getPackedColor();
        float[] vertices = new float[20];

        vertices[0] = bottomLeftX;  vertices[1] = bottomLeftY;  vertices[2] = packedColor; vertices[3] = uMin;  vertices[4] = vMin;
        vertices[5] = bottomRightX; vertices[6] = bottomRightY; vertices[7] = packedColor; vertices[8] = uMax;  vertices[9] = vMin;
        vertices[10] = topRightX;   vertices[11] = topRightY;   vertices[12] = packedColor; vertices[13] = uMax; vertices[14] = vMax;
        vertices[15] = topLeftX;    vertices[16] = topLeftY;    vertices[17] = packedColor; vertices[18] = uMin; vertices[19] = vMax;

        batch.draw(region.getTexture(), vertices, 0, 20);
    }

    public boolean isPlayerInBush(MapGenerator map, float worldX, float worldY, float worldWidth, float worldHeight) {
        int leftTile = Math.max(0, (int) (worldX / TILE_SIZE) - 2);
        int rightTile = Math.min(map.mapCols - 1, (int) ((worldX + worldWidth) / TILE_SIZE) + 2);
        int topTile = Math.max(0, (int) (worldY / TILE_SIZE) - 2);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((worldY + worldHeight) / TILE_SIZE) + 2);

        for (int tileCol = leftTile; tileCol <= rightTile; tileCol++) {
            for (int tileRow = topTile; tileRow <= bottomTile; tileRow++) {
                int treeIdx = map.mapTreeNum[tileCol][tileRow];
                if ((treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || treeIdx == 39) {
                    float hitBoxY = tileRow * TILE_SIZE + 4, hitBoxHeight = TILE_SIZE - 8, hitBoxX, hitBoxWidth;
                    if (treeIdx == 30 || treeIdx == 31 || treeIdx == 38) {
                        float bushWidth = TILE_SIZE * 1.5f;
                        hitBoxX = tileCol * TILE_SIZE + (TILE_SIZE - bushWidth) / 2f;
                        hitBoxWidth = bushWidth;
                    } else {
                        hitBoxX = tileCol * TILE_SIZE + 4;
                        hitBoxWidth = TILE_SIZE - 8;
                    }
                    if (worldX < hitBoxX + hitBoxWidth && worldX + worldWidth > hitBoxX &&
                        worldY < hitBoxY + hitBoxHeight && worldY + worldHeight > hitBoxY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isPlayerInTallGrass(MapGenerator map, float worldX, float worldY, float worldWidth, float worldHeight) {
        int leftTile = Math.max(0, (int) (worldX / TILE_SIZE) - 2);
        int rightTile = Math.min(map.mapCols - 1, (int) ((worldX + worldWidth) / TILE_SIZE) + 2);
        int topTile = Math.max(0, (int) (worldY / TILE_SIZE) - 2);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((worldY + worldHeight) / TILE_SIZE) + 2);

        for (int tileCol = leftTile; tileCol <= rightTile; tileCol++) {
            for (int tileRow = topTile; tileRow <= bottomTile; tileRow++) {
                int treeIdx = map.mapTreeNum[tileCol][tileRow];
                if (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3)
                        || treeIdx == MapGenerator.TILE_CATTAIL) {
                    float hitBoxX = tileCol * TILE_SIZE + 4, hitBoxWidth = TILE_SIZE - 8;
                    float hitBoxY = tileRow * TILE_SIZE + 4, hitBoxHeight = TILE_SIZE - 8;
                    if (worldX < hitBoxX + hitBoxWidth && worldX + worldWidth > hitBoxX &&
                        worldY < hitBoxY + hitBoxHeight && worldY + worldHeight > hitBoxY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void checkBushInteraction(MapGenerator map, float worldX, float worldY, float worldWidth, float worldHeight,
                                     float movementDeltaX, float movementDeltaY, SnowParticleSystem snowPS) {
        int leftTile = Math.max(0, (int) (worldX / TILE_SIZE) - 2);
        int rightTile = Math.min(map.mapCols - 1, (int) ((worldX + worldWidth) / TILE_SIZE) + 2);
        int topTile = Math.max(0, (int) (worldY / TILE_SIZE) - 2);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((worldY + worldHeight) / TILE_SIZE) + 2);

        for (int tileCol = leftTile; tileCol <= rightTile; tileCol++) {
            for (int tileRow = topTile; tileRow <= bottomTile; tileRow++) {
                int treeIdx = map.mapTreeNum[tileCol][tileRow];
                boolean isBush = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || (treeIdx == 39);
                boolean isTallGrass = (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3)
                        || treeIdx == MapGenerator.TILE_CATTAIL);

                if (isBush || isTallGrass) {
                    float hitBoxY = tileRow * TILE_SIZE + 4, hitBoxHeight = TILE_SIZE - 8, hitBoxX, hitBoxWidth;
                    if (treeIdx == 30 || treeIdx == 31 || treeIdx == 38) {
                        float bushWidth = TILE_SIZE * 1.5f;
                        hitBoxX = tileCol * TILE_SIZE + (TILE_SIZE - bushWidth) / 2f;
                        hitBoxWidth = bushWidth;
                    } else {
                        hitBoxX = tileCol * TILE_SIZE + 4;
                        hitBoxWidth = TILE_SIZE - 8;
                    }
                    if (worldX < hitBoxX + hitBoxWidth && worldX + worldWidth > hitBoxX &&
                        worldY < hitBoxY + hitBoxHeight && worldY + worldHeight > hitBoxY) {
                        long key = (((long) tileCol) << 32) | (tileRow & 0xFFFFFFFFL);
                        TileManager.BushSway bushSway = swayingBushes.get(key);
                        if (bushSway == null) {
                            bushSway = new TileManager.BushSway();
                            swayingBushes.put(key, bushSway);
                        }
                        float maxPush = (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3)
                                ? 0f : (isTallGrass ? 2.6f : 3.2f);
                        float push = 0f;
                        if (movementDeltaX > 0.05f) push = maxPush;
                        else if (movementDeltaX < -0.05f) push = -maxPush;
                        else if (movementDeltaY > 0.05f) push = (tileCol % 2 == 0) ? (maxPush * 0.7f) : -(maxPush * 0.7f);
                        else if (movementDeltaY < -0.05f) push = (tileCol % 2 == 0) ? -(maxPush * 0.7f) : (maxPush * 0.7f);

                        if (push != 0f) bushSway.velocity += (push - bushSway.velocity) * 0.45f;
                        if (isBush && bushSway.cooldown <= 0) {
                            if (snowPS != null) snowPS.spawnLeafParticles(tileCol, tileRow, TILE_SIZE);
                            bushSway.cooldown = 45;
                        }
                    }
                }
            }
        }
    }

    public void trackVisibleForWind(MapGenerator map, float minX, float minY, float maxX, float maxY) {
        if (!gustActive || gustStrength < 0.02f) return;
        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 3);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 3);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 3);

        for (int worldRow = startRow; worldRow <= endRow; worldRow++) {
            for (int worldCol = startCol; worldCol <= endCol; worldCol++) {
                int treeIdx = map.mapTreeNum[worldCol][worldRow];
                if (treeIdx == 0) continue;
                boolean isTree = (treeIdx >= 12 && treeIdx <= 22)
                        || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_MANGROVE);
                boolean isBush = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || (treeIdx == 39);
                boolean isGrassDecoration = (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_CATTAIL);

                if (!isTree && !isBush && !isGrassDecoration) continue;
                long key = (((long) worldCol) << 32) | (worldRow & 0xFFFFFFFFL);
                if (!swayingBushes.containsKey(key)) {
                    swayingBushes.put(key, new TileManager.BushSway());
                }
            }
        }
    }
}
