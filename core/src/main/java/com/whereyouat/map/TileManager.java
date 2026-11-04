package com.whereyouat.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import com.whereyouat.lighting.DayNightCycle;
import com.whereyouat.effects.SnowParticleSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TileManager for LibGDX â€” faithful 1:1 port of the original Swing TileManager.
 *
 * All biome tinting (snow white, swamp olive, water depth) is done via Pixmap
 * pixel-level HSB manipulation at load time, exactly matching the original
 * tintGrass / tintSwampGrass / tintWater / tintSwampWater algorithms.
 *
 * Bush/grass sway uses base-anchored horizontal shear (not rotation).
 * Wind gust system drives organic, staggered vegetation movement.
 * Tree illumination cache recolors foliage canopies per sun/moon direction.
 */
public class TileManager {

    public static final int ORIGINAL_TILE_SIZE = 16;
    public static final int SCALE = 3;
    public static final int TILE_SIZE = ORIGINAL_TILE_SIZE * SCALE; // 48 px

    // Tree trunk collision box (relative to tree tile top-left pixel)
    public static final int TRUNK_HIT_OFFSET_X = 40;
    public static final int TRUNK_HIT_OFFSET_Y = 32;
    public static final int TRUNK_HIT_W = 16;
    public static final int TRUNK_HIT_H = 16;

    // Bush sway animation state (Terraria-style vegetation reaction)
    public static class BushSway {
        public float topShift = 0f;
        public float velocity = 0f;
        public int cooldown = 0;
    }

    public final Map<Long, BushSway> swayingBushes = new HashMap<>();

    // Natural organic wind gust simulation state
    private int windGustTimer = 480;
    private boolean gustActive = false;
    private int gustTicks = 0;
    private int gustDuration = 420;
    private float gustDirX = 1.0f;
    private float gustStrength = 0.0f;

    // Raw tile textures
    private final TextureRegion[] tiles = new TextureRegion[65];
    private final Pixmap[] tilePixmaps = new Pixmap[65];
    private final List<Texture> loadedTextures = new ArrayList<>();

    // Pre-tinted biome caches
    private final TextureRegion[][] snowGrass = new TextureRegion[7][11];
    private final TextureRegion[][] swampGrass = new TextureRegion[7][11];
    private final TextureRegion[][][] tintedWater = new TextureRegion[4][11][11];
    private final TextureRegion[][][] tintedSwampWater = new TextureRegion[4][11][11];

    // 12-variant foam cache
    private final TextureRegion[] foamCache = new TextureRegion[12];

    // Submerged sprite cache
    private final TextureRegion[][][] submergedTileCache = new TextureRegion[60][5][2];

    // Directional sun/moonshine illuminated tree cache
    private final TextureRegion[] illuminatedTreeCache = new TextureRegion[96];
    private float lastIllumSunDirX = -999f;
    private float lastIllumSunDirY = -999f;
    private float lastIllumIntensity = -999f;
    private boolean lastIllumNight = false;

    public final TransitionRenderer transRenderer = new TransitionRenderer();

    // Water animation
    private float waterAnimTimer = 0f;
    private int waterFrame = 0;
    private static final float WATER_ANIM_SPEED = 18f / 60f;
    private static final int WATER_FRAMES = 4;

    private SnowParticleSystem snowPS;

    public TileManager() {
        loadTiles();
        buildFoamImages();
        buildTintCaches();
    }

    public void setSnowPS(SnowParticleSystem ps) {
        this.snowPS = ps;
    }

    // =========================================================================
    // ASSET LOADING
    // =========================================================================

    private Texture loadTex(String path) {
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        loadedTextures.add(tex);
        return tex;
    }

    private TextureRegion loadRegion(String path) {
        Texture tex = loadTex(path);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        return tr;
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

        // Flipped sandgrass_border
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

    // =========================================================================
    // PIXEL-LEVEL TINTING (identical to original)
    // =========================================================================

    public static Pixmap tintGrass(Pixmap src, float blend) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        Pixmap tinted = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int targetR = 230, targetG = 240, targetB = 255;
        float[] hsb = new float[3];

        float totalLum = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a > 0) {
                    int r = (rgba >>> 24) & 0xFF;
                    int g = (rgba >>> 16) & 0xFF;
                    int b = (rgba >>> 8) & 0xFF;
                    rgbToHsb(r, g, b, hsb);
                    if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                        totalLum += (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                        count++;
                    }
                }
            }
        }
        float avgLum = count > 0 ? (totalLum / count) : 0.4f;
        if (avgLum < 0.01f) avgLum = 0.01f;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a == 0) { tinted.drawPixel(x, y, 0); continue; }
                int r = (rgba >>> 24) & 0xFF;
                int g = (rgba >>> 16) & 0xFF;
                int b = (rgba >>> 8) & 0xFF;
                rgbToHsb(r, g, b, hsb);
                if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                    float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                    float ratio = lum / avgLum;
                    int tr2 = Math.min(255, (int) (targetR * ratio));
                    int tg2 = Math.min(255, (int) (targetG * ratio));
                    int tb2 = Math.min(255, (int) (targetB * ratio));
                    int newR = clamp255((int) (r + (tr2 - r) * blend));
                    int newG = clamp255((int) (g + (tg2 - g) * blend));
                    int newB = clamp255((int) (b + (tb2 - b) * blend));
                    tinted.drawPixel(x, y, (newR << 24) | (newG << 16) | (newB << 8) | a);
                } else {
                    tinted.drawPixel(x, y, rgba);
                }
            }
        }
        return tinted;
    }

    public static Pixmap tintSwampGrass(Pixmap src, float blend) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        Pixmap tinted = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int targetR = 46, targetG = 76, targetB = 32;
        float[] hsb = new float[3];

        float totalLum = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a > 0) {
                    int r = (rgba >>> 24) & 0xFF;
                    int g = (rgba >>> 16) & 0xFF;
                    int b = (rgba >>> 8) & 0xFF;
                    rgbToHsb(r, g, b, hsb);
                    if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                        totalLum += (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                        count++;
                    }
                }
            }
        }
        float avgLum = count > 0 ? (totalLum / count) : 0.4f;
        if (avgLum < 0.01f) avgLum = 0.01f;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a == 0) { tinted.drawPixel(x, y, 0); continue; }
                int r = (rgba >>> 24) & 0xFF;
                int g = (rgba >>> 16) & 0xFF;
                int b = (rgba >>> 8) & 0xFF;
                rgbToHsb(r, g, b, hsb);
                if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                    float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                    float ratio = lum / avgLum;
                    int tr2 = Math.min(255, (int) (targetR * ratio));
                    int tg2 = Math.min(255, (int) (targetG * ratio));
                    int tb2 = Math.min(255, (int) (targetB * ratio));
                    int newR = clamp255((int) (r + (tr2 - r) * blend));
                    int newG = clamp255((int) (g + (tg2 - g) * blend));
                    int newB = clamp255((int) (b + (tb2 - b) * blend));
                    tinted.drawPixel(x, y, (newR << 24) | (newG << 16) | (newB << 8) | a);
                } else {
                    float darken = 1.0f - (0.45f * blend);
                    int newR = (int) (r * darken);
                    int newG = (int) (g * darken);
                    int newB = (int) (b * darken);
                    tinted.drawPixel(x, y, (newR << 24) | (newG << 16) | (newB << 8) | a);
                }
            }
        }
        return tinted;
    }

    public static Pixmap tintWater(Pixmap src, float snowBlend, float deepBlend) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        Pixmap tinted = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int snR = 100, snG = 200, snB = 255;
        int dpR = 10, dpG = 40, dpB = 140;
        float[] hsb = new float[3];

        float totalLum = 0; int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a > 0) {
                    int r = (rgba >>> 24) & 0xFF;
                    int g = (rgba >>> 16) & 0xFF;
                    int b = (rgba >>> 8) & 0xFF;
                    totalLum += (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                    count++;
                }
            }
        }
        float avgLum = count > 0 ? (totalLum / count) : 0.5f;
        if (avgLum < 0.01f) avgLum = 0.01f;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a == 0) { tinted.drawPixel(x, y, 0); continue; }
                int r = (rgba >>> 24) & 0xFF;
                int g = (rgba >>> 16) & 0xFF;
                int b = (rgba >>> 8) & 0xFF;
                rgbToHsb(r, g, b, hsb);
                if (hsb[0] >= 0.45f && hsb[0] <= 0.75f) {
                    float totalB = snowBlend + deepBlend;
                    if (totalB > 0) {
                        float snWeight = snowBlend / totalB;
                        float dpWeight = deepBlend / totalB;
                        int baseTargetR = (int) (snR * snWeight + dpR * dpWeight);
                        int baseTargetG = (int) (snG * snWeight + dpG * dpWeight);
                        int baseTargetB = (int) (snB * snWeight + dpB * dpWeight);
                        float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                        float ratio = lum / avgLum;
                        int tr2 = Math.min(255, (int) (baseTargetR * ratio));
                        int tg2 = Math.min(255, (int) (baseTargetG * ratio));
                        int tb2 = Math.min(255, (int) (baseTargetB * ratio));
                        float blendForce = Math.min(1.0f, totalB);
                        int newR = clamp255((int) (r + (tr2 - r) * blendForce));
                        int newG = clamp255((int) (g + (tg2 - g) * blendForce));
                        int newB = clamp255((int) (b + (tb2 - b) * blendForce));
                        tinted.drawPixel(x, y, (newR << 24) | (newG << 16) | (newB << 8) | a);
                    } else {
                        tinted.drawPixel(x, y, rgba);
                    }
                } else {
                    tinted.drawPixel(x, y, rgba);
                }
            }
        }
        return tinted;
    }

    public static Pixmap tintSwampWater(Pixmap src, float swampBlend, float deepBlend) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        Pixmap tinted = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int swR = 52, swG = 96, swB = 65;
        int dpR = 12, dpG = 38, dpB = 30;
        float[] hsb = new float[3];

        float totalLum = 0; int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a > 0) {
                    int r = (rgba >>> 24) & 0xFF;
                    int g = (rgba >>> 16) & 0xFF;
                    int b = (rgba >>> 8) & 0xFF;
                    totalLum += (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                    count++;
                }
            }
        }
        float avgLum = count > 0 ? (totalLum / count) : 0.5f;
        if (avgLum < 0.01f) avgLum = 0.01f;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getPixel(x, y);
                int a = rgba & 0xFF;
                if (a == 0) { tinted.drawPixel(x, y, 0); continue; }
                int r = (rgba >>> 24) & 0xFF;
                int g = (rgba >>> 16) & 0xFF;
                int b = (rgba >>> 8) & 0xFF;
                rgbToHsb(r, g, b, hsb);
                if (hsb[0] >= 0.45f && hsb[0] <= 0.75f) {
                    float totalB = swampBlend + deepBlend;
                    if (totalB > 0) {
                        float swWeight = swampBlend / totalB;
                        float dpWeight = deepBlend / totalB;
                        int baseTargetR = (int) (swR * swWeight + dpR * dpWeight);
                        int baseTargetG = (int) (swG * swWeight + dpG * dpWeight);
                        int baseTargetB = (int) (swB * swWeight + dpB * dpWeight);
                        float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                        float ratio = lum / avgLum;
                        int tr2 = Math.min(255, (int) (baseTargetR * ratio));
                        int tg2 = Math.min(255, (int) (baseTargetG * ratio));
                        int tb2 = Math.min(255, (int) (baseTargetB * ratio));
                        float blendForce = Math.min(1.0f, totalB);
                        int newR = clamp255((int) (r + (tr2 - r) * blendForce));
                        int newG = clamp255((int) (g + (tg2 - g) * blendForce));
                        int newB = clamp255((int) (b + (tb2 - b) * blendForce));
                        tinted.drawPixel(x, y, (newR << 24) | (newG << 16) | (newB << 8) | a);
                    } else {
                        tinted.drawPixel(x, y, rgba);
                    }
                } else {
                    tinted.drawPixel(x, y, rgba);
                }
            }
        }
        return tinted;
    }

    private static void rgbToHsb(int r, int g, int b, float[] hsb) {
        float fr = r / 255f, fg = g / 255f, fb = b / 255f;
        float max = Math.max(fr, Math.max(fg, fb));
        float min = Math.min(fr, Math.min(fg, fb));
        float delta = max - min;
        hsb[2] = max;
        hsb[1] = (max == 0) ? 0 : (delta / max);
        if (delta == 0) { hsb[0] = 0; }
        else if (max == fr) { hsb[0] = ((fg - fb) / delta) / 6f; if (hsb[0] < 0) hsb[0] += 1f; }
        else if (max == fg) { hsb[0] = (2f + (fb - fr) / delta) / 6f; }
        else { hsb[0] = (4f + (fr - fg) / delta) / 6f; }
    }

    private static int clamp255(int v) { return Math.min(255, Math.max(0, v)); }

    private TextureRegion pixmapToRegion(Pixmap pm) {
        Texture tex = new Texture(pm);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        loadedTextures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        return tr;
    }

    // =========================================================================
    // BUILD TINT CACHES
    // =========================================================================

    private void buildTintCaches() {
        int[] grassIndices = { 0, 1, 2, 3, 23, 25, 26 };
        for (int tileIdx : grassIndices) {
            Pixmap pm = tilePixmaps[tileIdx];
            if (pm == null) continue;
            int snowIdx = tileIdx;
            if (tileIdx == 23) snowIdx = 4;
            if (tileIdx == 25) snowIdx = 5;
            if (tileIdx == 26) snowIdx = 6;

            for (int b = 0; b <= 10; b++) {
                float blend = b / 10f;
                if (b == 0) {
                    snowGrass[snowIdx][b] = tiles[tileIdx];
                    Pixmap swampBase = (tileIdx == 23 && tilePixmaps[57] != null) ? tilePixmaps[57] : pm;
                    swampGrass[snowIdx][b] = (tileIdx == 23 && tiles[57] != null) ? tiles[57] : tiles[tileIdx];
                } else {
                    Pixmap snowPm = tintGrass(pm, blend);
                    snowGrass[snowIdx][b] = pixmapToRegion(snowPm);
                    snowPm.dispose();
                    Pixmap swampBase = (tileIdx == 23 && tilePixmaps[57] != null) ? tilePixmaps[57] : pm;
                    Pixmap swampPm = tintSwampGrass(swampBase, blend);
                    swampGrass[snowIdx][b] = pixmapToRegion(swampPm);
                    swampPm.dispose();
                }
            }
        }

        for (int frame = 0; frame < 4; frame++) {
            Pixmap waterPm = tilePixmaps[5 + frame];
            if (waterPm == null) continue;
            for (int sb = 0; sb <= 10; sb++) {
                for (int db = 0; db <= 10; db++) {
                    float sBlend = sb / 10f;
                    float dBlend = db / 10f;
                    if (sb == 0 && db == 0) {
                        tintedWater[frame][sb][db] = tiles[5 + frame];
                        tintedSwampWater[frame][sb][db] = tiles[5 + frame];
                    } else {
                        Pixmap snowWaterPm = tintWater(waterPm, sBlend, dBlend);
                        tintedWater[frame][sb][db] = pixmapToRegion(snowWaterPm);
                        snowWaterPm.dispose();
                        Pixmap swampWaterPm = tintSwampWater(waterPm, sBlend, dBlend);
                        tintedSwampWater[frame][sb][db] = pixmapToRegion(swampWaterPm);
                        swampWaterPm.dispose();
                    }
                }
            }
        }

        int[] submergedTrees = { MapGenerator.TILE_MANGROVE, MapGenerator.TILE_CATTAIL, MapGenerator.TILE_WATER_ROCK };
        for (int sTree : submergedTrees) {
            for (int sink = 1; sink <= 4; sink++) {
                for (int sw = 0; sw <= 1; sw++) {
                    getSubmergedSprite(sTree, sink, sw == 1);
                }
            }
        }
    }

    // =========================================================================
    // FOAM IMAGES (12 variants)
    // =========================================================================

    private void buildFoamImages() {
        Random r = new Random(12345);
        boolean[][] noise1Grid = new boolean[16][16];
        boolean[][] noise2Grid = new boolean[16][16];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                noise1Grid[i][j] = r.nextDouble() > 0.3;
                noise2Grid[i][j] = r.nextDouble() > 0.6;
            }
        }
        int c1 = Color.rgba8888(1f, 1f, 1f, 160 / 255f);
        int c2 = Color.rgba8888(1f, 1f, 1f, 80 / 255f);

        for (int idx = 0; idx < 12; idx++) {
            Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    boolean n1 = noise1Grid[i][j];
                    boolean n2 = noise2Grid[i][j];
                    boolean draw = false;
                    int color = 0;
                    switch (idx) {
                        case 0: if (j == 0 && n1) { draw = true; color = c1; } else if (j == 1 && n2) { draw = true; color = c2; } break;
                        case 1: if (i == 15 && n1) { draw = true; color = c1; } else if (i == 14 && n2) { draw = true; color = c2; } break;
                        case 2: if (j == 15 && n1) { draw = true; color = c1; } else if (j == 14 && n2) { draw = true; color = c2; } break;
                        case 3: if (i == 0 && n1) { draw = true; color = c1; } else if (i == 1 && n2) { draw = true; color = c2; } break;
                        case 4: if ((j == 0 || i == 15) && n1) { draw = true; color = c1; } else if ((j == 1 || i == 14) && n2) { draw = true; color = c2; } break;
                        case 5: if ((j == 15 || i == 15) && n1) { draw = true; color = c1; } else if ((j == 14 || i == 14) && n2) { draw = true; color = c2; } break;
                        case 6: if ((j == 15 || i == 0) && n1) { draw = true; color = c1; } else if ((j == 14 || i == 1) && n2) { draw = true; color = c2; } break;
                        case 7: if ((j == 0 || i == 0) && n1) { draw = true; color = c1; } else if ((j == 1 || i == 1) && n2) { draw = true; color = c2; } break;
                        case 8: if ((j == 0 && i >= 14) || (i == 15 && j <= 1)) { if (n1) { draw = true; color = c1; } } break;
                        case 9: if ((j == 15 && i >= 14) || (i == 15 && j >= 14)) { if (n1) { draw = true; color = c1; } } break;
                        case 10: if ((j == 15 && i <= 1) || (i == 0 && j >= 14)) { if (n1) { draw = true; color = c1; } } break;
                        case 11: if ((j == 0 && i <= 1) || (i == 0 && j <= 1)) { if (n1) { draw = true; color = c1; } } break;
                    }
                    if (draw) pm.drawPixel(i, j, color);
                }
            }
            Texture foamTex = new Texture(pm);
            foamTex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            loadedTextures.add(foamTex);
            pm.dispose();
            foamCache[idx] = new TextureRegion(foamTex);
            foamCache[idx].flip(false, true);
        }
    }

    // =========================================================================
    // SUBMERGED SPRITES
    // =========================================================================

    public TextureRegion getSubmergedSprite(int treeIdx, int sinkTexels, boolean isSwamp) {
        if (treeIdx < 0 || treeIdx >= submergedTileCache.length) return tiles[treeIdx];
        int s = Math.max(0, Math.min(4, sinkTexels));
        int sw = isSwamp ? 1 : 0;
        TextureRegion cached = submergedTileCache[treeIdx][s][sw];
        if (cached != null) return cached;
        Pixmap srcPm = tilePixmaps[treeIdx];
        if (srcPm == null) return tiles[treeIdx];
        Pixmap dest = buildSubmergedPixmap(srcPm, s);
        cached = pixmapToRegion(dest);
        dest.dispose();
        submergedTileCache[treeIdx][s][sw] = cached;
        return cached;
    }

    private Pixmap buildSubmergedPixmap(Pixmap src, int sinkTexels) {
        int w = src.getWidth(), h = src.getHeight();
        Pixmap dest = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) dest.drawPixel(x, y, src.getPixel(x, y));
        int waterlineY = Math.max(0, Math.min(h - 1, h - sinkTexels));
        for (int y = waterlineY + 1; y < h; y++) for (int x = 0; x < w; x++) dest.drawPixel(x, y, 0);
        int cCore = Color.rgba8888(1f, 1f, 1f, 215 / 255f);
        int cSoft = Color.rgba8888(1f, 1f, 1f, 125 / 255f);
        for (int x = 0; x < w; x++) {
            int a = src.getPixel(x, waterlineY) & 0xFF;
            int aBelow = (waterlineY + 1 < h) ? (src.getPixel(x, waterlineY + 1) & 0xFF) : 0;
            if (a > 30 || aBelow > 30) {
                dest.drawPixel(x, waterlineY, cCore);
                if (x > 0 && (src.getPixel(x - 1, waterlineY) & 0xFF) <= 30
                        && (waterlineY + 1 >= h || (src.getPixel(x - 1, waterlineY + 1) & 0xFF) <= 30))
                    dest.drawPixel(x - 1, waterlineY, cSoft);
                if (x < w - 1 && (src.getPixel(x + 1, waterlineY) & 0xFF) <= 30
                        && (waterlineY + 1 >= h || (src.getPixel(x + 1, waterlineY + 1) & 0xFF) <= 30))
                    dest.drawPixel(x + 1, waterlineY, cSoft);
            }
        }
        return dest;
    }

    // =========================================================================
    // PUBLIC ACCESSORS
    // =========================================================================

    public TextureRegion getTileRegion(int idx) {
        return (idx >= 0 && idx < tiles.length) ? tiles[idx] : null;
    }

    public TextureRegion getFoamRegion() { return foamCache[0]; }

    public TextureRegion getFoamRegion(int idx) {
        if (idx >= 0 && idx < foamCache.length) return foamCache[idx];
        return null;
    }

    public int getWaterFrame() { return waterFrame; }

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
        long key = (((long) col) << 32) | (row & 0xFFFFFFFFL);
        BushSway bs = swayingBushes.get(key);
        if (bs == null) return false;
        return Math.abs(bs.topShift) > 0.35f || (gustActive && gustStrength > 0.25f);
    }

    public boolean isWindGustActive() { return gustActive && gustStrength > 0.15f; }
    public float getWindGustDirX() { return gustDirX; }

    // =========================================================================
    // UPDATE
    // =========================================================================

    public void update(float delta, MapGenerator map, DayNightCycle cycle) {
        waterAnimTimer += delta;
        if (waterAnimTimer >= WATER_ANIM_SPEED) {
            waterAnimTimer = 0f;
            waterFrame = (waterFrame + 1) % WATER_FRAMES;
        }
        updateBushSway();
        updateWindGust(map);
        updateTreeIllumination(cycle);
    }

    private void updateBushSway() {
        if (swayingBushes.isEmpty()) return;
        Iterator<Map.Entry<Long, BushSway>> it = swayingBushes.entrySet().iterator();
        while (it.hasNext()) {
            BushSway bs = it.next().getValue();
            if (bs.cooldown > 0) bs.cooldown--;
            if (!gustActive || gustStrength < 0.05f) {
                bs.velocity += (0f - bs.topShift) * 0.09f;
                bs.velocity *= 0.88f;
                bs.topShift += bs.velocity;
                if (Math.abs(bs.topShift) < 0.05f && Math.abs(bs.velocity) < 0.05f && bs.cooldown <= 0) {
                    it.remove();
                }
            }
        }
    }

    private void updateWindGust(MapGenerator map) {
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
        if (gustTicks < 120) gustStrength = gustTicks / 120.0f;
        else if (gustTicks > gustDuration - 120) gustStrength = Math.max(0.0f, (gustDuration - gustTicks) / 120.0f);
        else gustStrength = 1.0f;

        if (map != null && gustStrength > 0.02f) {
            boolean peakWind = (gustStrength > 0.55f && gustTicks % 48 == 0);
            for (Map.Entry<Long, BushSway> entry : swayingBushes.entrySet()) {
                long key = entry.getKey();
                int wc = (int) (key >>> 32);
                int wr = (int) (key & 0xFFFFFFFFL);
                BushSway bs = entry.getValue();
                if (wc < 0 || wc >= map.mapCols || wr < 0 || wr >= map.mapRows) continue;
                int treeIdx = map.mapTreeNum[wc][wr];
                boolean isTree = (treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_MANGROVE);
                boolean isBush = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || (treeIdx == 39);
                boolean isGrassD = (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS || treeIdx == MapGenerator.TILE_CATTAIL);
                if (!isTree && !isBush && !isGrassD) continue;
                float spatialPhase = (wc * 0.73f) + (wr * 0.57f) + (float) Math.sin(wc * 1.15f + wr * 0.85f) * 1.8f;
                float osc = (float) Math.sin((gustTicks * 0.065f) + spatialPhase);
                float swayAmount = gustDirX * (0.55f + 0.45f * osc) * gustStrength;
                if (isTree) {
                    float targetShift = swayAmount * 6.8f;
                    bs.topShift += (targetShift - bs.topShift) * 0.10f;
                    if (peakWind && snowPS != null && Math.random() < 0.08) {
                        snowPS.spawnWindLeaves(wc * TILE_SIZE + TILE_SIZE, wr * TILE_SIZE - (TILE_SIZE * 0.5f), wr * TILE_SIZE + TILE_SIZE - 4f, 1, gustDirX);
                    }
                } else if (isBush) {
                    float targetShift = swayAmount * 3.8f;
                    bs.topShift += (targetShift - bs.topShift) * 0.12f;
                    if (peakWind && snowPS != null && Math.random() < 0.02) {
                        snowPS.spawnWindLeaves(wc * TILE_SIZE + TILE_SIZE / 2.0f, wr * TILE_SIZE + TILE_SIZE * 0.3f, wr * TILE_SIZE + TILE_SIZE - 4f, 1, gustDirX);
                    }
                } else if (isGrassD) {
                    float targetShift = swayAmount * 3.0f;
                    bs.topShift += (targetShift - bs.topShift) * 0.14f;
                }
            }
        }
        if (gustTicks >= gustDuration) {
            gustActive = false;
            windGustTimer = 700 + (int) (Math.random() * 700);
        }
    }

    // =========================================================================
    // TREE ILLUMINATION
    // =========================================================================

    private void updateTreeIllumination(DayNightCycle cycle) {
        if (cycle == null) return;
        float sunDirX = cycle.getSunDirX();
        float sunDirY = cycle.getSunDirY();
        float intensity = cycle.getAmbientIntensity();
        boolean isNight = cycle.isNight();
        if (Math.abs(sunDirX - lastIllumSunDirX) < 0.015f && Math.abs(sunDirY - lastIllumSunDirY) < 0.015f
                && Math.abs(intensity - lastIllumIntensity) < 0.015f && isNight == lastIllumNight) return;
        lastIllumSunDirX = sunDirX; lastIllumSunDirY = sunDirY; lastIllumIntensity = intensity; lastIllumNight = isNight;

        float dirX, dirY, hlR, hlG, hlB, hlPower;
        if (!isNight) {
            dirX = sunDirX; dirY = sunDirY;
            hlR = Math.min(255f, cycle.getAmbientR() * 255f * 1.20f);
            hlG = Math.min(255f, cycle.getAmbientG() * 255f * 1.08f);
            hlB = Math.min(255f, cycle.getAmbientB() * 255f * 0.85f);
            hlPower = Math.min(0.22f, intensity * 0.25f);
        } else {
            dirX = -cycle.getShadowDirX(); dirY = -0.45f;
            hlR = 160f; hlG = 195f; hlB = 255f; hlPower = 0.24f;
        }

        int[] illuminatedIndices = { 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 48, 49, 50, 51 };
        for (int treeIdx : illuminatedIndices) {
            Pixmap srcPm = tilePixmaps[treeIdx];
            if (srcPm == null) continue;
            int w = srcPm.getWidth(), h = srcPm.getHeight();
            Pixmap dest = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            float cx = w / 2.0f, cy = h * 0.35f;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgba = srcPm.getPixel(x, y);
                    int a = rgba & 0xFF;
                    if (a <= 20) { dest.drawPixel(x, y, 0); continue; }
                    int rr = (rgba >>> 24) & 0xFF, gg = (rgba >>> 16) & 0xFF, bb = (rgba >>> 8) & 0xFF;
                    if (y < h * 0.72f) {
                        float nx = (x - cx) / (w * 0.45f), ny = (y - cy) / (h * 0.35f);
                        float dist = (float) Math.sqrt(nx * nx + ny * ny);
                        if (dist > 0.05f) { nx /= dist; ny /= dist; }
                        float dot = nx * dirX + ny * dirY;
                        if (dot > 0.05f) {
                            float factor = (float) Math.pow(dot, 1.15) * hlPower;
                            int newR = Math.min(255, (int) (rr + (hlR - rr * 0.15f) * factor));
                            int newG = Math.min(255, (int) (gg + (hlG - gg * 0.15f) * factor));
                            int newB = Math.min(255, (int) (bb + (hlB - bb * 0.15f) * factor));
                            dest.drawPixel(x, y, (newR << 24) | (newG << 16) | (newB << 8) | a);
                        } else { dest.drawPixel(x, y, rgba); }
                    } else { dest.drawPixel(x, y, rgba); }
                }
            }
            illuminatedTreeCache[treeIdx] = pixmapToRegion(dest);
            dest.dispose();
        }
    }

    // =========================================================================
    // DRAW GROUND
    // =========================================================================

    public void drawGround(SpriteBatch batch, MapGenerator map, float minX, float minY, float maxX, float maxY) {
        int startCol = Math.max(0, (int) (minX / TILE_SIZE));
        int startRow = Math.max(0, (int) (minY / TILE_SIZE));
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 2);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 2);

        for (int wr = startRow; wr <= endRow; wr++) {
            for (int wc = startCol; wc <= endCol; wc++) {
                float sx = wc * TILE_SIZE, sy = wr * TILE_SIZE;
                int idx = map.mapTileNum[wc][wr];
                boolean isWater = (idx >= 5 && idx <= 8);
                if (isWater) idx = 5 + waterFrame;

                float blendSnow = map.snowBlend[wc][wr];
                boolean hasSnow = (blendSnow > 0.01f && !map.isDeepWater[wc][wr] && !map.isShallowWater[wc][wr]);
                float swbFloat = (map.swampBlend != null) ? map.swampBlend[wc][wr] : 0f;
                boolean hasSwamp = (swbFloat > 0.01f && !map.isDeepWater[wc][wr] && !map.isShallowWater[wc][wr]);

                batch.setColor(1f, 1f, 1f, 1f);

                if (isWater) {
                    if (swbFloat > 0.01f) {
                        int swb = Math.min(10, (int) (swbFloat * 10));
                        int db = Math.min(10, (int) (map.deepWaterBlend[wc][wr] * 10));
                        TextureRegion tr = tintedSwampWater[waterFrame][swb][db];
                        if (tr != null) batch.draw(tr, sx, sy, TILE_SIZE, TILE_SIZE);
                        else if (tiles[idx] != null) batch.draw(tiles[idx], sx, sy, TILE_SIZE, TILE_SIZE);
                    } else {
                        int sb = Math.min(10, (int) (blendSnow * 10));
                        int db = Math.min(10, (int) (map.deepWaterBlend[wc][wr] * 10));
                        TextureRegion tr = tintedWater[waterFrame][sb][db];
                        if (tr != null) batch.draw(tr, sx, sy, TILE_SIZE, TILE_SIZE);
                        else if (tiles[idx] != null) batch.draw(tiles[idx], sx, sy, TILE_SIZE, TILE_SIZE);
                    }
                } else if (hasSnow && idx >= 0 && idx <= 3) {
                    int step = Math.min(10, (int) (blendSnow * 10));
                    TextureRegion tr = snowGrass[idx][step];
                    if (tr != null) batch.draw(tr, sx, sy, TILE_SIZE, TILE_SIZE);
                    else if (tiles[idx] != null) batch.draw(tiles[idx], sx, sy, TILE_SIZE, TILE_SIZE);
                } else if (hasSwamp && idx >= 0 && idx <= 3) {
                    int step = Math.min(10, (int) (swbFloat * 10));
                    TextureRegion tr = swampGrass[idx][step];
                    if (tr != null) batch.draw(tr, sx, sy, TILE_SIZE, TILE_SIZE);
                    else if (tiles[idx] != null) batch.draw(tiles[idx], sx, sy, TILE_SIZE, TILE_SIZE);
                } else {
                    if (tiles[idx] != null) batch.draw(tiles[idx], sx, sy, TILE_SIZE, TILE_SIZE);
                }

                // Depth Borders (Cliffs)
                boolean waterBelow = false;
                if (!isWater && wr < map.mapRows - 1) {
                    int nextIdx = map.mapTileNum[wc][wr + 1];
                    waterBelow = (nextIdx >= 5 && nextIdx <= 8);
                    if (waterBelow) {
                        boolean isGrass = (idx >= 0 && idx <= 3);
                        boolean isSand = (idx == 9 || idx == 10);
                        boolean isMud = (idx == MapGenerator.TILE_MUD);
                        if (isGrass) {
                            boolean sandRight = (wc < map.mapCols - 1) && (map.mapTileNum[wc + 1][wr] == 9 || map.mapTileNum[wc + 1][wr] == 10);
                            boolean sandLeft = (wc > 0) && (map.mapTileNum[wc - 1][wr] == 9 || map.mapTileNum[wc - 1][wr] == 10);
                            int tileToDraw = 23; int snowIndex = 4;
                            if (sandRight) { tileToDraw = 25; snowIndex = 5; }
                            else if (sandLeft) { tileToDraw = 26; snowIndex = 6; }
                            if (hasSnow) {
                                int step = Math.min(10, (int) (blendSnow * 10));
                                TextureRegion tr = snowGrass[snowIndex][step];
                                if (tr != null) batch.draw(tr, sx, sy, TILE_SIZE, TILE_SIZE);
                                else if (tiles[tileToDraw] != null) batch.draw(tiles[tileToDraw], sx, sy, TILE_SIZE, TILE_SIZE);
                            } else if (hasSwamp) {
                                int step = Math.min(10, (int) (swbFloat * 10));
                                TextureRegion tr = swampGrass[snowIndex][step];
                                if (tr != null) batch.draw(tr, sx, sy, TILE_SIZE, TILE_SIZE);
                                else if (tiles[57] != null) batch.draw(tiles[57], sx, sy, TILE_SIZE, TILE_SIZE);
                            } else if (tiles[tileToDraw] != null) {
                                batch.draw(tiles[tileToDraw], sx, sy, TILE_SIZE, TILE_SIZE);
                            }
                        } else if (isSand && tiles[24] != null) {
                            batch.draw(tiles[24], sx, sy, TILE_SIZE, TILE_SIZE);
                        } else if (isMud && tiles[58] != null) {
                            batch.draw(tiles[58], sx, sy, TILE_SIZE, TILE_SIZE);
                        }
                    }
                }

                // Universal Grass transition overlay
                int transType = map.mapTransType[wc][wr];
                if (transType != TransitionRenderer.TRANS_NONE) {
                    boolean isSwampTrans = (swbFloat > 0.01f);
                    int step = hasSnow ? (int) (blendSnow * 10) : (isSwampTrans ? (int) (swbFloat * 10) : 0);
                    if (step > 10) step = 10;
                    int rot = map.mapTransRot[wc][wr];
                    transRenderer.draw(batch, transType, rot, sx, sy, TILE_SIZE, step, isSwampTrans);
                }

                // Water Edge Foam
                if (isWater) {
                    boolean n = (wr > 0) && (getWaterLevel(map, wc, wr - 1) == 0);
                    boolean s2 = (wr < map.mapRows - 1) && (getWaterLevel(map, wc, wr + 1) == 0);
                    boolean w = (wc > 0) && (getWaterLevel(map, wc - 1, wr) == 0);
                    boolean e = (wc < map.mapCols - 1) && (getWaterLevel(map, wc + 1, wr) == 0);
                    boolean nw = (wc > 0 && wr > 0) && (getWaterLevel(map, wc - 1, wr - 1) == 0);
                    boolean ne = (wc < map.mapCols - 1 && wr > 0) && (getWaterLevel(map, wc + 1, wr - 1) == 0);
                    boolean sw = (wc > 0 && wr < map.mapRows - 1) && (getWaterLevel(map, wc - 1, wr + 1) == 0);
                    boolean se = (wc < map.mapCols - 1 && wr < map.mapRows - 1) && (getWaterLevel(map, wc + 1, wr + 1) == 0);

                    if (n && !e && !w) batch.draw(foamCache[0], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (e && !n && !s2) batch.draw(foamCache[1], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (s2 && !e && !w) batch.draw(foamCache[2], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (w && !n && !s2) batch.draw(foamCache[3], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (n && e) batch.draw(foamCache[4], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (s2 && e) batch.draw(foamCache[5], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (s2 && w) batch.draw(foamCache[6], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (n && w) batch.draw(foamCache[7], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (ne && !n && !e) batch.draw(foamCache[8], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (se && !s2 && !e) batch.draw(foamCache[9], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (sw && !s2 && !w) batch.draw(foamCache[10], sx, sy, TILE_SIZE, TILE_SIZE);
                    if (nw && !n && !w) batch.draw(foamCache[11], sx, sy, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    // =========================================================================
    // DRAW DECORATIONS
    // =========================================================================

    public void drawDecorations(SpriteBatch batch, MapGenerator map, float minX, float minY, float maxX, float maxY,
                                float playerWorldY, boolean inFront, OrthographicCamera camera) {
        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 2);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 4);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 4);
        int playerFootY = (int) playerWorldY + TILE_SIZE;
        batch.setColor(1f, 1f, 1f, 1f);

        for (int wr = startRow; wr <= endRow; wr++) {
            for (int wc = startCol; wc <= endCol; wc++) {
                int treeIdx = map.mapTreeNum[wc][wr];
                if (treeIdx == 0) continue;

                float scale = SCALE;
                int sinkOffset = 0;
                if (treeIdx == MapGenerator.TILE_MANGROVE) sinkOffset = (((wc * 31 + wr * 17) & 1) == 0 ? 2 : 3) * SCALE;
                else if (treeIdx == MapGenerator.TILE_CATTAIL) sinkOffset = (2 + Math.abs(wc * 17 + wr * 31) % 3) * SCALE;
                else if (treeIdx == MapGenerator.TILE_WATER_ROCK) sinkOffset = 3 * SCALE;
                int treeFootY = wr * TILE_SIZE + TILE_SIZE + sinkOffset;
                boolean isLilipad = (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3);
                boolean treeInFront = !isLilipad && (treeFootY > playerFootY);
                if (treeInFront != inFront) continue;

                float sx = wc * TILE_SIZE, sy = wr * TILE_SIZE;
                TextureRegion tileRegion = tiles[treeIdx];
                if (tileRegion == null) continue;

                if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    int sinkTexels = (((wc * 31 + wr * 17) & 1) == 0 ? 2 : 3);
                    int sinkPx = sinkTexels * SCALE;
                    TextureRegion img = getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[wc][wr]);
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + TILE_SIZE - drawW / 2f;
                    float drawY = sy + TILE_SIZE - drawH + sinkPx;
                    float baseY = sy + TILE_SIZE + sinkPx;
                    long key = (((long) wc) << 32) | (wr & 0xFFFFFFFFL);
                    BushSway bs = swayingBushes.get(key);
                    // Clip mangroves so only the portion above the waterline is visible
                    boolean mgShallow = map.isShallowWater[wc][wr];
                    boolean mgDeep    = map.isDeepWater[wc][wr];
                    if ((mgShallow || mgDeep) && camera != null) {
                        float waterSurfaceY = mgDeep ? sy : (sy + TILE_SIZE / 2f);
                        float visibleH = waterSurfaceY - drawY;
                        if (visibleH > 0) {
                            Vector3 mgTl = camera.project(new Vector3(drawX, drawY, 0));
                            Vector3 mgBr = camera.project(new Vector3(drawX + drawW, drawY + visibleH, 0));
                            int mgSx = (int) Math.min(mgTl.x, mgBr.x);
                            int mgSy = (int) Math.min(mgTl.y, mgBr.y);
                            int mgSw = Math.max(1, (int) Math.abs(mgBr.x - mgTl.x));
                            int mgSh = Math.max(1, (int) Math.abs(mgBr.y - mgTl.y));
                            batch.flush();
                            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                            Gdx.gl.glScissor(mgSx, mgSy, mgSw, mgSh);
                            if (bs != null && Math.abs(bs.topShift) > 0.05f) drawSheared(batch, img, drawX, drawY, drawW, drawH, baseY, bs.topShift);
                            else batch.draw(img, drawX, drawY, drawW, drawH);
                            batch.flush();
                            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                            // Draw foam at waterline for mangrove (outside scissor)
                            TextureRegion decFoam = getFoamRegion();
                            if (decFoam != null) {
                                batch.setColor(1f, 1f, 1f, 0.9f);
                                batch.draw(decFoam, drawX + drawW / 2f - TILE_SIZE / 2f, waterSurfaceY - 3f, TILE_SIZE, TILE_SIZE);
                                batch.setColor(1f, 1f, 1f, 1f);
                            }
                        }
                    } else {
                        if (bs != null && Math.abs(bs.topShift) > 0.05f) drawSheared(batch, img, drawX, drawY, drawW, drawH, baseY, bs.topShift);
                        else batch.draw(img, drawX, drawY, drawW, drawH);
                    }

                } else if ((treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_WILLOW3)) {
                    TextureRegion img = (illuminatedTreeCache[treeIdx] != null) ? illuminatedTreeCache[treeIdx] : tileRegion;
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + TILE_SIZE - drawW / 2f;
                    float drawY = sy + TILE_SIZE - drawH;
                    float baseY = sy + TILE_SIZE;
                    long key = (((long) wc) << 32) | (wr & 0xFFFFFFFFL);
                    BushSway bs = swayingBushes.get(key);
                    if (bs != null && Math.abs(bs.topShift) > 0.05f) drawSheared(batch, img, drawX, drawY, drawW, drawH, baseY, bs.topShift);
                    else batch.draw(img, drawX, drawY, drawW, drawH);

                } else if (isLilipad) {
                    float drawW = tileRegion.getRegionWidth() * scale, drawH = tileRegion.getRegionHeight() * scale;
                    int jitterX = (Math.abs(wc * 13 + wr * 7) % 7) - 3;
                    int jitterY = (Math.abs(wc * 17 + wr * 11) % 7) - 3;
                    float drawX = sx + (TILE_SIZE - drawW) / 2f + jitterX;
                    float drawY = sy + (TILE_SIZE - drawH) / 2f + jitterY;
                    batch.draw(tileRegion, drawX, drawY, drawW, drawH);

                } else if (treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    TextureRegion img = getSubmergedSprite(treeIdx, 3, map.isSwampBiome[wc][wr]);
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + (TILE_SIZE - drawW) / 2f;
                    float drawY = sy + TILE_SIZE - drawH + 3 * SCALE;
                    // Clip water rocks so only the portion above the waterline is visible
                    boolean wrShallow = map.isShallowWater[wc][wr];
                    boolean wrDeep    = map.isDeepWater[wc][wr];
                    if ((wrShallow || wrDeep) && camera != null) {
                        float waterSurfaceY = wrDeep ? sy : (sy + TILE_SIZE / 2f);
                        float visibleH = waterSurfaceY - drawY;
                        if (visibleH > 0) {
                            Vector3 wrTl = camera.project(new Vector3(drawX, drawY, 0));
                            Vector3 wrBr = camera.project(new Vector3(drawX + drawW, drawY + visibleH, 0));
                            int wrSx = (int) Math.min(wrTl.x, wrBr.x);
                            int wrSy = (int) Math.min(wrTl.y, wrBr.y);
                            int wrSw = Math.max(1, (int) Math.abs(wrBr.x - wrTl.x));
                            int wrSh = Math.max(1, (int) Math.abs(wrBr.y - wrTl.y));
                            batch.flush();
                            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                            Gdx.gl.glScissor(wrSx, wrSy, wrSw, wrSh);
                            batch.draw(img, drawX, drawY, drawW, drawH);
                            batch.flush();
                            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                            // Draw foam at waterline for water rock (outside scissor)
                            TextureRegion decFoam = getFoamRegion();
                            if (decFoam != null) {
                                batch.setColor(1f, 1f, 1f, 0.9f);
                                batch.draw(decFoam, sx, waterSurfaceY - 3f, TILE_SIZE, TILE_SIZE);
                                batch.setColor(1f, 1f, 1f, 1f);
                            }
                        }
                    } else {
                        batch.draw(img, drawX, drawY, drawW, drawH);
                    }

                } else {
                    int sinkTexels = (treeIdx == MapGenerator.TILE_CATTAIL) ? (2 + Math.abs(wc * 17 + wr * 31) % 3) : 0;
                    int sinkPx = sinkTexels * SCALE;
                    TextureRegion img = (treeIdx == MapGenerator.TILE_CATTAIL)
                            ? getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[wc][wr]) : tileRegion;
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + (TILE_SIZE - drawW) / 2f;
                    float drawY = sy + TILE_SIZE - drawH + sinkPx;


                    boolean isSwayingPlant = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 34 && treeIdx <= 41)
                            || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS || treeIdx == MapGenerator.TILE_CATTAIL;
                    // For water tiles: clip decorations so only the portion ABOVE the water surface is visible.
                    // Uses GL scissor test, mirroring original g2.clipRect approach.
                    boolean tileIsShallow = map.isShallowWater[wc][wr];
                    boolean tileIsDeep = map.isDeepWater[wc][wr];
                    if ((tileIsShallow || tileIsDeep) && camera != null) {
                        // Water surface Y in world coords (Y-down). For deep water the full tile is submerged.
                        float waterSurfaceY = tileIsDeep ? sy : (sy + TILE_SIZE / 2f);
                        float visibleH = waterSurfaceY - drawY;
                        if (visibleH <= 0) continue; // fully submerged — skip

                        // Pre-fetch sway data to expand scissor for shear offset
                        long swayKey2 = (((long) wc) << 32) | (wr & 0xFFFFFFFFL);
                        BushSway bs2 = isSwayingPlant ? swayingBushes.get(swayKey2) : null;
                        float shearMargin = (bs2 != null) ? (Math.abs(bs2.topShift) + 2f) : 0f;
                        // Project clip rect corners from world space to GL window pixels (Y-up origin).
                        Vector3 tl = camera.project(new Vector3(drawX - shearMargin, drawY,             0));
                        Vector3 br = camera.project(new Vector3(drawX + drawW + shearMargin, drawY + visibleH, 0));
                        int sx2 = (int) Math.min(tl.x, br.x);
                        int sy2 = (int) Math.min(tl.y, br.y);
                        int sw2 = Math.max(1, (int) Math.abs(br.x - tl.x));
                        int sh2 = Math.max(1, (int) Math.abs(br.y - tl.y));

                        batch.flush();
                        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                        Gdx.gl.glScissor(sx2, sy2, sw2, sh2);

                        if (isSwayingPlant && !swayingBushes.isEmpty()) {
                            long key = (((long) wc) << 32) | (wr & 0xFFFFFFFFL);
                            BushSway bs = swayingBushes.get(key);
                            if (bs != null && Math.abs(bs.topShift) > 0.05f) {
                                float baseY = drawY + drawH;
                                drawSheared(batch, img, drawX, drawY, drawW, drawH, baseY, bs.topShift);
                            } else batch.draw(img, drawX, drawY, drawW, drawH);
                        } else batch.draw(img, drawX, drawY, drawW, drawH);

                        batch.flush();
                        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                        // Draw foam at waterline for aquatic decoration (outside scissor)
                        TextureRegion decFoam = getFoamRegion();
                        if (decFoam != null) {
                            batch.setColor(1f, 1f, 1f, 0.9f);
                            batch.draw(decFoam, sx, waterSurfaceY - 3f, TILE_SIZE, TILE_SIZE);
                            batch.setColor(1f, 1f, 1f, 1f);
                        }
                        continue;
                    }

                    if (isSwayingPlant && !swayingBushes.isEmpty()) {
                        long key = (((long) wc) << 32) | (wr & 0xFFFFFFFFL);
                        BushSway bs = swayingBushes.get(key);
                        if (bs != null && Math.abs(bs.topShift) > 0.05f) {
                            float baseY = drawY + drawH;
                            drawSheared(batch, img, drawX, drawY, drawW, drawH, baseY, bs.topShift);
                        } else batch.draw(img, drawX, drawY, drawW, drawH);
                    } else batch.draw(img, drawX, drawY, drawW, drawH);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Draws all decorations (trees, bushes, rocks, cattails, lilipads) without depth sorting.
     * Used for full-map screenshot generation (F12).
     */
    public void drawAllDecorations(SpriteBatch batch, MapGenerator map, float minX, float minY, float maxX, float maxY) {
        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 2);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 4);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 4);
        batch.setColor(1f, 1f, 1f, 1f);

        for (int wr = startRow; wr <= endRow; wr++) {
            for (int wc = startCol; wc <= endCol; wc++) {
                int treeIdx = map.mapTreeNum[wc][wr];
                if (treeIdx == 0) continue;

                float scale = SCALE;
                float sx = wc * TILE_SIZE, sy = wr * TILE_SIZE;
                TextureRegion tileRegion = tiles[treeIdx];
                if (tileRegion == null) continue;

                boolean isLilipad = (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3);

                if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    int sinkTexels = (((wc * 31 + wr * 17) & 1) == 0 ? 2 : 3);
                    int sinkPx = sinkTexels * SCALE;
                    TextureRegion img = getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[wc][wr]);
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + TILE_SIZE - drawW / 2f;
                    float drawY = sy + TILE_SIZE - drawH + sinkPx;
                    batch.draw(img, drawX, drawY, drawW, drawH);

                } else if ((treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_WILLOW3)) {
                    TextureRegion img = (illuminatedTreeCache[treeIdx] != null) ? illuminatedTreeCache[treeIdx] : tileRegion;
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + TILE_SIZE - drawW / 2f;
                    float drawY = sy + TILE_SIZE - drawH;
                    batch.draw(img, drawX, drawY, drawW, drawH);

                } else if (isLilipad) {
                    float drawW = tileRegion.getRegionWidth() * scale, drawH = tileRegion.getRegionHeight() * scale;
                    int jitterX = (Math.abs(wc * 13 + wr * 7) % 7) - 3;
                    int jitterY = (Math.abs(wc * 17 + wr * 11) % 7) - 3;
                    float drawX = sx + (TILE_SIZE - drawW) / 2f + jitterX;
                    float drawY = sy + (TILE_SIZE - drawH) / 2f + jitterY;
                    batch.draw(tileRegion, drawX, drawY, drawW, drawH);

                } else if (treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    TextureRegion img = getSubmergedSprite(treeIdx, 3, map.isSwampBiome[wc][wr]);
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + (TILE_SIZE - drawW) / 2f;
                    float drawY = sy + TILE_SIZE - drawH + 3 * SCALE;
                    batch.draw(img, drawX, drawY, drawW, drawH);

                } else {
                    int sinkTexels = (treeIdx == MapGenerator.TILE_CATTAIL) ? (2 + Math.abs(wc * 17 + wr * 31) % 3) : 0;
                    int sinkPx = sinkTexels * SCALE;
                    TextureRegion img = (treeIdx == MapGenerator.TILE_CATTAIL)
                            ? getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[wc][wr]) : tileRegion;
                    float drawW = img.getRegionWidth() * scale, drawH = img.getRegionHeight() * scale;
                    float drawX = sx + (TILE_SIZE - drawW) / 2f;
                    float drawY = sy + TILE_SIZE - drawH + sinkPx;
                    batch.draw(img, drawX, drawY, drawW, drawH);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Draws a TextureRegion with horizontal shear anchored at baseY.
     * Top pixels shift by -topShift, bottom pixels stay fixed.
     */
    private void drawSheared(SpriteBatch batch, TextureRegion region, float drawX, float drawY,
                              float drawW, float drawH, float baseY, float topShift) {
        float topOffset = -topShift;
        float u = region.getU(), v = region.getV(), u2 = region.getU2(), v2 = region.getV2();
        float x1 = drawX + topOffset, y1 = drawY;
        float x2 = drawX, y2 = baseY;
        float x3 = drawX + drawW, y3 = baseY;
        float x4 = drawX + drawW + topOffset, y4 = drawY;
        float color = batch.getPackedColor();
        float[] vertices = new float[20];
        // Y-down camera with flipped regions: v=visual bottom, v2=visual top
        // Bottom vertices (baseY, larger Y) get v (bottom of image)
        // Top vertices (drawY, smaller Y) get v2 (top of image)
        vertices[0] = x2; vertices[1] = y2; vertices[2] = color; vertices[3] = u;  vertices[4] = v;
        vertices[5] = x3; vertices[6] = y3; vertices[7] = color; vertices[8] = u2; vertices[9] = v;
        vertices[10] = x4; vertices[11] = y4; vertices[12] = color; vertices[13] = u2; vertices[14] = v2;
        vertices[15] = x1; vertices[16] = y1; vertices[17] = color; vertices[18] = u;  vertices[19] = v2;
        batch.draw(region.getTexture(), vertices, 0, 20);
    }


    // =========================================================================
    // GROUND SHADOWS (directional sun/moon projected silhouettes)
    // =========================================================================

    // Shadow silhouette caches
    private final TextureRegion[] shadowSilhouetteCache = new TextureRegion[65];
    private final int[] shadowBottomPadCache = new int[65];
    private boolean shadowCachesBuilt = false;

    private void buildShadowCaches() {
        if (shadowCachesBuilt) return;
        shadowCachesBuilt = true;
        java.util.Arrays.fill(shadowBottomPadCache, -1);

        for (int i = 0; i < tilePixmaps.length; i++) {
            Pixmap pm = tilePixmaps[i];
            if (pm == null) continue;

            int w = pm.getWidth();
            int h = pm.getHeight();
            Pixmap sil = new Pixmap(w, h, Pixmap.Format.RGBA8888);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgba = pm.getPixel(x, y);
                    int a = rgba & 0xFF;
                    if (a > 25) {
                        sil.drawPixel(x, y, (0x0A << 24) | (0x14 << 16) | (0x0A << 8) | a);
                    }
                }
            }
            shadowSilhouetteCache[i] = pixmapToRegion(sil);
            sil.dispose();

            // Compute bottom padding
            int lowestY = h - 1;
            for (int y = h - 1; y >= 0; y--) {
                boolean hasOpaque = false;
                for (int x = 0; x < w; x++) {
                    if ((pm.getPixel(x, y) & 0xFF) > 20) {
                        hasOpaque = true;
                        break;
                    }
                }
                if (hasOpaque) { lowestY = y; break; }
            }
            shadowBottomPadCache[i] = (h - 1) - lowestY;
        }
    }

    /**
     * Draws directional ground shadows beneath trees, bushes, rocks, and grass.
     * Uses sheared vertex-based rendering to project shadows based on sun direction.
     */
    public void drawGroundShadows(SpriteBatch batch, MapGenerator map, DayNightCycle cycle,
                                   float minX, float minY, float maxX, float maxY) {
        drawGroundShadows(batch, map, cycle, minX, minY, maxX, maxY, null, 0f, 0f);
    }

    public void drawGroundShadows(SpriteBatch batch, MapGenerator map, DayNightCycle cycle,
                                   float minX, float minY, float maxX, float maxY,
                                   TextureRegion playerSprite, float playerX, float playerY) {
        if (cycle == null) return;
        if (!shadowCachesBuilt) buildShadowCaches();

        float sDirX = cycle.getShadowDirX();
        float sLen = cycle.getShadowLength();
        float sAlpha = cycle.getShadowOpacity();

        if (sAlpha <= 0.01f || sLen <= 1.0f) return;

        float drawAlpha = Math.min(0.35f, sAlpha * 0.45f);

        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 3);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 4);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 4);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 4);

        for (int wr = startRow; wr <= endRow; wr++) {
            for (int wc = startCol; wc <= endCol; wc++) {
                int treeIdx = map.mapTreeNum[wc][wr];
                int tileIdx = map.mapTileNum[wc][wr];
                float sx = wc * TILE_SIZE;
                float sy = wr * TILE_SIZE;

                // Trees
                if (((treeIdx >= 12 && treeIdx <= 22)
                        || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_MANGROVE))
                        && treeIdx < shadowSilhouetteCache.length && shadowSilhouetteCache[treeIdx] != null) {
                    TextureRegion sil = shadowSilhouetteCache[treeIdx];
                    float drawW = sil.getRegionWidth() * SCALE;
                    float drawH = sil.getRegionHeight() * SCALE;
                    float baseX = sx + TILE_SIZE;
                    int sinkPx = (treeIdx == MapGenerator.TILE_MANGROVE)
                            ? (((wc * 31 + wr * 17) & 1) == 0 ? 2 : 3) * SCALE : 0;
                    int bottomPad = shadowBottomPadCache[treeIdx];
                    float contactY = (sy + TILE_SIZE + sinkPx) - (bottomPad * SCALE);

                    float scaleY = (float)(0.90 + (sLen / 50.0) * 0.35);
                    float xOffset = sDirX * sLen * 1.0f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, sil, baseX, contactY, drawW, drawH, scaleY, xOffset);
                }

                // Bushes and Rocks
                else if (((treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 39)
                        || treeIdx == MapGenerator.TILE_ROCK1 || treeIdx == MapGenerator.TILE_SNOWY_ROCK1
                        || treeIdx == MapGenerator.TILE_SANDSTONE_ROCK || treeIdx == MapGenerator.TILE_WATER_ROCK)
                        && treeIdx < shadowSilhouetteCache.length && shadowSilhouetteCache[treeIdx] != null) {
                    TextureRegion sil = shadowSilhouetteCache[treeIdx];
                    float drawW = sil.getRegionWidth() * SCALE;
                    float drawH = sil.getRegionHeight() * SCALE;
                    float baseX = sx + TILE_SIZE / 2.0f;
                    int sinkPx = (treeIdx == MapGenerator.TILE_WATER_ROCK) ? 3 * SCALE : 0;
                    float baseY = sy + TILE_SIZE - 2 + sinkPx;

                    float scaleY = (float)(0.90 + (sLen / 50.0) * 0.30);
                    float xOffset = sDirX * sLen * 0.70f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, sil, baseX, baseY, drawW, drawH, scaleY, xOffset);
                }

                // Grass
                else if ((treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_CATTAIL)
                        && treeIdx < shadowSilhouetteCache.length && shadowSilhouetteCache[treeIdx] != null) {
                    TextureRegion sil = shadowSilhouetteCache[treeIdx];
                    float drawW = sil.getRegionWidth() * SCALE;
                    float drawH = sil.getRegionHeight() * SCALE;
                    float baseX = sx + TILE_SIZE / 2.0f;
                    int sinkPx = (treeIdx == MapGenerator.TILE_CATTAIL)
                            ? ((2 + Math.abs(wc * 17 + wr * 31) % 3) * SCALE) : 0;
                    int bottomPad = shadowBottomPadCache[treeIdx];
                    float contactY = (sy + TILE_SIZE - 2 + sinkPx) - (bottomPad * SCALE);

                    float scaleY = (float)(0.90 + (sLen / 50.0) * 0.30);
                    float xOffset = sDirX * sLen * 0.70f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, sil, baseX, contactY, drawW, drawH, scaleY, xOffset);
                }

                // Stone mountains
                if (tileIdx == 4 && shadowSilhouetteCache[4] != null) {
                    TextureRegion sil = shadowSilhouetteCache[4];
                    float baseX = sx + TILE_SIZE / 2.0f;
                    float baseY = sy + TILE_SIZE - 2;

                    float scaleY = (float)(0.90 + (sLen / 50.0) * 0.30);
                    float xOffset = sDirX * sLen * 0.65f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, sil, baseX, baseY, TILE_SIZE, TILE_SIZE, scaleY, xOffset);
                }
            }
        }

        // 5. Player Foot Shadow (projected North like original)
        if (playerSprite != null) {
            float playerBaseX = playerX + TILE_SIZE / 2.0f;
            float playerBaseY = playerY + TILE_SIZE - 2f;
            float scaleY = (float) (0.90 + (sLen / 50.0) * 0.30);
            float xOffset = sDirX * sLen * 0.60f;
            batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
            drawShadowSheared(batch, playerSprite, playerBaseX, playerBaseY, TILE_SIZE, TILE_SIZE, scaleY, xOffset);
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Renders a shadow silhouette with shear+scale projection (matching original AffineTransform).
     * baseX,baseY = contact point on ground; drawW,drawH = unscaled size;
     * scaleY = vertical stretch factor; xOffset = horizontal displacement at top.
     */
    private void drawShadowSheared(SpriteBatch batch, TextureRegion region,
                                    float baseX, float baseY, float drawW, float drawH,
                                    float scaleY, float xOffset) {
        float groundH = drawH * scaleY;
        float halfW = drawW / 2f;

        // Base vertices: on ground at baseY, centered at baseX
        float bx1 = baseX - halfW;
        float bx2 = baseX + halfW;
        float by = baseY;

        // Far vertices: projected NORTH (baseY - groundH in Y-down coordinates) and sheared sideways
        float tx1 = baseX - halfW + xOffset;
        float tx2 = baseX + halfW + xOffset;
        float ty = baseY - groundH;

        float u = region.getU(), v = region.getV(), u2 = region.getU2(), v2 = region.getV2();
        float color = batch.getPackedColor();

        float[] vertices = new float[20];
        // With tr.flip(false, true): v = visual bottom (trunk/base), v2 = visual top (leaves/canopy).
        // Triangle order: (0, 1, 2) and (2, 3, 0).
        // Vertex 0: Base-left (ground contact, bottom of sprite: v)
        vertices[0] = bx1; vertices[1] = by; vertices[2] = color; vertices[3] = u;  vertices[4] = v;
        // Vertex 1: Base-right (ground contact, bottom of sprite: v)
        vertices[5] = bx2; vertices[6] = by; vertices[7] = color; vertices[8] = u2; vertices[9] = v;
        // Vertex 2: Far-right (pointing North, top of sprite: v2)
        vertices[10] = tx2; vertices[11] = ty; vertices[12] = color; vertices[13] = u2; vertices[14] = v2;
        // Vertex 3: Far-left (pointing North, top of sprite: v2)
        vertices[15] = tx1; vertices[16] = ty; vertices[17] = color; vertices[18] = u;  vertices[19] = v2;

        batch.draw(region.getTexture(), vertices, 0, 20);
    }

    // =========================================================================
    // COLLISION
    // =========================================================================

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
                    hitX = tc * TILE_SIZE + TRUNK_HIT_OFFSET_X; hitY = tr * TILE_SIZE + TRUNK_HIT_OFFSET_Y; hitW = TRUNK_HIT_W; hitH = TRUNK_HIT_H;
                } else if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    int sinkPx = (((tc * 31 + tr * 17) & 1) == 0 ? 2 : 3) * SCALE;
                    hitX = tc * TILE_SIZE + TILE_SIZE - TRUNK_HIT_W / 2f; hitY = tr * TILE_SIZE + TRUNK_HIT_OFFSET_Y + sinkPx - 12; hitW = TRUNK_HIT_W; hitH = TRUNK_HIT_H;
                } else if (treeIdx == 32 || treeIdx == 33) {
                    hitX = tc * TILE_SIZE + 4; hitY = tr * TILE_SIZE + TILE_SIZE - 16; hitW = TILE_SIZE - 8; hitH = 16;
                } else if (treeIdx == MapGenerator.TILE_ROCK1 || treeIdx == MapGenerator.TILE_SNOWY_ROCK1
                        || treeIdx == MapGenerator.TILE_SANDSTONE_ROCK || treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    hitW = 34; hitH = 22; hitX = tc * TILE_SIZE + (TILE_SIZE - hitW) / 2f;
                    int sinkPx = (treeIdx == MapGenerator.TILE_WATER_ROCK) ? 3 * SCALE : 0;
                    hitY = tr * TILE_SIZE + TILE_SIZE - hitH - 2 + sinkPx;
                } else { continue; }
                if (wx < hitX + hitW && wx + ww > hitX && wy < hitY + hitH && wy + wh > hitY) return true;
            }
        }
        return false;
    }

    public boolean isPlayerInBush(MapGenerator map, float wx, float wy, float ww, float wh) {
        int leftTile = Math.max(0, (int) (wx / TILE_SIZE) - 2);
        int rightTile = Math.min(map.mapCols - 1, (int) ((wx + ww) / TILE_SIZE) + 2);
        int topTile = Math.max(0, (int) (wy / TILE_SIZE) - 2);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((wy + wh) / TILE_SIZE) + 2);
        for (int tc = leftTile; tc <= rightTile; tc++) {
            for (int tr = topTile; tr <= bottomTile; tr++) {
                int treeIdx = map.mapTreeNum[tc][tr];
                if ((treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || treeIdx == 39) {
                    float hitY = tr * TILE_SIZE + 4, hitH = TILE_SIZE - 8, hitX, hitW;
                    if (treeIdx == 30 || treeIdx == 31 || treeIdx == 38) {
                        float bushW = TILE_SIZE * 1.5f; hitX = tc * TILE_SIZE + (TILE_SIZE - bushW) / 2f; hitW = bushW;
                    } else { hitX = tc * TILE_SIZE + 4; hitW = TILE_SIZE - 8; }
                    if (wx < hitX + hitW && wx + ww > hitX && wy < hitY + hitH && wy + wh > hitY) return true;
                }
            }
        }
        return false;
    }

    public boolean isPlayerInTallGrass(MapGenerator map, float wx, float wy, float ww, float wh) {
        int leftTile = Math.max(0, (int) (wx / TILE_SIZE) - 2);
        int rightTile = Math.min(map.mapCols - 1, (int) ((wx + ww) / TILE_SIZE) + 2);
        int topTile = Math.max(0, (int) (wy / TILE_SIZE) - 2);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((wy + wh) / TILE_SIZE) + 2);
        for (int tc = leftTile; tc <= rightTile; tc++) {
            for (int tr = topTile; tr <= bottomTile; tr++) {
                int treeIdx = map.mapTreeNum[tc][tr];
                if (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3)
                        || treeIdx == MapGenerator.TILE_CATTAIL) {
                    float hitX = tc * TILE_SIZE + 4, hitW = TILE_SIZE - 8, hitY = tr * TILE_SIZE + 4, hitH = TILE_SIZE - 8;
                    if (wx < hitX + hitW && wx + ww > hitX && wy < hitY + hitH && wy + wh > hitY) return true;
                }
            }
        }
        return false;
    }

    public void checkBushInteraction(MapGenerator map, float wx, float wy, float ww, float wh, float moveDx, float moveDy) {
        int leftTile = Math.max(0, (int) (wx / TILE_SIZE) - 2);
        int rightTile = Math.min(map.mapCols - 1, (int) ((wx + ww) / TILE_SIZE) + 2);
        int topTile = Math.max(0, (int) (wy / TILE_SIZE) - 2);
        int bottomTile = Math.min(map.mapRows - 1, (int) ((wy + wh) / TILE_SIZE) + 2);
        for (int tc = leftTile; tc <= rightTile; tc++) {
            for (int tr = topTile; tr <= bottomTile; tr++) {
                int treeIdx = map.mapTreeNum[tc][tr];
                boolean isBush = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || (treeIdx == 39);
                boolean isTallGrass = (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3)
                        || treeIdx == MapGenerator.TILE_CATTAIL);
                if (isBush || isTallGrass) {
                    float hitY = tr * TILE_SIZE + 4, hitH = TILE_SIZE - 8, hitX, hitW;
                    if (treeIdx == 30 || treeIdx == 31 || treeIdx == 38) {
                        float bushW = TILE_SIZE * 1.5f; hitX = tc * TILE_SIZE + (TILE_SIZE - bushW) / 2f; hitW = bushW;
                    } else { hitX = tc * TILE_SIZE + 4; hitW = TILE_SIZE - 8; }
                    if (wx < hitX + hitW && wx + ww > hitX && wy < hitY + hitH && wy + wh > hitY) {
                        long key = (((long) tc) << 32) | (tr & 0xFFFFFFFFL);
                        BushSway bs = swayingBushes.get(key);
                        if (bs == null) { bs = new BushSway(); swayingBushes.put(key, bs); }
                        float maxPush = (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3) ? 0f : (isTallGrass ? 2.6f : 3.2f);
                        float push = 0f;
                        if (moveDx > 0.05f) push = maxPush;
                        else if (moveDx < -0.05f) push = -maxPush;
                        else if (moveDy > 0.05f) push = (tc % 2 == 0) ? (maxPush * 0.7f) : -(maxPush * 0.7f);
                        else if (moveDy < -0.05f) push = (tc % 2 == 0) ? -(maxPush * 0.7f) : (maxPush * 0.7f);
                        if (push != 0f) bs.velocity += (push - bs.velocity) * 0.45f;
                        if (isBush && bs.cooldown <= 0) {
                            if (snowPS != null) snowPS.spawnLeafParticles(tc, tr, TILE_SIZE);
                            bs.cooldown = 45;
                        }
                    }
                }
            }
        }
    }

    /** Add visible tiles to wind tracking each frame. */
    public void trackVisibleForWind(MapGenerator map, float minX, float minY, float maxX, float maxY) {
        if (!gustActive || gustStrength < 0.02f) return;
        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 3);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 3);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 3);
        for (int wr = startRow; wr <= endRow; wr++) {
            for (int wc = startCol; wc <= endCol; wc++) {
                int treeIdx = map.mapTreeNum[wc][wr];
                if (treeIdx == 0) continue;
                boolean isTree = (treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_MANGROVE);
                boolean isBush = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 38) || (treeIdx == 39);
                boolean isGrassD = (treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS || treeIdx == MapGenerator.TILE_CATTAIL);
                if (!isTree && !isBush && !isGrassD) continue;
                long key = (((long) wc) << 32) | (wr & 0xFFFFFFFFL);
                if (!swayingBushes.containsKey(key)) swayingBushes.put(key, new BushSway());
            }
        }
    }

    // =========================================================================
    // DISPOSE
    // =========================================================================

    public void dispose() {
        for (Texture tex : loadedTextures) tex.dispose();
        loadedTextures.clear();
        for (int i = 0; i < tilePixmaps.length; i++) {
            if (tilePixmaps[i] != null) { tilePixmaps[i].dispose(); tilePixmaps[i] = null; }
        }
    }
}
