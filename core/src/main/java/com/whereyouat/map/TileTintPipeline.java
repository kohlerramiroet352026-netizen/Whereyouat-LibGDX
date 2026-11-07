package com.whereyouat.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TileTintPipeline - Responsable de la manipulación de píxeles a nivel de CPU (HSB),
 * generación de variantes de bioma para agua y vegetación (nieve y pantano),
 * espumas y sprites parcialmente sumergidos.
 */
public class TileTintPipeline {

    // Cachés de variantes tintadas
    private final TextureRegion[][] snowGrass = new TextureRegion[7][11];
    private final TextureRegion[][] swampGrass = new TextureRegion[7][11];
    private final TextureRegion[][][] tintedWater = new TextureRegion[4][11][11];
    private final TextureRegion[][][] tintedSwampWater = new TextureRegion[4][11][11];

    // Caché de 12 variantes de espuma en bordes de agua
    private final TextureRegion[] foamCache = new TextureRegion[12];

    // Caché de sprites sumergidos en agua o lodo
    private final TextureRegion[][][] submergedTileCache = new TextureRegion[60][5][2];

    private final List<Texture> generatedTextures = new ArrayList<>();

    // =========================================================================
    // ALGORITMOS DE TINTADO HSB A NIVEL DE PÍXEL (CPU)
    // =========================================================================

    public static Pixmap tintGrass(Pixmap sourcePixmap, float blend) {
        if (sourcePixmap == null) return null;
        int width = sourcePixmap.getWidth();
        int height = sourcePixmap.getHeight();
        Pixmap tintedPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        int targetRed = 230, targetGreen = 240, targetBlue = 255;
        float[] hsb = new float[3];

        float totalLuminance = 0;
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha > 0) {
                    int red = (rgba >>> 24) & 0xFF;
                    int green = (rgba >>> 16) & 0xFF;
                    int blue = (rgba >>> 8) & 0xFF;
                    rgbToHsb(red, green, blue, hsb);
                    if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                        totalLuminance += (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                        count++;
                    }
                }
            }
        }
        float averageLuminance = count > 0 ? (totalLuminance / count) : 0.4f;
        if (averageLuminance < 0.01f) averageLuminance = 0.01f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha == 0) {
                    tintedPixmap.drawPixel(x, y, 0);
                    continue;
                }
                int red = (rgba >>> 24) & 0xFF;
                int green = (rgba >>> 16) & 0xFF;
                int blue = (rgba >>> 8) & 0xFF;
                rgbToHsb(red, green, blue, hsb);
                if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                    float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                    float ratio = luminance / averageLuminance;
                    int adjustedTargetRed = Math.min(255, (int) (targetRed * ratio));
                    int adjustedTargetGreen = Math.min(255, (int) (targetGreen * ratio));
                    int adjustedTargetBlue = Math.min(255, (int) (targetBlue * ratio));
                    int blendedRed = clamp255((int) (red + (adjustedTargetRed - red) * blend));
                    int blendedGreen = clamp255((int) (green + (adjustedTargetGreen - green) * blend));
                    int blendedBlue = clamp255((int) (blue + (adjustedTargetBlue - blue) * blend));
                    tintedPixmap.drawPixel(x, y, (blendedRed << 24) | (blendedGreen << 16) | (blendedBlue << 8) | alpha);
                } else {
                    tintedPixmap.drawPixel(x, y, rgba);
                }
            }
        }
        return tintedPixmap;
    }

    public static Pixmap tintSwampGrass(Pixmap sourcePixmap, float blend) {
        if (sourcePixmap == null) return null;
        int width = sourcePixmap.getWidth();
        int height = sourcePixmap.getHeight();
        Pixmap tintedPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        int targetRed = 46, targetGreen = 76, targetBlue = 32;
        float[] hsb = new float[3];

        float totalLuminance = 0;
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha > 0) {
                    int red = (rgba >>> 24) & 0xFF;
                    int green = (rgba >>> 16) & 0xFF;
                    int blue = (rgba >>> 8) & 0xFF;
                    rgbToHsb(red, green, blue, hsb);
                    if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                        totalLuminance += (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                        count++;
                    }
                }
            }
        }
        float averageLuminance = count > 0 ? (totalLuminance / count) : 0.4f;
        if (averageLuminance < 0.01f) averageLuminance = 0.01f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha == 0) {
                    tintedPixmap.drawPixel(x, y, 0);
                    continue;
                }
                int red = (rgba >>> 24) & 0xFF;
                int green = (rgba >>> 16) & 0xFF;
                int blue = (rgba >>> 8) & 0xFF;
                rgbToHsb(red, green, blue, hsb);
                if (hsb[0] >= 0.16f && hsb[0] <= 0.45f && hsb[1] >= 0.10f) {
                    float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                    float ratio = luminance / averageLuminance;
                    int adjustedTargetRed = Math.min(255, (int) (targetRed * ratio));
                    int adjustedTargetGreen = Math.min(255, (int) (targetGreen * ratio));
                    int adjustedTargetBlue = Math.min(255, (int) (targetBlue * ratio));
                    int blendedRed = clamp255((int) (red + (adjustedTargetRed - red) * blend));
                    int blendedGreen = clamp255((int) (green + (adjustedTargetGreen - green) * blend));
                    int blendedBlue = clamp255((int) (blue + (adjustedTargetBlue - blue) * blend));
                    tintedPixmap.drawPixel(x, y, (blendedRed << 24) | (blendedGreen << 16) | (blendedBlue << 8) | alpha);
                } else {
                    float darken = 1.0f - (0.45f * blend);
                    int blendedRed = (int) (red * darken);
                    int blendedGreen = (int) (green * darken);
                    int blendedBlue = (int) (blue * darken);
                    tintedPixmap.drawPixel(x, y, (blendedRed << 24) | (blendedGreen << 16) | (blendedBlue << 8) | alpha);
                }
            }
        }
        return tintedPixmap;
    }

    public static Pixmap tintWater(Pixmap sourcePixmap, float snowBlend, float deepBlend) {
        if (sourcePixmap == null) return null;
        int width = sourcePixmap.getWidth();
        int height = sourcePixmap.getHeight();
        Pixmap tintedPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        int snowTargetRed = 100, snowTargetGreen = 200, snowTargetBlue = 255;
        int deepTargetRed = 10, deepTargetGreen = 40, deepTargetBlue = 140;
        float[] hsb = new float[3];

        float totalLuminance = 0;
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha > 0) {
                    int red = (rgba >>> 24) & 0xFF;
                    int green = (rgba >>> 16) & 0xFF;
                    int blue = (rgba >>> 8) & 0xFF;
                    totalLuminance += (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                    count++;
                }
            }
        }
        float averageLuminance = count > 0 ? (totalLuminance / count) : 0.5f;
        if (averageLuminance < 0.01f) averageLuminance = 0.01f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha == 0) {
                    tintedPixmap.drawPixel(x, y, 0);
                    continue;
                }
                int red = (rgba >>> 24) & 0xFF;
                int green = (rgba >>> 16) & 0xFF;
                int blue = (rgba >>> 8) & 0xFF;
                rgbToHsb(red, green, blue, hsb);
                if (hsb[0] >= 0.45f && hsb[0] <= 0.75f) {
                    float totalBlend = snowBlend + deepBlend;
                    if (totalBlend > 0) {
                        float snowWeight = snowBlend / totalBlend;
                        float deepWeight = deepBlend / totalBlend;
                        int baseTargetRed = (int) (snowTargetRed * snowWeight + deepTargetRed * deepWeight);
                        int baseTargetGreen = (int) (snowTargetGreen * snowWeight + deepTargetGreen * deepWeight);
                        int baseTargetBlue = (int) (snowTargetBlue * snowWeight + deepTargetBlue * deepWeight);
                        float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                        float ratio = luminance / averageLuminance;
                        int adjustedTargetRed = Math.min(255, (int) (baseTargetRed * ratio));
                        int adjustedTargetGreen = Math.min(255, (int) (baseTargetGreen * ratio));
                        int adjustedTargetBlue = Math.min(255, (int) (baseTargetBlue * ratio));
                        float blendForce = Math.min(1.0f, totalBlend);
                        int blendedRed = clamp255((int) (red + (adjustedTargetRed - red) * blendForce));
                        int blendedGreen = clamp255((int) (green + (adjustedTargetGreen - green) * blendForce));
                        int blendedBlue = clamp255((int) (blue + (adjustedTargetBlue - blue) * blendForce));
                        tintedPixmap.drawPixel(x, y, (blendedRed << 24) | (blendedGreen << 16) | (blendedBlue << 8) | alpha);
                    } else {
                        tintedPixmap.drawPixel(x, y, rgba);
                    }
                } else {
                    tintedPixmap.drawPixel(x, y, rgba);
                }
            }
        }
        return tintedPixmap;
    }

    public static Pixmap tintSwampWater(Pixmap sourcePixmap, float swampBlend, float deepBlend) {
        if (sourcePixmap == null) return null;
        int width = sourcePixmap.getWidth();
        int height = sourcePixmap.getHeight();
        Pixmap tintedPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        int swampTargetRed = 52, swampTargetGreen = 96, swampTargetBlue = 65;
        int deepTargetRed = 12, deepTargetGreen = 38, deepTargetBlue = 30;
        float[] hsb = new float[3];

        float totalLuminance = 0;
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha > 0) {
                    int red = (rgba >>> 24) & 0xFF;
                    int green = (rgba >>> 16) & 0xFF;
                    int blue = (rgba >>> 8) & 0xFF;
                    totalLuminance += (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                    count++;
                }
            }
        }
        float averageLuminance = count > 0 ? (totalLuminance / count) : 0.5f;
        if (averageLuminance < 0.01f) averageLuminance = 0.01f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sourcePixmap.getPixel(x, y);
                int alpha = rgba & 0xFF;
                if (alpha == 0) {
                    tintedPixmap.drawPixel(x, y, 0);
                    continue;
                }
                int red = (rgba >>> 24) & 0xFF;
                int green = (rgba >>> 16) & 0xFF;
                int blue = (rgba >>> 8) & 0xFF;
                rgbToHsb(red, green, blue, hsb);
                if (hsb[0] >= 0.45f && hsb[0] <= 0.75f) {
                    float totalBlend = swampBlend + deepBlend;
                    if (totalBlend > 0) {
                        float swampWeight = swampBlend / totalBlend;
                        float deepWeight = deepBlend / totalBlend;
                        int baseTargetRed = (int) (swampTargetRed * swampWeight + deepTargetRed * deepWeight);
                        int baseTargetGreen = (int) (swampTargetGreen * swampWeight + deepTargetGreen * deepWeight);
                        int baseTargetBlue = (int) (swampTargetBlue * swampWeight + deepTargetBlue * deepWeight);
                        float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
                        float ratio = luminance / averageLuminance;
                        int adjustedTargetRed = Math.min(255, (int) (baseTargetRed * ratio));
                        int adjustedTargetGreen = Math.min(255, (int) (baseTargetGreen * ratio));
                        int adjustedTargetBlue = Math.min(255, (int) (baseTargetBlue * ratio));
                        float blendForce = Math.min(1.0f, totalBlend);
                        int blendedRed = clamp255((int) (red + (adjustedTargetRed - red) * blendForce));
                        int blendedGreen = clamp255((int) (green + (adjustedTargetGreen - green) * blendForce));
                        int blendedBlue = clamp255((int) (blue + (adjustedTargetBlue - blue) * blendForce));
                        tintedPixmap.drawPixel(x, y, (blendedRed << 24) | (blendedGreen << 16) | (blendedBlue << 8) | alpha);
                    } else {
                        tintedPixmap.drawPixel(x, y, rgba);
                    }
                } else {
                    tintedPixmap.drawPixel(x, y, rgba);
                }
            }
        }
        return tintedPixmap;
    }

    public static void rgbToHsb(int red, int green, int blue, float[] hsb) {
        float normalizedRed = red / 255f, normalizedGreen = green / 255f, normalizedBlue = blue / 255f;
        float max = Math.max(normalizedRed, Math.max(normalizedGreen, normalizedBlue));
        float min = Math.min(normalizedRed, Math.min(normalizedGreen, normalizedBlue));
        float delta = max - min;
        hsb[2] = max;
        hsb[1] = (max == 0) ? 0 : (delta / max);
        if (delta == 0) {
            hsb[0] = 0;
        } else if (max == normalizedRed) {
            hsb[0] = ((normalizedGreen - normalizedBlue) / delta) / 6f;
            if (hsb[0] < 0) hsb[0] += 1f;
        } else if (max == normalizedGreen) {
            hsb[0] = (2f + (normalizedBlue - normalizedRed) / delta) / 6f;
        } else {
            hsb[0] = (4f + (normalizedRed - normalizedGreen) / delta) / 6f;
        }
    }

    public static int clamp255(int value) {
        return Math.min(255, Math.max(0, value));
    }

    private TextureRegion pixmapToRegion(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        generatedTextures.add(texture);
        TextureRegion region = new TextureRegion(texture);
        region.flip(false, true);
        return region;
    }

    // =========================================================================
    // CONSTRUCCIÓN DE CACHÉS
    // =========================================================================

    public void buildTintCaches(TextureRegion[] tiles, Pixmap[] tilePixmaps) {
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
                    getSubmergedSprite(sTree, sink, sw == 1, tiles, tilePixmaps);
                }
            }
        }
    }

    public void buildFoamImages() {
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
            generatedTextures.add(foamTex);
            pm.dispose();
            foamCache[idx] = new TextureRegion(foamTex);
            foamCache[idx].flip(false, true);
        }
    }

    public TextureRegion getSubmergedSprite(int treeIdx, int sinkTexels, boolean isSwamp,
                                            TextureRegion[] tiles, Pixmap[] tilePixmaps) {
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
        int width = src.getWidth(), height = src.getHeight();
        Pixmap dest = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dest.drawPixel(x, y, src.getPixel(x, y));
            }
        }
        int waterlineY = Math.max(0, Math.min(height - 1, height - sinkTexels));
        for (int y = waterlineY + 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dest.drawPixel(x, y, 0);
            }
        }
        int cCore = Color.rgba8888(1f, 1f, 1f, 215 / 255f);
        int cSoft = Color.rgba8888(1f, 1f, 1f, 125 / 255f);
        for (int x = 0; x < width; x++) {
            int alpha = src.getPixel(x, waterlineY) & 0xFF;
            int alphaBelow = (waterlineY + 1 < height) ? (src.getPixel(x, waterlineY + 1) & 0xFF) : 0;
            if (alpha > 30 || alphaBelow > 30) {
                dest.drawPixel(x, waterlineY, cCore);
                if (x > 0 && (src.getPixel(x - 1, waterlineY) & 0xFF) <= 30
                        && (waterlineY + 1 >= height || (src.getPixel(x - 1, waterlineY + 1) & 0xFF) <= 30)) {
                    dest.drawPixel(x - 1, waterlineY, cSoft);
                }
                if (x < width - 1 && (src.getPixel(x + 1, waterlineY) & 0xFF) <= 30
                        && (waterlineY + 1 >= height || (src.getPixel(x + 1, waterlineY + 1) & 0xFF) <= 30)) {
                    dest.drawPixel(x + 1, waterlineY, cSoft);
                }
            }
        }
        return dest;
    }

    // --- Getters de regiones cacheadas ---

    public TextureRegion getSnowGrassRegion(int snowIdx, int step) {
        return snowGrass[snowIdx][step];
    }

    public TextureRegion getSwampGrassRegion(int swampIdx, int step) {
        return swampGrass[swampIdx][step];
    }

    public TextureRegion getTintedWaterRegion(int frame, int snowStep, int deepStep) {
        return tintedWater[frame][snowStep][deepStep];
    }

    public TextureRegion getTintedSwampWaterRegion(int frame, int swampStep, int deepStep) {
        return tintedSwampWater[frame][swampStep][deepStep];
    }

    public TextureRegion getFoamRegion(int idx) {
        if (idx >= 0 && idx < foamCache.length) return foamCache[idx];
        return null;
    }

    public void dispose() {
        for (Texture tex : generatedTextures) {
            tex.dispose();
        }
        generatedTextures.clear();
    }
}

