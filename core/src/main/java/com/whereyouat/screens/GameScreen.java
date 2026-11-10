package com.whereyouat.screens;

import java.util.ArrayList;
import java.util.Comparator;

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
import com.whereyouat.AssetSetter;
import com.whereyouat.WhereYouAtGame;
import com.whereyouat.audio.AudioManager;
import com.whereyouat.effects.FootprintManager;
import com.whereyouat.effects.SnowParticleSystem;
import com.whereyouat.entity.Entity;
import com.whereyouat.entity.Player;
import com.whereyouat.items.Item;
import com.whereyouat.items.ItemPotion;
import com.whereyouat.items.OddMushroom;
import com.whereyouat.items.Sword;
import com.whereyouat.lighting.DayNightCycle;
import com.whereyouat.lighting.LightingEngine;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_frogger;
import com.whereyouat.monster.MON_mushroom;
import com.whereyouat.monster.MON_Ghost;
import com.whereyouat.save.WorldSaveManager;

/**
 * GameScreen - Pantalla principal de juego activo para Where you at (LibGDX
 * Edition).
 *
 * Administra el bucle de juego (Game Loop), la coordinación de subsistemas y
 * el orden de capas de renderizado en el mundo.
 * La interfaz de usuario (HUD), pausa y pantallas de muerte/reaparición están
 * delegadas en GameHUD.
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
    public final LightingEngine lighting;
    public final AudioManager audio;

    // Delegado de interfaz de usuario
    private final GameHUD hud;

    // Monsters
    public Entity monster[] = new Entity[20];
    public final AssetSetter assetSetter;
    public final int tileSize = TileManager.TILE_SIZE;

    // Entity list for depth sorting (ordenamiento por la base/pies de la hitbox
    // física)
    public final ArrayList<Entity> entityList = new ArrayList<>();
    private final Comparator<Entity> entitySorter = (e1, e2) -> Float.compare(
            e1.worldY + e1.solidArea.y + e1.solidArea.height,
            e2.worldY + e2.solidArea.y + e2.solidArea.height);

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
    private boolean wasNightLastFrame = false; // para detectar la transición DÍA→NOCHE
    private float nightSpawnTimer = 0f; // temporizador para spawn continuo de noche
    private static final float NIGHT_SPAWN_INTERVAL = 10f; // cada 10s de noche intenta reponer fantasmas
    private float snowSpawnTimer = 0f; // ← nuevo
    private static final float SNOW_SPAWN_INTERVAL = 15f; // cada 15 segundos en clima nevado
    private static final int MAX_NIGHT_GHOSTS = 5; // cuántos fantasmas spawnear por noche
    private boolean showRespawnScreen = false;
    private float saveFeedbackTimer = 0f;

    public boolean showInventory = false;

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
        player.gp = this;
        player.initSpawn(mapGen);

        dayNight = new DayNightCycle();
        footprintM = new FootprintManager();
        snowPS = new SnowParticleSystem(seed);
        tileM.setSnowPS(snowPS);

        // Asset setter & initial game setup
        assetSetter = new AssetSetter(this);
        setupGame();

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

        if (saveFeedbackTimer > 0f)
            saveFeedbackTimer -= dt;

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

        if (!isPaused && !showDeathScreen && Gdx.input.isKeyJustPressed(Keys.E)) {
            showInventory = !showInventory;
        }

        // Navegacion del inventario

        if (showInventory) {
            if (Gdx.input.isKeyJustPressed(Keys.UP)) {
                hud.slotRow = Math.max(0, hud.slotRow - 1);
            }

            if (Gdx.input.isKeyJustPressed(Keys.DOWN)) {
                hud.slotRow = Math.min(3, hud.slotRow + 1);
            }

            if (Gdx.input.isKeyJustPressed(Keys.LEFT)) {
                hud.slotCol = Math.max(0, hud.slotCol - 1);
            }

            if (Gdx.input.isKeyJustPressed(Keys.RIGHT)) {
                hud.slotCol = Math.min(4, hud.slotCol + 1);
            }

            // Usar item con enter

            if (Gdx.input.isKeyJustPressed(Keys.ENTER) || Gdx.input.isKeyJustPressed(Keys.SPACE)) {
                int itemIndex = hud.slotCol + (hud.slotRow * 5);

                if (itemIndex < player.inventory.size()) {
                    Item item = player.inventory.get(itemIndex);
                    if (item.use(player)) {
                        player.inventory.remove(itemIndex);
                    }
                }
            }
        }

        // ataque con clic izquierdo del mouse
        if (!isPaused && !showInventory && !showDeathScreen && Gdx.input.isButtonJustPressed(0)
                && player.attackCooldown <= 0f && player.hasSword()) {
            // convierte las coordenadas del click de pantalla al mundo
            Vector3 clickWorld = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));

            float playerCenterX = player.worldX + TileManager.TILE_SIZE / 2f;
            float playerCenterY = player.worldY + TileManager.TILE_SIZE / 2f;

            float dx = clickWorld.x - playerCenterX;
            float dy = clickWorld.y - playerCenterY;

            // determinar si el click tira mas a horizontal o vertical

            String attackDir;
            if (Math.abs(dx) >= Math.abs(dy)) {
                attackDir = (dx > 0) ? "right" : "left";
            } else {
                attackDir = (dy > 0) ? "down" : "up"; // "Y" crece hacia abajo
            }

            // ejecutar el ataque con 2 puntos de daño hacia la dirección del click

            player.attack(attackDir, 2);
        }
    }

    private void updateGame(float delta) {
        // 1. Actualizar física del jugador, colisiones e interacción con monstruos
        player.update(delta, mapGen, tileM, monster);
        // --- Spawn/despawn de fantasmas por ciclo día/noche ---
        boolean isNightNow = dayNight.isNight();
        if (isNightNow && !wasNightLastFrame) {
            // Acaba de caer la noche: spawnear fantasmas
            spawnNightGhosts();
            nightSpawnTimer = 0f;
        } else if (!isNightNow && wasNightLastFrame) {
            // Acaba de amanecer: eliminar fantasmas nocturnos
            despawnNightGhosts();
            nightSpawnTimer = 0f;
        } else if (isNightNow) {
            // Spawn continuo: cada 10 segundos de noche repone fantasmas si murieron o te moviste
            nightSpawnTimer += delta;
            if (nightSpawnTimer >= NIGHT_SPAWN_INTERVAL) {
                spawnNightGhosts();
                nightSpawnTimer = 0f;
            }
        }
        wasNightLastFrame = isNightNow;

        // 2. Actualizar tiles (animación de agua y balanceo de arbustos)
        tileM.update(delta, mapGen, dayNight);
        dayNight.update();
        footprintM.update(delta);

        int playerCol = (int) (player.worldX / TileManager.TILE_SIZE);
        int playerRow = (int) (player.worldY / TileManager.TILE_SIZE);
        boolean inSnow = (playerCol >= 0 && playerCol < mapGen.mapCols && playerRow >= 0 && playerRow < mapGen.mapRows)
                && mapGen.isSnowBiome[playerCol][playerRow];
        snowPS.update(delta, inSnow, 1.0f, hudCamera.viewportWidth, hudCamera.viewportHeight);

        // --- Spawn por evento climático (nieve) ---
        if (inSnow) {
            snowSpawnTimer += delta;
            if (snowSpawnTimer >= SNOW_SPAWN_INTERVAL) {
                spawnSnowGhost();
                snowSpawnTimer = 0f;
            }
        } else {
            snowSpawnTimer = 0f; // si sale de la nieve, se reinicia el contador
        }

        // Limpiar fantasmas que quedaron demasiado lejos del jugador (> 25 tiles)
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost && monster[i].distanceTo(player) > 25 * tileSize) {
                monster[i].dispose();
                monster[i] = null;
            }
        }

        // Actualizar IA de monstruos
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] != null) {
                monster[i].update(delta);
            }
        }

        audio.update(delta, dayNight);

        autoSaveTimer -= delta;
        if (autoSaveTimer <= 0f) {
            autoSaveTimer = 60f;
            saveProgress();
        }
    }

    private void saveProgress() {
        if (worldFileName != null && !worldFileName.isEmpty()) {
            WorldSaveManager.saveGameProgress(worldFileName, worldName, seed, createdTime, player, dayNight, monster);
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

        // 5. Entidades y Decoraciones con ordenamiento unificado por profundidad Y
        // (evita oclusiones prematuras)
        entityList.add(player);
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] != null) {
                entityList.add(monster[i]);
            }
        }
        entityList.sort(entitySorter);

        int startCol = Math.max(0, (int) (minX / TileManager.TILE_SIZE) - 2);
        int startRow = Math.max(0, (int) (minY / TileManager.TILE_SIZE) - 2);
        int endCol = Math.min(mapGen.mapCols - 1, (int) (maxX / TileManager.TILE_SIZE) + 4);
        int endRow = Math.min(mapGen.mapRows - 1, (int) (maxY / TileManager.TILE_SIZE) + 4);

        // Nenúfares en la superficie del agua (bajo todas las entidades)
        tileM.drawLilipads(game.batch, mapGen, startCol, endCol, startRow, endRow);

        int entityIndex = 0;
        for (int row = startRow; row <= endRow; row++) {
            float rowFootY = (row + 1) * TileManager.TILE_SIZE;

            // Dibuja entidades situadas detrás de las decoraciones de esta fila
            while (entityIndex < entityList.size()) {
                Entity e = entityList.get(entityIndex);
                float eFootY = e.worldY + e.solidArea.y + e.solidArea.height;
                if (eFootY < rowFootY) {
                    if (e == player)
                        drawPlayer();
                    else
                        e.draw(game.batch);
                    entityIndex++;
                } else {
                    break;
                }
            }

            // Dibuja decoraciones (árboles, rocas, arbustos) de la fila actual
            tileM.drawDecorationsRow(game.batch, mapGen, startCol, endCol, row, camera);
        }

        // Dibuja entidades restantes por delante de las filas visibles
        while (entityIndex < entityList.size()) {
            Entity e = entityList.get(entityIndex);
            if (e == player)
                drawPlayer();
            else
                e.draw(game.batch);
            entityIndex++;
        }

        entityList.clear();

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
        hud.render(game.batch, player, isPaused, showDeathScreen, showRespawnScreen, saveFeedbackTimer, animTime,
                showInventory);
    }

    /**
     * Initializes world entities and assets.
     * Equivalent to RyiSnow setupGame().
     */
    public void setupGame() {
        assetSetter.setMonster();
    }

    /**
     * Updates world entities, game systems, and audio.
     */
    public void update() {
        updateGame(Gdx.graphics != null ? Gdx.graphics.getDeltaTime() : 1f / 60f);
    }

    public void update(float delta) {
        updateGame(delta);
    }

    /**
     * Renders player entity with water submersion via OpenGL scissor test.
     */
    private void drawPlayer() {
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
            Vector3 topLeft = camera.project(new Vector3(player.worldX, clipWorldY, 0));
            Vector3 bottomRight = camera
                    .project(new Vector3(player.worldX + TileManager.TILE_SIZE, clipWorldY + visibleWorldH, 0));

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

            // Draw foam OUTSIDE scissor at the waterline, mirroring original g2.setClip
            // restore
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
    }

    public void loadSavedState(WorldSaveManager.WorldInfo info) {
        if (info == null)
            return;

        // 1. Estado del jugador
        if (info.hasPlayerData) {
            player.worldX = info.playerWorldX;
            player.worldY = info.playerWorldY;
            player.direction = info.playerDirection;
            player.health = info.playerHealth;
            player.lives = info.playerLives;
        }

        // 2. Restaurar inventario guardado
        if (info.hasInventoryData) {
            player.inventory.clear();
            for (String itemId : info.inventoryItems) {
                Item item = createItem(itemId);
                if (item != null)
                    player.addItem(item);
            }
        }

        // 3. Restaurar monstruos guardados
        if (info.hasMonsterData) {
            // Limpiar los monstruos por defecto de AssetSetter
            for (int i = 0; i < monster.length; i++) {
                if (monster[i] != null) {
                    monster[i].dispose();
                    monster[i] = null;
                }
            }
            // Cargar los monstruos guardados
            for (int i = 0; i < info.savedMonsters.size() && i < monster.length; i++) {
                WorldSaveManager.WorldInfo.SavedMonster sm = info.savedMonsters.get(i);
                Entity m = createMonster(sm.id);
                if (m != null) {
                    m.worldX = sm.x;
                    m.worldY = sm.y;
                    m.life = sm.life;
                    monster[i] = m;
                }
            }
        }

        // 4. Ciclo día/noche
        if (info.hasDayNightData && info.dayNightTicks >= 0) {
            dayNight.setTimeTicks(info.dayNightTicks);
        }
    }

    private Item createItem(String id) {
        if (id == null)
            return null;
        switch (id) {
            case "Sword":
                return new Sword();
            case "OddMushroom":
                return new OddMushroom();
            case "ItemPotion":
                return new ItemPotion();
            default:
                return null;
        }
    }

    private Entity createMonster(String id) {
        if (id == null)
            return null;
        switch (id) {
            case "MON_mushroom":
                return new MON_mushroom(this);
            case "MON_frogger":
                return new MON_frogger(this);
            case "MON_Ghost":
                return new MON_Ghost(this); // ← nuevo
            default:
                return null;
        }
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
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] != null) {
                monster[i].dispose();
                monster[i] = null;
            }
        }
        footprintM.dispose();
        snowPS.dispose();
        lighting.dispose();
        audio.dispose();
        hud.dispose();
    }

    /**
     * Spawnea fantasmas en tiles walkables aleatorios cerca del jugador.
     * Se llama una sola vez al caer la noche.
     */
    private void spawnNightGhosts() {
        if (mapGen == null || player == null)
            return;

        // Contar cuántos fantasmas hay vivos cerca del jugador
        int nearbyGhosts = 0;
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost && monster[i].distanceTo(player) < 25 * tileSize) {
                nearbyGhosts++;
            }
        }
        int toSpawn = MAX_NIGHT_GHOSTS - nearbyGhosts;
        if (toSpawn <= 0)
            return; // Ya hay suficientes fantasmas cerca

        // Centro de búsqueda: posición del jugador en tiles
        int playerCol = (int) (player.worldX / tileSize);
        int playerRow = (int) (player.worldY / tileSize);

        // Radio de spawn: entre 6 y 11 tiles del jugador (alrededor del borde de la pantalla)
        int minRadius = 6;
        int maxRadius = 11;

        java.util.Random rand = new java.util.Random();
        int spawned = 0;

        // Intentamos hasta 60 veces para encontrar tiles válidos
        for (int attempt = 0; attempt < 60 && spawned < toSpawn; attempt++) {
            // Elegir un desplazamiento aleatorio dentro del anillo
            int angle = rand.nextInt(360);
            int radius = minRadius + rand.nextInt(maxRadius - minRadius);
            int col = playerCol + (int) (Math.cos(Math.toRadians(angle)) * radius);
            int row = playerRow + (int) (Math.sin(Math.toRadians(angle)) * radius);

            // Validar límites del mapa
            if (col < 1 || col >= mapGen.mapCols - 1 || row < 1 || row >= mapGen.mapRows - 1)
                continue;

            // No spawnear en agua profunda, árboles o rocas
            if (mapGen.isDeepWater[col][row])
                continue;
            if (tileM.collidesWithTree(mapGen, col * tileSize, row * tileSize, tileSize, tileSize))
                continue;

            // Buscar un slot libre en el array de monstruos
            int slotIndex = -1;
            for (int i = 0; i < monster.length; i++) {
                if (monster[i] == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex == -1)
                break; // array lleno

            // Crear el fantasma y posicionarlo
            MON_Ghost ghost = new MON_Ghost(this);
            ghost.worldX = col * tileSize;
            ghost.worldY = row * tileSize;
            monster[slotIndex] = ghost;
            spawned++;

            Gdx.app.log("GameScreen", "Ghost spawned at (" + col + "," + row + ") slot " + slotIndex);
        }
    }

    /**
     * Elimina todos los fantasmas del mundo (llamado al amanecer).
     */
    private void despawnNightGhosts() {
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost) {
                monster[i].dispose();
                monster[i] = null;
            }
        }
    }

    /**
     * Spawnea UN fantasma extra si el jugador está en bioma de nieve.
     * Se llama cada SNOW_SPAWN_INTERVAL segundos mientras nieva.
     */
    private void spawnSnowGhost() {
        if (mapGen == null || player == null)
            return;

        int playerCol = (int) (player.worldX / tileSize);
        int playerRow = (int) (player.worldY / tileSize);

        // Solo spawnear si el jugador está en zona nevada
        if (playerCol < 0 || playerCol >= mapGen.mapCols ||
                playerRow < 0 || playerRow >= mapGen.mapRows)
            return;
        if (!mapGen.isSnowBiome[playerCol][playerRow])
            return;

        // Buscar slot libre
        int slotIndex = -1;
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] == null) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1)
            return;

        java.util.Random rand = new java.util.Random();
        // Spawn en un radio de 6-11 tiles
        for (int attempt = 0; attempt < 30; attempt++) {
            int angle = rand.nextInt(360);
            int radius = 6 + rand.nextInt(6);
            int col = playerCol + (int) (Math.cos(Math.toRadians(angle)) * radius);
            int row = playerRow + (int) (Math.sin(Math.toRadians(angle)) * radius);

            if (col < 1 || col >= mapGen.mapCols - 1 || row < 1 || row >= mapGen.mapRows - 1)
                continue;
            if (mapGen.isDeepWater[col][row])
                continue;
            if (!mapGen.isSnowBiome[col][row])
                continue;
            if (tileM.collidesWithTree(mapGen, col * tileSize, row * tileSize, tileSize, tileSize))
                continue;

            MON_Ghost ghost = new MON_Ghost(this);
            ghost.worldX = col * tileSize;
            ghost.worldY = row * tileSize;
            monster[slotIndex] = ghost;
            Gdx.app.log("GameScreen", "Snow ghost spawned at (" + col + "," + row + ")");
            return;
        }
    }
}
