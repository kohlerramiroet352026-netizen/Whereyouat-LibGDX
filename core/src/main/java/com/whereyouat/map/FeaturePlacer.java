package com.whereyouat.map;

import java.util.Random;

/**
 * FeaturePlacer - Responsable de la colocación y validación de decoraciones naturales,
 * árboles, rocas de superficie, vegetación costera y elementos acuáticos.
 */
public class FeaturePlacer {

    /**
     * Valida que ningún árbol, pino o sauce quede flotando en el agua,
     * sobre acantilados sin base de tierra o fuera de los límites del mapa.
     */
    public void validateTrees(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int tree = map.mapTreeNum[col][row];
                if (tree == 0) {
                    continue;
                }

                // 1. Árboles altos (Robles 12-15, Pinos 16-20, Palmeras 21-22, Sauces 48-50)
                if ((tree >= 12 && tree <= 22) || (tree >= MapGenerator.TILE_WILLOW1 && tree <= MapGenerator.TILE_WILLOW3)) {
                    boolean isPalm = (tree == MapGenerator.TILE_PALM1 || tree == MapGenerator.TILE_PALM2);
                    boolean invalid = false;

                    // Límites del mapa para el footprint 2x3: [col, col+1] x [row-2, row]
                    if (col + 1 >= cols || row - 2 < 0 || row >= rows) {
                        invalid = true;
                    }

                    // Chequear que no haya agua en el área 2x3 del árbol
                    if (!invalid) {
                        for (int tileCol = col; tileCol <= col + 1; tileCol++) {
                            for (int tileRow = row - 2; tileRow <= row; tileRow++) {
                                if (map.isWater(tileCol, tileRow)) {
                                    invalid = true;
                                    break;
                                }
                            }
                            if (invalid) break;
                        }
                    }

                    // Chequear que la base del tronco descanse firmemente en suelo sólido
                    if (!invalid) {
                        if (isPalm) {
                            if (!map.isSand(col, row) || !map.isSand(col + 1, row)) {
                                invalid = true;
                            }
                        } else {
                            if (!map.isGrass(col, row) || !map.isGrass(col + 1, row)) {
                                invalid = true;
                            }
                        }
                        // La base no debe caer directamente a un acantilado de agua
                        if (row + 1 < rows && (map.isWater(col, row + 1) || map.isWater(col + 1, row + 1))) {
                            invalid = true;
                        }
                    }

                    if (invalid) {
                        map.mapTreeNum[col][row] = 0;
                    }
                } else {
                    // Vegetación menor (arbustos, pastos altos, troncos caídos)
                    if (map.isWater(col, row)) {
                        map.mapTreeNum[col][row] = 0;
                    } else if (isLandVegetation(tree)) {
                        int radius = isLargeBush(tree) ? 2 : 1;
                        if (map.isNearWater(col, row, radius)) {
                            map.mapTreeNum[col][row] = 0;
                        }
                    }
                }
            }
        }
    }

    /**
     * Distribuye rocas en pasto abierto respetando distancias mínimas con árboles, agua y otras rocas.
     */
    public void placeRocks(MapGenerator map, long seed) {
        Random rockRand = new Random(seed + 424242L);
        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int col = 6; col < cols - 6; col++) {
            for (int row = 6; row < rows - 6; row++) {
                int ground = map.mapTileNum[col][row];
                if (ground < MapGenerator.TILE_GRASS1 || ground > MapGenerator.TILE_GRASS4) {
                    continue;
                }

                if (map.isDeepWater[col][row] || map.isShallowWater[col][row]) {
                    continue;
                }

                if (map.mapTransType[col][row] != TransitionRenderer.TRANS_NONE) {
                    continue;
                }

                if (map.mapTreeNum[col][row] != 0) {
                    continue;
                }

                boolean blocked = false;
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        int obstacle = map.mapTreeNum[col + dx][row + dy];
                        if (obstacle == 0) {
                            continue;
                        }

                        if (obstacle == MapGenerator.TILE_ROCK1 || obstacle == MapGenerator.TILE_SNOWY_ROCK1) {
                            blocked = true;
                            break;
                        }

                        if (Math.abs(dx) <= 4 && Math.abs(dy) <= 4) {
                            if (obstacle >= MapGenerator.TILE_WILLOW1 && obstacle <= MapGenerator.TILE_WILLOW3) {
                                blocked = true;
                                break;
                            }
                        }

                        if (Math.abs(dx) <= 3 && Math.abs(dy) <= 3) {
                            if ((obstacle >= 12 && obstacle <= 22) || obstacle == MapGenerator.TILE_MANGROVE ||
                                obstacle == MapGenerator.TILE_LOG1 || obstacle == MapGenerator.TILE_LOG2) {
                                blocked = true;
                                break;
                            }
                        }

                        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                            if ((obstacle >= 27 && obstacle <= 31) || (obstacle >= 36 && obstacle <= 39)) {
                                blocked = true;
                                break;
                            }
                        }
                    }
                    if (blocked) break;
                }
                if (blocked) continue;

                boolean nearSandOrWater = false;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int nc = col + dx, nr = row + dy;
                        int groundTile = map.mapTileNum[nc][nr];
                        if (groundTile == MapGenerator.TILE_SAND1 || groundTile == MapGenerator.TILE_SAND2 ||
                            groundTile == MapGenerator.TILE_MUD || map.isWater(nc, nr)) {
                            nearSandOrWater = true;
                            break;
                        }
                    }
                    if (nearSandOrWater) break;
                }
                if (nearSandOrWater) continue;

                if (rockRand.nextDouble() < 0.005) {
                    boolean isSnow = map.isSnowBiome[col][row];
                    map.mapTreeNum[col][row] = isSnow ? MapGenerator.TILE_SNOWY_ROCK1 : MapGenerator.TILE_ROCK1;
                }
            }
        }
    }

    /**
     * Coloca rocas de arenisca y vegetación de arena en zonas de playa secas.
     */
    public void placeSandDecorations(MapGenerator map, long seed) {
        Random sandRand = new Random(seed + 848484L);
        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int col = 2; col < cols - 2; col++) {
            for (int row = 2; row < rows - 2; row++) {
                if (map.mapTileNum[col][row] != MapGenerator.TILE_SAND1) {
                    continue;
                }

                if (map.isDeepWater[col][row] || map.isShallowWater[col][row] || map.isWater(col, row)) {
                    continue;
                }

                if (map.mapTransType[col][row] != TransitionRenderer.TRANS_NONE) {
                    continue;
                }

                if (map.mapTreeNum[col][row] != 0) {
                    continue;
                }

                // 1. Roca de arenisca (~0.7% de probabilidad)
                if (sandRand.nextDouble() < 0.007) {
                    boolean blocked = false;
                    for (int dx = -4; dx <= 4; dx++) {
                        for (int dy = -4; dy <= 4; dy++) {
                            int nc = col + dx;
                            int nr = row + dy;
                            if (nc < 0 || nc >= cols || nr < 0 || nr >= rows) {
                                continue;
                            }

                            int obstacle = map.mapTreeNum[nc][nr];
                            if (obstacle == 0) {
                                continue;
                            }

                            if (obstacle == MapGenerator.TILE_SANDSTONE_ROCK || obstacle == MapGenerator.TILE_ROCK1 ||
                                obstacle == MapGenerator.TILE_SNOWY_ROCK1) {
                                blocked = true;
                                break;
                            }

                            if (Math.abs(dx) <= 2 && Math.abs(dy) <= 2) {
                                if ((obstacle >= 12 && obstacle <= 22) ||
                                    (obstacle >= MapGenerator.TILE_WILLOW1 && obstacle <= MapGenerator.TILE_MANGROVE) ||
                                    obstacle == MapGenerator.TILE_LOG1 || obstacle == MapGenerator.TILE_LOG2) {
                                    blocked = true;
                                    break;
                                }
                            }
                        }
                        if (blocked) break;
                    }

                    if (!blocked) {
                        boolean waterNear = false;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                int nc = col + dx;
                                int nr = row + dy;
                                if (map.isWater(nc, nr)) {
                                    waterNear = true;
                                    break;
                                }
                            }
                            if (waterNear) break;
                        }

                        if (!waterNear && row + 1 < rows && !map.isWater(col, row + 1)) {
                            map.mapTreeNum[col][row] = MapGenerator.TILE_SANDSTONE_ROCK;
                            continue;
                        }
                    }
                }

                // 2. Pasto de arena (~5.5% de probabilidad)
                if (sandRand.nextDouble() < 0.055) {
                    if (row + 1 < rows && map.isWater(col, row + 1)) {
                        continue;
                    }
                    map.mapTreeNum[col][row] = sandRand.nextBoolean() ?
                        MapGenerator.TILE_SHORT_SAND_GRASS : MapGenerator.TILE_TALL_SAND_GRASS;
                }
            }
        }
    }

    /**
     * Coloca manglares, nenúfares, espadañas y rocas sumergidas en aguas poco profundas de ríos y lagos.
     */
    public void placeAquaticFeatures(MapGenerator map, long seed) {
        Random aquaticRand = new Random(seed + 551122L);
        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int col = 2; col < cols - 2; col++) {
            for (int row = 2; row < rows - 2; row++) {
                if (!map.isWater(col, row) || map.isDeepWater[col][row]) {
                    continue;
                }

                // Fuera del océano abierto (distancia mínima de los bordes del mapa >= 75)
                double distX = Math.min(col, cols - 1 - col);
                double distY = Math.min(row, rows - 1 - row);
                if (Math.min(distX, distY) < 75) {
                    continue;
                }

                boolean isSwamp = map.isSwampBiome[col][row];
                boolean isSnow = map.isSnowBiome[col][row];

                boolean pureWater3x3 = true;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nc = col + dx, nr = row + dy;
                        if (nc < 0 || nc >= cols || nr < 0 || nr >= rows || !map.isWater(nc, nr)) {
                            pureWater3x3 = false;
                            break;
                        }
                    }
                    if (!pureWater3x3) break;
                }

                boolean nearShore5x5 = false;
                for (int offsetX = -2; offsetX <= 2; offsetX++) {
                    for (int offsetY = -2; offsetY <= 2; offsetY++) {
                        int checkCol = col + offsetX, checkRow = row + offsetY;
                        if (checkCol >= 0 && checkCol < cols && checkRow >= 0 && checkRow < rows && !map.isWater(checkCol, checkRow)) {
                            nearShore5x5 = true;
                            break;
                        }
                    }
                    if (nearShore5x5) break;
                }

                // 1. Manglares (exclusivo de pantano)
                if (isSwamp && map.mapTreeNum[col][row] == 0) {
                    boolean pureWaterMangrove = true;
                    for (int checkCol = col - 1; checkCol <= col + 2; checkCol++) {
                        for (int checkRow = row - 3; checkRow <= row + 1; checkRow++) {
                            if (checkCol < 0 || checkCol >= cols || checkRow < 0 || checkRow >= rows || !map.isWater(checkCol, checkRow)) {
                                pureWaterMangrove = false;
                                break;
                            }
                        }
                        if (!pureWaterMangrove) break;
                    }

                    boolean nearSwampShore = false;
                    if (pureWaterMangrove) {
                        for (int checkCol = col - 4; checkCol <= col + 5; checkCol++) {
                            for (int checkRow = row - 5; checkRow <= row + 3; checkRow++) {
                                if (checkCol >= 0 && checkCol < cols && checkRow >= 0 && checkRow < rows && !map.isWater(checkCol, checkRow)) {
                                    nearSwampShore = true;
                                    break;
                                }
                            }
                            if (nearSwampShore) break;
                        }
                    }

                    if (pureWaterMangrove && nearSwampShore) {
                        boolean tooClose = false;
                        for (int offsetX = -3; offsetX <= 3; offsetX++) {
                            for (int offsetY = -3; offsetY <= 3; offsetY++) {
                                int checkCol = col + offsetX, checkRow = row + offsetY;
                                if (checkCol >= 0 && checkCol < cols && checkRow >= 0 && checkRow < rows) {
                                    int checkTree = map.mapTreeNum[checkCol][checkRow];
                                    if (checkTree == MapGenerator.TILE_MANGROVE || (checkTree >= 12 && checkTree <= 22) ||
                                        (checkTree >= MapGenerator.TILE_WILLOW1 && checkTree <= MapGenerator.TILE_WILLOW3)) {
                                        tooClose = true;
                                        break;
                                    }
                                }
                            }
                            if (tooClose) break;
                        }

                        if (!tooClose && aquaticRand.nextDouble() < 0.045) {
                            map.mapTreeNum[col][row] = MapGenerator.TILE_MANGROVE;
                            continue;
                        }
                    }
                }

                if (map.mapTreeNum[col][row] != 0) {
                    continue;
                }

                // 2. Espadañas (Cattails)
                if (!isSnow && pureWater3x3 && nearShore5x5) {
                    boolean nearMangrove = false;
                    for (int offsetX = -2; offsetX <= 2; offsetX++) {
                        for (int offsetY = -2; offsetY <= 2; offsetY++) {
                            int checkCol = col + offsetX, checkRow = row + offsetY;
                            if (checkCol >= 0 && checkCol < cols && checkRow >= 0 && checkRow < rows) {
                                if (map.mapTreeNum[checkCol][checkRow] == MapGenerator.TILE_MANGROVE) {
                                    nearMangrove = true;
                                    break;
                                }
                            }
                        }
                        if (nearMangrove) break;
                    }

                    if (!nearMangrove && aquaticRand.nextDouble() < 0.08) {
                        map.mapTreeNum[col][row] = MapGenerator.TILE_CATTAIL;
                        continue;
                    }
                }

                // 3. Nenúfares (Lilipads)
                if (isSwamp && map.mapTreeNum[col][row] == 0) {
                    boolean cardinalWater = map.isWater(col, row - 1) && map.isWater(col, row + 1)
                            && map.isWater(col - 1, row) && map.isWater(col + 1, row);
                    if (cardinalWater) {
                        boolean nearMangrove = false;
                        for (int offsetX = -1; offsetX <= 1; offsetX++) {
                            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                                int checkCol = col + offsetX, checkRow = row + offsetY;
                                if (checkCol >= 0 && checkCol < cols && checkRow >= 0 && checkRow < rows) {
                                    if (map.mapTreeNum[checkCol][checkRow] == MapGenerator.TILE_MANGROVE) {
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
                                int lilipadRoll = aquaticRand.nextInt(3);
                                if (lilipadRoll == 0) map.mapTreeNum[col][row] = MapGenerator.TILE_LILIPAD1;
                                else if (lilipadRoll == 1) map.mapTreeNum[col][row] = MapGenerator.TILE_LILIPAD2;
                                else map.mapTreeNum[col][row] = MapGenerator.TILE_LILIPAD3;
                                continue;
                            }
                        }
                    }
                }

                // 4. Rocas sumergidas
                if (!isSnow && pureWater3x3 && map.mapTreeNum[col][row] == 0) {
                    if (aquaticRand.nextDouble() < 0.0022) {
                        boolean nearObstacle = false;
                        for (int offsetX = -3; offsetX <= 3; offsetX++) {
                            for (int offsetY = -3; offsetY <= 3; offsetY++) {
                                int checkCol = col + offsetX, checkRow = row + offsetY;
                                if (checkCol >= 0 && checkCol < cols && checkRow >= 0 && checkRow < rows) {
                                    int obstacle = map.mapTreeNum[checkCol][checkRow];
                                    if (obstacle == MapGenerator.TILE_MANGROVE || obstacle == MapGenerator.TILE_WATER_ROCK ||
                                        obstacle == MapGenerator.TILE_ROCK1) {
                                        nearObstacle = true;
                                        break;
                                    }
                                }
                            }
                            if (nearObstacle) break;
                        }
                        if (!nearObstacle) {
                            map.mapTreeNum[col][row] = MapGenerator.TILE_WATER_ROCK;
                        }
                    }
                }
            }
        }
    }

    private boolean isLandVegetation(int tree) {
        return (tree >= MapGenerator.TILE_BUSH1 && tree <= MapGenerator.TILE_BUSH5_LARGE)
                || (tree >= MapGenerator.TILE_LOG1 && tree <= MapGenerator.TILE_TALL_GRASS2)
                || (tree >= MapGenerator.TILE_SNOWY_BUSH1 && tree <= MapGenerator.TILE_SNOWY_BUSH3_LARGE)
                || (tree >= MapGenerator.TILE_SMALL_BUSH && tree <= MapGenerator.TILE_SHORTER_GRASS);
    }

    private boolean isLargeBush(int tree) {
        return tree == MapGenerator.TILE_BUSH4_LARGE || tree == MapGenerator.TILE_BUSH5_LARGE ||
               tree == MapGenerator.TILE_SNOWY_BUSH3_LARGE;
    }
}

