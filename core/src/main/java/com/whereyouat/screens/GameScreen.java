package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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

/**
 * GameScreen - Pantalla principal de juego activo para Where you at (LibGDX Edition).
 *
 * Administra el bucle de juego (Game Loop), la coordinación de subsistemas y
 * el orden de capas de renderizado en el mundo.
 * La interfaz de usuario (HUD), pausa y pantallas de muerte/reaparición están delegadas en GameHUD.
 */
public class GameScreen extends ScreenAdapter {

    public final WhereYouAtGame game;

    // Cámaras y Viewports
    public final OrthographicCamera camera;
    public final Viewport viewport;
    public final OrthographicCamera hudCamera;
    public final Viewport hudViewport;

    // Subsistemas centrales del juego
    public final MapGenerator mapGen;
    public final TileManager tileM;
    public final Player player;
    public final DayNightCycle dayNight;
    public final FootprintManager footprintM;
    public final SnowParticleSystem snowPS;
    public final BirdManager birdM;
    public final LightingEngine lighting;
    public final AudioManager audio;

    // Delegado de interfaz de usuario
    private final GameHUD hud;

    // Metadatos del mundo
    public final String worldFileName;
    public final String worldName;
    public final long seed;
    public final long createdTime;

    // Estado del juego
    private float animTime = 0f;
    private float autoSaveTimer = 60f; // Guardado automático cada 60s
    private boolean isPaused = false;
    private boolean showDeathScreen = false;
    private boolean showRespawnScreen = false;
    private float saveFeedbackTimer = 0f;

    public GameScreen(WhereYouAtGame game, long seed, String worldName, String worldFileName) {
        this.game = game;
        this.seed = seed;
        this.worldName = (worldName != null && !worldName.isEmpty()) ? worldName : "World";
        this.worldFileName = worldFileName;
        this.createdTime = System.currentTimeMillis();

        // 1. Configurar cámara del mundo con orientación Y-down
        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new ExtendViewport(768, 576, camera);

        // 2. Configurar cámara de HUD para interfaz fija en pantalla
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(true, 768, 576);
        hudViewport = new FitViewport(768, 576, hudCamera);

        // 3. Generar mundo procedural 512x512
        Gdx.app.log("GameScreen", "Generating 512x512 procedural world with seed: " + seed);
        mapGen = new MapGenerator(512, 512, seed, String.valueOf(seed));
        mapGen.generate();

        // 4. Inicializar subsistemas
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

        // Conectar eventos de pisadas y follaje del jugador
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

        // 5. Inicializar HUD con callbacks de acción
        hud = new GameHUD(game, hudCamera, audio, new GameHUD.HUDCallback() {
            @Override
            public void onReturnToTitle() {
                isPaused = false;
                returnToTitle();
            }

            @Override
            public void onSaveProgress() {
                saveProgress();
                saveFeedbackTimer = 1.5f;
            }

            @Override
            public void onRespawn() {
                showRespawnScreen = false;
            }
        });

        // Atenuación suave de música de menú al ingresar al mundo
        game.fadeOutMenuMusic();
    }

    public GameScreen(WhereYouAtGame game, long seed, String seedString) {
        this(game, seed, "New World", null);
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);
        animTime += dt;
        if (saveFeedbackTimer > 0f) saveFeedbackTimer -= dt;

        // Pausa con ESC (solo vivo y fuera de pantallas de muerte)
        if (!showDeathScreen && !showRespawnScreen && Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            isPaused = !isPaused;
            if (isPaused) {
                saveProgress();
            }
        }

        if (!isPaused && !showDeathScreen && !showRespawnScreen) {
            int livesBefore = player.lives;
            updateGame(dt);

            if (player.lives < livesBefore && !player.dead) {
                showRespawnScreen = true;
                player.randomSpawn(mapGen);
            } else if (player.dead && !showDeathScreen) {
                showDeathScreen = true;
                saveProgress();
            }
        }

        renderScene();
    }

    private void updateGame(float delta) {
        player.update(delta, mapGen, tileM);
        tileM.update(delta, mapGen, dayNight);
        dayNight.update();
        footprintM.update(delta);

        int playerCol = (int) (player.worldX / TileManager.TILE_SIZE);
        int playerRow = (int) (player.worldY / TileManager.TILE_SIZE);
        boolean inSnow = (playerCol >= 0 && playerCol < mapGen.mapCols && playerRow >= 0 && playerRow < mapGen.mapRows) && mapGen.isSnowBiome[playerCol][playerRow];
        snowPS.update(delta, inSnow, 1.0f, hudCamera.viewportWidth, hudCamera.viewportHeight);

        birdM.update(delta, mapGen, tileM, player);
        audio.update(delta, dayNight);

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
        // Centrar cámara suavemente en el jugador
        camera.position.set(player.worldX + TileManager.TILE_SIZE / 2f,
                            player.worldY + TileManager.TILE_SIZE / 2f, 0);
        camera.update();

        float halfViewportWidth = (camera.viewportWidth * camera.zoom) / 2f;
        float halfViewportHeight = (camera.viewportHeight * camera.zoom) / 2f;
        float minX = camera.position.x - halfViewportWidth;
        float maxX = camera.position.x + halfViewportWidth;
        float minY = camera.position.y - halfViewportHeight;
        float maxY = camera.position.y + halfViewportHeight;

        Gdx.gl.glClearColor(0.04f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // -------------------------------------------------------------
        // PASE 1: Renderizado del Mundo (Escena)
        // -------------------------------------------------------------
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // 1. Suelo y costas
        tileM.trackVisibleForWind(mapGen, minX, minY, maxX, maxY);
        tileM.drawGround(game.batch, mapGen, minX, minY, maxX, maxY);

        // 2. Huellas
        footprintM.draw(game.batch, minX, minY, maxX, maxY);

        // 3. Sombras direccionales proyectadas en el suelo
        tileM.drawGroundShadows(game.batch, mapGen, dayNight, minX, minY, maxX, maxY,
                player.getCurrentSprite(), player.worldX, player.worldY);

        // 4. Hojas cayendo en el mundo
        snowPS.drawWorldLeaves(game.batch, minX, minY, maxX, maxY);

        // 5. Aves en el suelo (detrás del jugador)
        birdM.drawBehindPlayer(game.batch, minX, minY, maxX, maxY);

        // 6. Decoraciones detrás del jugador
        tileM.drawDecorations(game.batch, mapGen, minX, minY, maxX, maxY, player.worldY, false, camera);

        // 7. Jugador: recorte de sumersión en agua con GL Scissor Test
        int footCol = (int) ((player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int footRow = (int) ((player.worldY + Player.HIT_OFFSET_Y + Player.HIT_H / 2f) / TileManager.TILE_SIZE);
        int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);

        if (waterLevel > 0) {
            int cutOff = player.getWaterCutOff(waterLevel);
            float visibleWorldH = TileManager.TILE_SIZE - cutOff;
            float clipWorldY = player.worldY;

            Vector3 topLeft     = camera.project(new Vector3(player.worldX, clipWorldY, 0));
            Vector3 bottomRight = camera.project(new Vector3(player.worldX + TileManager.TILE_SIZE, clipWorldY + visibleWorldH, 0));

            int scissorX = (int) Math.min(topLeft.x, bottomRight.x);
            int scissorY = (int) Math.min(topLeft.y, bottomRight.y);
            int scissorWidth = (int) Math.abs(bottomRight.x - topLeft.x);
            int scissorHeight = (int) Math.abs(bottomRight.y - topLeft.y);

            game.batch.flush();
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(scissorX, scissorY, Math.max(1, scissorWidth), Math.max(1, scissorHeight));

            player.draw(game.batch, waterLevel, null);

            game.batch.flush();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

            // Espuma en la línea de agua (fuera del scissor)
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

        // 8. Decoraciones frente al jugador (copas de árboles, rocas altas)
        tileM.drawDecorations(game.batch, mapGen, minX, minY, maxX, maxY, player.worldY, true, camera);

        // 9. Aves elevadas y en vuelo
        birdM.drawInFrontOfPlayer(game.batch, minX, minY, maxX, maxY);

        game.batch.end();

        // -------------------------------------------------------------
        // PASE 2: Iluminación Dinámica GPU (FBO + Mezcla Multiplicativa)
        // -------------------------------------------------------------
        hudCamera.update();
        game.batch.setProjectionMatrix(hudCamera.combined);
        lighting.renderLightmap(game.batch, dayNight, hudCamera.viewportWidth / 2f, hudCamera.viewportHeight / 2f);

        game.batch.begin();
        lighting.drawLightmap(game.batch, 0, 0, hudCamera.viewportWidth, hudCamera.viewportHeight);
        game.batch.end();

        // -------------------------------------------------------------
        // PASE 3: Precipitación Climatológica (Nieve en espacio de pantalla)
        // -------------------------------------------------------------
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();
        snowPS.drawScreenSnow(game.batch, 0, 0);
        game.batch.end();

        // -------------------------------------------------------------
        // PASE 4: Interfaz de usuario (HUD delegado en GameHUD)
        // -------------------------------------------------------------
        hud.render(game.batch, player, isPaused, showDeathScreen, showRespawnScreen, saveFeedbackTimer, animTime);
    }

    private void returnToTitle() {
        saveProgress();
        Music music = audio.releaseCurrentMusic();
        game.returnToMenuFromWorld(music);
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
        hud.dispose();
    }
}
