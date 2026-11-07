package com.whereyouat.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.lighting.DayNightCycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TileShadowRenderer - Responsable de construir siluetas de sombra para tiles
 * y proyectarlas direccionalmente sobre el suelo según el ángulo del sol o la luna.
 */
public class TileShadowRenderer {

    private static final int TILE_SIZE = TileManager.TILE_SIZE;
    private static final int SCALE = TileManager.SCALE;

    private final TextureRegion[] shadowSilhouetteCache = new TextureRegion[65];
    private final int[] shadowBottomPadCache = new int[65];
    private boolean shadowCachesBuilt = false;
    private final List<Texture> generatedTextures = new ArrayList<>();

    public void buildShadowCaches(Pixmap[] tilePixmaps) {
        if (shadowCachesBuilt) return;
        shadowCachesBuilt = true;
        Arrays.fill(shadowBottomPadCache, -1);

        for (int i = 0; i < tilePixmaps.length; i++) {
            Pixmap sourcePixmap = tilePixmaps[i];
            if (sourcePixmap == null) continue;

            int width = sourcePixmap.getWidth();
            int height = sourcePixmap.getHeight();
            Pixmap silhouette = new Pixmap(width, height, Pixmap.Format.RGBA8888);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgba = sourcePixmap.getPixel(x, y);
                    int alpha = rgba & 0xFF;
                    if (alpha > 25) {
                        silhouette.drawPixel(x, y, (0x0A << 24) | (0x14 << 16) | (0x0A << 8) | alpha);
                    }
                }
            }

            Texture texture = new Texture(silhouette);
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            generatedTextures.add(texture);
            TextureRegion region = new TextureRegion(texture);
            region.flip(false, true);
            shadowSilhouetteCache[i] = region;
            silhouette.dispose();

            // Calcular padding inferior para el punto de apoyo en el suelo
            int lowestY = height - 1;
            for (int y = height - 1; y >= 0; y--) {
                boolean hasOpaque = false;
                for (int x = 0; x < width; x++) {
                    if ((sourcePixmap.getPixel(x, y) & 0xFF) > 20) {
                        hasOpaque = true;
                        break;
                    }
                }
                if (hasOpaque) {
                    lowestY = y;
                    break;
                }
            }
            shadowBottomPadCache[i] = (height - 1) - lowestY;
        }
    }

    public void drawGroundShadows(SpriteBatch batch, MapGenerator map, DayNightCycle cycle,
                                  Pixmap[] tilePixmaps,
                                  float minX, float minY, float maxX, float maxY,
                                  TextureRegion playerSprite, float playerX, float playerY) {
        if (cycle == null) return;
        if (!shadowCachesBuilt) buildShadowCaches(tilePixmaps);

        float shadowDirX = cycle.getShadowDirX();
        float shadowLength = cycle.getShadowLength();
        float shadowOpacity = cycle.getShadowOpacity();

        if (shadowOpacity <= 0.01f || shadowLength <= 1.0f) return;

        float drawAlpha = Math.min(0.35f, shadowOpacity * 0.45f);

        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 3);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 4);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 4);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 4);

        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                int treeIdx = map.mapTreeNum[col][row];
                int tileIdx = map.mapTileNum[col][row];
                float screenX = col * TILE_SIZE;
                float screenY = row * TILE_SIZE;

                // 1. Árboles
                if (((treeIdx >= 12 && treeIdx <= 22)
                        || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_MANGROVE))
                        && treeIdx < shadowSilhouetteCache.length && shadowSilhouetteCache[treeIdx] != null) {
                    TextureRegion silhouetteRegion = shadowSilhouetteCache[treeIdx];
                    float drawWidth = silhouetteRegion.getRegionWidth() * SCALE;
                    float drawHeight = silhouetteRegion.getRegionHeight() * SCALE;
                    float baseX = screenX + TILE_SIZE;
                    int sinkPixels = (treeIdx == MapGenerator.TILE_MANGROVE)
                            ? (((col * 31 + row * 17) & 1) == 0 ? 2 : 3) * SCALE : 0;
                    int bottomPad = shadowBottomPadCache[treeIdx];
                    float contactY = (screenY + TILE_SIZE + sinkPixels) - (bottomPad * SCALE);

                    float scaleY = (float) (0.90 + (shadowLength / 50.0) * 0.35);
                    float xOffset = shadowDirX * shadowLength * 1.0f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, silhouetteRegion, baseX, contactY, drawWidth, drawHeight, scaleY, xOffset);
                }

                // 2. Arbustos y Rocas
                else if (((treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 36 && treeIdx <= 39)
                        || treeIdx == MapGenerator.TILE_ROCK1 || treeIdx == MapGenerator.TILE_SNOWY_ROCK1
                        || treeIdx == MapGenerator.TILE_SANDSTONE_ROCK || treeIdx == MapGenerator.TILE_WATER_ROCK)
                        && treeIdx < shadowSilhouetteCache.length && shadowSilhouetteCache[treeIdx] != null) {
                    TextureRegion silhouetteRegion = shadowSilhouetteCache[treeIdx];
                    float drawWidth = silhouetteRegion.getRegionWidth() * SCALE;
                    float drawHeight = silhouetteRegion.getRegionHeight() * SCALE;
                    float baseX = screenX + TILE_SIZE / 2.0f;
                    int sinkPixels = (treeIdx == MapGenerator.TILE_WATER_ROCK) ? 3 * SCALE : 0;
                    float baseY = screenY + TILE_SIZE - 2 + sinkPixels;

                    float scaleY = (float) (0.90 + (shadowLength / 50.0) * 0.30);
                    float xOffset = shadowDirX * shadowLength * 0.70f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, silhouetteRegion, baseX, baseY, drawWidth, drawHeight, scaleY, xOffset);
                }

                // 3. Pasto y espadañas
                else if ((treeIdx == 34 || treeIdx == 35 || treeIdx == 40 || treeIdx == 41
                        || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                        || treeIdx == MapGenerator.TILE_CATTAIL)
                        && treeIdx < shadowSilhouetteCache.length && shadowSilhouetteCache[treeIdx] != null) {
                    TextureRegion silhouetteRegion = shadowSilhouetteCache[treeIdx];
                    float drawWidth = silhouetteRegion.getRegionWidth() * SCALE;
                    float drawHeight = silhouetteRegion.getRegionHeight() * SCALE;
                    float baseX = screenX + TILE_SIZE / 2.0f;
                    int sinkPixels = (treeIdx == MapGenerator.TILE_CATTAIL)
                            ? ((2 + Math.abs(col * 17 + row * 31) % 3) * SCALE) : 0;
                    int bottomPad = shadowBottomPadCache[treeIdx];
                    float contactY = (screenY + TILE_SIZE - 2 + sinkPixels) - (bottomPad * SCALE);

                    float scaleY = (float) (0.90 + (shadowLength / 50.0) * 0.30);
                    float xOffset = shadowDirX * shadowLength * 0.70f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, silhouetteRegion, baseX, contactY, drawWidth, drawHeight, scaleY, xOffset);
                }

                // 4. Montañas de roca
                if (tileIdx == 4 && shadowSilhouetteCache[4] != null) {
                    TextureRegion silhouetteRegion = shadowSilhouetteCache[4];
                    float baseX = screenX + TILE_SIZE / 2.0f;
                    float baseY = screenY + TILE_SIZE - 2;

                    float scaleY = (float) (0.90 + (shadowLength / 50.0) * 0.30);
                    float xOffset = shadowDirX * shadowLength * 0.65f;

                    batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
                    drawShadowSheared(batch, silhouetteRegion, baseX, baseY, TILE_SIZE, TILE_SIZE, scaleY, xOffset);
                }
            }
        }

        // 5. Sombra proyectada del jugador
        if (playerSprite != null) {
            float playerBaseX = playerX + TILE_SIZE / 2.0f;
            float playerBaseY = playerY + TILE_SIZE - 2f;
            float scaleY = (float) (0.90 + (shadowLength / 50.0) * 0.30);
            float xOffset = shadowDirX * shadowLength * 0.60f;
            batch.setColor(0.04f, 0.08f, 0.04f, drawAlpha);
            drawShadowSheared(batch, playerSprite, playerBaseX, playerBaseY, TILE_SIZE, TILE_SIZE, scaleY, xOffset);
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void drawShadowSheared(SpriteBatch batch, TextureRegion region,
                                  float baseX, float baseY, float drawWidth, float drawHeight,
                                  float scaleY, float xOffset) {
        float groundHeight = drawHeight * scaleY;
        float halfWidth = drawWidth / 2f;

        float baseLeftX = baseX - halfWidth;
        float baseRightX = baseX + halfWidth;
        float groundY = baseY;

        float topLeftX = baseX - halfWidth + xOffset;
        float topRightX = baseX + halfWidth + xOffset;
        float topY = baseY - groundHeight;

        float uMin = region.getU(), vMin = region.getV(), uMax = region.getU2(), vMax = region.getV2();
        float packedColor = batch.getPackedColor();

        float[] vertices = new float[20];
        vertices[0] = baseLeftX;  vertices[1] = groundY; vertices[2] = packedColor; vertices[3] = uMin; vertices[4] = vMin;
        vertices[5] = baseRightX; vertices[6] = groundY; vertices[7] = packedColor; vertices[8] = uMax; vertices[9] = vMin;
        vertices[10] = topRightX; vertices[11] = topY;    vertices[12] = packedColor; vertices[13] = uMax; vertices[14] = vMax;
        vertices[15] = topLeftX;  vertices[16] = topY;    vertices[17] = packedColor; vertices[18] = uMin; vertices[19] = vMax;

        batch.draw(region.getTexture(), vertices, 0, 20);
    }

    public void dispose() {
        for (Texture texture : generatedTextures) {
            texture.dispose();
        }
        generatedTextures.clear();
    }
}

