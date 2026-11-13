package com.whereyouat.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.effects.SnowParticleSystem;
import com.whereyouat.lighting.DayNightCycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TileManager {
    public static final int ORIGINAL_TILE_SIZE = 16;
    public static final int SCALE = 3;
    public static final int TILE_SIZE = ORIGINAL_TILE_SIZE * SCALE;

    public static final int TRUNK_HIT_OFFSET_X = 40;
    public static final int TRUNK_HIT_OFFSET_Y = 32;
    public static final int TRUNK_HIT_W = 16;
    public static final int TRUNK_HIT_H = 16;

    public static class BushSway {
        public float topShift = 0f;
        public float velocity = 0f;
        public int cooldown = 0;
    }

    private final TileTintPipeline tintPipeline = new TileTintPipeline();
    private final TileShadowRenderer shadowRenderer = new TileShadowRenderer();
    private final FoliageSwayManager foliageSway = new FoliageSwayManager();
    private final TileDecorationRenderer decorationRenderer = new TileDecorationRenderer();

    public final Map<Long, BushSway> swayingBushes = foliageSway.swayingBushes;

    private final TextureRegion[] tiles = new TextureRegion[65];
    private final Pixmap[] tilePixmaps = new Pixmap[65];
    private final List<Texture> loadedTextures = new ArrayList<>();

    private final TextureRegion[] illuminatedTreeCache = new TextureRegion[96];
    private float lastIllumSunDirX = -999f;
    private float lastIllumSunDirY = -999f;
    private float lastIllumIntensity = -999f;
    private boolean lastIllumNight = false;

    public final TransitionRenderer transRenderer = new TransitionRenderer();

    private float waterAnimTimer = 0f;
    private int waterFrame = 0;
    private static final float WATER_ANIM_SPEED = 18f / 60f;
    private static final int WATER_FRAMES = 4;

    private SnowParticleSystem snowPS;

    public TileManager() {
        loadTiles();
        tintPipeline.buildTintCaches(tiles, tilePixmaps);
        tintPipeline.buildFoamImages();
    }

    public void setSnowPS(SnowParticleSystem ps) {
        this.snowPS = ps;
    }

    public static Pixmap tintGrass(Pixmap src, float blend) {
        return TileTintPipeline.tintGrass(src, blend);
    }

    public static Pixmap tintSwampGrass(Pixmap src, float blend) {
        return TileTintPipeline.tintSwampGrass(src, blend);
    }

    public static Pixmap tintWater(Pixmap src, float snowBlend, float deepBlend) {
        return TileTintPipeline.tintWater(src, snowBlend, deepBlend);
    }

    public static Pixmap tintSwampWater(Pixmap src, float swampBlend, float deepBlend) {
        return TileTintPipeline.tintSwampWater(src, swampBlend, deepBlend);
    }

    private Texture loadTex(String path) {
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        loadedTextures.add(tex);
        return tex;
    }

    private TextureRegion loadTileWithPixmap(int idx, String path) {
        Pixmap pm = new Pixmap(Gdx.files.internal(path));
        tilePixmaps[idx] = pm;
        Texture tex = new Texture(pm);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        loadedTextures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        tiles[idx] = tr;
        return tr;
    }

    private void loadTiles() {
        loadTileWithPixmap(0, "tiles/grass1.png");
        loadTileWithPixmap(1, "tiles/grass2.png");
        loadTileWithPixmap(2, "tiles/grass3.png");
        loadTileWithPixmap(3, "tiles/grass4.png");
        loadTileWithPixmap(4, "tiles/stone.png");
        loadTileWithPixmap(5, "tiles/water0.png");
        loadTileWithPixmap(6, "tiles/water1.png");
        loadTileWithPixmap(7, "tiles/water2.png");
        loadTileWithPixmap(8, "tiles/water3.png");
        loadTileWithPixmap(9, "tiles/sand.png");
        loadTileWithPixmap(10, "tiles/sand2.png");
        loadTileWithPixmap(11, "tiles/dirt1.png");
        loadTileWithPixmap(12, "tiles/tree1.png");
        loadTileWithPixmap(13, "tiles/tree2.png");
        loadTileWithPixmap(14, "tiles/tree3.png");
        loadTileWithPixmap(15, "tiles/tree4.png");
        loadTileWithPixmap(16, "tiles/pine1.png");
        loadTileWithPixmap(17, "tiles/pine2.png");
        loadTileWithPixmap(18, "tiles/pine3.png");
        loadTileWithPixmap(19, "tiles/pine4.png");
        loadTileWithPixmap(20, "tiles/pine5.png");
        loadTileWithPixmap(21, "tiles/palm1.png");
        loadTileWithPixmap(22, "tiles/palm2.png");
        loadTileWithPixmap(23, "tiles/grass_border.png");
        loadTileWithPixmap(24, "tiles/sand_border.png");
        loadTileWithPixmap(25, "tiles/sandgrass_border.png");

        if (tilePixmaps[25] != null) {
            Pixmap src = tilePixmaps[25];
            int w = src.getWidth();
            int h = src.getHeight();
            Pixmap flipped = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    flipped.drawPixel(x, y, src.getPixel(w - 1 - x, y));
                }
            }
            tilePixmaps[26] = flipped;
            Texture flippedTex = new Texture(flipped);
            flippedTex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            loadedTextures.add(flippedTex);
            TextureRegion flippedTr = new TextureRegion(flippedTex);
            flippedTr.flip(false, true);
            tiles[26] = flippedTr;
        }

        loadTileWithPixmap(27, "tiles/bush1.png");
        loadTileWithPixmap(28, "tiles/bush2.png");
        loadTileWithPixmap(29, "tiles/bush3.png");
        loadTileWithPixmap(30, "tiles/bush4_large.png");
        loadTileWithPixmap(31, "tiles/bush5_large.png");
        loadTileWithPixmap(32, "tiles/log1.png");
        loadTileWithPixmap(33, "tiles/log2.png");
        loadTileWithPixmap(34, "tiles/tall_grass1.png");
        loadTileWithPixmap(35, "tiles/tall_grass2.png");
        loadTileWithPixmap(36, "tiles/snowy_bush1.png");
        loadTileWithPixmap(37, "tiles/snowy_bush2.png");
        loadTileWithPixmap(38, "tiles/snowy_bush3_large.png");
        loadTileWithPixmap(39, "tiles/small_bush.png");
        loadTileWithPixmap(40, "tiles/short_grass.png");
        loadTileWithPixmap(41, "tiles/shorter_grass.png");
        loadTileWithPixmap(42, "tiles/rock1.png");
        loadTileWithPixmap(43, "tiles/snowy_rock1.png");
        loadTileWithPixmap(44, "tiles/Sandstone_rock.png");
        loadTileWithPixmap(45, "tiles/Short_sand_grass.png");
        loadTileWithPixmap(46, "tiles/Tall_sand_grass.png");
        loadTileWithPixmap(47, "tiles/mud.png");
        loadTileWithPixmap(48, "tiles/willow_tree1.png");
        loadTileWithPixmap(49, "tiles/willow_tree2.png");
        loadTileWithPixmap(50, "tiles/willow_tree3.png");
        loadTileWithPixmap(51, "tiles/mangrove_tree.png");
        loadTileWithPixmap(52, "tiles/lilipad1.png");
        loadTileWithPixmap(53, "tiles/lilipad2.png");
        loadTileWithPixmap(54, "tiles/lilipad3.png");
        loadTileWithPixmap(55, "tiles/cattail.png");
        loadTileWithPixmap(56, "tiles/rock1.png");
        loadTileWithPixmap(57, "tiles/mud_border.png");
        loadTileWithPixmap(58, "tiles/wet_mud_border.png");

        Texture sideTex = loadTex("tiles/transition_grass_sides.png");
        Texture outCTex = loadTex("tiles/transition_grass_out_corner.png");
        Texture inCTex  = loadTex("tiles/transition_grass_in_corner.png");
        transRenderer.load(sideTex, outCTex, inCTex, this);
    }

    private TextureRegion pixmapToRegion(Pixmap pm) {
        Texture tex = new Texture(pm);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        loadedTextures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        return tr;
    }

    public TextureRegion getTileRegion(int idx) {
        return (idx >= 0 && idx < tiles.length) ? tiles[idx] : null;
    }

    public TextureRegion getFoamRegion() {
        return tintPipeline.getFoamRegion(0);
    }

    public TextureRegion getFoamRegion(int idx) {
        return tintPipeline.getFoamRegion(idx);
    }

    public TextureRegion getSubmergedSprite(int treeIdx, int sinkTexels, boolean isSwamp) {
        return tintPipeline.getSubmergedSprite(treeIdx, sinkTexels, isSwamp, tiles, tilePixmaps);
    }

    public int getWaterFrame() {
        return waterFrame;
    }

    public int getWaterLevel(MapGenerator map, int col, int row) {
        if (col < 0 || col >= map.mapCols || row < 0 || row >= map.mapRows) return 0;
        if (map.isDeepWater != null && map.isDeepWater[col][row]) return 2;
        if (map.isShallowWater != null && map.isShallowWater[col][row]) return 1;
        if (map.mapTileNum != null && map.mapTileNum[col][row] >= 5 && map.mapTileNum[col][row] <= 8) return 1;
        return 0;
    }

    public boolean hasTree(MapGenerator map, int col, int row) {
        if (col < 0 || col >= map.mapCols || row < 0 || row >= map.mapRows) return false;
        int t = map.mapTreeNum[col][row];
        return t >= 12 && t <= 22;
    }

    public boolean isFoliageSwaying(int col, int row) {
        return foliageSway.isFoliageSwaying(col, row);
    }

    public boolean isWindGustActive() {
        return foliageSway.isWindGustActive();
    }

    public float getWindGustDirX() {
        return foliageSway.getWindGustDirX();
    }

    public void update(float delta, MapGenerator map, DayNightCycle cycle) {
        waterAnimTimer += delta;
        if (waterAnimTimer >= WATER_ANIM_SPEED) {
            waterAnimTimer = 0f;
            waterFrame = (waterFrame + 1) % WATER_FRAMES;
        }
        foliageSway.update(delta, map, snowPS);
        updateTreeIllumination(cycle);
    }

    private void updateTreeIllumination(DayNightCycle cycle) {
        if (cycle == null) return;
        float sunDirX = cycle.getSunDirX();
        float sunDirY = cycle.getSunDirY();
        float intensity = cycle.getAmbientIntensity();
        boolean isNight = cycle.isNight();

        if (Math.abs(sunDirX - lastIllumSunDirX) < 0.015f && Math.abs(sunDirY - lastIllumSunDirY) < 0.015f
                && Math.abs(intensity - lastIllumIntensity) < 0.015f && isNight == lastIllumNight) {
            return;
        }

        lastIllumSunDirX = sunDirX;
        lastIllumSunDirY = sunDirY;
        lastIllumIntensity = intensity;
        lastIllumNight = isNight;

        float dirX, dirY, highlightRed, highlightGreen, highlightBlue, highlightPower;
        if (!isNight) {
            dirX = sunDirX;
            dirY = sunDirY;
            highlightRed = Math.min(255f, cycle.getAmbientR() * 255f * 1.20f);
            highlightGreen = Math.min(255f, cycle.getAmbientG() * 255f * 1.08f);
            highlightBlue = Math.min(255f, cycle.getAmbientB() * 255f * 0.85f);
            highlightPower = Math.min(0.22f, intensity * 0.25f);
        } else {
            dirX = -cycle.getShadowDirX();
            dirY = -0.45f;
            highlightRed = 160f;
            highlightGreen = 195f;
            highlightBlue = 255f;
            highlightPower = 0.24f;
        }

        int[] illuminatedIndices = { 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 48, 49, 50, 51 };
        for (int treeIdx : illuminatedIndices) {
            Pixmap sourcePixmap = tilePixmaps[treeIdx];
            if (sourcePixmap == null) continue;
            int width = sourcePixmap.getWidth(), height = sourcePixmap.getHeight();
            Pixmap illuminatedPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            float centerPointX = width / 2.0f, centerPointY = height * 0.35f;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgba = sourcePixmap.getPixel(x, y);
                    int alpha = rgba & 0xFF;
                    if (alpha <= 20) {
                        illuminatedPixmap.drawPixel(x, y, 0);
                        continue;
                    }
                    int red = (rgba >>> 24) & 0xFF, green = (rgba >>> 16) & 0xFF, blue = (rgba >>> 8) & 0xFF;
                    if (y < height * 0.72f) {
                        float normalX = (x - centerPointX) / (width * 0.45f), normalY = (y - centerPointY) / (height * 0.35f);
                        float dist = (float) Math.sqrt(normalX * normalX + normalY * normalY);
                        if (dist > 0.05f) {
                            normalX /= dist;
                            normalY /= dist;
                        }
                        float dot = normalX * dirX + normalY * dirY;
                        if (dot > 0.05f) {
                            float factor = (float) Math.pow(dot, 1.15) * highlightPower;
                            int illuminatedRed = Math.min(255, (int) (red + (highlightRed - red * 0.15f) * factor));
                            int illuminatedGreen = Math.min(255, (int) (green + (highlightGreen - green * 0.15f) * factor));
                            int illuminatedBlue = Math.min(255, (int) (blue + (highlightBlue - blue * 0.15f) * factor));
                            illuminatedPixmap.drawPixel(x, y, (illuminatedRed << 24) | (illuminatedGreen << 16) | (illuminatedBlue << 8) | alpha);
                        } else {
                            illuminatedPixmap.drawPixel(x, y, rgba);
                        }
                    } else {
                        illuminatedPixmap.drawPixel(x, y, rgba);
                    }
                }
            }
            illuminatedTreeCache[treeIdx] = pixmapToRegion(illuminatedPixmap);
            illuminatedPixmap.dispose();
        }
    }

    public void drawGround(SpriteBatch batch, MapGenerator map, float minX, float minY, float maxX, float maxY) {
        int startCol = Math.max(0, (int) (minX / TILE_SIZE));
        int startRow = Math.max(0, (int) (minY / TILE_SIZE));
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 2);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 2);

        for (int worldRow = startRow; worldRow <= endRow; worldRow++) {
            for (int worldCol = startCol; worldCol <= endCol; worldCol++) {
                float screenX = worldCol * TILE_SIZE, screenY = worldRow * TILE_SIZE;
                int tileIndex = map.mapTileNum[worldCol][worldRow];
                boolean isWater = (tileIndex >= 5 && tileIndex <= 8);
                if (isWater) tileIndex = 5 + waterFrame;

                float snowBlendValue = map.snowBlend[worldCol][worldRow];
                boolean hasSnow = (snowBlendValue > 0.01f && !map.isDeepWater[worldCol][worldRow] && !map.isShallowWater[worldCol][worldRow]);
                float swampBlendValue = (map.swampBlend != null) ? map.swampBlend[worldCol][worldRow] : 0f;
                boolean hasSwamp = (swampBlendValue > 0.01f && !map.isDeepWater[worldCol][worldRow] && !map.isShallowWater[worldCol][worldRow]);

                batch.setColor(1f, 1f, 1f, 1f);

                if (isWater) {
                    if (swampBlendValue > 0.01f) {
                        int swampBlendStep = Math.min(10, (int) (swampBlendValue * 10));
                        int deepWaterBlendStep = Math.min(10, (int) (map.deepWaterBlend[worldCol][worldRow] * 10));
                        TextureRegion waterRegion = tintPipeline.getTintedSwampWaterRegion(waterFrame, swampBlendStep, deepWaterBlendStep);
                        if (waterRegion != null) batch.draw(waterRegion, screenX, screenY, TILE_SIZE, TILE_SIZE);
                        else if (tiles[tileIndex] != null) batch.draw(tiles[tileIndex], screenX, screenY, TILE_SIZE, TILE_SIZE);
                    } else {
                        int snowBlendStep = Math.min(10, (int) (snowBlendValue * 10));
                        int deepWaterBlendStep = Math.min(10, (int) (map.deepWaterBlend[worldCol][worldRow] * 10));
                        TextureRegion waterRegion = tintPipeline.getTintedWaterRegion(waterFrame, snowBlendStep, deepWaterBlendStep);
                        if (waterRegion != null) batch.draw(waterRegion, screenX, screenY, TILE_SIZE, TILE_SIZE);
                        else if (tiles[tileIndex] != null) batch.draw(tiles[tileIndex], screenX, screenY, TILE_SIZE, TILE_SIZE);
                    }
                } else if (hasSnow && tileIndex >= 0 && tileIndex <= 3) {
                    int step = Math.min(10, (int) (snowBlendValue * 10));
                    TextureRegion grassRegion = tintPipeline.getSnowGrassRegion(tileIndex, step);
                    if (grassRegion != null) batch.draw(grassRegion, screenX, screenY, TILE_SIZE, TILE_SIZE);
                    else if (tiles[tileIndex] != null) batch.draw(tiles[tileIndex], screenX, screenY, TILE_SIZE, TILE_SIZE);
                } else if (hasSwamp && tileIndex >= 0 && tileIndex <= 3) {
                    int step = Math.min(10, (int) (swampBlendValue * 10));
                    TextureRegion grassRegion = tintPipeline.getSwampGrassRegion(tileIndex, step);
                    if (grassRegion != null) batch.draw(grassRegion, screenX, screenY, TILE_SIZE, TILE_SIZE);
                    else if (tiles[tileIndex] != null) batch.draw(tiles[tileIndex], screenX, screenY, TILE_SIZE, TILE_SIZE);
                } else {
                    if (tiles[tileIndex] != null) batch.draw(tiles[tileIndex], screenX, screenY, TILE_SIZE, TILE_SIZE);
                }

                if (!isWater && worldRow < map.mapRows - 1) {
                    int nextTileIndex = map.mapTileNum[worldCol][worldRow + 1];
                    boolean waterBelow = (nextTileIndex >= 5 && nextTileIndex <= 8);
                    if (waterBelow) {
                        boolean isGrass = (tileIndex >= 0 && tileIndex <= 3);
                        boolean isSand = (tileIndex == 9 || tileIndex == 10);
                        boolean isMud = (tileIndex == MapGenerator.TILE_MUD);

                        if (isGrass) {
                            boolean sandRight = (worldCol < map.mapCols - 1) && (map.mapTileNum[worldCol + 1][worldRow] == 9 || map.mapTileNum[worldCol + 1][worldRow] == 10);
                            boolean sandLeft = (worldCol > 0) && (map.mapTileNum[worldCol - 1][worldRow] == 9 || map.mapTileNum[worldCol - 1][worldRow] == 10);
                            int tileToDraw = 23;
                            int snowIndex = 4;
                            if (sandRight) { tileToDraw = 25; snowIndex = 5; }
                            else if (sandLeft) { tileToDraw = 26; snowIndex = 6; }

                            if (hasSnow) {
                                int step = Math.min(10, (int) (snowBlendValue * 10));
                                TextureRegion cliffRegion = tintPipeline.getSnowGrassRegion(snowIndex, step);
                                if (cliffRegion != null) batch.draw(cliffRegion, screenX, screenY, TILE_SIZE, TILE_SIZE);
                                else if (tiles[tileToDraw] != null) batch.draw(tiles[tileToDraw], screenX, screenY, TILE_SIZE, TILE_SIZE);
                            } else if (hasSwamp) {
                                int step = Math.min(10, (int) (swampBlendValue * 10));
                                TextureRegion cliffRegion = tintPipeline.getSwampGrassRegion(snowIndex, step);
                                if (cliffRegion != null) batch.draw(cliffRegion, screenX, screenY, TILE_SIZE, TILE_SIZE);
                                else if (tiles[57] != null) batch.draw(tiles[57], screenX, screenY, TILE_SIZE, TILE_SIZE);
                            } else if (tiles[tileToDraw] != null) {
                                batch.draw(tiles[tileToDraw], screenX, screenY, TILE_SIZE, TILE_SIZE);
                            }
                        } else if (isSand && tiles[24] != null) {
                            batch.draw(tiles[24], screenX, screenY, TILE_SIZE, TILE_SIZE);
                        } else if (isMud && tiles[58] != null) {
                            batch.draw(tiles[58], screenX, screenY, TILE_SIZE, TILE_SIZE);
                        }
                    }
                }

                int transType = map.mapTransType[worldCol][worldRow];
                if (transType != TransitionRenderer.TRANS_NONE) {
                    boolean isSwampTrans = (swampBlendValue > 0.01f);
                    int step = hasSnow ? (int) (snowBlendValue * 10) : (isSwampTrans ? (int) (swampBlendValue * 10) : 0);
                    if (step > 10) step = 10;
                    int rot = map.mapTransRot[worldCol][worldRow];
                    transRenderer.draw(batch, transType, rot, screenX, screenY, TILE_SIZE, step, isSwampTrans);
                }

                if (isWater) {
                    boolean landNorth     = (worldRow > 0) && (getWaterLevel(map, worldCol, worldRow - 1) == 0);
                    boolean landSouth     = (worldRow < map.mapRows - 1) && (getWaterLevel(map, worldCol, worldRow + 1) == 0);
                    boolean landWest      = (worldCol > 0) && (getWaterLevel(map, worldCol - 1, worldRow) == 0);
                    boolean landEast      = (worldCol < map.mapCols - 1) && (getWaterLevel(map, worldCol + 1, worldRow) == 0);
                    boolean landNorthWest = (worldCol > 0 && worldRow > 0) && (getWaterLevel(map, worldCol - 1, worldRow - 1) == 0);
                    boolean landNorthEast = (worldCol < map.mapCols - 1 && worldRow > 0) && (getWaterLevel(map, worldCol + 1, worldRow - 1) == 0);
                    boolean landSouthWest = (worldCol > 0 && worldRow < map.mapRows - 1) && (getWaterLevel(map, worldCol - 1, worldRow + 1) == 0);
                    boolean landSouthEast = (worldCol < map.mapCols - 1 && worldRow < map.mapRows - 1) && (getWaterLevel(map, worldCol + 1, worldRow + 1) == 0);

                    if (landNorth && !landEast && !landWest)  batch.draw(tintPipeline.getFoamRegion(0), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landEast && !landNorth && !landSouth) batch.draw(tintPipeline.getFoamRegion(1), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landSouth && !landEast && !landWest)  batch.draw(tintPipeline.getFoamRegion(2), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landWest && !landNorth && !landSouth) batch.draw(tintPipeline.getFoamRegion(3), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landNorth && landEast)                batch.draw(tintPipeline.getFoamRegion(4), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landSouth && landEast)                batch.draw(tintPipeline.getFoamRegion(5), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landSouth && landWest)                batch.draw(tintPipeline.getFoamRegion(6), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landNorth && landWest)                batch.draw(tintPipeline.getFoamRegion(7), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landNorthEast && !landNorth && !landEast) batch.draw(tintPipeline.getFoamRegion(8), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landSouthEast && !landSouth && !landEast) batch.draw(tintPipeline.getFoamRegion(9), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landSouthWest && !landSouth && !landWest) batch.draw(tintPipeline.getFoamRegion(10), screenX, screenY, TILE_SIZE, TILE_SIZE);
                    if (landNorthWest && !landNorth && !landWest) batch.draw(tintPipeline.getFoamRegion(11), screenX, screenY, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void drawDecorations(SpriteBatch batch, MapGenerator map, float minX, float minY, float maxX, float maxY,
                                float playerWorldY, boolean inFront, OrthographicCamera camera) {
        decorationRenderer.drawDecorations(batch, map, minX, minY, maxX, maxY, playerWorldY, inFront, camera,
                tiles, illuminatedTreeCache, tintPipeline, foliageSway);
    }

    public void drawDecorationsRow(SpriteBatch batch, MapGenerator map, int startCol, int endCol, int row,
                                  OrthographicCamera camera) {
        decorationRenderer.drawDecorationsRow(batch, map, startCol, endCol, row, camera,
                tiles, illuminatedTreeCache, tintPipeline, foliageSway);
    }

    public void drawLilipads(SpriteBatch batch, MapGenerator map, int startCol, int endCol, int startRow, int endRow) {
        decorationRenderer.drawLilipads(batch, map, startCol, endCol, startRow, endRow, tiles);
    }

    public void drawAllDecorations(SpriteBatch batch, MapGenerator map, float minX, float minY, float maxX, float maxY) {
        decorationRenderer.drawAllDecorations(batch, map, minX, minY, maxX, maxY,
                tiles, illuminatedTreeCache, tintPipeline);
    }

    public void drawGroundShadows(SpriteBatch batch, MapGenerator map, DayNightCycle cycle,
                                  float minX, float minY, float maxX, float maxY) {
        shadowRenderer.drawGroundShadows(batch, map, cycle, tilePixmaps, minX, minY, maxX, maxY, null, 0f, 0f);
    }

    public void drawGroundShadows(SpriteBatch batch, MapGenerator map, DayNightCycle cycle,
                                  float minX, float minY, float maxX, float maxY,
                                  TextureRegion playerSprite, float playerX, float playerY) {
        shadowRenderer.drawGroundShadows(batch, map, cycle, tilePixmaps, minX, minY, maxX, maxY, playerSprite, playerX, playerY);
    }

    public boolean collidesWithTree(MapGenerator map, float wx, float wy, float ww, float wh) {
        int leftTile = Math.max(0, (int) (wx / TILE_SIZE) - 1);
        int rightTile = Math.min(map.mapCols - 1, (int) ((wx + ww) / TILE_SIZE) + 1);
        int topTile = Math.max(0, (int) (wy / TILE_SIZE) - 1);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((wy + wh) / TILE_SIZE) + 1);

        for (int tc = leftTile; tc <= rightTile; tc++) {
            for (int tr = topTile; tr <= bottomTile; tr++) {
                int treeIdx = map.mapTreeNum[tc][tr];
                if (treeIdx == 0) continue;
                float hitX, hitY, hitW, hitH;

                if ((treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_WILLOW3)) {
                    hitX = tc * TILE_SIZE + TRUNK_HIT_OFFSET_X;
                    hitY = tr * TILE_SIZE + TRUNK_HIT_OFFSET_Y;
                    hitW = TRUNK_HIT_W;
                    hitH = TRUNK_HIT_H;
                } else if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    int sinkPx = (((tc * 31 + tr * 17) & 1) == 0 ? 2 : 3) * SCALE;
                    hitX = tc * TILE_SIZE + TILE_SIZE - TRUNK_HIT_W / 2f;
                    hitY = tr * TILE_SIZE + TRUNK_HIT_OFFSET_Y + sinkPx - 12;
                    hitW = TRUNK_HIT_W;
                    hitH = TRUNK_HIT_H;
                } else if (treeIdx == 32 || treeIdx == 33) {
                    hitX = tc * TILE_SIZE + 4;
                    hitY = tr * TILE_SIZE + TILE_SIZE - 16;
                    hitW = TILE_SIZE - 8;
                    hitH = 16;
                } else if (treeIdx == MapGenerator.TILE_ROCK1 || treeIdx == MapGenerator.TILE_SNOWY_ROCK1
                        || treeIdx == MapGenerator.TILE_SANDSTONE_ROCK || treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    hitW = 34;
                    hitH = 22;
                    hitX = tc * TILE_SIZE + (TILE_SIZE - hitW) / 2f;
                    int sinkPx = (treeIdx == MapGenerator.TILE_WATER_ROCK) ? 3 * SCALE : 0;
                    hitY = tr * TILE_SIZE + TILE_SIZE - hitH - 2 + sinkPx;
                } else {
                    continue;
                }

                if (wx < hitX + hitW && wx + ww > hitX && wy < hitY + hitH && wy + wh > hitY) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPlayerInBush(MapGenerator map, float wx, float wy, float ww, float wh) {
        return foliageSway.isPlayerInBush(map, wx, wy, ww, wh);
    }

    public boolean isPlayerInTallGrass(MapGenerator map, float wx, float wy, float ww, float wh) {
        return foliageSway.isPlayerInTallGrass(map, wx, wy, ww, wh);
    }

    public void checkBushInteraction(MapGenerator map, float wx, float wy, float ww, float wh, float moveDx, float moveDy) {
        foliageSway.checkBushInteraction(map, wx, wy, ww, wh, moveDx, moveDy, snowPS);
    }

    public void trackVisibleForWind(MapGenerator map, float minX, float minY, float maxX, float maxY) {
        foliageSway.trackVisibleForWind(map, minX, minY, maxX, maxY);
    }

    public void dispose() {
        tintPipeline.dispose();
        shadowRenderer.dispose();
        for (Texture tex : loadedTextures) {
            tex.dispose();
        }
        loadedTextures.clear();
        for (int i = 0; i < tilePixmaps.length; i++) {
            if (tilePixmaps[i] != null) {
                tilePixmaps[i].dispose();
                tilePixmaps[i] = null;
            }
        }
    }
}
