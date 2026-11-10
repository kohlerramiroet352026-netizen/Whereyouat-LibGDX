package com.whereyouat;

import java.util.ArrayList;
import java.util.List;

import com.whereyouat.screens.GameScreen;

/**
 * AssetSetter for Where you at (LibGDX Edition).
 *
 * Adapts the RyiSnow tutorial AssetSetter to LibGDX.
 * Responsible for instantiating and placing monsters and world objects.
 */
public class AssetSetter {

    public final GameScreen gp;

    public AssetSetter(GameScreen gp) {
        this.gp = gp;
    }

    /**
     * Instantiates monsters and places them in their respective biomes:
     * - Frogger in the swamp
     * - Mushroom in the forest
     */
    public void setMonster() {

        List<int[]> snowTiles = findSnowWalkableTiles();
        if (!snowTiles.isEmpty()) {
            int[] tile = snowTiles.get(0);
            com.whereyouat.monster.MON_snowGhost boss = new com.whereyouat.monster.MON_snowGhost(gp);
            boss.worldX = tile[0] * gp.tileSize;
            boss.worldY = tile[1] * gp.tileSize;
            gp.monster[0] = boss;
            com.badlogic.gdx.Gdx.app.log("AssetSetter", "Spawned Espectro de las Nieves en tile de nieve: (" + tile[0] + ", " + tile[1] + ")");
        }
        /*
        // 1. Spawning Froggers in the swamp (indices 0 and 1)
        List<int[]> swampWalkableTiles = findSwampWalkableTiles();
        if (!swampWalkableTiles.isEmpty()) {
            int froggerCount = Math.min(swampWalkableTiles.size(), 2);
            for (int i = 0; i < froggerCount; i++) {
                int[] tile = swampWalkableTiles.get(i);
                gp.monster[i] = new MON_frogger(gp);
                gp.monster[i].worldX = gp.tileSize * tile[0];
                gp.monster[i].worldY = gp.tileSize * tile[1];
                Gdx.app.log("AssetSetter", "Spawned Frogger [" + i + "] at swamp tile: (" + tile[0] + ", " + tile[1] + ")");
            }
        } else {
            gp.monster[0] = new MON_frogger(gp);
            gp.monster[0].worldX = gp.tileSize * 23;
            gp.monster[0].worldY = gp.tileSize * 36;

            gp.monster[1] = new MON_frogger(gp);
            gp.monster[1].worldX = gp.tileSize * 23;
            gp.monster[1].worldY = gp.tileSize * 37;
        }

        // 2. Spawning Mushrooms in the forest (indices 2 and 3)
        List<int[]> forestWalkableTiles = findForestWalkableTiles();
        if (!forestWalkableTiles.isEmpty()) {
            int mushroomCount = Math.min(forestWalkableTiles.size(), 4);
            for (int i = 0; i < mushroomCount; i++) {
                int index = 2 + i;
                int[] tile = forestWalkableTiles.get(i);
                gp.monster[index] = new MON_mushroom(gp);
                gp.monster[index].worldX = gp.tileSize * tile[0];
                gp.monster[index].worldY = gp.tileSize * tile[1];
                Gdx.app.log("AssetSetter", "Spawned Mushroom [" + index + "] at forest tile: (" + tile[0] + ", " + tile[1] + ")");
            }
        } else {
            // Fallback en bosque asegurando que no haya arboles ni obstaculos
            int safeCol = 150;
            int safeRow = 150;
            if (gp.mapGen != null && gp.mapGen.mapTreeNum != null) {
                for (int ox = 0; ox <= 3; ox++) {
                    for (int oy = 0; oy <= 3; oy++) {
                        int tc = safeCol + ox;
                        int tr = safeRow + oy;
                        if (tc < gp.mapGen.mapCols && tr < gp.mapGen.mapRows) {
                            gp.mapGen.mapTreeNum[tc][tr] = 0;
                        }
                    }
                }
            }
            gp.monster[2] = new MON_mushroom(gp);
            gp.monster[2].worldX = gp.tileSize * safeCol;
            gp.monster[2].worldY = gp.tileSize * safeRow;
        }
            */
    }

    /**
     * Finds suitable walkable tiles (not deep water, not blocked by trees/mangroves)
     * located inside the swamp biome.
     */
    private List<int[]> findSwampWalkableTiles() {
        List<int[]> tiles = new ArrayList<>();
        if (gp.mapGen == null || gp.mapGen.isSwampBiome == null) {
            return tiles;
        }

        // 1. Calculate approximate center of the swamp
        int sumX = 0, sumY = 0, count = 0;
        for (int c = 0; c < gp.mapGen.mapCols; c += 8) {
            for (int r = 0; r < gp.mapGen.mapRows; r += 8) {
                if (gp.mapGen.isSwampBiome[c][r]) {
                    sumX += c;
                    sumY += r;
                    count++;
                }
            }
        }

        if (count == 0) return tiles;

        int centerX = sumX / count;
        int centerY = sumY / count;

        // 2. Collect valid walkable tiles around swamp center
        for (int radius = 0; radius < 80 && tiles.size() < 10; radius += 2) {
            for (int dx = -radius; dx <= radius && tiles.size() < 10; dx += 3) {
                for (int dy = -radius; dy <= radius && tiles.size() < 10; dy += 3) {
                    int c = centerX + dx;
                    int r = centerY + dy;
                    if (c >= 5 && c < gp.mapGen.mapCols - 5 && r >= 5 && r < gp.mapGen.mapRows - 5) {
                        if (gp.mapGen.isSwampBiome[c][r]
                                && !gp.mapGen.isDeepWater[c][r]
                                && gp.mapGen.mapTreeNum[c][r] == 0) {
                            tiles.add(new int[]{c, r});
                        }
                    }
                }
            }
        }

        return tiles;
    }

    /**
     * Finds suitable walkable tiles deep inside the forest biome,
     * ensuring they are located in clearings far away from beaches, coasts, snow, and swamps.
     */
    private List<int[]> findForestWalkableTiles() {
        List<int[]> tiles = new ArrayList<>();
        if (gp.mapGen == null || gp.mapGen.isForestBiome == null) {
            return tiles;
        }

        int centerX = gp.mapGen.mapCols / 2; // 256
        int centerY = gp.mapGen.mapRows / 2; // 256

        // Search outward from the interior of the mainland
        for (int radius = 15; radius < 140 && tiles.size() < 10; radius += 4) {
            for (int dx = -radius; dx <= radius && tiles.size() < 10; dx += 5) {
                for (int dy = -radius; dy <= radius && tiles.size() < 10; dy += 5) {
                    int c = centerX + dx;
                    int r = centerY + dy;

                    if (c < 80 || c >= gp.mapGen.mapCols - 80 || r < 80 || r >= gp.mapGen.mapRows - 80) {
                        continue;
                    }

                    // Must be in temperate forest biome and free of obstructions
                    if (gp.mapGen.isForestBiome[c][r]
                            && !gp.mapGen.isSwampBiome[c][r]
                            && !gp.mapGen.isSnowBiome[c][r]
                            && !gp.mapGen.isBeachBiome[c][r]
                            && !gp.mapGen.isDeepWater[c][r]
                            && !gp.mapGen.isShallowWater[c][r]
                            && gp.mapGen.mapTreeNum[c][r] == 0) {

                        // Buffer check: ensure no beach or water within 8 tiles (~384px)
                        boolean nearCoastOrWater = false;
                        for (int bx = -8; bx <= 8; bx++) {
                            for (int by = -8; by <= 8; by++) {
                                int nc = c + bx;
                                int nr = r + by;
                                if (nc >= 0 && nc < gp.mapGen.mapCols && nr >= 0 && nr < gp.mapGen.mapRows) {
                                    if (gp.mapGen.isBeachBiome[nc][nr]
                                            || gp.mapGen.isDeepWater[nc][nr]
                                            || gp.mapGen.isShallowWater[nc][nr]) {
                                        nearCoastOrWater = true;
                                        break;
                                    }
                                }
                            }
                            if (nearCoastOrWater) break;
                        }

                        if (nearCoastOrWater) continue;

                        // Verificar que no haya arboles ni rocas en el area de spawn
                        boolean hasObstacle = false;
                        for (int ox = 0; ox <= 3; ox++) {
                            for (int oy = 0; oy <= 3; oy++) {
                                int tc = c + ox;
                                int tr = r + oy;
                                if (tc >= gp.mapGen.mapCols || tr >= gp.mapGen.mapRows
                                        || gp.mapGen.mapTreeNum[tc][tr] != 0) {
                                    hasObstacle = true;
                                    break;
                                }
                            }
                            if (hasObstacle) break;
                        }
                        if (hasObstacle) continue;

                        float hitX = c * gp.tileSize + 155f;
                        float hitY = r * gp.tileSize + 246f;
                        if (gp.tileM != null && gp.tileM.collidesWithTree(gp.mapGen, hitX, hitY, 40f, 26f)) {
                            continue;
                        }

                        tiles.add(new int[]{c, r});
                    }
                }
            }
        }

        return tiles;
    }

    /* 
    Encuentra baldosas transitables dentro del bioma de nieve, asegurando que no haya
    agua ni obstáculos (árboles o rocas)
    */

    private List<int[]> findSnowWalkableTiles() {
        List<int[]> tiles = new ArrayList<>();
        if (gp.mapGen == null || gp.mapGen.isSnowBiome == null) {
            return tiles;
        }

        for (int c = 10; c < gp.mapGen.mapCols - 10 && tiles.size() < 10; c += 4) {
            for (int r = 10; r < gp.mapGen.mapRows - 10 && tiles.size() < 10; r += 4) {
                if (gp.mapGen.isSnowBiome[c][r]
                && !gp.mapGen.isDeepWater[c][r]
                && !gp.mapGen.isShallowWater[c][r]
                && gp.mapGen.mapTreeNum[c][r] == 0) {
                    tiles.add(new int[]{c, r});
                }
            }
        }

        return tiles;
    }
}
