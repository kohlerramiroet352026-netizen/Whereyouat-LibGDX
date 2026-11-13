package com.whereyouat.map;

import java.util.Random;

public class MapGenerator {
    public static final int TILE_GRASS1 = 0;
    public static final int TILE_GRASS2 = 1;
    public static final int TILE_GRASS3 = 2;
    public static final int TILE_GRASS4 = 3;
    public static final int TILE_STONE = 4;
    public static final int TILE_WATER0 = 5;
    public static final int TILE_SAND1 = 9;
    public static final int TILE_SAND2 = 10;
    public static final int TILE_DIRT1 = 11;

    public static final int TILE_TREE1 = 12;
    public static final int TILE_TREE2 = 13;
    public static final int TILE_TREE3 = 14;
    public static final int TILE_TREE4 = 15;

    public static final int TILE_PINE1 = 16;
    public static final int TILE_PINE2 = 17;
    public static final int TILE_PINE3 = 18;
    public static final int TILE_PINE4 = 19;
    public static final int TILE_PINE5 = 20;

    public static final int TILE_PALM1 = 21;
    public static final int TILE_PALM2 = 22;

    public static final int TILE_BUSH1 = 27;
    public static final int TILE_BUSH2 = 28;
    public static final int TILE_BUSH3 = 29;
    public static final int TILE_BUSH4_LARGE = 30;
    public static final int TILE_BUSH5_LARGE = 31;
    public static final int TILE_LOG1 = 32;
    public static final int TILE_LOG2 = 33;
    public static final int TILE_TALL_GRASS1 = 34;
    public static final int TILE_TALL_GRASS2 = 35;
    public static final int TILE_SNOWY_BUSH1 = 36;
    public static final int TILE_SNOWY_BUSH2 = 37;
    public static final int TILE_SNOWY_BUSH3_LARGE = 38;
    public static final int TILE_SMALL_BUSH = 39;
    public static final int TILE_SHORT_GRASS = 40;
    public static final int TILE_SHORTER_GRASS = 41;
    public static final int TILE_ROCK1 = 42;
    public static final int TILE_SNOWY_ROCK1 = 43;
    public static final int TILE_SANDSTONE_ROCK = 44;
    public static final int TILE_SHORT_SAND_GRASS = 45;
    public static final int TILE_TALL_SAND_GRASS = 46;

    public static final int TILE_MUD = 47;
    public static final int TILE_WILLOW1 = 48;
    public static final int TILE_WILLOW2 = 49;
    public static final int TILE_WILLOW3 = 50;
    public static final int TILE_MANGROVE = 51;
    public static final int TILE_LILIPAD1 = 52;
    public static final int TILE_LILIPAD2 = 53;
    public static final int TILE_LILIPAD3 = 54;
    public static final int TILE_CATTAIL = 55;
    public static final int TILE_WATER_ROCK = 56;
    public static final int TILE_MUD_BORDER = 57;
    public static final int TILE_WET_MUD_BORDER = 58;

    public static final double DEEP_WATER_THRESHOLD = 0.30;
    public static final double SHALLOW_WATER_THRESHOLD = 0.38;
    public static final double SAND_THRESHOLD = 0.45;
    public static final double GRASS_THRESHOLD = 0.78;

    public static final double SNOW_THRESHOLD = 0.28;
    public static final double SNOW_BLEND_EDGE = 0.32;
    public static final double FOREST_THRESHOLD = 0.60;
    public static final double FOREST_BLEND_LOW = 0.50;

    public static final int MIN_SAND_WIDTH = 3;
    static final int MAX_SAND_WIDTH = 7;

    public final int mapCols;
    public final int mapRows;
    public final long seed;
    public final boolean isSnowyDay;
    final Random rand;

    final double[][] grassVarCache;

    public int[][] mapTileNum;
    public int[][] mapTreeNum;
    public boolean[][] isShallowWater;
    public boolean[][] isDeepWater;
    public boolean[][] isSnowBiome;
    public boolean[][] isSwampBiome;
    public boolean[][] isBeachBiome;
    public float[][] snowBlend;
    public float[][] swampBlend;
    public float[][] deepWaterBlend;
    public boolean[][] isForestBiome;
    public int[][] mapTransType;
    public int[][] mapTransRot;

    boolean[][] riverWater;
    boolean[][] riverSand;

    public MapGenerator(int cols, int rows, long seed, String seedString) {
        this.mapCols = cols;
        this.mapRows = rows;
        this.seed = seed;
        this.isSnowyDay = (seedString != null && seedString.equalsIgnoreCase("snowy day"));
        this.rand = new Random(seed);

        mapTileNum = new int[cols][rows];
        mapTreeNum = new int[cols][rows];
        isShallowWater = new boolean[cols][rows];
        isDeepWater = new boolean[cols][rows];
        isSnowBiome = new boolean[cols][rows];
        isSwampBiome = new boolean[cols][rows];
        isBeachBiome = new boolean[cols][rows];
        snowBlend = new float[cols][rows];
        swampBlend = new float[cols][rows];
        deepWaterBlend = new float[cols][rows];
        isForestBiome = new boolean[cols][rows];
        mapTransType = new int[cols][rows];
        mapTransRot = new int[cols][rows];
        grassVarCache = new double[cols][rows];
    }

    public void generate() {
        PerlinNoise elevNoise = new PerlinNoise(seed);
        PerlinNoise detailNoise = new PerlinNoise(seed + 1337);
        PerlinNoise tempNoise = new PerlinNoise(seed + 7777);
        PerlinNoise forestNoise = new PerlinNoise(seed + 3141);
        PerlinNoise dirtNoise = new PerlinNoise(seed + 9999);
        PerlinNoise clearNoise = new PerlinNoise(seed + 2718);
        PerlinNoise grassVarNoise = new PerlinNoise(seed + 6543);
        PerlinNoise lakeNoise = new PerlinNoise(seed + 5555);

        PerlinNoise swampNoise = new PerlinNoise(seed + 7778);
        PerlinNoise swampChannelNoise = new PerlinNoise(seed + 4444);
        PerlinNoise swampLakeNoise = new PerlinNoise(seed + 8888);

        double scaleElev = 0.005;
        double scaleDetail = 0.025;
        double scaleForest = 0.009;
        double scaleDirt = 0.055;
        double scaleClear = 0.040;
        double scaleGrassVar = 0.080;

        Random biomeRand = new Random(seed + 98765L);
        int snowCorner = biomeRand.nextInt(4);
        int swampCorner = (snowCorner + 2) % 4;

        int cornerMargin = 135;
        int[][] cornerCoords = {
            { cornerMargin, cornerMargin },
            { mapCols - cornerMargin, cornerMargin },
            { mapCols - cornerMargin, mapRows - cornerMargin },
            { cornerMargin, mapRows - cornerMargin }
        };

        int snowCenterX = cornerCoords[snowCorner][0] + (biomeRand.nextInt(21) - 10);
        int snowCenterY = cornerCoords[snowCorner][1] + (biomeRand.nextInt(21) - 10);
        double maxSnowRadius = 120 + biomeRand.nextDouble() * 15;

        int swampCenterX = cornerCoords[swampCorner][0] + (biomeRand.nextInt(21) - 10);
        int swampCenterY = cornerCoords[swampCorner][1] + (biomeRand.nextInt(21) - 10);
        double maxSwampRadius = 120 + biomeRand.nextDouble() * 15;

        Random beachRand = new Random(seed + 12345L);
        int numBeaches = 3;
        int[][] beachCenters = new int[numBeaches][2];
        double[] beachRadii = new double[numBeaches];
        for (int i = 0; i < numBeaches; i++) {
            int edge = beachRand.nextInt(4);
            int margin = 20;
            int depth = 40;

            if (edge == 0) {
                beachCenters[i][0] = margin + beachRand.nextInt(mapCols - 2 * margin);
                beachCenters[i][1] = margin + beachRand.nextInt(depth);
            } else if (edge == 1) {
                beachCenters[i][0] = margin + beachRand.nextInt(mapCols - 2 * margin);
                beachCenters[i][1] = mapRows - margin - beachRand.nextInt(depth);
            } else if (edge == 2) {
                beachCenters[i][0] = margin + beachRand.nextInt(depth);
                beachCenters[i][1] = margin + beachRand.nextInt(mapRows - 2 * margin);
            } else {
                beachCenters[i][0] = mapCols - margin - beachRand.nextInt(depth);
                beachCenters[i][1] = margin + beachRand.nextInt(mapRows - 2 * margin);
            }
            beachRadii[i] = 40 + beachRand.nextDouble() * 30;
        }

        new RiverGenerator().generateRivers(this);

        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                double elev = elevNoise.octaveNoise(col * scaleElev, row * scaleElev, 6, 0.55, 2.0);
                double detail = detailNoise.octaveNoise(col * scaleDetail, row * scaleDetail, 3, 0.4, 2.0);
                elev = elev * 0.85 + detail * 0.15;

                double distX = Math.min(col, mapCols - 1 - col);
                double distY = Math.min(row, mapRows - 1 - row);
                double dist = Math.min(distX, distY);

                if (dist < 80) {
                    double drop = (80.0 - dist) / 50.0;
                    if (drop > 0) {
                        elev -= Math.pow(drop, 1.5) * 1.0;
                    }
                }

                double lake = lakeNoise.octaveNoise(col * 0.02, row * 0.02, 2, 0.5, 2.0);
                boolean isLake = (lake > 0.82);

                if (riverWater[col][row] || isLake) {
                    elev = Math.min(elev, SHALLOW_WATER_THRESHOLD - 0.01);
                } else if (riverSand[col][row] || lake > 0.78) {
                    elev = Math.min(elev, SAND_THRESHOLD - 0.01);
                }

                double forest = forestNoise.octaveNoise(col * scaleForest, row * scaleForest, 4, 0.55, 2.0);
                double dirt = dirtNoise.octaveNoise(col * scaleDirt, row * scaleDirt, 2, 0.5, 2.0);
                double clear = clearNoise.octaveNoise(col * scaleClear, row * scaleClear, 3, 0.5, 2.0);
                double grassVar = grassVarNoise.octaveNoise(col * scaleGrassVar, row * scaleGrassVar, 2, 0.5, 2.0);

                grassVarCache[col][row] = grassVar;

                boolean snow = false;
                float sBlend = 0f;
                if (isSnowyDay) {
                    snow = true;
                    sBlend = 1.0f;
                } else {
                    double distToSnowCenter = Math.hypot(col - snowCenterX, row - snowCenterY);
                    double edgeNoise = tempNoise.octaveNoise(col * 0.02, row * 0.02, 4, 0.5, 2.0);
                    double noisyDist = distToSnowCenter + (edgeNoise - 0.5) * 50.0;

                    if (noisyDist < maxSnowRadius) {
                        snow = true;
                        sBlend = 1.0f;
                    } else if (noisyDist < maxSnowRadius + 14.0) {
                        snow = false;
                        double t = 1.0 - (noisyDist - maxSnowRadius) / 14.0;
                        sBlend = (float) Math.pow(Math.max(0, t), 0.30);
                    }
                }
                isSnowBiome[col][row] = snow;
                snowBlend[col][row] = sBlend;

                boolean swamp = false;
                float swBlend = 0f;
                if (!isSnowyDay) {
                    double distToSwampCenter = Math.hypot(col - swampCenterX, row - swampCenterY);
                    double swEdgeNoise = swampNoise.octaveNoise(col * 0.02, row * 0.02, 4, 0.5, 2.0);
                    double noisySwampDist = distToSwampCenter + (swEdgeNoise - 0.5) * 50.0;

                    if (noisySwampDist < maxSwampRadius) {
                        swamp = true;
                        swBlend = 1.0f;
                    } else if (noisySwampDist < maxSwampRadius + 14.0) {
                        swamp = false;
                        double t = 1.0 - (noisySwampDist - maxSwampRadius) / 14.0;
                        swBlend = (float) Math.pow(Math.max(0, t), 0.30);
                    }
                }
                isSwampBiome[col][row] = swamp;
                swampBlend[col][row] = swBlend;

                boolean isSwampMud = false;
                if (swamp && dist >= 70) {
                    double channel = Math.abs(swampChannelNoise.octaveNoise(col * 0.038, row * 0.038, 3, 0.5, 2.0) - 0.5);
                    double swampLake = swampLakeNoise.octaveNoise(col * 0.028, row * 0.028, 2, 0.5, 2.0);

                    if (channel < 0.032 || swampLake > 0.70) {
                        elev = Math.min(elev, SHALLOW_WATER_THRESHOLD - 0.01);
                    } else {
                        boolean riverMud = (channel >= 0.032 && channel < 0.046);
                        boolean lakeMud = (swampLake >= 0.67 && swampLake <= 0.70);
                        if (riverMud || lakeMud || riverSand[col][row] || (lake > 0.78)) {
                            isSwampMud = true;
                        }
                    }
                }

                boolean beach = false;
                for (int i = 0; i < numBeaches; i++) {
                    double distToBeach = Math.hypot(col - beachCenters[i][0], row - beachCenters[i][1]);
                    double beachEdgeNoise = tempNoise.octaveNoise((col + 50) * 0.02, (row + 50) * 0.02, 4, 0.5, 2.0);
                    double noisyBeachDist = distToBeach + (beachEdgeNoise - 0.5) * 40.0;
                    if (noisyBeachDist < beachRadii[i]) {
                        beach = true;
                        break;
                    }
                }
                isBeachBiome[col][row] = beach;

                assignTile(col, row, elev, forest, dirt, clear, grassVar, snow, beach, swamp, isSwampMud, dist);
            }
        }

        ShorelineProcessor shoreline = new ShorelineProcessor();
        FeaturePlacer features = new FeaturePlacer();

        shoreline.normalizeSandWidth(this);
        shoreline.fixDirtPlacement(this);
        shoreline.removeIsolatedTiles(this);
        features.validateTrees(this);
        shoreline.computeTransitions(this);
        shoreline.calculateDeepWaterBlend(this);
        features.placeRocks(this, seed);
        features.placeSandDecorations(this, seed);
        features.placeAquaticFeatures(this, seed);
    }

    private void assignTile(int col, int row, double elev,
                            double forest, double dirt, double clear,
                            double grassVar, boolean isSnow, boolean isBeach,
                            boolean isSwamp, boolean isSwampMud, double distFromEdge) {
        mapTreeNum[col][row] = 0;

        if (elev < DEEP_WATER_THRESHOLD) {
            mapTileNum[col][row] = TILE_WATER0;
            isDeepWater[col][row] = true;
            isShallowWater[col][row] = false;
            isForestBiome[col][row] = false;
            return;
        }
        if (elev < SHALLOW_WATER_THRESHOLD) {
            mapTileNum[col][row] = TILE_WATER0;
            isDeepWater[col][row] = false;
            isShallowWater[col][row] = true;
            isForestBiome[col][row] = false;
            return;
        }
        isDeepWater[col][row] = false;
        isShallowWater[col][row] = false;

        double localSandThreshold = isBeach ? 0.52 : SAND_THRESHOLD;

        if (isSwamp && (isSwampMud || elev < localSandThreshold)) {
            mapTileNum[col][row] = TILE_MUD;
            isForestBiome[col][row] = false;
            return;
        }

        if (elev < localSandThreshold) {
            mapTileNum[col][row] = pickSand();
            isForestBiome[col][row] = false;

            if (isBeach && rand.nextInt(50) == 0) {
                if (canPlaceTree(col, row)) {
                    mapTreeNum[col][row] = rand.nextBoolean() ? TILE_PALM1 : TILE_PALM2;
                }
            }
            return;
        }

        if (isSnow) {
            boolean inForest = forest >= FOREST_THRESHOLD;
            boolean inTransition = !inForest && forest >= FOREST_BLEND_LOW;

            if (inForest) {
                isForestBiome[col][row] = true;
                if (clear > 0.68) {
                    mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                    mapTreeNum[col][row] = 0;
                } else {
                    mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                    if (canPlaceTree(col, row)) {
                        mapTreeNum[col][row] = pickPine();
                    }
                }
            } else if (inTransition) {
                isForestBiome[col][row] = true;
                if (clear > 0.55) {
                    mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                    mapTreeNum[col][row] = 0;
                } else {
                    mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                    if (canPlaceTree(col, row)) {
                        mapTreeNum[col][row] = rand.nextBoolean() ? TILE_PINE1 : TILE_PINE3;
                    }
                }
            } else {
                isForestBiome[col][row] = false;
                mapTileNum[col][row] = pickGrass(grassVar, isSnow);

                if (canPlaceTree(col, row) && !isNearWater(col, row, 1) && rand.nextInt(100) < 2) {
                    int bushRoll = rand.nextInt(3);
                    if (bushRoll == 0) {
                        mapTreeNum[col][row] = TILE_SNOWY_BUSH1;
                    } else if (bushRoll == 1) {
                        mapTreeNum[col][row] = TILE_SNOWY_BUSH2;
                    } else if (!isNearWater(col, row, 2)) {
                        mapTreeNum[col][row] = TILE_SNOWY_BUSH3_LARGE;
                    }
                }
            }
            return;
        }

        if (isSwamp) {
            isForestBiome[col][row] = true;
            boolean inWillowForest = (forest >= 0.38);

            if (inWillowForest) {
                if (clear > 0.78) {
                    mapTileNum[col][row] = pickGrass(grassVar, false);
                    mapTreeNum[col][row] = 0;
                } else {
                    mapTileNum[col][row] = pickGrass(grassVar, false);
                    if (canPlaceTree(col, row)) {
                        mapTreeNum[col][row] = pickWillow();
                    }
                }
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, false);
                if (canPlaceTree(col, row) && !isNearWater(col, row, 1)) {
                    if (rand.nextInt(100) < 15) {
                        int vegetationRoll = rand.nextInt(100);
                        if (vegetationRoll < 28) {
                            mapTreeNum[col][row] = TILE_SHORTER_GRASS;
                        } else if (vegetationRoll < 54) {
                            mapTreeNum[col][row] = TILE_SHORT_GRASS;
                        } else if (vegetationRoll < 78) {
                            mapTreeNum[col][row] = rand.nextBoolean() ? TILE_TALL_GRASS1 : TILE_TALL_GRASS2;
                        } else if (vegetationRoll < 87) {
                            mapTreeNum[col][row] = TILE_SMALL_BUSH;
                        } else if (vegetationRoll < 94) {
                            mapTreeNum[col][row] = rand.nextBoolean() ? TILE_BUSH1 : TILE_BUSH2;
                        } else if (vegetationRoll < 97) {
                            mapTreeNum[col][row] = TILE_BUSH3;
                        } else if (vegetationRoll < 99) {
                            if (!isNearWater(col, row, 2)) {
                                mapTreeNum[col][row] = rand.nextBoolean() ? TILE_BUSH4_LARGE : TILE_BUSH5_LARGE;
                            }
                        } else {
                            mapTreeNum[col][row] = rand.nextBoolean() ? TILE_LOG1 : TILE_LOG2;
                        }
                    }
                }
            }
            return;
        }

        boolean inForest = forest >= FOREST_THRESHOLD;
        boolean inTransition = !inForest && forest >= FOREST_BLEND_LOW;

        if (inForest) {
            isForestBiome[col][row] = true;
            if (clear > 0.68) {
                mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                mapTreeNum[col][row] = 0;
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                if (canPlaceTree(col, row)) {
                    mapTreeNum[col][row] = pickTree();
                }
            }
        } else if (inTransition) {
            isForestBiome[col][row] = true;
            if (clear > 0.55) {
                mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                mapTreeNum[col][row] = 0;
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, isSnow);
                if (canPlaceTree(col, row)) {
                    mapTreeNum[col][row] = rand.nextBoolean() ? TILE_TREE1 : TILE_TREE3;
                }
            }
        } else {
            isForestBiome[col][row] = false;
            mapTreeNum[col][row] = 0;

            if (elev > SAND_THRESHOLD + 0.05 && dirt > 0.82) {
                mapTileNum[col][row] = TILE_DIRT1;
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, isSnow);

                if (canPlaceTree(col, row) && !isNearWater(col, row, 1)) {
                    if (rand.nextInt(2000) == 0) {
                        mapTreeNum[col][row] = rand.nextBoolean() ? TILE_LOG1 : TILE_LOG2;
                    } else if (rand.nextInt(100) < 6) {
                        int roll = rand.nextInt(100);
                        if (roll < 33) {
                            mapTreeNum[col][row] = TILE_SHORTER_GRASS;
                        } else if (roll < 63) {
                            mapTreeNum[col][row] = TILE_SHORT_GRASS;
                        } else if (roll < 87) {
                            mapTreeNum[col][row] = rand.nextBoolean() ? TILE_TALL_GRASS1 : TILE_TALL_GRASS2;
                        } else if (roll < 95) {
                            mapTreeNum[col][row] = TILE_SMALL_BUSH;
                        } else if (roll < 98) {
                            mapTreeNum[col][row] = rand.nextBoolean() ? TILE_BUSH1 : TILE_BUSH2;
                        } else if (roll < 99) {
                            mapTreeNum[col][row] = TILE_BUSH3;
                        } else {
                            if (!isNearWater(col, row, 2)) {
                                mapTreeNum[col][row] = rand.nextBoolean() ? TILE_BUSH4_LARGE : TILE_BUSH5_LARGE;
                            }
                        }
                    }
                }
            }
        }
    }

    boolean isTransitionTarget(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows) return false;
        if (isShallowWater[col][row] || isDeepWater[col][row]) return false;
        int tile = mapTileNum[col][row];
        if (tile >= TILE_WATER0 && tile <= TILE_WATER0 + 3) return false;
        return !isGrass(col, row);
    }

    boolean isSand(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows) return false;
        int tile = mapTileNum[col][row];
        return (tile == TILE_SAND1 || tile == TILE_SAND2);
    }

    boolean isMud(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows) return false;
        return mapTileNum[col][row] == TILE_MUD;
    }

    boolean isGrass(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows) return false;
        int tile = mapTileNum[col][row];
        return (tile >= TILE_GRASS1 && tile <= TILE_GRASS4);
    }

    boolean isWater(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows) return false;
        if (isShallowWater[col][row] || isDeepWater[col][row]) return true;
        int tile = mapTileNum[col][row];
        return (tile >= TILE_WATER0 && tile <= TILE_WATER0 + 3);
    }

    public boolean isNearWater(int col, int row, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (isWater(col + dx, row + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isDirt(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows) return false;
        return mapTileNum[col][row] == TILE_DIRT1;
    }

    int pickGrass(double grassVar, boolean isSnow) {
        if (isSnow && rand.nextDouble() < 0.85) return TILE_GRASS1;
        double value = grassVar + (rand.nextDouble() - 0.5) * 0.15;
        if (value < 0.28) return TILE_GRASS2;
        else if (value < 0.75) return TILE_GRASS1;
        else if (value < 0.90) return TILE_GRASS3;
        else return TILE_GRASS4;
    }

    int pickSand() {
        return (rand.nextInt(12) == 0) ? TILE_SAND2 : TILE_SAND1;
    }

    private int pickTree() {
        int roll = rand.nextInt(10);
        if (roll < 3) return TILE_TREE1;
        else if (roll < 6) return TILE_TREE2;
        else if (roll < 8) return TILE_TREE3;
        else return TILE_TREE4;
    }

    private int pickPine() {
        int roll = rand.nextInt(10);
        if (roll < 3) return TILE_PINE1;
        else if (roll < 6) return TILE_PINE2;
        else if (roll < 8) return TILE_PINE3;
        else if (roll < 9) return TILE_PINE4;
        else return TILE_PINE5;
    }

    private int pickWillow() {
        int roll = rand.nextInt(100);
        if (roll < 14) return TILE_WILLOW1;
        else if (roll < 57) return TILE_WILLOW2;
        else return TILE_WILLOW3;
    }

    private boolean canPlaceTree(int col, int row) {
        if (col < 1 || col + 2 >= mapCols || row - 3 < 0 || row + 1 >= mapRows) {
            return false;
        }

        for (int checkCol = col - 1; checkCol <= col + 2; checkCol++) {
            for (int checkRow = row - 3; checkRow <= row + 1; checkRow++) {
                if (riverWater[checkCol][checkRow]) return false;
                if (checkCol < col && isWater(checkCol, checkRow)) return false;
            }
        }

        int adjacentTreesCount = 0;
        boolean hasLeft = (col > 0 && mapTreeNum[col - 1][row] != 0);
        boolean hasUp = (row > 0 && mapTreeNum[col][row - 1] != 0);
        boolean hasUpLeft = (col > 0 && row > 0 && mapTreeNum[col - 1][row - 1] != 0);
        boolean hasUpRight = (col < mapCols - 1 && row > 0 && mapTreeNum[col + 1][row - 1] != 0);

        if (hasLeft) adjacentTreesCount++;
        if (hasUp) adjacentTreesCount++;
        if (hasUpLeft) adjacentTreesCount++;
        if (hasUpRight) adjacentTreesCount++;

        if (adjacentTreesCount > 1) return false;

        if (hasLeft && rand.nextInt(100) < 85) return false;

        if (hasUp && rand.nextInt(100) < 60) return false;

        if ((hasUpLeft || hasUpRight) && rand.nextInt(100) < 30) return false;

        return true;
    }
}
