package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.whereyouat.WhereYouAtGame;
import com.whereyouat.audio.AudioManager;
import com.whereyouat.effects.FootprintManager;
import com.whereyouat.effects.SnowParticleSystem;
import com.whereyouat.entity.BirdManager;
import com.whereyouat.entity.Player;
import com.whereyouat.lighting.DayNightCycle;
import com.whereyouat.lighting.LightingEngine;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;
import com.whereyouat.save.WorldSaveManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GameScreen - Active gameplay screen for Where you at (LibGDX Edition).
 *
 * Coordinates:
 * - Procedural world generation (512x512)
 * - Y-down camera tracking and viewport management
 * - SpriteBatch depth sorting (ground, footprints, foliage, critters, player, weather)
 * - Dynamic GPU lighting (FBO + multiplicative blend)
 * - OpenAL surface-adaptive audio and OST streaming
 */
public class GameScreen extends ScreenAdapter {

    public final WhereYouAtGame game;

    // Viewports and cameras
    public final OrthographicCamera camera;
    public final Viewport viewport;
    public final OrthographicCamera hudCamera;
    public final Viewport hudViewport;

    // Core game systems
    public final MapGenerator mapGen;
    public final TileManager tileM;
    public final Player player;
    public final DayNightCycle dayNight;
    public final FootprintManager footprintM;
    public final SnowParticleSystem snowPS;
    public final BirdManager birdM;
    public final LightingEngine lighting;
    public final AudioManager audio;

    // World metadata
    public final String worldFileName;
    public final String worldName;
    public final long seed;
    public final long createdTime;

    // HUD and Fonts
    private BitmapFont fontSmall;
    private BitmapFont fontMedium;
    private BitmapFont fontLarge;
    private Texture heartTexture;
    private Texture whiteTex;
    private TextureRegion whitePixel;
    private float animTime = 0f;
    private TextureRegion heartRegion;
    private Texture healthBarSheet;
    private Texture heartContainerSheet;
    private final TextureRegion[] heartContainerFrames = new TextureRegion[11];
    private final TextureRegion[] healthBarFrames = new TextureRegion[9];
    private final List<Texture> hudTextures = new ArrayList<>();

    // GlyphLayout for centering text
    private final GlyphLayout glyphLayout = new GlyphLayout();

    // Mouse position in HUD space (for death screen button)
    private final Vector3 hudMousePos = new Vector3();

    // Save and autosave timer
    private float autoSaveTimer = 60f; // Save every 60s
    private boolean isPaused = false;
    private boolean showDeathScreen = false;
    private boolean showRespawnScreen = false;
    private float saveFeedbackTimer = 0f;

    // Button textures for pause/death/respawn screens
    private Texture buttonSlotTex;
    private TextureRegion buttonSlotRegion;
    private Texture backButtonTex;
    private TextureRegion backButtonRegion;

    public GameScreen(WhereYouAtGame game, long seed, String worldName, String worldFileName) {
        this.game = game;
        this.seed = seed;
        this.worldName = (worldName != null && !worldName.isEmpty()) ? worldName : "World";
        this.worldFileName = worldFileName;
        this.createdTime = System.currentTimeMillis();

        // 1. Setup World Camera with Y-down orientation
        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new ExtendViewport(768, 576, camera);

        // 2. Setup HUD Camera for fixed on-screen overlay
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(true, 768, 576);
        hudViewport = new FitViewport(768, 576, hudCamera);

        // 3. Generate world
        Gdx.app.log("GameScreen", "Generating 512x512 procedural world with seed: " + seed);
        mapGen = new MapGenerator(512, 512, seed, String.valueOf(seed));
        mapGen.generate();

        // 4. Initialize managers and systems
        tileM = new TileManager();
        player = new Player();
        player.initSpawn(mapGen);

        dayNight = new DayNightCycle();
        footprintM = new FootprintManager();
        snowPS = new SnowParticleSystem(seed);
        birdM = new BirdManager(seed);
        birdM.initForWorld(seed, mapGen, player);
        tileM.setSnowPS(snowPS);

        lighting = new LightingEngine(768, 576);
        audio = new AudioManager();

        // Connect player step listener to footprint and audio engines
        player.setStepListener(new Player.PlayerStepListener() {
            @Override
            public void onStep(float footX, float footY, double angle, boolean isLeftFoot, int footCol, int footRow) {
                int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);
                if (waterLevel == 0) {
                    footprintM.addFootprint(footX, footY, angle, isLeftFoot);
                }
                audio.playFootstep(mapGen, tileM, footCol, footRow);
            }

            @Override
            public void onBushRustle() {
                audio.playBushRustle();
            }
        });

        // 5. Load HUD assets and Fonts
        loadHUDAssets();

        // Fade out menu music on world entry (instead of stopping it abruptly from menu screens)
        game.fadeOutMenuMusic();
    }

    public GameScreen(WhereYouAtGame game, long seed, String seedString) {
        this(game, seed, "New World", null);
    }

    private void loadHUDAssets() {
        // Load TrueType pixel font
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter param = new FreeTypeFontParameter();
            param.flip = true;
            param.size = 11;
            param.shadowOffsetX = 1;
            param.shadowOffsetY = 1;
            param.shadowColor = new Color(0f, 0f, 0f, 0.85f);
            fontSmall = gen.generateFont(param);
            fontSmall.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            param.size = 16;
            fontMedium = gen.generateFont(param);
            fontMedium.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            param.size = 28;
            param.shadowOffsetX = 2;
            param.shadowOffsetY = 2;
            fontLarge = gen.generateFont(param);
            fontLarge.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            gen.dispose();
        } else {
            fontSmall = new BitmapFont();
            fontMedium = new BitmapFont();
            fontLarge = new BitmapFont();
        }

        // White pixel for clean geometric UI elements
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
        whiteTex = new Texture(p);
        p.dispose();
        hudTextures.add(whiteTex);

        // Button textures for pause/death/respawn screens
        String slotPath = Gdx.files.internal("buttons/button_slot.png").exists() ? "buttons/button_slot.png" : "button_slot.png";
        if (Gdx.files.internal(slotPath).exists()) {
            buttonSlotTex = new Texture(Gdx.files.internal(slotPath));
            buttonSlotTex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(buttonSlotTex);
            buttonSlotRegion = new TextureRegion(buttonSlotTex);
            buttonSlotRegion.flip(false, true);
        }
        String backPath = Gdx.files.internal("buttons/back_button.png").exists() ? "buttons/back_button.png" : "back_button.png";
        if (Gdx.files.internal(backPath).exists()) {
            backButtonTex = new Texture(Gdx.files.internal(backPath));
            backButtonTex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(backButtonTex);
            backButtonRegion = new TextureRegion(backButtonTex);
            backButtonRegion.flip(false, true);
        }
        whitePixel = new TextureRegion(whiteTex);

        // Heart sprite (13x12)
        if (Gdx.files.internal("player/heart.png").exists()) {
            heartTexture = new Texture(Gdx.files.internal("player/heart.png"));
            heartTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(heartTexture);
            heartRegion = new TextureRegion(heartTexture);
            heartRegion.flip(false, true);
        }

        // Heart container frame sheet (128x96, 2 columns x 6 rows, each frame 64x16, 11 usable frames)
        if (Gdx.files.internal("player/heartcontainer.png").exists()) {
            heartContainerSheet = new Texture(Gdx.files.internal("player/heartcontainer.png"));
            heartContainerSheet.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(heartContainerSheet);
            final int FRAME_W = 64, FRAME_H = 16, COLS = 2;
            for (int i = 0; i < heartContainerFrames.length; i++) {
                int col = i % COLS;
                int row = i / COLS;
                heartContainerFrames[i] = new TextureRegion(heartContainerSheet, col * FRAME_W, row * FRAME_H, FRAME_W, FRAME_H);
                heartContainerFrames[i].flip(false, true);
            }
        }

        // Health bar frames (70x144, 9 frames of 70x16)
        if (Gdx.files.internal("player/healthbar.png").exists()) {
            healthBarSheet = new Texture(Gdx.files.internal("player/healthbar.png"));
            healthBarSheet.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(healthBarSheet);
            for (int i = 0; i < 9; i++) {
                healthBarFrames[i] = new TextureRegion(healthBarSheet, 0, i * 16, 70, 16);
                healthBarFrames[i].flip(false, true);
            }
        }
    }

    @Override
    public void render(float delta) {
        // Clamp delta to prevent spiral of death on long frames
        float dt = Math.min(delta, 0.1f);
        animTime += dt;
        if (saveFeedbackTimer > 0f) saveFeedbackTimer -= dt;

        // Handle Escape / Pause (only when alive and not on death screen)
        if (!showDeathScreen && !showRespawnScreen && Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            isPaused = !isPaused;
            if (isPaused) {
                saveProgress();
            }
        }

        if (!isPaused && !showDeathScreen && !showRespawnScreen) {
            int livesBefore = player.lives;
            updateGame(dt);
            // Check if player lost a life this frame
            if (player.lives < livesBefore && !player.dead) {
                // Lost a life but still alive: show respawn screen, random spawn
                showRespawnScreen = true;
                player.randomSpawn(mapGen);
            } else if (player.dead && !showDeathScreen) {
                showDeathScreen = true;
            }
        }
        renderScene();
    }

    private void updateGame(float delta) {
        // 1. Update player physics and collision
        player.update(delta, mapGen, tileM);

        // 2. Update tiles (water animation & bush sway)
        tileM.update(delta, mapGen, dayNight);

        // 3. Update day/night cycle simulation
        dayNight.update();

        // 4. Update footprints fade
        footprintM.update(delta);

        // 5. Update snow & ambient particles
        int pCol = (int) (player.worldX / TileManager.TILE_SIZE);
        int pRow = (int) (player.worldY / TileManager.TILE_SIZE);
        boolean inSnow = (pCol >= 0 && pCol < mapGen.mapCols && pRow >= 0 && pRow < mapGen.mapRows) && mapGen.isSnowBiome[pCol][pRow];
        // Use HUD viewport dimensions so snow flakes cover the whole screen correctly
        snowPS.update(delta, inSnow, 1.0f, hudCamera.viewportWidth, hudCamera.viewportHeight);

        // 6. Update birds AI
        birdM.update(delta, mapGen, tileM, player);

        // 7. Update audio engine (OST cycling and nature ambience)
        audio.update(delta, dayNight);

        // 8. Auto-save progress
        autoSaveTimer -= delta;
        if (autoSaveTimer <= 0f) {
            autoSaveTimer = 60f;
            saveProgress();
        }
    }

    private void saveProgress() {
        if (worldFileName != null && !worldFileName.isEmpty()) {
            WorldSaveManager.saveGameProgress(worldFileName, worldName, seed, createdTime, player, dayNight);
        }
    }

    private void renderScene() {
        // Center camera smoothly on player
        camera.position.set(player.worldX + TileManager.TILE_SIZE / 2f,
                            player.worldY + TileManager.TILE_SIZE / 2f, 0);
        camera.update();

        // Frustum bounds in world coordinates
        float halfW = (camera.viewportWidth * camera.zoom) / 2f;
        float halfH = (camera.viewportHeight * camera.zoom) / 2f;
        float minX = camera.position.x - halfW;
        float maxX = camera.position.x + halfW;
        float minY = camera.position.y - halfH;
        float maxY = camera.position.y + halfH;

        // Clear OpenGL buffer
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // -------------------------------------------------------------
        // PASS 1: World Scene Rendering
        // -------------------------------------------------------------
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // 1. Ground tiles, depth cliff borders, and universal transitions
        tileM.trackVisibleForWind(mapGen, minX, minY, maxX, maxY);
        tileM.drawGround(game.batch, mapGen, minX, minY, maxX, maxY);

        // 2. Footprints on snow/sand
        footprintM.draw(game.batch, minX, minY, maxX, maxY);

        // 3. Ground shadows (directional sun/moon projected silhouettes)
        tileM.drawGroundShadows(game.batch, mapGen, dayNight, minX, minY, maxX, maxY, player.getCurrentSprite(), player.worldX, player.worldY);

        // 4. Falling leaves on ground/air
        snowPS.drawWorldLeaves(game.batch, minX, minY, maxX, maxY);

        // 5. Ground birds (perched on grass)
        birdM.drawBehindPlayer(game.batch, minX, minY, maxX, maxY);

        // 6. Decorations behind player (including lilipads which are always behind player)
        tileM.drawDecorations(game.batch, mapGen, minX, minY, maxX, maxY, player.worldY, false, camera);

        // 7. Player entity — water submersion via GL scissor test, mirroring original g2.clipRect approach.
        int footCol = (int) ((player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int footRow = (int) ((player.worldY + Player.HIT_OFFSET_Y + Player.HIT_H / 2f) / TileManager.TILE_SIZE);
        int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);

        if (waterLevel > 0) {
            int cutOff = player.getWaterCutOff(waterLevel);
            int yShift = player.getWaterYShift(waterLevel);
            // Visible world-pixel height: full tile minus the submerged cutOff portion
            float visibleWorldH = TileManager.TILE_SIZE - cutOff;
            // Top-left of the visible area in world coords
            float clipWorldY = player.worldY; // clip starts at sprite top (not shifted — we clip fixed to screen top)

            // Project the clip rectangle corners from world space to GL window pixels.
            // camera.project() returns GL coords (Y-up, origin bottom-left of window).
            Vector3 topLeft     = camera.project(new Vector3(player.worldX,                       clipWorldY,                    0));
            Vector3 bottomRight = camera.project(new Vector3(player.worldX + TileManager.TILE_SIZE, clipWorldY + visibleWorldH, 0));

            int sx = (int) Math.min(topLeft.x, bottomRight.x);
            int sy = (int) Math.min(topLeft.y, bottomRight.y);
            int sw = (int) Math.abs(bottomRight.x - topLeft.x);
            int sh = (int) Math.abs(bottomRight.y - topLeft.y);

            // Flush all pending draw calls BEFORE changing GL state
            game.batch.flush();
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(sx, sy, Math.max(1, sw), Math.max(1, sh));

            // Draw player without foam inside the scissor (sprite only)
            player.draw(game.batch, waterLevel, null);

            // Flush and restore GL state AFTER drawing
            game.batch.flush();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

            // Draw foam OUTSIDE scissor at the waterline, mirroring original g2.setClip restore
            TextureRegion playerFoam = tileM.getFoamRegion();
            if (playerFoam != null) {
                float foamY = player.worldY + player.getWaterYShift(waterLevel)
                              + TileManager.TILE_SIZE - player.getWaterCutOff(waterLevel) - 3f;
                game.batch.setColor(1f, 1f, 1f, 0.9f);
                game.batch.draw(playerFoam, player.worldX, foamY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }
        } else {
            player.draw(game.batch, waterLevel, null);
        }

        // 8. Decorations in front of player (tree canopies, tall rocks)
        tileM.drawDecorations(game.batch, mapGen, minX, minY, maxX, maxY, player.worldY, true, camera);

        // 9. Elevated birds (perched on tree/bush tops and flying)
        birdM.drawInFrontOfPlayer(game.batch, minX, minY, maxX, maxY);

        game.batch.end();

        // -------------------------------------------------------------
        // PASS 2: Dynamic GPU Lighting (FBO + Multiplicative Blend)
        // -------------------------------------------------------------
        hudCamera.update();
        game.batch.setProjectionMatrix(hudCamera.combined);
        lighting.renderLightmap(game.batch, dayNight, hudCamera.viewportWidth / 2f, hudCamera.viewportHeight / 2f);

        game.batch.begin();
        lighting.drawLightmap(game.batch, 0, 0, hudCamera.viewportWidth, hudCamera.viewportHeight);
        game.batch.end();

        // -------------------------------------------------------------
        // PASS 3: Weather Precipitation (screen-space, HUD projection)
        // -------------------------------------------------------------
        // Explicitly set HUD projection so snow always draws in screen-space,
        // regardless of what the previous pass left active.
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();
        snowPS.drawScreenSnow(game.batch, 0, 0);
        game.batch.end();

        // -------------------------------------------------------------
        // PASS 4: In-Game HUD (Screen Space Overlay)
        // -------------------------------------------------------------
        renderHUD();
    }

    private void renderHUD() {
        hudCamera.update();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();

        float screenW = hudCamera.viewportWidth;
        float screenH = hudCamera.viewportHeight;

        // Adaptive HUD scale matching original UI.java
        float hcScale;
        if (screenH >= 1300f) {
            hcScale = 4.0f;
        } else if (screenH >= 900f) {
            hcScale = 3.0f;
        } else if (screenH >= 500f) {
            hcScale = 2.0f;
        } else {
            hcScale = 1.5f;
        }

        float hbScale = hcScale * 0.82f;
        float gap = Math.round(5f * hcScale);
        float startX = Math.round(10f * hcScale);
        float startY = Math.round(9f * hcScale);

        // 1. Heart Container & Lives (Top-Left)
        // heartcontainer.png is 128x96 → 2 cols x 6 rows, each frame 64x16, 11 usable frames.
        int hcFrame = (int) (animTime * 8f) % 11;

        // Each frame is 64px wide in the original texture.
        float hcW = Math.round(64f * hcScale);
        float hcH = Math.round(16f * hcScale);
        float hcX = startX;
        float hcY = startY;

        // Render hearts inside the 3 cutout slots.
        // These offsets (in 64px-wide frame pixels) measured from the container left edge.
        int[] slotXCoords = { 10, 25, 40 };
        // Use actual heart.png dimensions (13x12) rather than hardcoded guesses
        float heartW = (heartRegion != null)
                ? Math.round(heartRegion.getRegionWidth()  * hcScale)
                : Math.round(13f * hcScale);
        float heartH = (heartRegion != null)
                ? Math.round(heartRegion.getRegionHeight() * hcScale)
                : Math.round(12f * hcScale);
        for (int i = 0; i < player.maxLives; i++) {
            float slotX = hcX + Math.round(slotXCoords[i] * hcScale);
            float slotY = hcY + Math.round(2f * hcScale);
            if (i < player.lives) {
                game.batch.setColor(1f, 1f, 1f, 1f);
            } else {
                game.batch.setColor(0.18f, 0.08f, 0.08f, 0.75f);
            }
            if (heartRegion != null) {
                game.batch.draw(heartRegion, slotX, slotY, heartW, heartH);
            }
        }
        game.batch.setColor(1f, 1f, 1f, 1f);

        // Ornate animated gold heart container frame
        if (heartContainerFrames[hcFrame] != null) {
            game.batch.draw(heartContainerFrames[hcFrame], hcX, hcY, hcW, hcH);
        }

        // 2. Health Bar (Bottom, centered under heart container)
        int hbFrame = (int) (animTime * 8f) % 9;
        float hbW = Math.round(70f * hbScale);
        float hbH = Math.round(16f * hbScale);
        // Center bar symmetrically under heart container — matches original: hcX + round(31 * hcScale)
        float hcCenter = hcX + Math.round(31f * hcScale);
        float hbX = hcCenter - Math.round(34f * hbScale);
        float hbY = hcY + hcH + gap;

        float innerX = hbX + Math.round(3f * hbScale);
        float innerY = hbY + Math.round(2f * hbScale);
        float maxInnerW = Math.round(63f * hbScale);
        float innerH = Math.round(6f * hbScale);
        float curInnerW = Math.round(maxInnerW * player.getHealthRatio());

        if (whitePixel != null) {
            // Dark cavity background inside the bar
            game.batch.setColor(0.14f, 0.05f, 0.05f, 0.86f);
            game.batch.draw(whitePixel, innerX, innerY, maxInnerW, innerH);

            // Red health fill with authentic 3-tier pixel bevel
            if (curInnerW > 0) {
                float topH = Math.max(1f, Math.round(1f * hbScale));
                float botH = Math.max(1f, Math.round(1f * hbScale));
                float midH = Math.max(1f, innerH - topH - botH);

                // Top highlight
                game.batch.setColor(0.92f, 0.20f, 0.20f, 1f);
                game.batch.draw(whitePixel, innerX, innerY, curInnerW, topH);
                // Middle core red
                game.batch.setColor(0.80f, 0.12f, 0.12f, 1f);
                game.batch.draw(whitePixel, innerX, innerY + topH, curInnerW, midH);
                // Bottom row shadow
                game.batch.setColor(0.57f, 0.06f, 0.06f, 1f);
                game.batch.draw(whitePixel, innerX, innerY + topH + midH, curInnerW, botH);
            }
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        // Animated gold health bar frame
        if (healthBarFrames[hbFrame] != null) {
            game.batch.draw(healthBarFrames[hbFrame], hbX, hbY, hbW, hbH);
        }

        // 3. Pause Overlay
        if (isPaused) {
            // Dim background
            if (whitePixel != null) {
                game.batch.setColor(0f, 0f, 0f, 0.65f);
                game.batch.draw(whitePixel, 0, 0, screenW, screenH);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }

            // Title - only 'Juego en pausa'
            if (fontMedium != null) {
                fontMedium.setColor(Color.GOLD);
                glyphLayout.setText(fontMedium, "Juego en pausa");
                fontMedium.draw(game.batch, "Juego en pausa",
                        (screenW - glyphLayout.width) / 2f, 200f);
            }

            // Pause buttons: back_button enlarged proportionally to 224x58 (1.6x, no distortion)
            float pScale = 1.6f;
            float pbW = (backButtonRegion != null) ? (backButtonRegion.getRegionWidth() * pScale) : 224f;
            float pbH = (backButtonRegion != null) ? (backButtonRegion.getRegionHeight() * pScale) : 58f;
            float pbX = (screenW - pbW) / 2f;
            float pbY1 = 250f;                 // 'Volver al menu' button
            float pbY2 = pbY1 + pbH + 16f;     // 'Guardar progreso' button

            // Mouse coords in HUD space
            float pmx = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
            float pmy = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
            boolean hoverBack = pmx >= pbX && pmx <= pbX + pbW && pmy >= pbY1 && pmy <= pbY1 + pbH;
            boolean hoverSave = pmx >= pbX && pmx <= pbX + pbW && pmy >= pbY2 && pmy <= pbY2 + pbH;

            // Draw 'Volver al menu' button
            if (backButtonRegion != null) {
                game.batch.setColor(hoverBack ? 0.85f : 1f, hoverBack ? 0.85f : 1f, hoverBack ? 0.85f : 1f, 1f);
                game.batch.draw(backButtonRegion, pbX, pbY1, pbW, pbH);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }
            if (fontSmall != null) {
                fontSmall.setColor(hoverBack ? Color.GOLD : Color.WHITE);
                String txt = "Volver al men\u00fa";
                glyphLayout.setText(fontSmall, txt);
                fontSmall.draw(game.batch, txt,
                        pbX + (pbW - glyphLayout.width) / 2f,
                        pbY1 + (pbH - glyphLayout.height) / 2f);
            }

            // Draw 'Guardar progreso' button
            if (backButtonRegion != null) {
                game.batch.setColor(hoverSave ? 0.85f : 1f, hoverSave ? 0.85f : 1f, hoverSave ? 0.85f : 1f, 1f);
                game.batch.draw(backButtonRegion, pbX, pbY2, pbW, pbH);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }
            if (fontSmall != null) {
                fontSmall.setColor(hoverSave ? Color.GOLD : Color.WHITE);
                String saveLabel = (saveFeedbackTimer > 0f) ? "\u00a1Guardado!" : "Guardar progreso";
                glyphLayout.setText(fontSmall, saveLabel);
                fontSmall.draw(game.batch, saveLabel,
                        pbX + (pbW - glyphLayout.width) / 2f,
                        pbY2 + (pbH - glyphLayout.height) / 2f);
            }

            // Handle clicks
            if (Gdx.input.isButtonJustPressed(0)) {
                if (hoverBack) {
                    audio.playButtonClick();
                    isPaused = false;
                    returnToTitle();
                } else if (hoverSave) {
                    audio.playButtonClick();
                    saveProgress();
                    saveFeedbackTimer = 1.5f;
                }
            }
        }

        // 4. Death Screen - shown when player has no lives left
        if (showDeathScreen) {
            renderDeathScreen(screenW, screenH);
        }


        // 5. Respawn Screen - shown when player lost a life but still has lives
        if (showRespawnScreen) {
            renderRespawnScreen(screenW, screenH);
        }
        game.batch.end();
    }

    private void renderDeathScreen(float screenW, float screenH) {
        // Dark overlay
        if (whitePixel != null) {
            game.batch.setColor(0f, 0f, 0f, 0.78f);
            game.batch.draw(whitePixel, 0, 0, screenW, screenH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        // MORISTE title (vertically centered layout)
        if (fontLarge != null) {
            fontLarge.setColor(new Color(0.85f, 0.1f, 0.1f, 1f));
            glyphLayout.setText(fontLarge, "MORISTE");
            fontLarge.draw(game.batch, "MORISTE",
                    (screenW - glyphLayout.width) / 2f, 220f);
        }

        // 'Volver al menu' button using button_slot at native size (576x48), no red tint
        float dbW = (buttonSlotRegion != null) ? buttonSlotRegion.getRegionWidth() : 576f;
        float dbH = (buttonSlotRegion != null) ? buttonSlotRegion.getRegionHeight() : 48f;
        float dbX = (screenW - dbW) / 2f;
        float dbY = 290f;
        float dmx = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
        float dmy = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
        boolean dhover = dmx >= dbX && dmx <= dbX + dbW && dmy >= dbY && dmy <= dbY + dbH;

        if (buttonSlotRegion != null) {
            game.batch.setColor(dhover ? 0.85f : 1f, dhover ? 0.85f : 1f, dhover ? 0.85f : 1f, 1f);
            game.batch.draw(buttonSlotRegion, dbX, dbY, dbW, dbH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontMedium != null) {
            fontMedium.setColor(dhover ? Color.GOLD : Color.WHITE);
            glyphLayout.setText(fontMedium, "Volver al men\u00fa");
            fontMedium.draw(game.batch, "Volver al men\u00fa",
                    dbX + (dbW - glyphLayout.width) / 2f,
                    dbY + (dbH - glyphLayout.height) / 2f);
        }

        // Handle click on button
        if (Gdx.input.isButtonJustPressed(0) && dhover) {
            audio.playButtonClick();
            returnToTitle();
        }
    }

    private void renderRespawnScreen(float screenW, float screenH) {
        // Dark overlay
        if (whitePixel != null) {
            game.batch.setColor(0f, 0f, 0f, 0.72f);
            game.batch.draw(whitePixel, 0, 0, screenW, screenH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        // Moriste! te quedan X vidas (vertically centered layout)
        String title = "Moriste! te quedan " + player.lives + (player.lives == 1 ? " vida" : " vidas");
        if (fontMedium != null) {
            fontMedium.setColor(new Color(0.95f, 0.25f, 0.2f, 1f));
            glyphLayout.setText(fontMedium, title);
            fontMedium.draw(game.batch, title,
                    (screenW - glyphLayout.width) / 2f, 200f);
        }

        // Button dimensions: back_button enlarged to 224x58 (1.6x scale, proportional)
        float scale = 1.6f;
        float rbW = (backButtonRegion != null) ? (backButtonRegion.getRegionWidth() * scale) : 224f;
        float rbH = (backButtonRegion != null) ? (backButtonRegion.getRegionHeight() * scale) : 58f;
        float rbX = (screenW - rbW) / 2f;
        float rbY1 = 242f;                 // 'Reaparecer' button
        float rbY2 = rbY1 + rbH + 14f;     // 'Volver al menu' button

        // Mouse coordinates in HUD space
        float rmx = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
        float rmy = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
        boolean rhoverRespawn = rmx >= rbX && rmx <= rbX + rbW && rmy >= rbY1 && rmy <= rbY1 + rbH;
        boolean rhoverMenu    = rmx >= rbX && rmx <= rbX + rbW && rmy >= rbY2 && rmy <= rbY2 + rbH;

        // 1. Draw 'Reaparecer' button
        if (backButtonRegion != null) {
            game.batch.setColor(rhoverRespawn ? 0.85f : 1f, rhoverRespawn ? 0.85f : 1f, rhoverRespawn ? 0.85f : 1f, 1f);
            game.batch.draw(backButtonRegion, rbX, rbY1, rbW, rbH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontSmall != null) {
            fontSmall.setColor(rhoverRespawn ? Color.GOLD : Color.WHITE);
            glyphLayout.setText(fontSmall, "Reaparecer");
            fontSmall.draw(game.batch, "Reaparecer",
                    rbX + (rbW - glyphLayout.width) / 2f,
                    rbY1 + (rbH - glyphLayout.height) / 2f);
        }

        // 2. Draw 'Volver al menu' button
        if (backButtonRegion != null) {
            game.batch.setColor(rhoverMenu ? 0.85f : 1f, rhoverMenu ? 0.85f : 1f, rhoverMenu ? 0.85f : 1f, 1f);
            game.batch.draw(backButtonRegion, rbX, rbY2, rbW, rbH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontSmall != null) {
            fontSmall.setColor(rhoverMenu ? Color.GOLD : Color.WHITE);
            String txt = "Volver al men\u00fa";
            glyphLayout.setText(fontSmall, txt);
            fontSmall.draw(game.batch, txt,
                    rbX + (rbW - glyphLayout.width) / 2f,
                    rbY2 + (rbH - glyphLayout.height) / 2f);
        }

        // Handle button clicks & keyboard shortcuts
        if (Gdx.input.isButtonJustPressed(0)) {
            if (rhoverRespawn) {
                audio.playButtonClick();
                showRespawnScreen = false;
            } else if (rhoverMenu) {
                audio.playButtonClick();
                saveProgress();
                showRespawnScreen = false;
                returnToTitle();
            }
        } else if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            audio.playButtonClick();
            showRespawnScreen = false;
        }
    }

    private void returnToTitle() {
        saveProgress();
        com.badlogic.gdx.audio.Music m = audio.releaseCurrentMusic();
        game.returnToMenuFromWorld(m);
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        camera.setToOrtho(true, width, height);
        camera.update();

        hudCamera.setToOrtho(true, width, height);
        hudCamera.update();

        lighting.resize(width, height);
    }

    @Override
    public void dispose() {
        saveProgress();
        tileM.dispose();
        player.dispose();
        footprintM.dispose();
        snowPS.dispose();
        birdM.dispose();
        lighting.dispose();
        audio.dispose();

        if (fontSmall != null) fontSmall.dispose();
        if (fontMedium != null) fontMedium.dispose();
        if (fontLarge != null) fontLarge.dispose();

        for (Texture tex : hudTextures) {
            tex.dispose();
        }
        hudTextures.clear();
    }
}
