package com.whereyouat;

import com.whereyouat.monster.MON_Ghost;
import java.util.ArrayList;
import java.util.List;

import com.whereyouat.screens.GameScreen;

public class AssetSetter {
    public final GameScreen gp;

    public AssetSetter(GameScreen gp) {
        this.gp = gp;
    }

    public void setMonster() {
    }

    private List<int[]> findSwampWalkableTiles() {
        List<int[]> tiles = new ArrayList<>();
        if (gp.mapGen == null || gp.mapGen.isSwampBiome == null) {
            return tiles;
        }

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

    private List<int[]> findForestWalkableTiles() {
        List<int[]> tiles = new ArrayList<>();
        if (gp.mapGen == null || gp.mapGen.isForestBiome == null) {
            return tiles;
        }

        int centerX = gp.mapGen.mapCols / 2;
        int centerY = gp.mapGen.mapRows / 2;

        for (int radius = 15; radius < 140 && tiles.size() < 10; radius += 4) {
            for (int dx = -radius; dx <= radius && tiles.size() < 10; dx += 5) {
                for (int dy = -radius; dy <= radius && tiles.size() < 10; dy += 5) {
                    int c = centerX + dx;
                    int r = centerY + dy;

                    if (c < 80 || c >= gp.mapGen.mapCols - 80 || r < 80 || r >= gp.mapGen.mapRows - 80) {
                        continue;
                    }

                    if (gp.mapGen.isForestBiome[c][r]
                            && !gp.mapGen.isSwampBiome[c][r]
                            && !gp.mapGen.isSnowBiome[c][r]
                            && !gp.mapGen.isBeachBiome[c][r]
                            && !gp.mapGen.isDeepWater[c][r]
                            && !gp.mapGen.isShallowWater[c][r]
                            && gp.mapGen.mapTreeNum[c][r] == 0) {
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
}
