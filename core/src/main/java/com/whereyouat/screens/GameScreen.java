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
import com.whereyouat.monster.MON_Ghost;
import com.whereyouat.monster.MON_frogger;
import com.whereyouat.monster.MON_mushroom;
import com.whereyouat.net.NetworkManager;
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
    public final NetworkManager netManager;
    public String roomCode = ""; // Código para mostrar en pantalla si es Host

    // --- CHAT ---
    // Lista de mensajes del chat: cada entrada es {texto, color, timerRestante}
    // Accedida por GameHUD para dibujar y por el bucle de red para insertar.
    public final java.util.LinkedList<ChatMessage> chatMessages = new java.util.LinkedList<>();
    public static final int CHAT_MAX_VISIBLE = 6;    // líneas visibles simultáneamente
    public static final float CHAT_FADE_SECONDS = 8f; // segundos que dura cada mensaje
    public boolean isChatOpen = false;
    public String chatInput = "";

    public static class ChatMessage {
        public final String text;
        public final com.badlogic.gdx.graphics.Color color;
        public float timer;
        public ChatMessage(String text, com.badlogic.gdx.graphics.Color color) {
            this.text = text;
            this.color = color;
            this.timer = CHAT_FADE_SECONDS;
        }
    }

    /** Agrega un mensaje al chat (hilo-seguro vía postRunnable si se llama desde red). */
    public void addChatMessage(String text, com.badlogic.gdx.graphics.Color color) {
        Gdx.app.postRunnable(() -> {
            chatMessages.addFirst(new ChatMessage(text, color));
            if (chatMessages.size() > 20) chatMessages.removeLast();
        });
    }

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
    // Guard para evitar que dispose() corra más de una vez (hide() lo llama automáticamente)
    private boolean disposed = false;
    private boolean showDeathScreen = false;
    private boolean wasNightLastFrame = false; // para detectar la transición DÍA→NOCHE
    private float nightSpawnTimer = 0f; // temporizador para spawn continuo de noche
    private static final float NIGHT_SPAWN_INTERVAL = 10f; // cada 10s de noche intenta reponer fantasmas
    private float snowSpawnTimer = 0f; // ← nuevo
    private static final float SNOW_SPAWN_INTERVAL = 15f; // cada 15 segundos en clima nevado
    private static final int MAX_NIGHT_GHOSTS = 2; // cuántos fantasmas spawnear por noche
    private boolean showRespawnScreen = false;
    private float saveFeedbackTimer = 0f;

    public boolean showInventory = false;

    public GameScreen(WhereYouAtGame game, long seed, String worldName, String worldFileName, NetworkManager netManager) {
        this.game = game;
        this.seed = seed;
        this.worldName = (worldName != null && !worldName.isEmpty()) ? worldName : "World";
        this.worldFileName = worldFileName;
        this.createdTime = System.currentTimeMillis();
        this.netManager = netManager;

        // 1. Configurar cámara del mundo con orientación Y-down
        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new ExtendViewport(768, 576, camera);

        // 2. Configurar cámara de HUD para interfaz fija en pantalla
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(true, 768, 576);
        hudViewport = new FitViewport(768, 576, hudCamera);

        // 3. Generar mundo procedural 512x512
        long actualSeed = (netManager != null && !netManager.isHost) ? (seed + 7777L) : seed;
        Gdx.app.log("GameScreen", "Generating 512x512 procedural world with seed: " + actualSeed + (netManager != null && !netManager.isHost ? " (Isla 2)" : "(Isla 1)"));
        mapGen = new MapGenerator(512, 512, actualSeed, String.valueOf(actualSeed));
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

        // 6. Registrar callbacks de red si estamos en modo multijugador
        if (netManager != null) {
            netManager.setListener(new NetworkManager.NetworkListener() {
                @Override
                public void onConnected() {
                    if (disposed) return;
                    String who = netManager.isHost ? "Jugador 2" : "Jugador 1 (host)";
                    addChatMessage(who + " se unio al mundo", com.badlogic.gdx.graphics.Color.YELLOW);
                }
                @Override
                public void onMessageReceived(String message) {
                    if (disposed) return;
                    if (message.startsWith("CHAT:")) {
                        String sender = netManager.isHost ? "J2" : "J1";
                        addChatMessage("[" + sender + "] " + message.substring(5),
                                com.badlogic.gdx.graphics.Color.WHITE);
                    }
                }
                @Override
                public void onDisconnected() {
                    if (disposed) return;
                    addChatMessage("El otro jugador se desconecto.", new com.badlogic.gdx.graphics.Color(1f,0.4f,0.4f,1f));
                }
            });
            // Si somos el cliente, notificar al host que nos conectamos
            if (!netManager.isHost) {
                netManager.send("CHAT:Jugador 2 se unio al mundo");
            }
        }
    }

    public GameScreen(WhereYouAtGame game, long seed, String worldName, String worldFileName) {
        this(game, seed, worldName, worldFileName, null);
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

        // Decrementar timers de mensajes del chat
        java.util.Iterator<ChatMessage> chatIt = chatMessages.iterator();
        while (chatIt.hasNext()) {
            ChatMessage cm = chatIt.next();
            cm.timer -= dt;
            if (cm.timer <= 0f) chatIt.remove();
        }

        // --- CHAT: capturar input de teclado ---
        if (netManager != null) {
            // T abre el chat (solo si no está en pausa/muerte y el chat está cerrado)
            if (!isPaused && !showDeathScreen && !showRespawnScreen && !isChatOpen
                    && Gdx.input.isKeyJustPressed(Keys.T)) {
                isChatOpen = true;
                chatInput = "";
                // Resetear timer de mensajes visibles para que aparezcan
                for (ChatMessage cm : chatMessages) cm.timer = CHAT_FADE_SECONDS;
                return; // no procesar más teclas este frame
            }

            if (isChatOpen) {
                // ENTER envía el mensaje
                if (Gdx.input.isKeyJustPressed(Keys.ENTER) && !chatInput.trim().isEmpty()) {
                    String me = netManager.isHost ? "J1" : "J2";
                    addChatMessage("[" + me + "] " + chatInput.trim(), com.badlogic.gdx.graphics.Color.WHITE);
                    netManager.send("CHAT:" + chatInput.trim());
                    chatInput = "";
                    isChatOpen = false;
                } else if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
                    isChatOpen = false;
                }
                // El texto lo captura GameHUD vía InputProcessor (ver abajo)
                renderScene();
                return; // bloquear movimiento/ataque mientras el chat está abierto
            }
        }

        // Pausa con ESC (solo vivo y fuera de pantallas de muerte)
        if (!showDeathScreen && !showRespawnScreen && Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            isPaused = !isPaused;
            if (isPaused) {
                showInventory = false; // Cerrar inventario al pausar
                saveProgress();
            }
        }

        if (!isPaused && !showDeathScreen && !showRespawnScreen) {
            int livesBefore = player.lives;
            updateGame(dt);

            if (player.lives < livesBefore && !player.dead) {
                showRespawnScreen = true;
                showInventory = false; // Cerrar inventario al morir
                player.randomSpawn(mapGen);
            } else if (player.dead && !showDeathScreen) {
                showDeathScreen = true;
                showInventory = false; // Cerrar inventario en pantalla de muerte
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

        // Limpiar fantasmas que quedaron demasiado lejos del jugador (> 25 tiles)
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost && monster[i].distanceTo(player) > 35 * tileSize) {
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
            // Persistir bandera de multijugador si somos el host
            boolean isHost = (netManager != null && netManager.isHost);
            WorldSaveManager.saveGameProgress(worldFileName, worldName, seed, createdTime, player, dayNight, monster, isHost);
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
                showInventory, this);
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
        // Guard: dispose() puede ser llamado por hide() automáticamente y también
        // por returnToTitle() antes. Solo ejecutar una vez.
        if (disposed) return;
        disposed = true;

        Gdx.app.log("GameScreen", "dispose() iniciado" + (netManager != null ? " [multijugador]" : ""));
        try { saveProgress(); } catch (Throwable t) { Gdx.app.error("GameScreen", "saveProgress falló", t); }
        try { tileM.dispose(); } catch (Throwable t) { Gdx.app.error("GameScreen", "tileM.dispose falló", t); }
        try { player.dispose(); } catch (Throwable t) { Gdx.app.error("GameScreen", "player.dispose falló", t); }
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] != null) {
                try { monster[i].dispose(); } catch (Throwable ignored) {}
                monster[i] = null;
            }
        }
        // Anular el listener de red ANTES de desconectar para que el callback
        // onDisconnected no se dispare sobre este GameScreen ya muerto.
        if (netManager != null) {
            try {
                netManager.setListener(null);
                netManager.disconnect();
            } catch (Throwable t) { Gdx.app.error("GameScreen", "netManager.disconnect falló", t); }
        }
        try { footprintM.dispose(); } catch (Throwable t) { Gdx.app.error("GameScreen", "footprintM.dispose falló", t); }
        try { snowPS.dispose();    } catch (Throwable t) { Gdx.app.error("GameScreen", "snowPS.dispose falló", t); }
        try { lighting.dispose();  } catch (Throwable t) { Gdx.app.error("GameScreen", "lighting.dispose falló", t); }
        try { audio.dispose();     } catch (Throwable t) { Gdx.app.error("GameScreen", "audio.dispose falló", t); }
        try { hud.dispose();       } catch (Throwable t) { Gdx.app.error("GameScreen", "hud.dispose falló", t); }
        Gdx.app.log("GameScreen", "dispose() completado");
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
            if (monster[i] instanceof MON_Ghost && monster[i].distanceTo(player) < 35 * tileSize) {
                nearbyGhosts++;
            }
        }
        int toSpawn = MAX_NIGHT_GHOSTS - nearbyGhosts;
        if (toSpawn <= 0)
            return; // Ya hay suficientes fantasmas cerca

        // Centro de búsqueda: posición del jugador en tiles
        int playerCol = (int) (player.worldX / tileSize);
        int playerRow = (int) (player.worldY / tileSize);

        // Radio de spawn
        float halfW = (camera.viewportWidth * camera.zoom) / 2f;
        float halfH = (camera.viewportHeight * camera.zoom) / 2f;
        int minRadius = (int) (Math.max(halfW, halfH) / tileSize) + 2;
        int maxRadius = minRadius + 4;

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
                ((MON_Ghost) monster[i]).takeDamage(999, 0, 0);
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
            float halfW = (camera.viewportWidth * camera.zoom) / 2f;
            float halfH = (camera.viewportHeight * camera.zoom) / 2f;
            int minRadius = (int) (Math.hypot(halfW, halfH) / tileSize) + 2;
            int radius = minRadius + rand.nextInt(5);
            int angle = rand.nextInt(360);
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
