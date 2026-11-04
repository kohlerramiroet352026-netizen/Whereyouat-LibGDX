package com.whereyouat.map;

import java.util.ArrayDeque;

/**
 * MapGenerator - Procedural terrain 512x512.
 *
 * TWO separate layers:
 * mapTileNum[col][row] = ground tile (grass, sand, water, stone, dirt)
 * mapTreeNum[col][row] = tree tile (12-15) placed ON TOP, OR 0 = no tree
 *
 * Post-process passes (in order after assignTile):
 * 1. normalizeSandWidth() – BFS from water; ensures sand along water is >= MIN_SAND_WIDTH (3 tiles)
 *    and <= MAX_SAND_WIDTH (7 tiles), eliminating thin 1-tile sand lines.
 * 2. removeIsolatedTiles() – removes single isolated sand tiles in grass and
 * vice-versa.
 * 3. fixDirtPlacement() – removes dirt adjacent to sand, ensures grass buffer.
 * 4. computeTransitions() – detects grass↔sand borders, fills
 * mapTransType/mapTransRot,
 * clears trees and dirt that land on transition tiles.
 * 5. clearDirtNearTransitions()– ensures a 1-tile grass gap between dirt and
 * transitions.
 */
public class MapGenerator {

    // Ground tile indices
    public static final int TILE_GRASS1 = 0;
    public static final int TILE_GRASS2 = 1;
    public static final int TILE_GRASS3 = 2;
    public static final int TILE_GRASS4 = 3;
    public static final int TILE_STONE = 4;
    public static final int TILE_WATER0 = 5;
    public static final int TILE_SAND1 = 9;
    public static final int TILE_SAND2 = 10;
    public static final int TILE_DIRT1 = 11;

    // Tree decoration tile indices (placed in mapTreeNum, not mapTileNum)
    public static final int TILE_TREE1 = 12;
    public static final int TILE_TREE2 = 13;
    public static final int TILE_TREE3 = 14;
    public static final int TILE_TREE4 = 15;

    // Pine trees for snow biome
    public static final int TILE_PINE1 = 16;
    public static final int TILE_PINE2 = 17;
    public static final int TILE_PINE3 = 18;
    public static final int TILE_PINE4 = 19;
    public static final int TILE_PINE5 = 20;

    public static final int TILE_PALM1 = 21;
    public static final int TILE_PALM2 = 22;

    // Natural decorations
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

    // Swamp tiles and aquatic decorations
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

    // -------------------------------------------------------------------------
    // Elevation thresholds
    // -------------------------------------------------------------------------
    public static final double DEEP_WATER_THRESHOLD = 0.30;
    public static final double SHALLOW_WATER_THRESHOLD = 0.38;
    /**
     * Sand extends from SHALLOW_WATER_THRESHOLD to SAND_THRESHOLD.
     * Generous beach width (~3 to 6 tiles) to prevent thin 1-tile sand lines.
     */
    public static final double SAND_THRESHOLD = 0.45;
    public static final double GRASS_THRESHOLD = 0.78; // stone starts here

    // Biome thresholds
    public static final double SNOW_THRESHOLD = 0.28; // was 0.35 → larger snow zones
    public static final double SNOW_BLEND_EDGE = 0.32; // was 0.43 → narrow 4% blend = ~1-2 tiles
    public static final double FOREST_THRESHOLD = 0.60;
    public static final double FOREST_BLEND_LOW = 0.50;

    /**
     * Minimum sand width (tiles) along water to prevent paper-thin lines
     * and broken depth border transitions.
     */
    public static final int MIN_SAND_WIDTH = 3;

    /**
     * Maximum sand width (tiles) measured from the nearest water tile.
     * - "Normal" land: sand strip is at most this wide.
     * - Scales up slightly where coastline is long (beach-like regions).
     */
    private static final int MAX_SAND_WIDTH = 7;

    public final int mapCols, mapRows;
    public final long seed;
    public final boolean isSnowyDay;
    private final java.util.Random rand;

    // --- Cached grassVar noise (needed in cleanup passes) ---
    private final double[][] grassVarCache;

    // --- Output ---
    public int[][] mapTileNum; // ground layer
    public int[][] mapTreeNum; // decoration / tree layer (0 = no tree)
    public boolean[][] isShallowWater;
    public boolean[][] isDeepWater;
    public boolean[][] isSnowBiome;
    public boolean[][] isSwampBiome;
    public boolean[][] isBeachBiome;
    public float[][] snowBlend;
    public float[][] swampBlend;
    public float[][] deepWaterBlend;
    public boolean[][] isForestBiome;
    /** Transition type per tile: 0=none, 1=side, 2=out_corner, 3=in_corner */
    public int[][] mapTransType;
    /** Rotation per tile: 0=0°, 1=90°CW, 2=180°, 3=270°CW */
    public int[][] mapTransRot;

    // --- Rivers ---
    private boolean[][] riverWater;
    private boolean[][] riverSand;

    public MapGenerator(int cols, int rows, long seed, String seedString) {
        this.mapCols = cols;
        this.mapRows = rows;
        this.seed = seed;
        this.isSnowyDay = (seedString != null && seedString.equalsIgnoreCase("snowy day"));
        this.rand = new java.util.Random(seed);

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

    // =========================================================================
    // MAIN GENERATE
    // =========================================================================

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

        // --- Snow & Swamp Biome Placement Setup ---
        // Snow and Swamp ALWAYS appear in opposite random corners
        java.util.Random biomeRand = new java.util.Random(seed + 98765L);
        int snowCorner = biomeRand.nextInt(4); // 0=Top-Left, 1=Top-Right, 2=Bottom-Right, 3=Bottom-Left
        int swampCorner = (snowCorner + 2) % 4; // Always diagonally opposite corner

        // Corner centers anchored primarily on the island landmass (island extends col/row 80 to 432).
        // Placing centers at margin 135 guarantees ~65-75% is on land, with a small portion extending into ocean.
        int cornerMargin = 135;
        int[][] cornerCoords = {
            { cornerMargin, cornerMargin },                                 // 0: Top-Left
            { mapCols - cornerMargin, cornerMargin },                       // 1: Top-Right
            { mapCols - cornerMargin, mapRows - cornerMargin },             // 2: Bottom-Right
            { cornerMargin, mapRows - cornerMargin }                        // 3: Bottom-Left
        };

        int snowCx = cornerCoords[snowCorner][0] + (biomeRand.nextInt(21) - 10);
        int snowCy = cornerCoords[snowCorner][1] + (biomeRand.nextInt(21) - 10);
        double maxSnowRadius = 120 + biomeRand.nextDouble() * 15;

        int swampCx = cornerCoords[swampCorner][0] + (biomeRand.nextInt(21) - 10);
        int swampCy = cornerCoords[swampCorner][1] + (biomeRand.nextInt(21) - 10);
        double maxSwampRadius = 120 + biomeRand.nextDouble() * 15;

        // --- Beach Biome Placement Setup ---
        java.util.Random beachRand = new java.util.Random(seed + 12345L);
        int numBeaches = 3;
        int[][] beachCenters = new int[numBeaches][2];
        double[] beachRadii = new double[numBeaches];
        for (int i = 0; i < numBeaches; i++) {
            int edge = beachRand.nextInt(4);
            int margin = 20;
            int depth = 40;

            if (edge == 0) { // Top
                beachCenters[i][0] = margin + beachRand.nextInt(mapCols - 2 * margin);
                beachCenters[i][1] = margin + beachRand.nextInt(depth);
            } else if (edge == 1) { // Bottom
                beachCenters[i][0] = margin + beachRand.nextInt(mapCols - 2 * margin);
                beachCenters[i][1] = mapRows - margin - beachRand.nextInt(depth);
            } else if (edge == 2) { // Left
                beachCenters[i][0] = margin + beachRand.nextInt(depth);
                beachCenters[i][1] = margin + beachRand.nextInt(mapRows - 2 * margin);
            } else { // Right
                beachCenters[i][0] = mapCols - margin - beachRand.nextInt(depth);
                beachCenters[i][1] = margin + beachRand.nextInt(mapRows - 2 * margin);
            }
            beachRadii[i] = 40 + beachRand.nextDouble() * 30; // 40 to 70 radius
        }

        generateRivers();

        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {

                double elev = elevNoise.octaveNoise(col * scaleElev, row * scaleElev, 6, 0.55, 2.0);
                double detail = detailNoise.octaveNoise(col * scaleDetail, row * scaleDetail, 3, 0.4, 2.0);
                elev = elev * 0.85 + detail * 0.15;

                // --- Edge Dropoff (Ocean & Beach) ---
                double distX = Math.min(col, mapCols - 1 - col);
                double distY = Math.min(row, mapRows - 1 - row);
                double dist = Math.min(distX, distY);

                if (dist < 80) {
                    double drop = (80.0 - dist) / 50.0; // 0 at dist 80, 1 at dist 30
                    if (drop > 0) {
                        elev -= Math.pow(drop, 1.5) * 1.0;
                    }
                }

                // --- Rivers and Lakes ---
                double lake = lakeNoise.octaveNoise(col * 0.02, row * 0.02, 2, 0.5, 2.0);
                boolean isLake = (lake > 0.82); // Small lakes

                // Carve water and sand banks (allow carving through natural sand beaches too)
                if (riverWater[col][row] || isLake) {
                    elev = Math.min(elev, SHALLOW_WATER_THRESHOLD - 0.01); // Force shallow water
                } else if (riverSand[col][row] || lake > 0.78) {
                    elev = Math.min(elev, SAND_THRESHOLD - 0.01); // Force sand banks (beach)
                }

                double forest = forestNoise.octaveNoise(col * scaleForest, row * scaleForest, 4, 0.55, 2.0);
                double dirt = dirtNoise.octaveNoise(col * scaleDirt, row * scaleDirt, 2, 0.5, 2.0);
                double clear = clearNoise.octaveNoise(col * scaleClear, row * scaleClear, 3, 0.5, 2.0);
                double grassVar = grassVarNoise.octaveNoise(col * scaleGrassVar, row * scaleGrassVar, 2, 0.5, 2.0);

                grassVarCache[col][row] = grassVar;

                // --- Structural Snow Biome ---
                boolean snow = false;
                float sBlend = 0f;

                if (isSnowyDay) {
                    snow = true;
                    sBlend = 1.0f;
                } else {
                    double distToSnowCenter = Math.hypot(col - snowCx, row - snowCy);
                    double edgeNoise = tempNoise.octaveNoise(col * 0.02, row * 0.02, 4, 0.5, 2.0);
                    double noisyDist = distToSnowCenter + (edgeNoise - 0.5) * 50.0;

                    if (noisyDist < maxSnowRadius) {
                        snow = true;
                        sBlend = 1.0f;
                    } else if (noisyDist < maxSnowRadius + 14.0) { // ~14 tiles wide steep blend edge
                        snow = false;
                        double t = 1.0 - (noisyDist - maxSnowRadius) / 14.0;
                        sBlend = (float) Math.pow(Math.max(0, t), 0.30);
                    }
                }

                isSnowBiome[col][row] = snow;
                snowBlend[col][row] = sBlend;

                // --- Structural Swamp Biome ---
                boolean swamp = false;
                float swBlend = 0f;

                if (!isSnowyDay) {
                    double distToSwampCenter = Math.hypot(col - swampCx, row - swampCy);
                    double swEdgeNoise = swampNoise.octaveNoise(col * 0.02, row * 0.02, 4, 0.5, 2.0);
                    double noisySwampDist = distToSwampCenter + (swEdgeNoise - 0.5) * 50.0;

                    if (noisySwampDist < maxSwampRadius) {
                        swamp = true;
                        swBlend = 1.0f;
                    } else if (noisySwampDist < maxSwampRadius + 14.0) { // ~14 tiles wide blend edge
                        swamp = false;
                        double t = 1.0 - (noisySwampDist - maxSwampRadius) / 14.0;
                        swBlend = (float) Math.pow(Math.max(0, t), 0.30);
                    }
                }

                isSwampBiome[col][row] = swamp;
                swampBlend[col][row] = swBlend;

                // --- Swamp Terrain: Meandering waterways, ponds, and narrow mud banks ---
                boolean isSwampMud = false;
                if (swamp && dist >= 70) {
                    double channel = Math.abs(swampChannelNoise.octaveNoise(col * 0.038, row * 0.038, 3, 0.5, 2.0) - 0.5);
                    double sLake = swampLakeNoise.octaveNoise(col * 0.028, row * 0.028, 2, 0.5, 2.0);

                    if (channel < 0.032 || sLake > 0.70) {
                        // Force shallow water for swamp rivers & lagoons
                        elev = Math.min(elev, SHALLOW_WATER_THRESHOLD - 0.01);
                    } else {
                        // Mud banks along swamp waterways: only immediately adjacent to water (1-2 tiles wide)
                        boolean riverMud = (channel >= 0.032 && channel < 0.046);
                        boolean lakeMud = (sLake >= 0.67 && sLake <= 0.70);
                        if (riverMud || lakeMud || riverSand[col][row] || (lake > 0.78)) {
                            isSwampMud = true;
                        }
                    }
                }

                // --- Structural Beach Biome ---
                boolean beach = false;
                for (int i = 0; i < numBeaches; i++) {
                    double distToBeach = Math
                            .sqrt(Math.pow(col - beachCenters[i][0], 2) + Math.pow(row - beachCenters[i][1], 2));
                    double bEdgeNoise = tempNoise.octaveNoise((col + 50) * 0.02, (row + 50) * 0.02, 4, 0.5, 2.0);
                    double noisyDist = distToBeach + (bEdgeNoise - 0.5) * 40.0;
                    if (noisyDist < beachRadii[i]) {
                        beach = true;
                        break;
                    }
                }
                isBeachBiome[col][row] = beach;

                assignTile(col, row, elev, forest, dirt, clear, grassVar, snow, beach, swamp, isSwampMud, dist);
            }
        }

        // --- Post-process passes ---
        normalizeSandWidth(); // 1. enforce minimum beach width (>= 3 tiles) and cap maximum width
        fixDirtPlacement(); // 2. dirt must not touch sand (convert violators to grass first)
        removeIsolatedTiles(); // 3. clean shoreline and smooth peninsulas/isolated tiles
        validateTrees(); // 4. ensure NO tree overhangs water, straddles water, or sits on cliffs
        computeTransitions(); // 5. universal grass↔non-grass border overlays
        calculateDeepWaterBlend(); // 6. smooth gradient for deep water
        placeRocks(); // 7. scatter rare surface rocks on open grass away from trees and obstacles
        placeSandDecorations(); // 8. scatter sandstone rocks and sand grass on non-transitioning Sand1
        placeAquaticFeatures(); // 9. place mangroves, lilipads, cattails, and submerged rocks
    }

    // =========================================================================
    // POST-PROCESS 6: Calculate deep water blend gradient
    // =========================================================================

    private void calculateDeepWaterBlend() {
        int[] distFromNonDeep = new int[mapCols * mapRows];
        java.util.Arrays.fill(distFromNonDeep, Integer.MAX_VALUE);

        ArrayDeque<Integer> queue = new ArrayDeque<>();

        // Seed BFS from every non-deep water tile
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (!isDeepWater[col][row]) {
                    distFromNonDeep[col * mapRows + row] = 0;
                    queue.add(col * mapRows + row);
                    deepWaterBlend[col][row] = 0f;
                }
            }
        }

        // 4-directional BFS
        int[] dc = { 0, 0, 1, -1 };
        int[] dr = { 1, -1, 0, 0 };

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int c = idx / mapRows;
            int r = idx % mapRows;
            int d = distFromNonDeep[idx];

            for (int k = 0; k < 4; k++) {
                int nc = c + dc[k];
                int nr = r + dr[k];
                if (nc < 0 || nc >= mapCols || nr < 0 || nr >= mapRows)
                    continue;
                int nIdx = nc * mapRows + nr;
                if (distFromNonDeep[nIdx] != Integer.MAX_VALUE)
                    continue;
                distFromNonDeep[nIdx] = d + 1;
                queue.add(nIdx);
            }
        }

        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (isDeepWater[col][row]) {
                    int dist = distFromNonDeep[col * mapRows + row];
                    // Over 8 tiles, it reaches max darkness (1.0f)
                    float blend = (float) dist / 8.0f;
                    if (blend > 1.0f)
                        blend = 1.0f;
                    deepWaterBlend[col][row] = blend;
                }
            }
        }
    }

    // =========================================================================
    // RIVER GENERATION
    // =========================================================================

    private void generateRivers() {
        riverWater = new boolean[mapCols][mapRows];
        riverSand = new boolean[mapCols][mapRows];

        int numRivers = rand.nextInt(3) + 1; // 1, 2, or 3

        if (numRivers == 1) {
            // One long river cutting the map in half (edge to opposite edge).
            int edge = rand.nextInt(6); // 0-1: Vertical-ish, 2-3: Horizontal-ish, 4-5: Diagonal
            int x0 = 5, y0 = 5, x1 = 5, y1 = 5;

            int minBoundX = mapCols / 4;
            int maxBoundX = mapCols * 3 / 4;
            int rangeX = maxBoundX - minBoundX;

            int minBoundY = mapRows / 4;
            int maxBoundY = mapRows * 3 / 4;
            int rangeY = maxBoundY - minBoundY;

            if (edge == 0 || edge == 1) { // Vertical
                x0 = minBoundX + rand.nextInt(rangeX);
                y0 = (edge == 0) ? 5 : mapRows - 5;
                x1 = minBoundX + rand.nextInt(rangeX);
                y1 = (edge == 0) ? mapRows - 5 : 5;
            } else if (edge == 2 || edge == 3) { // Horizontal
                x0 = (edge == 2) ? 5 : mapCols - 5;
                y0 = minBoundY + rand.nextInt(rangeY);
                x1 = (edge == 2) ? mapCols - 5 : 5;
                y1 = minBoundY + rand.nextInt(rangeY);
            } else if (edge == 4) { // Diagonal \
                x0 = rand.nextInt(minBoundX);
                y0 = 5;
                x1 = maxBoundX + rand.nextInt(minBoundX);
                y1 = mapRows - 5;
            } else if (edge == 5) { // Diagonal /
                x0 = maxBoundX + rand.nextInt(minBoundX);
                y0 = 5;
                x1 = rand.nextInt(minBoundX);
                y1 = mapRows - 5;
            }
            drawRiver(x0, y0, x1, y1, true); // force sand
        } else {
            // 2 or 3 rivers. Start inland, end in ocean.
            for (int i = 0; i < numRivers; i++) {
                int cx = 150 + rand.nextInt(mapCols - 300);
                int cy = 150 + rand.nextInt(mapRows - 300);

                int edge = rand.nextInt(4);
                int x1, y1;
                if (edge == 0) {
                    x1 = cx + (rand.nextInt(160) - 80);
                    y1 = 5;
                } else if (edge == 1) {
                    x1 = cx + (rand.nextInt(160) - 80);
                    y1 = mapRows - 5;
                } else if (edge == 2) {
                    x1 = 5;
                    y1 = cy + (rand.nextInt(160) - 80);
                } else {
                    x1 = mapCols - 5;
                    y1 = cy + (rand.nextInt(160) - 80);
                }

                drawRiver(cx, cy, x1, y1, false); // sand is patchy
            }
        }
    }

    private void drawRiver(int x0, int y0, int x1, int y1, boolean forceSand) {
        double dist = Math.hypot(x1 - x0, y1 - y0);
        int steps = (int) (dist * 2.0); // dense step size to carve completely
        double noiseOffsetX = rand.nextDouble() * 1000.0;

        PerlinNoise riverWiggle = new PerlinNoise(seed + rand.nextInt());
        PerlinNoise sandPatchNoise = new PerlinNoise(seed + rand.nextInt());

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double bx = x0 + t * (x1 - x0);
            double by = y0 + t * (y1 - y0);

            double dx = x1 - x0;
            double dy = y1 - y0;
            double length = Math.hypot(dx, dy);
            double nx = -dy / length;
            double ny = dx / length;

            // Generate a wiggle displacement, tapering at the start/end slightly if needed
            // (here we just use raw noise)
            double wiggle = riverWiggle.octaveNoise(t * 3.0, noiseOffsetX, 3, 0.5, 2.0) - 0.5;
            wiggle *= 120.0; // +/- 60 tiles max lateral displacement

            double cx = bx + nx * wiggle;
            double cy = by + ny * wiggle;

            double waterRadius = 2.2; // roughly 4-5 tiles wide across
            double sandRadius = forceSand ? 6.0 : 5.0; // wide, natural banks (eliminates 1-tile river sand lines)

            int rMax = (int) Math.ceil(Math.max(waterRadius, sandRadius));
            int icx = (int) cx;
            int icy = (int) cy;

            for (int x = icx - rMax; x <= icx + rMax; x++) {
                for (int y = icy - rMax; y <= icy + rMax; y++) {
                    if (x >= 0 && x < mapCols && y >= 0 && y < mapRows) {
                        double d = Math.hypot(x - cx, y - cy);
                        if (d <= waterRadius) {
                            riverWater[x][y] = true;
                        } else if (d <= sandRadius) {
                            if (forceSand) {
                                riverSand[x][y] = true;
                            } else {
                                if (sandPatchNoise.octaveNoise(x * 0.05, y * 0.05, 2, 0.5, 2.0) > 0.55) {
                                    riverSand[x][y] = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // TILE ASSIGNMENT (first pass)
    // =========================================================================

    private void assignTile(int col, int row, double elev,
            double forest, double dirt, double clear,
            double grassVar, boolean snow, boolean beach,
            boolean swamp, boolean isSwampMud, double dist) {
        mapTreeNum[col][row] = 0;

        // --- Water zones ---
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

        double localSandThreshold = beach ? 0.52 : SAND_THRESHOLD;

        // --- Swamp Biome: ONLY mud along shores and waterways, ZERO sand in swamp biome! ---
        if (swamp && (isSwampMud || elev < localSandThreshold)) {
            mapTileNum[col][row] = TILE_MUD;
            isForestBiome[col][row] = false;
            return;
        }

        // --- Beach / sand (narrow strip, also on ocean shores of swamp) ---
        if (elev < localSandThreshold) {
            mapTileNum[col][row] = pickSand();
            isForestBiome[col][row] = false;

            // Generate palm trees on beaches in beach biomes
            if (beach && rand.nextInt(50) == 0) {
                if (canPlaceTree(col, row)) {
                    mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_PALM1 : TILE_PALM2;
                }
            }
            return;
        }

        // Snow biome: open tundra or pine forests
        if (snow) {
            boolean inForest = forest >= FOREST_THRESHOLD;
            boolean inTransition = !inForest && forest >= FOREST_BLEND_LOW;

            if (inForest) {
                isForestBiome[col][row] = true;
                if (clear > 0.68) {
                    mapTileNum[col][row] = pickGrass(grassVar, snow);
                    mapTreeNum[col][row] = 0;
                } else {
                    mapTileNum[col][row] = pickGrass(grassVar, snow);
                    if (canPlaceTree(col, row)) {
                        mapTreeNum[col][row] = pickPine();
                    }
                }
            } else if (inTransition) {
                isForestBiome[col][row] = true;
                if (clear > 0.55) {
                    mapTileNum[col][row] = pickGrass(grassVar, snow);
                    mapTreeNum[col][row] = 0;
                } else {
                    mapTileNum[col][row] = pickGrass(grassVar, snow);
                    if (canPlaceTree(col, row)) {
                        mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_PINE1 : TILE_PINE3;
                    }
                }
            } else {
                // Tundra
                isForestBiome[col][row] = false;
                mapTileNum[col][row] = pickGrass(grassVar, snow);

                // Add snowy bushes with ~2% chance (never directly adjacent to water)
                if (canPlaceTree(col, row) && !isNearWater(col, row, 1) && rand.nextInt(100) < 2) {
                    int r = rand.nextInt(3);
                    if (r == 0)
                        mapTreeNum[col][row] = TILE_SNOWY_BUSH1;
                    else if (r == 1)
                        mapTreeNum[col][row] = TILE_SNOWY_BUSH2;
                    else if (!isNearWater(col, row, 2))
                        mapTreeNum[col][row] = TILE_SNOWY_BUSH3_LARGE;
                }
            }
            return;
        }

        // Swamp biome: lush willow forests and rich dense undergrowth
        if (swamp) {
            isForestBiome[col][row] = true;
            // Willow trees cover more ground than a standard forest
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
                // Clearings in swamp: abundant lush vegetation and rocks (never directly adjacent to water)
                mapTileNum[col][row] = pickGrass(grassVar, false);
                if (canPlaceTree(col, row) && !isNearWater(col, row, 1)) {
                    // ~15% chance for vegetation (richer than normal 6%)
                    if (rand.nextInt(100) < 15) {
                        int r = rand.nextInt(100);
                        if (r < 28) {
                            mapTreeNum[col][row] = TILE_SHORTER_GRASS;
                        } else if (r < 54) {
                            mapTreeNum[col][row] = TILE_SHORT_GRASS;
                        } else if (r < 78) {
                            mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_TALL_GRASS1 : TILE_TALL_GRASS2;
                        } else if (r < 87) {
                            mapTreeNum[col][row] = TILE_SMALL_BUSH;
                        } else if (r < 94) {
                            mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_BUSH1 : TILE_BUSH2;
                        } else if (r < 97) {
                            mapTreeNum[col][row] = TILE_BUSH3;
                        } else if (r < 99) {
                            if (!isNearWater(col, row, 2)) {
                                mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_BUSH4_LARGE : TILE_BUSH5_LARGE;
                            }
                        } else {
                            mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_LOG1 : TILE_LOG2;
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
                mapTileNum[col][row] = pickGrass(grassVar, snow);
                mapTreeNum[col][row] = 0;
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, snow);
                if (canPlaceTree(col, row)) {
                    mapTreeNum[col][row] = pickTree();
                }
            }

        } else if (inTransition) {
            isForestBiome[col][row] = true;
            if (clear > 0.55) {
                mapTileNum[col][row] = pickGrass(grassVar, snow);
                mapTreeNum[col][row] = 0;
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, snow);
                if (canPlaceTree(col, row)) {
                    mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_TREE1 : TILE_TREE3;
                }
            }

        } else {
            // Plains biome
            isForestBiome[col][row] = false;
            mapTreeNum[col][row] = 0;
            // Dirt patches: only here, only when elevation is safely above sand zone
            // (elev > SAND_THRESHOLD + 0.05 ensures distance from sand edge)
            // and dirt noise is high.
            if (elev > SAND_THRESHOLD + 0.05 && dirt > 0.82) {
                mapTileNum[col][row] = TILE_DIRT1;
            } else {
                mapTileNum[col][row] = pickGrass(grassVar, snow);

                // Add natural decorations with ~6% chance for lush vegetation (never directly adjacent to water)
                if (canPlaceTree(col, row) && !isNearWater(col, row, 1)) {
                    if (rand.nextInt(2000) == 0) {
                        // Super rare fallen logs (original rare discovery rate)
                        mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_LOG1 : TILE_LOG2;
                    } else if (rand.nextInt(100) < 6) {
                        int r = rand.nextInt(100);
                        if (r < 33) { // 33% Shorter grass
                            mapTreeNum[col][row] = TILE_SHORTER_GRASS;
                        } else if (r < 63) { // 30% Short grass
                            mapTreeNum[col][row] = TILE_SHORT_GRASS;
                        } else if (r < 87) { // 24% Tall grass (1 and 2)
                            mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_TALL_GRASS1 : TILE_TALL_GRASS2;
                        } else if (r < 95) { // 8% Small bush
                            mapTreeNum[col][row] = TILE_SMALL_BUSH;
                        } else if (r < 98) { // 3% Medium bushes (bush 1 and 2)
                            mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_BUSH1 : TILE_BUSH2;
                        } else if (r < 99) { // 1% Bush 3
                            mapTreeNum[col][row] = TILE_BUSH3;
                        } else { // 1% Large bushes (bush 4 and 5)
                            if (!isNearWater(col, row, 2)) {
                                mapTreeNum[col][row] = (rand.nextBoolean()) ? TILE_BUSH4_LARGE : TILE_BUSH5_LARGE;
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // POST-PROCESS 1: Normalize and enforce sand width using multi-source BFS
    // =========================================================================

    /**
     * Enforces and normalizes sand width along water:
     * 1. Multi-source BFS computes distance from the nearest water tile for all land tiles.
     * 2. Sand tiles farther than MAX_SAND_WIDTH are converted to grass (prevents sand oceans).
     * 3. Any legitimate sand shoreline (dist == 1) is expanded inland to guarantee a minimum
     *    width of MIN_SAND_WIDTH (3 tiles), ensuring that coastlines never have thin 1-tile sand
     *    lines that break depth borders (cliffs) or lack smooth transitions.
     * 4. Lone, isolated 1-tile sand specks along grass shores are cleaned back to grass.
     */
    private void normalizeSandWidth() {
        int[] distFromWater = new int[mapCols * mapRows];
        java.util.Arrays.fill(distFromWater, Integer.MAX_VALUE);

        ArrayDeque<Integer> queue = new ArrayDeque<>();

        // Seed BFS from every water tile (deep or shallow)
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (isDeepWater[col][row] || isShallowWater[col][row] || isWater(col, row)) {
                    distFromWater[col * mapRows + row] = 0;
                    queue.add(col * mapRows + row);
                }
            }
        }

        // 4-directional BFS
        int[] dc = { 0, 0, 1, -1 };
        int[] dr = { 1, -1, 0, 0 };

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int c = idx / mapRows;
            int r = idx % mapRows;
            int d = distFromWater[idx];

            for (int k = 0; k < 4; k++) {
                int nc = c + dc[k];
                int nr = r + dr[k];
                if (nc < 0 || nc >= mapCols || nr < 0 || nr >= mapRows)
                    continue;
                int nIdx = nc * mapRows + nr;
                if (distFromWater[nIdx] != Integer.MAX_VALUE)
                    continue;
                distFromWater[nIdx] = d + 1;
                queue.add(nIdx);
            }
        }

        // Pass 1: Cap maximum sand width, and convert any sand in swamp biome to mud
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (!isSand(col, row))
                    continue;
                if (isSwampBiome[col][row]) {
                    mapTileNum[col][row] = TILE_MUD;
                    mapTreeNum[col][row] = 0;
                    continue;
                }
                int dist = distFromWater[col * mapRows + row];
                int maxWidth = isBeachBiome[col][row] ? 30 : MAX_SAND_WIDTH;
                if (dist > maxWidth) {
                    // Too far from water — replace with grass
                    mapTileNum[col][row] = pickGrass(grassVarCache[col][row], isSnowBiome[col][row]);
                    mapTreeNum[col][row] = 0; // Clear any palm trees that were placed here
                }
            }
        }

        // Pass 2: Identify legitimate sand and swamp shoreline tiles and clean rogue isolated sand specks
        boolean[][] isShoreline = new boolean[mapCols][mapRows];
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (distFromWater[col * mapRows + row] != 1)
                    continue;

                if (isSwampBiome[col][row]) {
                    if (mapTileNum[col][row] == TILE_MUD) {
                        isShoreline[col][row] = true;
                    }
                    continue;
                }

                if (!isSand(col, row))
                    continue;

                int sandNeighbours = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nc = col + dx;
                        int nr = row + dy;
                        if (nc >= 0 && nc < mapCols && nr >= 0 && nr < mapRows) {
                            if (isSand(nc, nr)) {
                                sandNeighbours++;
                            }
                        }
                    }
                }

                // If completely isolated sand tile with 0 sand neighbors on a grass shore:
                if (sandNeighbours == 0 && !isBeachBiome[col][row] && !riverSand[col][row]) {
                    mapTileNum[col][row] = pickGrass(grassVarCache[col][row], isSnowBiome[col][row]);
                    mapTreeNum[col][row] = 0;
                } else {
                    isShoreline[col][row] = true;
                }
            }
        }

        // Pass 3: Enforce minimum sand/mud width (MIN_SAND_WIDTH = 3, MIN_MUD_WIDTH = 2)
        // Expand inland from sand/mud shorelines so that beaches and mud shores are at least 2-3 tiles deep
        ArrayDeque<Integer> expandQueue = new ArrayDeque<>();
        boolean[] inExpandQueue = new boolean[mapCols * mapRows];

        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (isShoreline[col][row]) {
                    int idx = col * mapRows + row;
                    expandQueue.add(idx);
                    inExpandQueue[idx] = true;
                }
            }
        }

        while (!expandQueue.isEmpty()) {
            int idx = expandQueue.poll();
            int c = idx / mapRows;
            int r = idx % mapRows;

            for (int k = 0; k < 4; k++) {
                int nc = c + dc[k];
                int nr = r + dr[k];
                if (nc < 0 || nc >= mapCols || nr < 0 || nr >= mapRows)
                    continue;
                int nIdx = nc * mapRows + nr;
                if (inExpandQueue[nIdx])
                    continue;

                int dist = distFromWater[nIdx];
                int targetWidth = isSwampBiome[nc][nr] ? 2 : MIN_SAND_WIDTH;
                if (dist >= 1 && dist <= targetWidth && !isWater(nc, nr)) {
                    inExpandQueue[nIdx] = true;
                    if (isGrass(nc, nr)) {
                        if (isSwampBiome[nc][nr]) {
                            mapTileNum[nc][nr] = TILE_MUD;
                            mapTreeNum[nc][nr] = 0; // Clear any grass vegetation
                            expandQueue.add(nIdx);
                        } else {
                            mapTileNum[nc][nr] = pickSand();
                            mapTreeNum[nc][nr] = 0; // Clear any grass vegetation
                            expandQueue.add(nIdx);
                        }
                    } else if (isSand(nc, nr) || (isSwampBiome[nc][nr] && mapTileNum[nc][nr] == TILE_MUD)) {
                        expandQueue.add(nIdx);
                    }
                }
            }
        }
    }

    // =========================================================================
    // POST-PROCESS 2: Remove isolated tiles & smooth shoreline/transition borders
    // =========================================================================

    /**
     * Eliminates rogue, unblended, and isolated tiles to ensure completely smooth,
     * natural transitions across the world:
     * 1. Shoreline & Beach Consistency:
     *    Grass tiles bordering water that touch sand or have <= 1 grass neighbour
     *    are absorbed into beach sand, preventing rogue grass blocks floating in the ocean
     *    or breaking cliff edges at beaches.
     * 2. Peninsula & Island Smoothing:
     *    - Sand tiles with >= 3 grass neighbours (or <= 1 sand neighbour away from water)
     *      are absorbed into grass, eliminating raw unblended 1-tile peninsulas.
     *    - Grass tiles with >= 3 sand neighbours or 0 grass neighbours are absorbed into sand.
     *    - Dirt tiles with >= 3 grass neighbours or <= 1 dirt neighbour are absorbed into grass.
     */
    private void removeIsolatedTiles() {
        // Step 1: Initial shoreline clean
        cleanShoreline();

        // Step 2: Multi-pass cellular smoothing for peninsulas and isolated spikes
        for (int pass = 0; pass < 3; pass++) {
            for (int col = 0; col < mapCols; col++) {
                for (int row = 0; row < mapRows; row++) {
                    int t = mapTileNum[col][row];
                    boolean isSand = (t == TILE_SAND1 || t == TILE_SAND2);
                    boolean isGrass = (t >= TILE_GRASS1 && t <= TILE_GRASS4);
                    boolean isDirt = (t == TILE_DIRT1);

                    if (isSand) {
                        int grassN = (isGrass(col, row - 1) ? 1 : 0) +
                                     (isGrass(col, row + 1) ? 1 : 0) +
                                     (isGrass(col - 1, row) ? 1 : 0) +
                                     (isGrass(col + 1, row) ? 1 : 0);
                        int sandN = (isSand(col, row - 1) ? 1 : 0) +
                                    (isSand(col, row + 1) ? 1 : 0) +
                                    (isSand(col - 1, row) ? 1 : 0) +
                                    (isSand(col + 1, row) ? 1 : 0);

                        boolean nearWater = isWater(col, row - 1) || isWater(col, row + 1)
                                || isWater(col - 1, row) || isWater(col + 1, row);

                        int waterN = (isWater(col, row - 1) ? 1 : 0) +
                                     (isWater(col, row + 1) ? 1 : 0) +
                                     (isWater(col - 1, row) ? 1 : 0) +
                                     (isWater(col + 1, row) ? 1 : 0);

                        // Sand peninsula sticking into grass (away from water) or isolated lone sand tile
                        if (!nearWater && (grassN >= 3 || sandN <= 1)) {
                            mapTileNum[col][row] = pickGrass(grassVarCache[col][row], isSnowBiome[col][row]);
                            mapTreeNum[col][row] = 0;
                        } else if (nearWater && sandN == 0 && !isBeachBiome[col][row] && !riverSand[col][row]) {
                            // Isolated lone 1-tile sand speck on a grassy shoreline -> restore clean grass shore
                            mapTileNum[col][row] = pickGrass(grassVarCache[col][row], isSnowBiome[col][row]);
                            mapTreeNum[col][row] = 0;
                        } else if (waterN >= 3 && grassN == 0 && sandN <= 1) {
                            // Isolated 1-2 tile sand speck floating in water -> convert to shallow water
                            mapTileNum[col][row] = TILE_WATER0;
                            isShallowWater[col][row] = true;
                            isDeepWater[col][row] = false;
                            mapTreeNum[col][row] = 0;
                        }
                    } else if (isGrass) {
                        int sandN = (isSand(col, row - 1) ? 1 : 0) +
                                     (isSand(col, row + 1) ? 1 : 0) +
                                     (isSand(col - 1, row) ? 1 : 0) +
                                     (isSand(col + 1, row) ? 1 : 0);
                        int grassN = (isGrass(col, row - 1) ? 1 : 0) +
                                     (isGrass(col, row + 1) ? 1 : 0) +
                                     (isGrass(col - 1, row) ? 1 : 0) +
                                     (isGrass(col + 1, row) ? 1 : 0);
                        int dirtN = (isDirt(col, row - 1) ? 1 : 0) +
                                    (isDirt(col, row + 1) ? 1 : 0) +
                                    (isDirt(col - 1, row) ? 1 : 0) +
                                    (isDirt(col + 1, row) ? 1 : 0);

                        if (!isSwampBiome[col][row] && (sandN >= 3 || (grassN == 0 && sandN > 0))) {
                            mapTileNum[col][row] = pickSand();
                            mapTreeNum[col][row] = 0;
                        } else if (dirtN >= 3 || (grassN == 0 && dirtN > 0)) {
                            mapTileNum[col][row] = TILE_DIRT1;
                            mapTreeNum[col][row] = 0;
                        }
                    } else if (isDirt) {
                        int grassN = (isGrass(col, row - 1) ? 1 : 0) +
                                     (isGrass(col, row + 1) ? 1 : 0) +
                                     (isGrass(col - 1, row) ? 1 : 0) +
                                     (isGrass(col + 1, row) ? 1 : 0);
                        int dirtN = (isDirt(col, row - 1) ? 1 : 0) +
                                    (isDirt(col, row + 1) ? 1 : 0) +
                                    (isDirt(col - 1, row) ? 1 : 0) +
                                    (isDirt(col + 1, row) ? 1 : 0);

                        if (grassN >= 3 || dirtN <= 1) {
                            mapTileNum[col][row] = pickGrass(grassVarCache[col][row], isSnowBiome[col][row]);
                            mapTreeNum[col][row] = 0;
                        }
                    }
                }
            }
        }

        // Step 3: Final shoreline consistency pass
        cleanShoreline();
    }

    /**
     * Eliminates rogue grass tiles directly adjacent to water and beach sand,
     * ensuring that coastlines never have disconnected 1x1 grass anomalies,
     * acute water-protruding spikes, or awkward beach-cliff borders.
     */
    private void cleanShoreline() {
        boolean changed = true;
        int pass = 0;
        while (changed && pass++ < 4) {
            changed = false;
            for (int col = 0; col < mapCols; col++) {
                for (int row = 0; row < mapRows; row++) {
                    if (!isGrass(col, row))
                        continue;

                    int waterNeighbours = (isWater(col, row - 1) ? 1 : 0) +
                                          (isWater(col, row + 1) ? 1 : 0) +
                                          (isWater(col - 1, row) ? 1 : 0) +
                                          (isWater(col + 1, row) ? 1 : 0);
                    if (waterNeighbours == 0)
                        continue;

                    int sandNeighbours = (isSand(col, row - 1) ? 1 : 0) +
                                         (isSand(col, row + 1) ? 1 : 0) +
                                         (isSand(col - 1, row) ? 1 : 0) +
                                         (isSand(col + 1, row) ? 1 : 0);

                    int grassNeighbours = (isGrass(col, row - 1) ? 1 : 0) +
                                          (isGrass(col, row + 1) ? 1 : 0) +
                                          (isGrass(col - 1, row) ? 1 : 0) +
                                          (isGrass(col + 1, row) ? 1 : 0);

                    // 1. Rogue grass tile protruding sharply or isolated in water -> smooth into shallow water
                    if (waterNeighbours >= 3 || (waterNeighbours >= 2 && grassNeighbours == 0 && sandNeighbours == 0)) {
                        mapTileNum[col][row] = TILE_WATER0;
                        isShallowWater[col][row] = true;
                        isDeepWater[col][row] = false;
                        mapTreeNum[col][row] = 0;
                        changed = true;
                        continue;
                    }

                    // 2. Shoreline in designated beach biome -> ensure beach sand (never in swamp)
                    if (!isSwampBiome[col][row] && isBeachBiome[col][row] && waterNeighbours > 0) {
                        mapTileNum[col][row] = pickSand();
                        mapTreeNum[col][row] = 0;
                        changed = true;
                        continue;
                    }

                    // 3. Grass tile embedded inside a real sand beach (at least 2 sand neighbors) -> absorb into sand (never in swamp)
                    if (!isSwampBiome[col][row] && sandNeighbours >= 2 && waterNeighbours > 0) {
                        mapTileNum[col][row] = pickSand();
                        mapTreeNum[col][row] = 0;
                        changed = true;
                    }

                    // 3b. Grass tile embedded inside a real swamp mud shore (at least 2 mud neighbors) -> absorb into mud
                    if (isSwampBiome[col][row] && waterNeighbours > 0) {
                        int mudNeighbours = (isMud(col, row - 1) ? 1 : 0) +
                                            (isMud(col, row + 1) ? 1 : 0) +
                                            (isMud(col - 1, row) ? 1 : 0) +
                                            (isMud(col + 1, row) ? 1 : 0);
                        if (mudNeighbours >= 2) {
                            mapTileNum[col][row] = TILE_MUD;
                            mapTreeNum[col][row] = 0;
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // POST-PROCESS 3: Dirt placement rules
    // =========================================================================

    /**
     * Dirt must:
     * - Only appear on GRASS tiles (already true by assignTile design).
     * - NOT be directly adjacent (cardinal) to sand.
     * Any dirt tile that violates this → converted to grass.
     */
    private void fixDirtPlacement() {
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                if (mapTileNum[col][row] != TILE_DIRT1)
                    continue;
                // Dirt cannot touch sand on any of the 4 cardinal directions
                if (isSand(col, row - 1) || isSand(col, row + 1) ||
                        isSand(col + 1, row) || isSand(col - 1, row)) {
                    mapTileNum[col][row] = pickGrass(grassVarCache[col][row], isSnowBiome[col][row]);
                }
            }
        }
    }

    // =========================================================================
    // POST-PROCESS 4: Tree and vegetation validation
    // =========================================================================

    /**
     * Eliminates trees, palms, and vegetation that overhang or border water,
     * ensuring that every tree has a solid, continuous land base under its entire
     * 2-tile wide trunk and 3-tile tall canopy.
     */
    private void validateTrees() {
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                int tree = mapTreeNum[col][row];
                if (tree == 0)
                    continue;

                // 1. Tall trees (Oaks 12-15, Pines 16-20, Palms 21-22, Willows 48-50)
                if ((tree >= 12 && tree <= 22) || (tree >= TILE_WILLOW1 && tree <= TILE_WILLOW3)) {
                    boolean isPalm = (tree == TILE_PALM1 || tree == TILE_PALM2);
                    boolean invalid = false;

                    // Out of map boundaries for 2x3 footprint: [col, col+1] x [row-2, row]
                    if (col + 1 >= mapCols || row - 2 < 0 || row >= mapRows) {
                        invalid = true;
                    }

                    // Check full 2x3 footprint for water
                    if (!invalid) {
                        for (int tc = col; tc <= col + 1; tc++) {
                            for (int tr = row - 2; tr <= row; tr++) {
                                if (isWater(tc, tr)) {
                                    invalid = true;
                                    break;
                                }
                            }
                            if (invalid)
                                break;
                        }
                    }

                    // Trunk base check: both (col, row) and (col+1, row) form the trunk base
                    if (!invalid) {
                        if (isPalm) {
                            // Palms must be firmly rooted in sand on BOTH trunk tiles
                            if (!isSand(col, row) || !isSand(col + 1, row)) {
                                invalid = true;
                            }
                        } else {
                            // Oaks, pines, and willows must be firmly rooted in grass on BOTH trunk tiles
                            if (!isGrass(col, row) || !isGrass(col + 1, row)) {
                                invalid = true;
                            }
                        }
                        // Trunk must not drop down directly into water (cliff drop)
                        if (row + 1 < mapRows && (isWater(col, row + 1) || isWater(col + 1, row + 1))) {
                            invalid = true;
                        }
                    }

                    if (invalid) {
                        mapTreeNum[col][row] = 0;
                    }
                } else {
                    // Small vegetation (bushes, tall grass, fallen logs) must not be in or near water
                    if (isWater(col, row)) {
                        mapTreeNum[col][row] = 0;
                    } else if (isLandVegetation(tree)) {
                        int radius = isLargeBush(tree) ? 2 : 1;
                        if (isNearWater(col, row, radius)) {
                            mapTreeNum[col][row] = 0;
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // POST-PROCESS 5: Transition detection
    // =========================================================================

    private void computeTransitions() {
        for (int col = 0; col < mapCols; col++) {
            for (int row = 0; row < mapRows; row++) {
                // Must be a non-grass, non-water ground tile (e.g. sand, dirt, stone)
                if (!isTransitionTarget(col, row))
                    continue;

                boolean n  = isGrass(col, row - 1);
                boolean s  = isGrass(col, row + 1);
                boolean e  = isGrass(col + 1, row);
                boolean w  = isGrass(col - 1, row);
                boolean ne = isGrass(col + 1, row - 1);
                boolean se = isGrass(col + 1, row + 1);
                boolean sw = isGrass(col - 1, row + 1);
                boolean nw = isGrass(col - 1, row - 1);

                if (!n && !s && !e && !w && !ne && !se && !sw && !nw)
                    continue;

                TransitionRenderer.TransInfo info = TransitionRenderer.classify(n, s, e, w, ne, se, sw, nw);

                mapTransType[col][row] = info.type;
                mapTransRot[col][row] = info.rot;

                // Transition tiles: clear decorations
                if (info.type != TransitionRenderer.TRANS_NONE) {
                    mapTreeNum[col][row] = 0;
                }
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Returns true if (col,row) is a valid ground tile candidate for grass overlay (non-grass, non-water). */
    private boolean isTransitionTarget(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows)
            return false;
        if (isShallowWater[col][row] || isDeepWater[col][row])
            return false;
        int t = mapTileNum[col][row];
        if (t >= TILE_WATER0 && t <= TILE_WATER0 + 3)
            return false;
        return !isGrass(col, row);
    }

    /** Returns true if the tile at (col,row) is a sand tile. */
    private boolean isSand(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows)
            return false;
        int t = mapTileNum[col][row];
        return (t == TILE_SAND1 || t == TILE_SAND2);
    }

    /** Returns true if the tile at (col,row) is a mud tile. */
    private boolean isMud(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows)
            return false;
        return mapTileNum[col][row] == TILE_MUD;
    }

    /** Returns true if the tile at (col,row) is any grass variant. */
    private boolean isGrass(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows)
            return false;
        int t = mapTileNum[col][row];
        return (t >= TILE_GRASS1 && t <= TILE_GRASS4);
    }

    /** Returns true if the tile at (col,row) is any water tile (deep, shallow, or water tile ID). */
    private boolean isWater(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows)
            return false;
        if (isShallowWater[col][row] || isDeepWater[col][row])
            return true;
        int t = mapTileNum[col][row];
        return (t >= TILE_WATER0 && t <= TILE_WATER0 + 3);
    }

    /** Returns true if any tile within the specified Chebyshev radius is water. */
    public boolean isNearWater(int col, int row, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int nc = col + dx;
                int nr = row + dy;
                if (isWater(nc, nr)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns true if tree value corresponds to terrestrial bushes or ground vegetation. */
    private boolean isLandVegetation(int tree) {
        return (tree >= TILE_BUSH1 && tree <= TILE_BUSH5_LARGE)
                || (tree >= TILE_LOG1 && tree <= TILE_TALL_GRASS2)
                || (tree >= TILE_SNOWY_BUSH1 && tree <= TILE_SNOWY_BUSH3_LARGE)
                || (tree >= TILE_SMALL_BUSH && tree <= TILE_SHORTER_GRASS);
    }

    /** Returns true if tree value corresponds to large bushes that extend across multiple tiles. */
    private boolean isLargeBush(int tree) {
        return tree == TILE_BUSH4_LARGE || tree == TILE_BUSH5_LARGE || tree == TILE_SNOWY_BUSH3_LARGE;
    }

    /** Returns true if the tile at (col,row) is dirt. */
    private boolean isDirt(int col, int row) {
        if (col < 0 || col >= mapCols || row < 0 || row >= mapRows)
            return false;
        return mapTileNum[col][row] == TILE_DIRT1;
    }

    private int pickGrass(double grassVar, boolean isSnow) {
        if (isSnow && rand.nextDouble() < 0.85)
            return TILE_GRASS1;
        double v = grassVar + (rand.nextDouble() - 0.5) * 0.15;
        // GRASS1 is the base, but we tighten the thresholds to allow more frequent and
        // larger patches of other grasses
        if (v < 0.28)
            return TILE_GRASS2;
        else if (v < 0.75)
            return TILE_GRASS1;
        else if (v < 0.90)
            return TILE_GRASS3;
        else
            return TILE_GRASS4;
    }

    private boolean canPlaceTree(int col, int row) {
        // World boundaries check for 2x3 tree footprint: [col, col+1] x [row-2, row]
        if (col < 1 || col + 2 >= mapCols || row - 3 < 0 || row + 1 >= mapRows) {
            return false;
        }

        // Not adjacent to river or known water: tree occupies [col, col+1] x [row-2, row].
        // Check this area plus 1 tile border.
        for (int c = col - 1; c <= col + 2; c++) {
            for (int r = row - 3; r <= row + 1; r++) {
                if (riverWater[c][r])
                    return false;
                if (c < col && isWater(c, r))
                    return false;
            }
        }

        // Organic clustering logic to prevent impassable walls and complete obscuring:
        int adjCount = 0;
        boolean hasLeft = false;
        boolean hasUp = false;
        boolean hasUpLeft = false;
        boolean hasUpRight = false;

        if (col > 0 && mapTreeNum[col - 1][row] != 0)
            hasLeft = true;
        if (row > 0 && mapTreeNum[col][row - 1] != 0)
            hasUp = true;
        if (col > 0 && row > 0 && mapTreeNum[col - 1][row - 1] != 0)
            hasUpLeft = true;
        if (col < mapCols - 1 && row > 0 && mapTreeNum[col + 1][row - 1] != 0)
            hasUpRight = true;

        if (hasLeft)
            adjCount++;
        if (hasUp)
            adjCount++;
        if (hasUpLeft)
            adjCount++;
        if (hasUpRight)
            adjCount++;

        // 1. Never allow a tree to have more than 1 already-placed adjacent neighbor
        // (this naturally forms sparse lines/diagonals instead of dense square clumps)
        if (adjCount > 1)
            return false;

        // 2. Strongly discourage horizontal neighbors to prevent impassable horizontal
        // walls
        if (hasLeft) {
            if (rand.nextInt(100) < 85)
                return false; // 85% chance to reject
        }

        // 3. Discourage vertical neighbors so the player isn't completely hidden behind
        // tall tree columns
        if (hasUp) {
            if (rand.nextInt(100) < 60)
                return false; // 60% chance to reject
        }

        // 4. Diagonal neighbors are fine (staggered, easy to pass), but thin them out
        // slightly
        if (hasUpLeft || hasUpRight) {
            if (rand.nextInt(100) < 30)
                return false; // 30% chance to reject
        }

        return true;
    }

    private int pickTree() {
        int r = rand.nextInt(10);
        if (r < 3)
            return TILE_TREE1;
        else if (r < 6)
            return TILE_TREE2;
        else if (r < 8)
            return TILE_TREE3;
        else
            return TILE_TREE4;
    }

    private int pickPine() {
        int r = rand.nextInt(10);
        if (r < 3)
            return TILE_PINE1;
        else if (r < 6)
            return TILE_PINE2;
        else if (r < 8)
            return TILE_PINE3;
        else if (r < 9)
            return TILE_PINE4;
        else
            return TILE_PINE5;
    }

    // =========================================================================
    // POST-PROCESS 7: Scatter rare surface rocks on open grass
    // =========================================================================

    /**
     * Places surface rocks (rock1) on open grass across all biomes.
     * Guarantees:
     * - Kept away from all trees and logs (at least 3 tiles clear in all directions)
     * - Kept away from sand, beaches, and water (at least 2 tiles clear)
     * - Kept away from bushes and other obstacles (at least 1 tile clear)
     * - Kept spaced out from other rocks (at least 4 tiles clear)
     * - Strictly on grass tiles, never on transitions, sand, or water
     */
    private void placeRocks() {
        java.util.Random rockRand = new java.util.Random(seed + 424242L);

        for (int col = 6; col < mapCols - 6; col++) {
            for (int row = 6; row < mapRows - 6; row++) {
                // Must be pure grass ground tile
                int ground = mapTileNum[col][row];
                if (ground < TILE_GRASS1 || ground > TILE_GRASS4)
                    continue;

                // Not in water
                if (isDeepWater[col][row] || isShallowWater[col][row])
                    continue;

                // Not on a transition border tile
                if (mapTransType[col][row] != TransitionRenderer.TRANS_NONE)
                    continue;

                // Tile itself must have no decoration
                if (mapTreeNum[col][row] != 0)
                    continue;

                // Check clearance from trees, logs, and other rocks (radius 3 for trees/logs, radius 5 for rocks)
                boolean blocked = false;
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        int t = mapTreeNum[col + dx][row + dy];
                        if (t == 0)
                            continue;

                        // Other rock within 5 tiles
                        if (t == TILE_ROCK1 || t == TILE_SNOWY_ROCK1) {
                            blocked = true;
                            break;
                        }

                        // Willow trees: large canopy, require 4 tiles clearance in all directions
                        if (Math.abs(dx) <= 4 && Math.abs(dy) <= 4) {
                            if (t >= TILE_WILLOW1 && t <= TILE_WILLOW3) {
                                blocked = true;
                                break;
                            }
                        }

                        // Trees, mangroves and logs within 3 tiles
                        if (Math.abs(dx) <= 3 && Math.abs(dy) <= 3) {
                            if ((t >= 12 && t <= 22) || t == TILE_MANGROVE || t == TILE_LOG1 || t == TILE_LOG2) {
                                blocked = true;
                                break;
                            }
                        }

                        // Bushes within 1 tile
                        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                            if ((t >= 27 && t <= 31) || (t >= 36 && t <= 39)) {
                                blocked = true;
                                break;
                            }
                        }
                    }
                    if (blocked)
                        break;
                }
                if (blocked)
                    continue;

                // Check clearance from sand / beach, mud, and water (at least 2 tiles clear)
                boolean nearSandOrWater = false;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int nc = col + dx, nr = row + dy;
                        int g = mapTileNum[nc][nr];
                        if (g == TILE_SAND1 || g == TILE_SAND2 || g == TILE_MUD || isWater(nc, nr)) {
                            nearSandOrWater = true;
                            break;
                        }
                    }
                    if (nearSandOrWater)
                        break;
                }
                if (nearSandOrWater)
                    continue;

                // Spawn chance: tuned so rocks are clearly less common than bushes (~130-150 worldwide)
                if (rockRand.nextDouble() < 0.005) {
                    boolean isSnow = isSnowBiome[col][row];
                    mapTreeNum[col][row] = isSnow ? TILE_SNOWY_ROCK1 : TILE_ROCK1;
                }
            }
        }
    }

    /**
     * Picks between TILE_SAND1 and TILE_SAND2.
     * Sand2 is an occasional decorative variation within the sand (~8% chance instead of 33-50%).
     */
    private int pickSand() {
        return (rand.nextInt(12) == 0) ? TILE_SAND2 : TILE_SAND1;
    }

    /**
     * Places sandstone rocks, short sand grass, and tall sand grass on dry sand (TILE_SAND1 only).
     * Guarantees:
     * - Strictly on TILE_SAND1, never on TILE_SAND2.
     * - Never on transition tiles (mapTransType must be TRANS_NONE).
     * - Never in water (isDeepWater, isShallowWater, isWater).
     * - Sandstone rocks are placed sparingly with proper spacing from palm trees, water, and other rocks.
     * - Short and tall sand grass appear more frequently, decorating the sand.
     */
    private void placeSandDecorations() {
        java.util.Random sandRand = new java.util.Random(seed + 848484L);

        for (int col = 2; col < mapCols - 2; col++) {
            for (int row = 2; row < mapRows - 2; row++) {
                // Must be pure Sand1, never Sand2
                if (mapTileNum[col][row] != TILE_SAND1) {
                    continue;
                }

                // Not in water
                if (isDeepWater[col][row] || isShallowWater[col][row] || isWater(col, row)) {
                    continue;
                }

                // Not on a transition border tile
                if (mapTransType[col][row] != TransitionRenderer.TRANS_NONE) {
                    continue;
                }

                // Tile itself must have no decoration yet
                if (mapTreeNum[col][row] != 0) {
                    continue;
                }

                // 1. Sandstone Rock (~0.7% chance, placed sparingly)
                if (sandRand.nextDouble() < 0.007) {
                    boolean blocked = false;

                    // Clearance from other rocks (radius 4) and palm trees/logs (radius 2)
                    for (int dx = -4; dx <= 4; dx++) {
                        for (int dy = -4; dy <= 4; dy++) {
                            int nc = col + dx;
                            int nr = row + dy;
                            if (nc < 0 || nc >= mapCols || nr < 0 || nr >= mapRows)
                                continue;

                            int t = mapTreeNum[nc][nr];
                            if (t == 0)
                                continue;

                            // Other rocks within 4 tiles
                            if (t == TILE_SANDSTONE_ROCK || t == TILE_ROCK1 || t == TILE_SNOWY_ROCK1) {
                                blocked = true;
                                break;
                            }

                            // Palm trees / other trees / willows / mangroves / logs within 2 tiles
                            if (Math.abs(dx) <= 2 && Math.abs(dy) <= 2) {
                                if ((t >= 12 && t <= 22) || (t >= TILE_WILLOW1 && t <= TILE_MANGROVE) || t == TILE_LOG1 || t == TILE_LOG2) {
                                    blocked = true;
                                    break;
                                }
                            }
                        }
                        if (blocked) break;
                    }

                    if (!blocked) {
                        // Check clearance from water in cardinal and diagonal directions (no water in 3x3)
                        boolean waterNear = false;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                int nc = col + dx;
                                int nr = row + dy;
                                if (isWater(nc, nr)) {
                                    waterNear = true;
                                    break;
                                }
                            }
                            if (waterNear) break;
                        }

                        // Also verify cliff edge below isn't water
                        if (!waterNear && row + 1 < mapRows && !isWater(col, row + 1)) {
                            mapTreeNum[col][row] = TILE_SANDSTONE_ROCK;
                            continue;
                        }
                    }
                }

                // 2. Sand Grass (~5.5% chance, more frequent than rocks)
                if (sandRand.nextDouble() < 0.055) {
                    // Do not place directly on the south cliff edge into water
                    if (row + 1 < mapRows && isWater(col, row + 1)) {
                        continue;
                    }

                    mapTreeNum[col][row] = sandRand.nextBoolean() ? TILE_SHORT_SAND_GRASS : TILE_TALL_SAND_GRASS;
                }
            }
        }
    }

    private int pickWillow() {
        int r = rand.nextInt(100);
        if (r < 14) {
            return TILE_WILLOW1; // willow_tree1 less frequent
        } else if (r < 57) {
            return TILE_WILLOW2;
        } else {
            return TILE_WILLOW3;
        }
    }

    /**
     * POST-PROCESS 9: Scatter aquatic features in lakes and rivers (Mangroves, Lilipads, Cattails, Submerged Rocks).
     * Strictly avoids ocean and guarantees complete clearance from land so sprites NEVER collide or overlap with shorelines or cliffs.
     */
    private void placeAquaticFeatures() {
        java.util.Random aquaticRand = new java.util.Random(seed + 551122L);

        for (int col = 2; col < mapCols - 2; col++) {
            for (int row = 2; row < mapRows - 2; row++) {
                // Must be pure water
                if (!isWater(col, row))
                    continue;

                // Never in deep water (shallow water only)
                if (isDeepWater[col][row])
                    continue;

                // Never in ocean (must be far enough from ocean edge: dist >= 75)
                double distX = Math.min(col, mapCols - 1 - col);
                double distY = Math.min(row, mapRows - 1 - row);
                double dist = Math.min(distX, distY);
                if (dist < 75)
                    continue;

                boolean isSwamp = isSwampBiome[col][row];
                boolean isSnow = isSnowBiome[col][row];

                // Check pure water clearance in 3x3
                boolean pureWater3x3 = true;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nc = col + dx, nr = row + dy;
                        if (nc < 0 || nc >= mapCols || nr < 0 || nr >= mapRows || !isWater(nc, nr)) {
                            pureWater3x3 = false;
                            break;
                        }
                    }
                    if (!pureWater3x3) break;
                }

                // Check distance to nearest land in 5x5 (~2 tiles away from shore)
                boolean nearShore5x5 = false;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int nc = col + dx, nr = row + dy;
                        if (nc >= 0 && nc < mapCols && nr >= 0 && nr < mapRows && !isWater(nc, nr)) {
                            nearShore5x5 = true;
                            break;
                        }
                    }
                    if (nearShore5x5) break;
                }

                // 1. Mangrove Tree: ONLY in swamp water, growing in water near shore, but STRICTLY clear of land
                // Footprint: [col, col+1] x [row-2, row], plus 1-tile water margin around full canopy & roots
                if (isSwamp && mapTreeNum[col][row] == 0) {
                    boolean pureWaterMangrove = true;
                    for (int c = col - 1; c <= col + 2; c++) {
                        for (int r = row - 3; r <= row + 1; r++) {
                            if (c < 0 || c >= mapCols || r < 0 || r >= mapRows || !isWater(c, r)) {
                                pureWaterMangrove = false;
                                break;
                            }
                        }
                        if (!pureWaterMangrove) break;
                    }

                    // Must be near the swamp shore (within 2-4 tiles) so it grows in riverbanks, not in deep open water
                    boolean nearSwampShore = false;
                    if (pureWaterMangrove) {
                        for (int c = col - 4; c <= col + 5; c++) {
                            for (int r = row - 5; r <= row + 3; r++) {
                                if (c >= 0 && c < mapCols && r >= 0 && r < mapRows && !isWater(c, r)) {
                                    nearSwampShore = true;
                                    break;
                                }
                            }
                            if (nearSwampShore) break;
                        }
                    }

                    if (pureWaterMangrove && nearSwampShore) {
                        boolean tooClose = false;
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dy = -3; dy <= 3; dy++) {
                                int nc = col + dx, nr = row + dy;
                                if (nc >= 0 && nc < mapCols && nr >= 0 && nr < mapRows) {
                                    int t = mapTreeNum[nc][nr];
                                    if (t == TILE_MANGROVE || (t >= 12 && t <= 22) || (t >= TILE_WILLOW1 && t <= TILE_WILLOW3)) {
                                        tooClose = true;
                                        break;
                                    }
                                }
                            }
                            if (tooClose) break;
                        }

                        if (!tooClose && aquaticRand.nextDouble() < 0.045) {
                            mapTreeNum[col][row] = TILE_MANGROVE;
                            continue;
                        }
                    }
                }

                if (mapTreeNum[col][row] != 0)
                    continue;

                // 2. Cattails: in swamp and forest waters, in lakes/rivers, near shore (distance ~2 tiles),
                // but STRICTLY surrounded by pure water in 3x3 so its 2-tile tall reeds NEVER touch cliffs or land
                if (!isSnow && pureWater3x3 && nearShore5x5) {
                    boolean nearMangrove = false;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            int nc = col + dx, nr = row + dy;
                            if (nc >= 0 && nc < mapCols && nr >= 0 && nr < mapRows) {
                                if (mapTreeNum[nc][nr] == TILE_MANGROVE) {
                                    nearMangrove = true;
                                    break;
                                }
                            }
                        }
                        if (nearMangrove) break;
                    }

                    if (!nearMangrove && aquaticRand.nextDouble() < 0.08) {
                        mapTreeNum[col][row] = TILE_CATTAIL;
                        continue;
                    }
                }

                // 3. Lilipads: ONLY in swamp water (lakes/rivers, not ocean)
                // Cardinally surrounded by water
                if (isSwamp && mapTreeNum[col][row] == 0) {
                    boolean cardinalWater = isWater(col, row - 1) && isWater(col, row + 1)
                            && isWater(col - 1, row) && isWater(col + 1, row);
                    if (cardinalWater) {
                        boolean nearMangrove = false;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                int nc = col + dx, nr = row + dy;
                                if (nc >= 0 && nc < mapCols && nr >= 0 && nr < mapRows) {
                                    if (mapTreeNum[nc][nr] == TILE_MANGROVE) {
                                        nearMangrove = true;
                                        break;
                                    }
                                }
                            }
                            if (nearMangrove) break;
                        }

                        if (!nearMangrove) {
                            double lilipadChance = nearShore5x5 ? 0.12 : 0.03;
                            if (aquaticRand.nextDouble() < lilipadChance) {
                                int lr = aquaticRand.nextInt(3);
                                if (lr == 0) mapTreeNum[col][row] = TILE_LILIPAD1;
                                else if (lr == 1) mapTreeNum[col][row] = TILE_LILIPAD2;
                                else mapTreeNum[col][row] = TILE_LILIPAD3;
                                continue;
                            }
                        }
                    }
                }

                // 4. Submerged Rocks: VERY rare, only in forest or swamp lakes/rivers.
                // STRICTLY requires 3x3 pure water so 96px rock sprite never overlaps land/sand
                if (!isSnow && pureWater3x3 && mapTreeNum[col][row] == 0) {
                    if (aquaticRand.nextDouble() < 0.0022) {
                        boolean nearObstacle = false;
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dy = -3; dy <= 3; dy++) {
                                int nc = col + dx, nr = row + dy;
                                if (nc >= 0 && nc < mapCols && nr >= 0 && nr < mapRows) {
                                    int t = mapTreeNum[nc][nr];
                                    if (t == TILE_MANGROVE || t == TILE_WATER_ROCK || t == TILE_ROCK1) {
                                        nearObstacle = true;
                                        break;
                                    }
                                }
                            }
                            if (nearObstacle) break;
                        }
                        if (!nearObstacle) {
                            mapTreeNum[col][row] = TILE_WATER_ROCK;
                        }
                    }
                }
            }
        }
    }
}
