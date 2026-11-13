package com.whereyouat.map;

import java.util.Random;

public class RiverGenerator {
    public void generateRivers(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;
        Random rand = map.rand;
        long seed = map.seed;

        map.riverWater = new boolean[cols][rows];
        map.riverSand = new boolean[cols][rows];

        int numRivers = rand.nextInt(3) + 1;

        if (numRivers == 1) {
            int riverOrientation = rand.nextInt(6);
            int startX = 5, startY = 5, endX = 5, endY = 5;

            int minBoundX = cols / 4;
            int maxBoundX = cols * 3 / 4;
            int rangeX = maxBoundX - minBoundX;

            int minBoundY = rows / 4;
            int maxBoundY = rows * 3 / 4;
            int rangeY = maxBoundY - minBoundY;

            if (riverOrientation == 0 || riverOrientation == 1) {
                startX = minBoundX + rand.nextInt(rangeX);
                startY = (riverOrientation == 0) ? 5 : rows - 5;
                endX = minBoundX + rand.nextInt(rangeX);
                endY = (riverOrientation == 0) ? rows - 5 : 5;
            } else if (riverOrientation == 2 || riverOrientation == 3) {
                startX = (riverOrientation == 2) ? 5 : cols - 5;
                startY = minBoundY + rand.nextInt(rangeY);
                endX = (riverOrientation == 2) ? cols - 5 : 5;
                endY = minBoundY + rand.nextInt(rangeY);
            } else if (riverOrientation == 4) {
                startX = rand.nextInt(minBoundX);
                startY = 5;
                endX = maxBoundX + rand.nextInt(minBoundX);
                endY = rows - 5;
            } else if (riverOrientation == 5) {
                startX = maxBoundX + rand.nextInt(minBoundX);
                startY = 5;
                endX = rand.nextInt(minBoundX);
                endY = rows - 5;
            }

            drawRiver(map, startX, startY, endX, endY, true);
        } else {
            int centerOriginX = cols / 2 + (rand.nextInt(80) - 40);
            int centerOriginY = rows / 2 + (rand.nextInt(80) - 40);

            for (int riverIndex = 0; riverIndex < numRivers; riverIndex++) {
                int outEdge = rand.nextInt(4);
                int targetX, targetY;
                if (outEdge == 0) {
                    targetX = centerOriginX + (rand.nextInt(160) - 80);
                    targetY = 5;
                } else if (outEdge == 1) {
                    targetX = centerOriginX + (rand.nextInt(160) - 80);
                    targetY = rows - 5;
                } else if (outEdge == 2) {
                    targetX = 5;
                    targetY = centerOriginY + (rand.nextInt(160) - 80);
                } else {
                    targetX = cols - 5;
                    targetY = centerOriginY + (rand.nextInt(160) - 80);
                }

                drawRiver(map, centerOriginX, centerOriginY, targetX, targetY, false);
            }
        }
    }

    private void drawRiver(MapGenerator map, int startX, int startY, int endX, int endY, boolean forceSand) {
        int cols = map.mapCols;
        int rows = map.mapRows;
        Random rand = map.rand;
        long seed = map.seed;

        double distance = Math.hypot(endX - startX, endY - startY);
        int steps = (int) (distance * 2.0);
        double noiseOffsetX = rand.nextDouble() * 1000.0;

        PerlinNoise riverWiggle = new PerlinNoise(seed + rand.nextInt());
        PerlinNoise sandPatchNoise = new PerlinNoise(seed + rand.nextInt());

        for (int stepIndex = 0; stepIndex <= steps; stepIndex++) {
            double progress = (double) stepIndex / steps;
            double baseX = startX + progress * (endX - startX);
            double baseY = startY + progress * (endY - startY);

            double deltaX = endX - startX;
            double deltaY = endY - startY;
            double length = Math.hypot(deltaX, deltaY);
            double normalX = -deltaY / length;
            double normalY = deltaX / length;

            double wiggle = riverWiggle.octaveNoise(progress * 3.0, noiseOffsetX, 3, 0.5, 2.0) - 0.5;
            wiggle *= 120.0;

            double currentX = baseX + normalX * wiggle;
            double currentY = baseY + normalY * wiggle;

            double waterRadius = 2.2;
            double sandRadius = forceSand ? 6.0 : 5.0;

            int maxRadius = (int) Math.ceil(Math.max(waterRadius, sandRadius));
            int centerTileX = (int) currentX;
            int centerTileY = (int) currentY;

            for (int tileX = centerTileX - maxRadius; tileX <= centerTileX + maxRadius; tileX++) {
                for (int tileY = centerTileY - maxRadius; tileY <= centerTileY + maxRadius; tileY++) {
                    if (tileX >= 0 && tileX < cols && tileY >= 0 && tileY < rows) {
                        double tileDistance = Math.hypot(tileX - currentX, tileY - currentY);
                        if (tileDistance <= waterRadius) {
                            map.riverWater[tileX][tileY] = true;
                        } else if (tileDistance <= sandRadius) {
                            if (forceSand) {
                                map.riverSand[tileX][tileY] = true;
                            } else {
                                if (sandPatchNoise.octaveNoise(tileX * 0.05, tileY * 0.05, 2, 0.5, 2.0) > 0.55) {
                                    map.riverSand[tileX][tileY] = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
