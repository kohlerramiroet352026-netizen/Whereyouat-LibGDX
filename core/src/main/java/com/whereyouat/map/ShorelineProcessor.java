package com.whereyouat.map;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * ShorelineProcessor - Responsable de normalizar costas, playas, transiciones de terreno
 * y suavizado de bordes para garantizar que el mundo procedimental no tenga artefactos visuales.
 */
public class ShorelineProcessor {

    /**
     * Asegura un ancho uniforme de arena (playas) mediante BFS desde el agua,
     * evitando líneas finas de 1 solo tile y convirtiendo arena en lodo en el bioma pantano.
     */
    public void normalizeSandWidth(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;
        int totalTiles = cols * rows;

        int[] distFromWater = new int[totalTiles];
        Arrays.fill(distFromWater, Integer.MAX_VALUE);

        ArrayDeque<Integer> queue = new ArrayDeque<>();

        // 1. Sembrar el BFS desde cada tile de agua
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (map.isDeepWater[col][row] || map.isShallowWater[col][row] || map.isWater(col, row)) {
                    int index = col * rows + row;
                    distFromWater[index] = 0;
                    queue.add(index);
                }
            }
        }

        // Direcciones cardinales (Este, Oeste, Sur, Norte)
        int[] deltaCol = { 0, 0, 1, -1 };
        int[] deltaRow = { 1, -1, 0, 0 };

        while (!queue.isEmpty()) {
            int currentIdx = queue.poll();
            int col = currentIdx / rows;
            int row = currentIdx % rows;
            int dist = distFromWater[currentIdx];

            for (int i = 0; i < 4; i++) {
                int nextCol = col + deltaCol[i];
                int nextRow = row + deltaRow[i];

                if (nextCol < 0 || nextCol >= cols || nextRow < 0 || nextRow >= rows) {
                    continue;
                }

                int nextIdx = nextCol * rows + nextRow;
                if (distFromWater[nextIdx] != Integer.MAX_VALUE) {
                    continue;
                }

                distFromWater[nextIdx] = dist + 1;
                queue.add(nextIdx);
            }
        }

        // Paso 1: Limitar el ancho máximo de la arena y convertir arena en pantano a lodo
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (!map.isSand(col, row)) {
                    continue;
                }
                if (map.isSwampBiome[col][row]) {
                    map.mapTileNum[col][row] = MapGenerator.TILE_MUD;
                    map.mapTreeNum[col][row] = 0;
                    continue;
                }
                int dist = distFromWater[col * rows + row];
                int maxWidth = map.isBeachBiome[col][row] ? 30 : MapGenerator.MAX_SAND_WIDTH;
                if (dist > maxWidth) {
                    // Demasiado lejos del agua: reemplazar por césped
                    map.mapTileNum[col][row] = map.pickGrass(map.grassVarCache[col][row], map.isSnowBiome[col][row]);
                    map.mapTreeNum[col][row] = 0;
                }
            }
        }

        // Paso 2: Identificar líneas de costa reales y limpiar fragmentos de arena aislados
        boolean[][] isShoreline = new boolean[cols][rows];
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (distFromWater[col * rows + row] != 1) {
                    continue;
                }

                if (map.isSwampBiome[col][row]) {
                    if (map.mapTileNum[col][row] == MapGenerator.TILE_MUD) {
                        isShoreline[col][row] = true;
                    }
                    continue;
                }

                if (!map.isSand(col, row)) {
                    continue;
                }

                int sandNeighbours = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int neighborCol = col + dx;
                        int neighborRow = row + dy;
                        if (neighborCol >= 0 && neighborCol < cols && neighborRow >= 0 && neighborRow < rows) {
                            if (map.isSand(neighborCol, neighborRow)) {
                                sandNeighbours++;
                            }
                        }
                    }
                }

                // Si es un pixel de arena completamente aislado en orilla de pasto:
                if (sandNeighbours == 0 && !map.isBeachBiome[col][row] && !map.riverSand[col][row]) {
                    map.mapTileNum[col][row] = map.pickGrass(map.grassVarCache[col][row], map.isSnowBiome[col][row]);
                    map.mapTreeNum[col][row] = 0;
                } else {
                    isShoreline[col][row] = true;
                }
            }
        }

        // Paso 3: Asegurar ancho mínimo de arena o lodo
        ArrayDeque<Integer> expandQueue = new ArrayDeque<>();
        boolean[] inExpandQueue = new boolean[totalTiles];

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (isShoreline[col][row]) {
                    int index = col * rows + row;
                    expandQueue.add(index);
                    inExpandQueue[index] = true;
                }
            }
        }

        while (!expandQueue.isEmpty()) {
            int currentIdx = expandQueue.poll();
            int col = currentIdx / rows;
            int row = currentIdx % rows;

            for (int i = 0; i < 4; i++) {
                int nextCol = col + deltaCol[i];
                int nextRow = row + deltaRow[i];

                if (nextCol < 0 || nextCol >= cols || nextRow < 0 || nextRow >= rows) {
                    continue;
                }

                int nextIdx = nextCol * rows + nextRow;
                if (inExpandQueue[nextIdx]) {
                    continue;
                }

                int dist = distFromWater[nextIdx];
                int targetWidth = map.isSwampBiome[nextCol][nextRow] ? 2 : MapGenerator.MIN_SAND_WIDTH;
                if (dist >= 1 && dist <= targetWidth && !map.isWater(nextCol, nextRow)) {
                    inExpandQueue[nextIdx] = true;
                    if (map.isGrass(nextCol, nextRow)) {
                        if (map.isSwampBiome[nextCol][nextRow]) {
                            map.mapTileNum[nextCol][nextRow] = MapGenerator.TILE_MUD;
                            map.mapTreeNum[nextCol][nextRow] = 0;
                            expandQueue.add(nextIdx);
                        } else {
                            map.mapTileNum[nextCol][nextRow] = map.pickSand();
                            map.mapTreeNum[nextCol][nextRow] = 0;
                            expandQueue.add(nextIdx);
                        }
                    } else if (map.isSand(nextCol, nextRow) || (map.isSwampBiome[nextCol][nextRow] && map.mapTileNum[nextCol][nextRow] == MapGenerator.TILE_MUD)) {
                        expandQueue.add(nextIdx);
                    }
                }
            }
        }
    }

    /**
     * Elimina tiles aislados mediante pasadas de suavizado celular en penínsulas y bordes de costa.
     */
    public void removeIsolatedTiles(MapGenerator map) {
        cleanShoreline(map);

        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int pass = 0; pass < 3; pass++) {
            for (int col = 0; col < cols; col++) {
                for (int row = 0; row < rows; row++) {
                    int tileType = map.mapTileNum[col][row];
                    boolean isSand = (tileType == MapGenerator.TILE_SAND1 || tileType == MapGenerator.TILE_SAND2);
                    boolean isGrass = (tileType >= MapGenerator.TILE_GRASS1 && tileType <= MapGenerator.TILE_GRASS4);
                    boolean isDirt = (tileType == MapGenerator.TILE_DIRT1);

                    if (isSand) {
                        int grassNeighbors = countCardinalGrass(map, col, row);
                        int sandNeighbors = countCardinalSand(map, col, row);
                        int waterNeighbors = countCardinalWater(map, col, row);
                        boolean nearWater = waterNeighbors > 0;

                        if (!nearWater && (grassNeighbors >= 3 || sandNeighbors <= 1)) {
                            map.mapTileNum[col][row] = map.pickGrass(map.grassVarCache[col][row], map.isSnowBiome[col][row]);
                            map.mapTreeNum[col][row] = 0;
                        } else if (nearWater && sandNeighbors == 0 && !map.isBeachBiome[col][row] && !map.riverSand[col][row]) {
                            map.mapTileNum[col][row] = map.pickGrass(map.grassVarCache[col][row], map.isSnowBiome[col][row]);
                            map.mapTreeNum[col][row] = 0;
                        } else if (waterNeighbors >= 3 && grassNeighbors == 0 && sandNeighbors <= 1) {
                            map.mapTileNum[col][row] = MapGenerator.TILE_WATER0;
                            map.isShallowWater[col][row] = true;
                            map.isDeepWater[col][row] = false;
                            map.mapTreeNum[col][row] = 0;
                        }
                    } else if (isGrass) {
                        int sandNeighbors = countCardinalSand(map, col, row);
                        int grassNeighbors = countCardinalGrass(map, col, row);
                        int dirtNeighbors = countCardinalDirt(map, col, row);

                        if (!map.isSwampBiome[col][row] && (sandNeighbors >= 3 || (grassNeighbors == 0 && sandNeighbors > 0))) {
                            map.mapTileNum[col][row] = map.pickSand();
                            map.mapTreeNum[col][row] = 0;
                        } else if (dirtNeighbors >= 3 || (grassNeighbors == 0 && dirtNeighbors > 0)) {
                            map.mapTileNum[col][row] = MapGenerator.TILE_DIRT1;
                            map.mapTreeNum[col][row] = 0;
                        }
                    } else if (isDirt) {
                        int grassNeighbors = countCardinalGrass(map, col, row);
                        int dirtNeighbors = countCardinalDirt(map, col, row);

                        if (grassNeighbors >= 3 || dirtNeighbors <= 1) {
                            map.mapTileNum[col][row] = map.pickGrass(map.grassVarCache[col][row], map.isSnowBiome[col][row]);
                            map.mapTreeNum[col][row] = 0;
                        }
                    }
                }
            }
        }

        cleanShoreline(map);
    }

    /**
     * Limpia la línea de costa para asegurar que los bordes con agua sean limpios y continuos.
     */
    public void cleanShoreline(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;
        boolean changed = true;
        int pass = 0;

        while (changed && pass++ < 4) {
            changed = false;
            for (int col = 0; col < cols; col++) {
                for (int row = 0; row < rows; row++) {
                    if (!map.isGrass(col, row)) {
                        continue;
                    }

                    int waterNeighbors = countCardinalWater(map, col, row);
                    if (waterNeighbors == 0) {
                        continue;
                    }

                    int sandNeighbors = countCardinalSand(map, col, row);
                    int grassNeighbors = countCardinalGrass(map, col, row);

                    if (waterNeighbors >= 3 || (waterNeighbors >= 2 && grassNeighbors == 0 && sandNeighbors == 0)) {
                        map.mapTileNum[col][row] = MapGenerator.TILE_WATER0;
                        map.isShallowWater[col][row] = true;
                        map.isDeepWater[col][row] = false;
                        map.mapTreeNum[col][row] = 0;
                        changed = true;
                        continue;
                    }

                    if (!map.isSwampBiome[col][row] && map.isBeachBiome[col][row] && waterNeighbors > 0) {
                        map.mapTileNum[col][row] = map.pickSand();
                        map.mapTreeNum[col][row] = 0;
                        changed = true;
                        continue;
                    }

                    if (!map.isSwampBiome[col][row] && sandNeighbors >= 2 && waterNeighbors > 0) {
                        map.mapTileNum[col][row] = map.pickSand();
                        map.mapTreeNum[col][row] = 0;
                        changed = true;
                        continue;
                    }

                    if (map.isSwampBiome[col][row] && waterNeighbors > 0) {
                        int mudNeighbors = countCardinalMud(map, col, row);
                        if (mudNeighbors >= 2) {
                            map.mapTileNum[col][row] = MapGenerator.TILE_MUD;
                            map.mapTreeNum[col][row] = 0;
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Convierte a pasto los bloques de tierra que toquen arena de forma cardinal.
     */
    public void fixDirtPlacement(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (map.mapTileNum[col][row] != MapGenerator.TILE_DIRT1) {
                    continue;
                }
                if (map.isSand(col, row - 1) || map.isSand(col, row + 1) ||
                    map.isSand(col + 1, row) || map.isSand(col - 1, row)) {
                    map.mapTileNum[col][row] = map.pickGrass(map.grassVarCache[col][row], map.isSnowBiome[col][row]);
                }
            }
        }
    }

    /**
     * Computa las transiciones gráficas (bordes y esquinas de autotiling) entre césped y otros terrenos.
     */
    public void computeTransitions(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (!map.isTransitionTarget(col, row)) {
                    continue;
                }

                boolean north     = map.isGrass(col, row - 1);
                boolean south     = map.isGrass(col, row + 1);
                boolean east      = map.isGrass(col + 1, row);
                boolean west      = map.isGrass(col - 1, row);
                boolean northEast = map.isGrass(col + 1, row - 1);
                boolean southEast = map.isGrass(col + 1, row + 1);
                boolean southWest = map.isGrass(col - 1, row + 1);
                boolean northWest = map.isGrass(col - 1, row - 1);

                if (!north && !south && !east && !west && !northEast && !southEast && !southWest && !northWest) {
                    continue;
                }

                TransitionRenderer.TransInfo info = TransitionRenderer.classify(
                    north, south, east, west, northEast, southEast, southWest, northWest
                );

                map.mapTransType[col][row] = info.type;
                map.mapTransRot[col][row] = info.rot;

                if (info.type != TransitionRenderer.TRANS_NONE) {
                    map.mapTreeNum[col][row] = 0;
                }
            }
        }
    }

    /**
     * Calcula el degradado visual de agua profunda mediante BFS.
     */
    public void calculateDeepWaterBlend(MapGenerator map) {
        int cols = map.mapCols;
        int rows = map.mapRows;
        int totalTiles = cols * rows;

        int[] distFromNonDeep = new int[totalTiles];
        Arrays.fill(distFromNonDeep, Integer.MAX_VALUE);

        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (!map.isDeepWater[col][row]) {
                    int index = col * rows + row;
                    distFromNonDeep[index] = 0;
                    queue.add(index);
                    map.deepWaterBlend[col][row] = 0f;
                }
            }
        }

        int[] deltaCol = { 0, 0, 1, -1 };
        int[] deltaRow = { 1, -1, 0, 0 };

        while (!queue.isEmpty()) {
            int currentIdx = queue.poll();
            int col = currentIdx / rows;
            int row = currentIdx % rows;
            int dist = distFromNonDeep[currentIdx];

            for (int i = 0; i < 4; i++) {
                int nextCol = col + deltaCol[i];
                int nextRow = row + deltaRow[i];

                if (nextCol < 0 || nextCol >= cols || nextRow < 0 || nextRow >= rows) {
                    continue;
                }

                int nextIdx = nextCol * rows + nextRow;
                if (distFromNonDeep[nextIdx] != Integer.MAX_VALUE) {
                    continue;
                }

                distFromNonDeep[nextIdx] = dist + 1;
                queue.add(nextIdx);
            }
        }

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                if (map.isDeepWater[col][row]) {
                    int dist = distFromNonDeep[col * rows + row];
                    float blend = (float) dist / 8.0f;
                    if (blend > 1.0f) {
                        blend = 1.0f;
                    }
                    map.deepWaterBlend[col][row] = blend;
                }
            }
        }
    }

    // --- Métodos de conteo cardinal de vecinos ---

    private int countCardinalGrass(MapGenerator map, int col, int row) {
        return (map.isGrass(col, row - 1) ? 1 : 0) +
               (map.isGrass(col, row + 1) ? 1 : 0) +
               (map.isGrass(col - 1, row) ? 1 : 0) +
               (map.isGrass(col + 1, row) ? 1 : 0);
    }

    private int countCardinalSand(MapGenerator map, int col, int row) {
        return (map.isSand(col, row - 1) ? 1 : 0) +
               (map.isSand(col, row + 1) ? 1 : 0) +
               (map.isSand(col - 1, row) ? 1 : 0) +
               (map.isSand(col + 1, row) ? 1 : 0);
    }

    private int countCardinalWater(MapGenerator map, int col, int row) {
        return (map.isWater(col, row - 1) ? 1 : 0) +
               (map.isWater(col, row + 1) ? 1 : 0) +
               (map.isWater(col - 1, row) ? 1 : 0) +
               (map.isWater(col + 1, row) ? 1 : 0);
    }

    private int countCardinalDirt(MapGenerator map, int col, int row) {
        return (map.isDirt(col, row - 1) ? 1 : 0) +
               (map.isDirt(col, row + 1) ? 1 : 0) +
               (map.isDirt(col - 1, row) ? 1 : 0) +
               (map.isDirt(col + 1, row) ? 1 : 0);
    }

    private int countCardinalMud(MapGenerator map, int col, int row) {
        return (map.isMud(col, row - 1) ? 1 : 0) +
               (map.isMud(col, row + 1) ? 1 : 0) +
               (map.isMud(col - 1, row) ? 1 : 0) +
               (map.isMud(col + 1, row) ? 1 : 0);
    }
}

