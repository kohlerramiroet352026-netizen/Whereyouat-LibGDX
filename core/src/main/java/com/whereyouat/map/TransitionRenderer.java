package com.whereyouat.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.List;

public class TransitionRenderer {
    public static final int TRANS_NONE       = 0;
    public static final int TRANS_SIDE       = 1;
    public static final int TRANS_OUT_CORNER = 2;
    public static final int TRANS_IN_CORNER  = 3;

    public static final int ROT_0   = 0;
    public static final int ROT_90  = 1;
    public static final int ROT_180 = 2;
    public static final int ROT_270 = 3;

    private final TextureRegion[][][] rotated = new TextureRegion[3][4][11];
    private final TextureRegion[][][] rotatedSwamp = new TextureRegion[3][4][11];

    private final List<Texture> generatedTextures = new ArrayList<>();

    public TransitionRenderer() {
    }

    public void load(Texture sideTex, Texture outCornerTex, Texture inCornerTex, TileManager tileMgr) {
        Pixmap sidePixmap = extractPixmap(sideTex);
        Pixmap outCornerPixmap = extractPixmap(outCornerTex);
        Pixmap inCornerPixmap  = extractPixmap(inCornerTex);

        Pixmap[] basePixmaps = { sidePixmap, outCornerPixmap, inCornerPixmap };

        for (int typeIdx = 0; typeIdx < 3; typeIdx++) {
            Pixmap basePixmap = basePixmaps[typeIdx];
            if (basePixmap == null) continue;

            for (int rotation = 0; rotation < 4; rotation++) {
                Pixmap rotatedPixmap = rotateCW(basePixmap, rotation);

                for (int blendStep = 0; blendStep <= 10; blendStep++) {
                    float blend = blendStep / 10f;

                    if (blendStep == 0) {
                        rotated[typeIdx][rotation][blendStep] = pixmapToRegion(rotatedPixmap);
                        rotatedSwamp[typeIdx][rotation][blendStep] = rotated[typeIdx][rotation][blendStep];
                    } else {
                        Pixmap snowPixmap = TileManager.tintGrass(rotatedPixmap, blend);
                        rotated[typeIdx][rotation][blendStep] = pixmapToRegion(snowPixmap);
                        snowPixmap.dispose();

                        Pixmap swampPixmap = TileManager.tintSwampGrass(rotatedPixmap, blend);
                        rotatedSwamp[typeIdx][rotation][blendStep] = pixmapToRegion(swampPixmap);
                        swampPixmap.dispose();
                    }
                }

                if (rotation != 0) {
                    rotatedPixmap.dispose();
                }
            }
        }

        sidePixmap.dispose();
        outCornerPixmap.dispose();
        inCornerPixmap.dispose();
    }

    public void load(Texture sideTex, Texture outCornerTex, Texture inCornerTex) {
        Pixmap sidePixmap = extractPixmap(sideTex);
        Pixmap outCornerPixmap = extractPixmap(outCornerTex);
        Pixmap inCornerPixmap  = extractPixmap(inCornerTex);

        Pixmap[] basePixmaps = { sidePixmap, outCornerPixmap, inCornerPixmap };

        for (int typeIdx = 0; typeIdx < 3; typeIdx++) {
            Pixmap basePixmap = basePixmaps[typeIdx];
            if (basePixmap == null) continue;

            for (int rotation = 0; rotation < 4; rotation++) {
                Pixmap rotatedPixmap = rotateCW(basePixmap, rotation);
                TextureRegion region = pixmapToRegion(rotatedPixmap);
                for (int blendStep = 0; blendStep <= 10; blendStep++) {
                    rotated[typeIdx][rotation][blendStep] = region;
                    rotatedSwamp[typeIdx][rotation][blendStep] = region;
                }
                if (rotation != 0) rotatedPixmap.dispose();
            }
        }

        sidePixmap.dispose();
        outCornerPixmap.dispose();
        inCornerPixmap.dispose();
    }

    private Pixmap extractPixmap(Texture tex) {
        if (tex == null) return null;
        if (!tex.getTextureData().isPrepared()) {
            tex.getTextureData().prepare();
        }
        Pixmap pixmap = tex.getTextureData().consumePixmap();

        Pixmap copy = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), Pixmap.Format.RGBA8888);
        copy.drawPixmap(pixmap, 0, 0);
        if (tex.getTextureData().disposePixmap()) {
            pixmap.dispose();
        }
        return copy;
    }

    private TextureRegion pixmapToRegion(Pixmap pixmap) {
        Texture tex = new Texture(pixmap);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        generatedTextures.add(tex);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true);
        return region;
    }

    public void draw(SpriteBatch batch, int type, int rot, float x, float y, float size,
                     int blendStep, boolean isSwamp) {
        if (type < TRANS_SIDE || type > TRANS_IN_CORNER) return;
        if (rot < 0 || rot > 3) rot = 0;
        if (blendStep < 0) blendStep = 0;
        if (blendStep > 10) blendStep = 10;

        TextureRegion region = isSwamp
                ? rotatedSwamp[type - 1][rot][blendStep]
                : rotated[type - 1][rot][blendStep];

        if (region == null) return;

        batch.draw(region, x, y, size, size);
    }

    public void draw(SpriteBatch batch, int type, int rot, float x, float y, float size,
                     float r, float g, float b, float a) {
        draw(batch, type, rot, x, y, size, 0, false);
    }

    private static Pixmap rotateCW(Pixmap src, int steps) {
        steps = ((steps % 4) + 4) % 4;
        if (steps == 0) return src;

        int w = src.getWidth();
        int h = src.getHeight();

        Pixmap current = src;
        for (int s = 0; s < steps; s++) {
            int cw = current.getWidth();
            int ch = current.getHeight();
            Pixmap rotated = new Pixmap(ch, cw, Pixmap.Format.RGBA8888);
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    rotated.drawPixel(ch - 1 - y, x, current.getPixel(x, y));
                }
            }
            if (current != src) {
                current.dispose();
            }
            current = rotated;
        }
        return current;
    }

    public static class TransInfo {
        public int type;
        public int rot;
        public TransInfo(int type, int rot) { this.type = type; this.rot = rot; }
    }

    public static TransInfo classify(boolean north, boolean south, boolean east, boolean west,
                                     boolean northEast, boolean southEast, boolean southWest, boolean northWest) {
        int cardinalCount = (north ? 1 : 0) + (south ? 1 : 0) + (east ? 1 : 0) + (west ? 1 : 0);

        if (cardinalCount == 1) {
            if (south) return new TransInfo(TRANS_SIDE, ROT_0);
            if (west)  return new TransInfo(TRANS_SIDE, ROT_90);
            if (north) return new TransInfo(TRANS_SIDE, ROT_180);
            return new TransInfo(TRANS_SIDE, ROT_270);
        }

        if (cardinalCount == 2) {
            if (south && west) return new TransInfo(TRANS_IN_CORNER, ROT_0);
            if (north && west) return new TransInfo(TRANS_IN_CORNER, ROT_90);
            if (north && east) return new TransInfo(TRANS_IN_CORNER, ROT_180);
            if (south && east) return new TransInfo(TRANS_IN_CORNER, ROT_270);
            if (north && south) return new TransInfo(TRANS_SIDE, ROT_180);
            if (east && west) return new TransInfo(TRANS_SIDE, ROT_90);
        }

        if (cardinalCount == 3) {
            if (!north) return new TransInfo(TRANS_IN_CORNER, ROT_0);
            if (!east)  return new TransInfo(TRANS_IN_CORNER, ROT_90);
            if (!south) return new TransInfo(TRANS_IN_CORNER, ROT_180);
            if (!west)  return new TransInfo(TRANS_IN_CORNER, ROT_270);
        }

        if (cardinalCount >= 4) {
            return new TransInfo(TRANS_NONE, ROT_0);
        }

        if (southWest) return new TransInfo(TRANS_OUT_CORNER, ROT_0);
        if (northWest) return new TransInfo(TRANS_OUT_CORNER, ROT_90);
        if (northEast) return new TransInfo(TRANS_OUT_CORNER, ROT_180);
        if (southEast) return new TransInfo(TRANS_OUT_CORNER, ROT_270);

        return new TransInfo(TRANS_NONE, ROT_0);
    }

    public void dispose() {
        for (Texture tex : generatedTextures) {
            tex.dispose();
        }
        generatedTextures.clear();
    }
}
