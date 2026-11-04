package com.whereyouat.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * TransitionRenderer — faithful 1:1 port of the original Swing TransitionRenderer.
 *
 * Pre-caches all rotation × blend level variants using pixel-level tinting
 * (tintGrass / tintSwampGrass) via Pixmap, exactly matching the original.
 *
 * Cache structure: [type 0-2][rotation 0-3][blendStep 0-10]
 *   type 0 = SIDE, type 1 = OUT_CORNER, type 2 = IN_CORNER
 */
public class TransitionRenderer {

    public static final int TRANS_NONE       = 0;
    public static final int TRANS_SIDE       = 1;
    public static final int TRANS_OUT_CORNER = 2;
    public static final int TRANS_IN_CORNER  = 3;

    public static final int ROT_0   = 0;
    public static final int ROT_90  = 1;
    public static final int ROT_180 = 2;
    public static final int ROT_270 = 3;

    // Pre-rotated and tinted image cache: [type][rotation][blendLevel 0-10]
    private final TextureRegion[][][] rotated = new TextureRegion[3][4][11];
    private final TextureRegion[][][] rotatedSwamp = new TextureRegion[3][4][11];

    private final List<Texture> generatedTextures = new ArrayList<>();

    public TransitionRenderer() {
    }

    /**
     * Load and pre-cache all transition sprite variants.
     * Called by TileManager after tile pixmaps are loaded.
     *
     * @param sideTex     transition_grass_sides texture
     * @param outCornerTex transition_grass_out_corner texture
     * @param inCornerTex  transition_grass_in_corner texture
     * @param tileMgr     TileManager reference for tinting methods
     */
    public void load(Texture sideTex, Texture outCornerTex, Texture inCornerTex, TileManager tileMgr) {
        // Extract pixmaps from textures
        Pixmap sidePm = extractPixmap(sideTex);
        Pixmap outCPm = extractPixmap(outCornerTex);
        Pixmap inCPm  = extractPixmap(inCornerTex);

        Pixmap[] basePms = { sidePm, outCPm, inCPm };

        for (int typeIdx = 0; typeIdx < 3; typeIdx++) {
            Pixmap basePm = basePms[typeIdx];
            if (basePm == null) continue;

            for (int r = 0; r < 4; r++) {
                Pixmap rotPm = rotateCW(basePm, r);

                for (int b = 0; b <= 10; b++) {
                    float blend = b / 10f;

                    if (b == 0) {
                        rotated[typeIdx][r][b] = pmToRegion(rotPm);
                        rotatedSwamp[typeIdx][r][b] = rotated[typeIdx][r][b]; // Same at blend 0
                    } else {
                        Pixmap snowPm = TileManager.tintGrass(rotPm, blend);
                        rotated[typeIdx][r][b] = pmToRegion(snowPm);
                        snowPm.dispose();

                        Pixmap swampPm = TileManager.tintSwampGrass(rotPm, blend);
                        rotatedSwamp[typeIdx][r][b] = pmToRegion(swampPm);
                        swampPm.dispose();
                    }
                }

                // Dispose rotated pixmap if it's not the original
                if (r != 0) {
                    rotPm.dispose();
                }
            }
        }

        // Dispose extracted pixmaps
        sidePm.dispose();
        outCPm.dispose();
        inCPm.dispose();
    }

    /**
     * Backwards-compatible load for existing code that doesn't pass TileManager.
     */
    public void load(Texture sideTex, Texture outCornerTex, Texture inCornerTex) {
        // Fallback: just store simple regions without tinting
        Pixmap sidePm = extractPixmap(sideTex);
        Pixmap outCPm = extractPixmap(outCornerTex);
        Pixmap inCPm  = extractPixmap(inCornerTex);

        Pixmap[] basePms = { sidePm, outCPm, inCPm };

        for (int typeIdx = 0; typeIdx < 3; typeIdx++) {
            Pixmap basePm = basePms[typeIdx];
            if (basePm == null) continue;

            for (int r = 0; r < 4; r++) {
                Pixmap rotPm = rotateCW(basePm, r);
                TextureRegion reg = pmToRegion(rotPm);
                for (int b = 0; b <= 10; b++) {
                    rotated[typeIdx][r][b] = reg;
                    rotatedSwamp[typeIdx][r][b] = reg;
                }
                if (r != 0) rotPm.dispose();
            }
        }

        sidePm.dispose();
        outCPm.dispose();
        inCPm.dispose();
    }

    /** Extract a Pixmap from a Texture by reading its texture data. */
    private Pixmap extractPixmap(Texture tex) {
        if (tex == null) return null;
        if (!tex.getTextureData().isPrepared()) {
            tex.getTextureData().prepare();
        }
        Pixmap pm = tex.getTextureData().consumePixmap();
        // Make a copy since consumePixmap may return a managed pixmap
        Pixmap copy = new Pixmap(pm.getWidth(), pm.getHeight(), Pixmap.Format.RGBA8888);
        copy.drawPixmap(pm, 0, 0);
        if (tex.getTextureData().disposePixmap()) {
            pm.dispose();
        }
        return copy;
    }

    /** Create a TextureRegion from a Pixmap, Y-flipped for Y-down camera. */
    private TextureRegion pmToRegion(Pixmap pm) {
        Texture tex = new Texture(pm);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        generatedTextures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        return tr;
    }

    /**
     * Renders a transition overlay at the given position.
     *
     * @param batch     SpriteBatch
     * @param type      TRANS_SIDE, TRANS_OUT_CORNER, or TRANS_IN_CORNER
     * @param rot       ROT_0..ROT_270
     * @param x         World X position
     * @param y         World Y position
     * @param size      Tile size in pixels
     * @param blendStep 0-10 for snow/swamp tint level
     * @param isSwamp   true to use swamp tint, false for snow tint
     */
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

    /**
     * Backwards-compatible draw with color tinting (legacy).
     */
    public void draw(SpriteBatch batch, int type, int rot, float x, float y, float size,
                     float r, float g, float b, float a) {
        draw(batch, type, rot, x, y, size, 0, false);
    }

    // =========================================================================
    // Rotation helper (Pixmap-based, matching original AffineTransform rotations)
    // =========================================================================

    private static Pixmap rotateCW(Pixmap src, int steps) {
        steps = ((steps % 4) + 4) % 4;
        if (steps == 0) return src; // Return original, caller must not dispose

        int w = src.getWidth();
        int h = src.getHeight();

        Pixmap current = src;
        for (int s = 0; s < steps; s++) {
            int cw = current.getWidth();
            int ch = current.getHeight();
            Pixmap rotated = new Pixmap(ch, cw, Pixmap.Format.RGBA8888);
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    // 90° CW: (x, y) -> (ch - 1 - y, x)
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

    // =========================================================================
    // Static analysis — determines transition type and rotation for one tile
    // =========================================================================

    public static class TransInfo {
        public int type;
        public int rot;
        public TransInfo(int type, int rot) { this.type = type; this.rot = rot; }
    }

    public static TransInfo classify(boolean n, boolean s, boolean e, boolean w,
                                     boolean ne, boolean se, boolean sw, boolean nw) {
        int cardinalCount = (n ? 1 : 0) + (s ? 1 : 0) + (e ? 1 : 0) + (w ? 1 : 0);

        if (cardinalCount == 1) {
            if (s) return new TransInfo(TRANS_SIDE, ROT_0);
            if (w) return new TransInfo(TRANS_SIDE, ROT_90);
            if (n) return new TransInfo(TRANS_SIDE, ROT_180);
            return new TransInfo(TRANS_SIDE, ROT_270);
        }

        if (cardinalCount == 2) {
            if (s && w) return new TransInfo(TRANS_IN_CORNER, ROT_0);
            if (n && w) return new TransInfo(TRANS_IN_CORNER, ROT_90);
            if (n && e) return new TransInfo(TRANS_IN_CORNER, ROT_180);
            if (s && e) return new TransInfo(TRANS_IN_CORNER, ROT_270);
            if (n && s) return new TransInfo(TRANS_SIDE, ROT_180);
            if (e && w) return new TransInfo(TRANS_SIDE, ROT_90);
        }

        if (cardinalCount == 3) {
            if (!n) return new TransInfo(TRANS_IN_CORNER, ROT_0);
            if (!e) return new TransInfo(TRANS_IN_CORNER, ROT_90);
            if (!s) return new TransInfo(TRANS_IN_CORNER, ROT_180);
            if (!w) return new TransInfo(TRANS_IN_CORNER, ROT_270);
        }

        if (cardinalCount >= 4) {
            return new TransInfo(TRANS_NONE, ROT_0);
        }

        if (sw) return new TransInfo(TRANS_OUT_CORNER, ROT_0);
        if (nw) return new TransInfo(TRANS_OUT_CORNER, ROT_90);
        if (ne) return new TransInfo(TRANS_OUT_CORNER, ROT_180);
        if (se) return new TransInfo(TRANS_OUT_CORNER, ROT_270);

        return new TransInfo(TRANS_NONE, ROT_0);
    }

    public void dispose() {
        for (Texture tex : generatedTextures) {
            tex.dispose();
        }
        generatedTextures.clear();
    }
}
