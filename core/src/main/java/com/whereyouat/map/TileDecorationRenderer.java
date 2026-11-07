package com.whereyouat.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;

/**
 * TileDecorationRenderer - Responsable del renderizado de árboles, arbustos, rocas,
 * vegetación sumergida y elementos acuáticos con soporte de ordenamiento en profundidad (Y-sorting)
 * y recorte por agua mediante tijera OpenGL (GL Scissor).
 */
public class TileDecorationRenderer {

    private static final int TILE_SIZE = TileManager.TILE_SIZE;
    private static final int SCALE = TileManager.SCALE;

    public void drawDecorations(SpriteBatch batch, MapGenerator map,
                                float minX, float minY, float maxX, float maxY,
                                float playerWorldY, boolean inFront, OrthographicCamera camera,
                                TextureRegion[] tiles, TextureRegion[] illuminatedTreeCache,
                                TileTintPipeline tintPipeline, FoliageSwayManager foliageSway) {

        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 2);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 4);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 4);
        int playerFootY = (int) playerWorldY + TILE_SIZE;
        batch.setColor(1f, 1f, 1f, 1f);

        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                int treeIdx = map.mapTreeNum[col][row];
                if (treeIdx == 0) continue;

                float scale = SCALE;
                int sinkOffset = 0;
                if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    sinkOffset = (((col * 31 + row * 17) & 1) == 0 ? 2 : 3) * SCALE;
                } else if (treeIdx == MapGenerator.TILE_CATTAIL) {
                    sinkOffset = (2 + Math.abs(col * 17 + row * 31) % 3) * SCALE;
                } else if (treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    sinkOffset = 3 * SCALE;
                }

                int treeFootY = row * TILE_SIZE + TILE_SIZE + sinkOffset;
                boolean isLilipad = (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3);
                boolean treeInFront = !isLilipad && (treeFootY > playerFootY);
                if (treeInFront != inFront) continue;

                float screenX = col * TILE_SIZE, screenY = row * TILE_SIZE;
                TextureRegion tileRegion = tiles[treeIdx];
                if (tileRegion == null) continue;

                if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    int sinkTexels = (((col * 31 + row * 17) & 1) == 0 ? 2 : 3);
                    int sinkPixels = sinkTexels * SCALE;
                    TextureRegion spriteImage = tintPipeline.getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[col][row], tiles, null);
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + TILE_SIZE - drawWidth / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight + sinkPixels;
                    float baseY = screenY + TILE_SIZE + sinkPixels;

                    long swayKey = (((long) col) << 32) | (row & 0xFFFFFFFFL);
                    TileManager.BushSway bushSway = foliageSway.swayingBushes.get(swayKey);

                    boolean isShallow = map.isShallowWater[col][row];
                    boolean isDeep = map.isDeepWater[col][row];
                    if ((isShallow || isDeep) && camera != null) {
                        float waterSurfaceY = isDeep ? screenY : (screenY + TILE_SIZE / 2f);
                        float visibleHeight = waterSurfaceY - drawY;
                        if (visibleHeight > 0) {
                            Vector3 scissorTopLeft = camera.project(new Vector3(drawX, drawY, 0));
                            Vector3 scissorBottomRight = camera.project(new Vector3(drawX + drawWidth, drawY + visibleHeight, 0));
                            int scissorX = (int) Math.min(scissorTopLeft.x, scissorBottomRight.x);
                            int scissorY = (int) Math.min(scissorTopLeft.y, scissorBottomRight.y);
                            int scissorWidth = Math.max(1, (int) Math.abs(scissorBottomRight.x - scissorTopLeft.x));
                            int scissorHeight = Math.max(1, (int) Math.abs(scissorBottomRight.y - scissorTopLeft.y));

                            batch.flush();
                            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                            Gdx.gl.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                            if (bushSway != null && Math.abs(bushSway.topShift) > 0.05f) {
                                foliageSway.drawSheared(batch, spriteImage, drawX, drawY, drawWidth, drawHeight, baseY, bushSway.topShift);
                            } else {
                                batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                            }
                            batch.flush();
                            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

                            TextureRegion decorationFoam = tintPipeline.getFoamRegion(0);
                            if (decorationFoam != null) {
                                batch.setColor(1f, 1f, 1f, 0.9f);
                                batch.draw(decorationFoam, drawX + drawWidth / 2f - TILE_SIZE / 2f, waterSurfaceY - 3f, TILE_SIZE, TILE_SIZE);
                                batch.setColor(1f, 1f, 1f, 1f);
                            }
                        }
                    } else {
                        if (bushSway != null && Math.abs(bushSway.topShift) > 0.05f) {
                            foliageSway.drawSheared(batch, spriteImage, drawX, drawY, drawWidth, drawHeight, baseY, bushSway.topShift);
                        } else {
                            batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                        }
                    }
                } else if ((treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_WILLOW3)) {
                    TextureRegion spriteImage = (illuminatedTreeCache[treeIdx] != null) ? illuminatedTreeCache[treeIdx] : tileRegion;
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + TILE_SIZE - drawWidth / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight;
                    float baseY = screenY + TILE_SIZE;
                    long swayKey = (((long) col) << 32) | (row & 0xFFFFFFFFL);
                    TileManager.BushSway bushSway = foliageSway.swayingBushes.get(swayKey);
                    if (bushSway != null && Math.abs(bushSway.topShift) > 0.05f) {
                        foliageSway.drawSheared(batch, spriteImage, drawX, drawY, drawWidth, drawHeight, baseY, bushSway.topShift);
                    } else {
                        batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                    }
                } else if (isLilipad) {
                    float drawWidth = tileRegion.getRegionWidth() * scale, drawHeight = tileRegion.getRegionHeight() * scale;
                    int jitterX = (Math.abs(col * 13 + row * 7) % 7) - 3;
                    int jitterY = (Math.abs(col * 17 + row * 11) % 7) - 3;
                    float drawX = screenX + (TILE_SIZE - drawWidth) / 2f + jitterX;
                    float drawY = screenY + (TILE_SIZE - drawHeight) / 2f + jitterY;
                    batch.draw(tileRegion, drawX, drawY, drawWidth, drawHeight);
                } else if (treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    TextureRegion spriteImage = tintPipeline.getSubmergedSprite(treeIdx, 3, map.isSwampBiome[col][row], tiles, null);
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + (TILE_SIZE - drawWidth) / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight + 3 * SCALE;

                    boolean isShallow = map.isShallowWater[col][row];
                    boolean isDeep = map.isDeepWater[col][row];
                    if ((isShallow || isDeep) && camera != null) {
                        float waterSurfaceY = isDeep ? screenY : (screenY + TILE_SIZE / 2f);
                        float visibleHeight = waterSurfaceY - drawY;
                        if (visibleHeight > 0) {
                            Vector3 scissorTopLeft = camera.project(new Vector3(drawX, drawY, 0));
                            Vector3 scissorBottomRight = camera.project(new Vector3(drawX + drawWidth, drawY + visibleHeight, 0));
                            int scissorX = (int) Math.min(scissorTopLeft.x, scissorBottomRight.x);
                            int scissorY = (int) Math.min(scissorTopLeft.y, scissorBottomRight.y);
                            int scissorWidth = Math.max(1, (int) Math.abs(scissorBottomRight.x - scissorTopLeft.x));
                            int scissorHeight = Math.max(1, (int) Math.abs(scissorBottomRight.y - scissorTopLeft.y));

                            batch.flush();
                            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                            Gdx.gl.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                            batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                            batch.flush();
                            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

                            TextureRegion decorationFoam = tintPipeline.getFoamRegion(0);
                            if (decorationFoam != null) {
                                batch.setColor(1f, 1f, 1f, 0.9f);
                                batch.draw(decorationFoam, screenX, waterSurfaceY - 3f, TILE_SIZE, TILE_SIZE);
                                batch.setColor(1f, 1f, 1f, 1f);
                            }
                        }
                    } else {
                        batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                    }
                } else {
                    int sinkTexels = (treeIdx == MapGenerator.TILE_CATTAIL) ? (2 + Math.abs(col * 17 + row * 31) % 3) : 0;
                    int sinkPixels = sinkTexels * SCALE;
                    TextureRegion spriteImage = (treeIdx == MapGenerator.TILE_CATTAIL)
                            ? tintPipeline.getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[col][row], tiles, null) : tileRegion;
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + (TILE_SIZE - drawWidth) / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight + sinkPixels;

                    boolean isSwayingPlant = (treeIdx >= 27 && treeIdx <= 31) || (treeIdx >= 34 && treeIdx <= 41)
                            || treeIdx == MapGenerator.TILE_SHORT_SAND_GRASS || treeIdx == MapGenerator.TILE_TALL_SAND_GRASS
                            || treeIdx == MapGenerator.TILE_CATTAIL;

                    boolean tileIsShallow = map.isShallowWater[col][row];
                    boolean tileIsDeep = map.isDeepWater[col][row];
                    if ((tileIsShallow || tileIsDeep) && camera != null) {
                        float waterSurfaceY = tileIsDeep ? screenY : (screenY + TILE_SIZE / 2f);
                        float visibleHeight = waterSurfaceY - drawY;
                        if (visibleHeight <= 0) continue;

                        long swayKey = (((long) col) << 32) | (row & 0xFFFFFFFFL);
                        TileManager.BushSway bushSway = isSwayingPlant ? foliageSway.swayingBushes.get(swayKey) : null;
                        float shearMargin = (bushSway != null) ? (Math.abs(bushSway.topShift) + 2f) : 0f;

                        Vector3 scissorTopLeft = camera.project(new Vector3(drawX - shearMargin, drawY, 0));
                        Vector3 scissorBottomRight = camera.project(new Vector3(drawX + drawWidth + shearMargin, drawY + visibleHeight, 0));
                        int scissorX = (int) Math.min(scissorTopLeft.x, scissorBottomRight.x);
                        int scissorY = (int) Math.min(scissorTopLeft.y, scissorBottomRight.y);
                        int scissorWidth = Math.max(1, (int) Math.abs(scissorBottomRight.x - scissorTopLeft.x));
                        int scissorHeight = Math.max(1, (int) Math.abs(scissorBottomRight.y - scissorTopLeft.y));

                        batch.flush();
                        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                        Gdx.gl.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);

                        if (isSwayingPlant && !foliageSway.swayingBushes.isEmpty()) {
                            if (bushSway != null && Math.abs(bushSway.topShift) > 0.05f) {
                                float baseY = drawY + drawHeight;
                                foliageSway.drawSheared(batch, spriteImage, drawX, drawY, drawWidth, drawHeight, baseY, bushSway.topShift);
                            } else {
                                batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                            }
                        } else {
                            batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                        }

                        batch.flush();
                        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

                        TextureRegion decorationFoam = tintPipeline.getFoamRegion(0);
                        if (decorationFoam != null) {
                            batch.setColor(1f, 1f, 1f, 0.9f);
                            batch.draw(decorationFoam, screenX, waterSurfaceY - 3f, TILE_SIZE, TILE_SIZE);
                            batch.setColor(1f, 1f, 1f, 1f);
                        }
                        continue;
                    }

                    if (isSwayingPlant && !foliageSway.swayingBushes.isEmpty()) {
                        long swayKey = (((long) col) << 32) | (row & 0xFFFFFFFFL);
                        TileManager.BushSway bushSway = foliageSway.swayingBushes.get(swayKey);
                        if (bushSway != null && Math.abs(bushSway.topShift) > 0.05f) {
                            float baseY = drawY + drawHeight;
                            foliageSway.drawSheared(batch, spriteImage, drawX, drawY, drawWidth, drawHeight, baseY, bushSway.topShift);
                        } else {
                            batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                        }
                    } else {
                        batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                    }
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void drawAllDecorations(SpriteBatch batch, MapGenerator map,
                                  float minX, float minY, float maxX, float maxY,
                                  TextureRegion[] tiles, TextureRegion[] illuminatedTreeCache,
                                  TileTintPipeline tintPipeline) {

        int startCol = Math.max(0, (int) (minX / TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TILE_SIZE) - 2);
        int endCol = Math.min(map.mapCols - 1, (int) (maxX / TILE_SIZE) + 4);
        int endRow = Math.min(map.mapRows - 1, (int) (maxY / TILE_SIZE) + 4);
        batch.setColor(1f, 1f, 1f, 1f);

        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                int treeIdx = map.mapTreeNum[col][row];
                if (treeIdx == 0) continue;

                float scale = SCALE;
                float screenX = col * TILE_SIZE, screenY = row * TILE_SIZE;
                TextureRegion tileRegion = tiles[treeIdx];
                if (tileRegion == null) continue;

                boolean isLilipad = (treeIdx >= MapGenerator.TILE_LILIPAD1 && treeIdx <= MapGenerator.TILE_LILIPAD3);

                if (treeIdx == MapGenerator.TILE_MANGROVE) {
                    int sinkTexels = (((col * 31 + row * 17) & 1) == 0 ? 2 : 3);
                    int sinkPixels = sinkTexels * SCALE;
                    TextureRegion spriteImage = tintPipeline.getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[col][row], tiles, null);
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + TILE_SIZE - drawWidth / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight + sinkPixels;
                    batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                } else if ((treeIdx >= 12 && treeIdx <= 22) || (treeIdx >= MapGenerator.TILE_WILLOW1 && treeIdx <= MapGenerator.TILE_WILLOW3)) {
                    TextureRegion spriteImage = (illuminatedTreeCache[treeIdx] != null) ? illuminatedTreeCache[treeIdx] : tileRegion;
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + TILE_SIZE - drawWidth / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight;
                    batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                } else if (isLilipad) {
                    float drawWidth = tileRegion.getRegionWidth() * scale, drawHeight = tileRegion.getRegionHeight() * scale;
                    int jitterX = (Math.abs(col * 13 + row * 7) % 7) - 3;
                    int jitterY = (Math.abs(col * 17 + row * 11) % 7) - 3;
                    float drawX = screenX + (TILE_SIZE - drawWidth) / 2f + jitterX;
                    float drawY = screenY + (TILE_SIZE - drawHeight) / 2f + jitterY;
                    batch.draw(tileRegion, drawX, drawY, drawWidth, drawHeight);
                } else if (treeIdx == MapGenerator.TILE_WATER_ROCK) {
                    TextureRegion spriteImage = tintPipeline.getSubmergedSprite(treeIdx, 3, map.isSwampBiome[col][row], tiles, null);
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + (TILE_SIZE - drawWidth) / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight + 3 * SCALE;
                    batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                } else {
                    int sinkTexels = (treeIdx == MapGenerator.TILE_CATTAIL) ? (2 + Math.abs(col * 17 + row * 31) % 3) : 0;
                    int sinkPixels = sinkTexels * SCALE;
                    TextureRegion spriteImage = (treeIdx == MapGenerator.TILE_CATTAIL)
                            ? tintPipeline.getSubmergedSprite(treeIdx, sinkTexels, map.isSwampBiome[col][row], tiles, null) : tileRegion;
                    float drawWidth = spriteImage.getRegionWidth() * scale, drawHeight = spriteImage.getRegionHeight() * scale;
                    float drawX = screenX + (TILE_SIZE - drawWidth) / 2f;
                    float drawY = screenY + TILE_SIZE - drawHeight + sinkPixels;
                    batch.draw(spriteImage, drawX, drawY, drawWidth, drawHeight);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }
}

