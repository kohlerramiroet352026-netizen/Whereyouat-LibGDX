package com.whereyouat.monster;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Entity;
import com.whereyouat.map.TileManager;
import com.whereyouat.screens.GameScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Frogger monster entity for Where you at (LibGDX Edition).
 *
 * Implements:
 * - Random 4-direction AI movement (picks a new direction every ~2 seconds)
 * - 2-frame walk animation per direction
 * - Proper LibGDX texture loading via Gdx.files.internal
 * - Y-down TextureRegion flipping (consistent with Player/Bird)
 * - Disposable texture lifecycle
 */
public class MON_frogger extends Entity {

    public GameScreen gp;
    public String name;

    public static final float HIT_OFFSET_X = 3f;
    public static final float HIT_OFFSET_Y = 18f;
    public static final float HIT_W = 42f;
    public static final float HIT_H = 32f;

    private final TextureRegion[][] walkFrames = new TextureRegion[4][4]; // [dir][frame]
    private final List<Texture> textures = new ArrayList<>();

    private static final int DIR_UP    = 0;
    private static final int DIR_DOWN  = 1;
    private static final int DIR_LEFT  = 2;
    private static final int DIR_RIGHT = 3;

    // Track last horizontal direction so UP/DOWN keep the correct facing side
    private String lastHorizontal = "right";

    // --- AI timer: picks a new random direction every ACTION_INTERVAL frames ---
    private int actionLockCounter = 0;
    private static final int ACTION_INTERVAL = 120; // ~2 seconds at 60fps

    // --- Movement speed (pixels per second) ---
    private static final float FROGGER_SPEED = 40f; // 1 px/frame * 40fps

    private final Random random = new Random();

    public MON_frogger() {
        this((GameScreen) null);
    }

    public MON_frogger(GameScreen gp) {
        super(0, 0, FROGGER_SPEED);
        this.gp = gp;

        name = "Frogger";
        maxLife = 4;
        life = maxLife;

        solidArea.set(HIT_OFFSET_X, HIT_OFFSET_Y, HIT_W, HIT_H);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();
    }

    public MON_frogger(float worldX, float worldY) {
        this((GameScreen) null);
        this.worldX = worldX;
        this.worldY = worldY;
    }

    /**
     * Loads the frogger sprite sheets from the assets folder.
     * Uses the same pattern as Player.loadRegion(): Gdx.files.internal,
     * Nearest filtering, and Y-flip for the Y-down coordinate system.
     *
     * Expected assets:
     *   monster/frogger_move1.png  (idle / frame 1)
     *   monster/frogger_move2.png  (walk  / frame 2)
     */
    private void loadSprites() {
        // Right-facing movement frames (1 to 4)
        TextureRegion frame1 = loadRegion("monster/frogger_move1.png");
        TextureRegion frame2 = loadRegion("monster/frogger_move2.png");
        TextureRegion frame3 = loadRegion("monster/frogger_move3.png");
        TextureRegion frame4 = loadRegion("monster/frogger_move4.png");

        // Left-facing movement frames (5 to 8)
        TextureRegion frame5 = loadRegion("monster/frogger_move5.png");
        TextureRegion frame6 = loadRegion("monster/frogger_move6.png");
        TextureRegion frame7 = loadRegion("monster/frogger_move7.png");
        TextureRegion frame8 = loadRegion("monster/frogger_move8.png");

        // DIR_RIGHT (3): frames 1-4
        walkFrames[DIR_RIGHT][0] = frame1;
        walkFrames[DIR_RIGHT][1] = frame2;
        walkFrames[DIR_RIGHT][2] = frame3;
        walkFrames[DIR_RIGHT][3] = frame4;

        // DIR_LEFT (2): frames 5-8
        walkFrames[DIR_LEFT][0] = frame5;
        walkFrames[DIR_LEFT][1] = frame6;
        walkFrames[DIR_LEFT][2] = frame7;
        walkFrames[DIR_LEFT][3] = frame8;

        // DIR_UP (0) and DIR_DOWN (1): default right frames
        walkFrames[DIR_UP][0] = frame1;
        walkFrames[DIR_UP][1] = frame2;
        walkFrames[DIR_UP][2] = frame3;
        walkFrames[DIR_UP][3] = frame4;

        walkFrames[DIR_DOWN][0] = frame1;
        walkFrames[DIR_DOWN][1] = frame2;
        walkFrames[DIR_DOWN][2] = frame3;
        walkFrames[DIR_DOWN][3] = frame4;
    }

    /**
     * Loads a single texture region, matching the Player's loading convention:
     * - Nearest-neighbour filtering (pixel art)
     * - Flipped vertically for Y-down rendering
     */
    private TextureRegion loadRegion(String path) {
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true); // Y-down, same as Player
        return tr;
    }

    /**
     * AI behaviour: every ACTION_INTERVAL ticks (~2 sec), pick a random direction.
     */
    private void setAction() {
        actionLockCounter++;

        if (actionLockCounter >= ACTION_INTERVAL) {
            int roll = random.nextInt(100) + 1;

            if (roll <= 25) {
                direction = "up";
            } else if (roll <= 50) {
                direction = "down";
            } else if (roll <= 75) {
                direction = "left";
            } else {
                direction = "right";
            }

            actionLockCounter = 0;
        }
    }

    @Override
    public void update(float delta) {
        setAction();

        // Move in the current direction (speed is in px/sec, delta is in seconds)
        switch (direction) {
            case "up":    worldY -= speed * delta; break;
            case "down":  worldY += speed * delta; break;
            case "left":  worldX -= speed * delta; lastHorizontal = "left"; break;
            case "right": worldX += speed * delta; lastHorizontal = "right"; break;
        }

        // Walk animation: cycle between frames 1, 2, 3, 4
        spriteCounter += delta * 60f; // normalise to ~60fps ticks
        if (spriteCounter >= 12f) {   // swap every 12 ticks (~0.2 sec)
            spriteNum++;
            if (spriteNum > 4) {
                spriteNum = 1;
            }
            spriteCounter = 0;
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        int dirIndex;
        switch (direction) {
            case "left":
                dirIndex = DIR_LEFT;
                break;
            case "right":
                dirIndex = DIR_RIGHT;
                break;
            case "up":
            case "down":
            default:
                // Keep the last horizontal direction (left or right) when moving vertically
                dirIndex = "left".equals(lastHorizontal) ? DIR_LEFT : DIR_RIGHT;
                break;
        }

        int frameIndex = Math.max(0, Math.min(3, spriteNum - 1));
        TextureRegion region = walkFrames[dirIndex][frameIndex];

        if (region != null) {
            batch.draw(region, worldX, worldY,
                       TileManager.TILE_SIZE, TileManager.TILE_SIZE);
        }
    }


    @Override
    public void dispose() {
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
    }
}
