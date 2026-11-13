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
import com.whereyouat.items.BoatPiece1;
import com.whereyouat.items.BoatPiece2;
import com.whereyouat.items.BoatPiece3;
import com.whereyouat.items.FrogEggs;
import com.whereyouat.items.IceGem;
import com.whereyouat.items.Item;
import com.whereyouat.items.ItemPotion;
import com.whereyouat.items.OddMushroom;
import com.whereyouat.items.Sword;
import com.whereyouat.items.boat;
import com.whereyouat.items.devsword;
import com.whereyouat.lighting.DayNightCycle;
import com.whereyouat.lighting.LightingEngine;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_Ghost;
import com.whereyouat.monster.MON_frogger;
import com.whereyouat.monster.MON_mushroom;
import com.whereyouat.monster.MON_snowGhost;
import com.whereyouat.net.NetworkManager;
import com.whereyouat.save.WorldSaveManager;
import com.whereyouat.monster.MON_zombie;
import com.whereyouat.monster.MON_bat;

public class GameScreen extends ScreenAdapter {
    public final WhereYouAtGame game;

    public final OrthographicCamera camera;
    public final Viewport viewport;
    public final OrthographicCamera hudCamera;
    public final Viewport hudViewport;

    public final MapGenerator mapGen;
    public final TileManager tileM;
    public final Player player;
    public final DayNightCycle dayNight;
    public final FootprintManager footprintM;
    public final SnowParticleSystem snowPS;
    public final LightingEngine lighting;
    public final AudioManager audio;
    public final NetworkManager netManager;
    public String roomCode = "";

    public final java.util.LinkedList<ChatMessage> chatMessages = new java.util.LinkedList<>();
    public static final int CHAT_MAX_VISIBLE = 6;
    public static final float CHAT_FADE_SECONDS = 8f;
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

    public void addChatMessage(String text, com.badlogic.gdx.graphics.Color color) {
        Gdx.app.postRunnable(() -> {
            chatMessages.addFirst(new ChatMessage(text, color));
            if (chatMessages.size() > 20)
                chatMessages.removeLast();
        });
    }

    private final GameHUD hud;

    public Entity monster[] = new Entity[20];
    public final AssetSetter assetSetter;
    public final int tileSize = TileManager.TILE_SIZE;

    public final ArrayList<Entity> entityList = new ArrayList<>();
    private final Comparator<Entity> entitySorter = (e1, e2) -> Float.compare(
            e1.worldY + e1.solidArea.y + e1.solidArea.height,
            e2.worldY + e2.solidArea.y + e2.solidArea.height);

    public final String worldFileName;
    public final String worldName;
    public final long seed;
    public final long createdTime;

    private float animTime = 0f;
    private float autoSaveTimer = 60f;
    private boolean isPaused = false;

    private boolean disposed = false;
    private boolean showDeathScreen = false;
    private boolean wasNightLastFrame = false;
    private float nightSpawnTimer = 0f;
    private static final float NIGHT_SPAWN_INTERVAL = 10f;
    private static final int MAX_NIGHT_GHOSTS = 2;
    private static final int MAX_NIGHT_ZOMBIES = 2;
    private static final int MAX_NIGHT_BATS = 2;
    private boolean showRespawnScreen = false;
    private float saveFeedbackTimer = 0f;

    public boolean showInventory = false;

    public boolean showSailHint = false;
    public boolean otherPlayerSailed = false;
    private boolean transitioningToEndGame = false;

    public GameScreen(WhereYouAtGame game, long seed, String worldName, String worldFileName,
            NetworkManager netManager) {
        this.game = game;
        this.seed = seed;
        this.worldName = (worldName != null && !worldName.isEmpty()) ? worldName : "World";
        this.worldFileName = worldFileName;
        this.createdTime = System.currentTimeMillis();
        this.netManager = netManager;

        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new ExtendViewport(768, 576, camera);

        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(true, 768, 576);
        hudViewport = new FitViewport(768, 576, hudCamera);

        long actualSeed = (netManager != null && !netManager.isHost) ? (seed + 7777L) : seed;
        Gdx.app.log("GameScreen", "Generating 512x512 procedural world with seed: " + actualSeed
                + (netManager != null && !netManager.isHost ? " (Isla 2)" : "(Isla 1)"));
        mapGen = new MapGenerator(512, 512, actualSeed, String.valueOf(actualSeed));
        mapGen.generate();

        tileM = new TileManager();
        player = new Player();
        player.gp = this;
        player.initSpawn(mapGen);

        dayNight = new DayNightCycle();
        footprintM = new FootprintManager();
        snowPS = new SnowParticleSystem(seed);
        tileM.setSnowPS(snowPS);

        assetSetter = new AssetSetter(this);
        setupGame();

        lighting = new LightingEngine(768, 576);
        audio = new AudioManager();

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

        game.fadeOutMenuMusic();

        if (netManager != null) {
            netManager.setListener(new NetworkManager.NetworkListener() {
                @Override
                public void onConnected() {
                    if (disposed)
                        return;
                    String who = netManager.isHost ? "Jugador 2" : "Jugador 1 (host)";
                    addChatMessage(who + " se unio al mundo", com.badlogic.gdx.graphics.Color.YELLOW);

                    if (netManager.isHost) {
                        netManager.send("SEED:" + seed);
                    }
                }

                @Override
                public void onMessageReceived(String message) {
                    if (disposed)
                        return;
                    if (message.startsWith("CHAT:")) {
                        String sender = netManager.isHost ? "J2" : "J1";
                        addChatMessage("[" + sender + "] " + message.substring(5),
                                com.badlogic.gdx.graphics.Color.WHITE);
                    } else if ("SAILED".equals(message)) {
                        otherPlayerSailed = true;
                        String who = netManager.isHost ? "Jugador 2" : "Jugador 1 (host)";
                        addChatMessage("[Aviso] " + who + " ya zarpó.", com.badlogic.gdx.graphics.Color.CYAN);
                        showNotification(who + " ya zarpó de la isla.");
                    }
                }

                @Override
                public void onDisconnected() {
                    if (disposed)
                        return;
                    addChatMessage("El otro jugador se desconecto.",
                            new com.badlogic.gdx.graphics.Color(1f, 0.4f, 0.4f, 1f));

                    if (netManager.isHost) {
                        netManager.reopenServer();

                        netManager.setListener(this);
                        addChatMessage("Esperando reconexion de Jugador 2...",
                                new com.badlogic.gdx.graphics.Color(1f, 0.8f, 0.2f, 1f));
                    }
                }
            });

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

        java.util.Iterator<ChatMessage> chatIt = chatMessages.iterator();
        while (chatIt.hasNext()) {
            ChatMessage cm = chatIt.next();
            cm.timer -= dt;
            if (cm.timer <= 0f)
                chatIt.remove();
        }

        if (netManager != null) {
            if (!isPaused && !showDeathScreen && !showRespawnScreen && !isChatOpen
                    && Gdx.input.isKeyJustPressed(Keys.T)) {
                isChatOpen = true;
                chatInput = "";

                for (ChatMessage cm : chatMessages)
                    cm.timer = CHAT_FADE_SECONDS;
                return;
            }

            if (isChatOpen) {
                if (Gdx.input.isKeyJustPressed(Keys.ENTER) && !chatInput.trim().isEmpty()) {
                    String me = netManager.isHost ? "J1" : "J2";
                    addChatMessage("[" + me + "] " + chatInput.trim(), com.badlogic.gdx.graphics.Color.WHITE);
                    netManager.send("CHAT:" + chatInput.trim());
                    chatInput = "";
                    isChatOpen = false;
                } else if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
                    isChatOpen = false;
                }

                renderScene();
                return;
            }
        }

        if (!showDeathScreen && !showRespawnScreen && Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            isPaused = !isPaused;
            if (isPaused) {
                showInventory = false;
                saveProgress();
            }
        }

        if (!isPaused && !showDeathScreen && !showRespawnScreen) {
            int livesBefore = player.lives;
            updateGame(dt);

            if (player.lives < livesBefore && !player.dead) {
                showRespawnScreen = true;
                showInventory = false;
                player.randomSpawn(mapGen);
            } else if (player.dead && !showDeathScreen) {
                showDeathScreen = true;
                showInventory = false;
                saveProgress();
            }
        }

        renderScene();

        if (!isPaused && !showDeathScreen && Gdx.input.isKeyJustPressed(Keys.E)) {
            showInventory = !showInventory;
        }

        if (!isPaused && !showDeathScreen && Gdx.input.isKeyJustPressed(Keys.B)) {
            if (player.hasBoat() && isNearOcean()) {
                transitioningToEndGame = true;
                NetworkManager nm = (netManager != null && netManager.isConnected) ? netManager : null;
                game.setScreen(new EndGameScreen(game, nm, otherPlayerSailed));
                return;
            } else if (player.hasBoat()) {
                showNotification("Tenés que estar cerca del océano para zarpar.");
            }
        }

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

        if (!isPaused && !showInventory && !showDeathScreen && Gdx.input.isButtonJustPressed(0)
                && player.attackCooldown <= 0f && player.hasSword()) {
            Vector3 clickWorld = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));

            float playerCenterX = player.worldX + TileManager.TILE_SIZE / 2f;
            float playerCenterY = player.worldY + TileManager.TILE_SIZE / 2f;

            float dx = clickWorld.x - playerCenterX;
            float dy = clickWorld.y - playerCenterY;

            String attackDir;
            if (Math.abs(dx) >= Math.abs(dy)) {
                attackDir = (dx > 0) ? "right" : "left";
            } else {
                attackDir = (dy > 0) ? "down" : "up";
            }

            player.attack(attackDir, player.getAttackDamage());
        }
    }

    private void updateGame(float delta) {
        player.update(delta, mapGen, tileM, monster);

        boolean isNightNow = dayNight.isNight();
        if (isNightNow && !wasNightLastFrame) {
            spawnNightGhosts();
            spawnNightZombies();
            spawnNightBats();
            nightSpawnTimer = 0f;
        } else if (!isNightNow && wasNightLastFrame) {
            despawnNightGhosts();
            despawnNightZombies();
            despawnNightBats();
            nightSpawnTimer = 0f;
        } else if (isNightNow) {
            nightSpawnTimer += delta;
            if (nightSpawnTimer >= NIGHT_SPAWN_INTERVAL) {
                spawnNightGhosts();
                spawnNightZombies();
                spawnNightBats();
                nightSpawnTimer = 0f;
            }
        }
        wasNightLastFrame = isNightNow;

        tileM.update(delta, mapGen, dayNight);
        dayNight.update();
        footprintM.update(delta);

        int playerCol = (int) (player.worldX / TileManager.TILE_SIZE);
        int playerRow = (int) (player.worldY / TileManager.TILE_SIZE);
        boolean inSnow = (playerCol >= 0 && playerCol < mapGen.mapCols && playerRow >= 0 && playerRow < mapGen.mapRows)
                && mapGen.isSnowBiome[playerCol][playerRow];
        snowPS.update(delta, inSnow, 1.0f, hudCamera.viewportWidth, hudCamera.viewportHeight);

        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost && monster[i].distanceTo(player) > 35 * tileSize) {
                monster[i].dispose();
                monster[i] = null;
            }
            if (monster[i] instanceof MON_zombie && monster[i].distanceTo(player) > 35 * tileSize) {
                monster[i].dispose();
                monster[i] = null;
            }
            if (monster[i] instanceof MON_bat && monster[i].distanceTo(player) > 35 * tileSize) {
                monster[i].dispose();
                monster[i] = null;
            }
        }

        for (int i = 0; i < monster.length; i++) {
            if (monster[i] != null) {
                monster[i].update(delta);
            }
        }

        audio.update(delta, dayNight);
        hud.update(delta);

        autoSaveTimer -= delta;
        if (autoSaveTimer <= 0f) {
            autoSaveTimer = 60f;
            saveProgress();
        }

        if (player.tryAssembleBoat()) {
            showNotification("¡Obtuviste las 3 piezas del barco y lo armás!");
        }

        showSailHint = player.hasBoat() && isNearOcean();
    }

    public boolean isNearOcean() {
        if (mapGen == null || mapGen.isDeepWater == null || player == null) return false;

        int centerCol = (int) ((player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int centerRow = (int) ((player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);

        int radius = 6;
        for (int dc = -radius; dc <= radius; dc++) {
            for (int dr = -radius; dr <= radius; dr++) {
                int c = centerCol + dc;
                int r = centerRow + dr;
                if (c >= 0 && c < mapGen.mapCols && r >= 0 && r < mapGen.mapRows) {
                    if (mapGen.isDeepWater[c][r]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void saveProgress() {
        if (worldFileName != null && !worldFileName.isEmpty()) {
            boolean isHost = (netManager != null && netManager.isHost);
            WorldSaveManager.saveGameProgress(worldFileName, worldName, seed, createdTime, player, dayNight, monster,
                    isHost);
        }
    }

    private void renderScene() {
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

        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        tileM.trackVisibleForWind(mapGen, minX, minY, maxX, maxY);
        tileM.drawGround(game.batch, mapGen, minX, minY, maxX, maxY);

        footprintM.draw(game.batch, minX, minY, maxX, maxY);

        tileM.drawGroundShadows(game.batch, mapGen, dayNight, minX, minY, maxX, maxY,
                player.getCurrentSprite(), player.worldX, player.worldY);

        snowPS.drawWorldLeaves(game.batch, minX, minY, maxX, maxY);

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

        tileM.drawLilipads(game.batch, mapGen, startCol, endCol, startRow, endRow);

        int entityIndex = 0;
        for (int row = startRow; row <= endRow; row++) {
            float rowFootY = (row + 1) * TileManager.TILE_SIZE;

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

            tileM.drawDecorationsRow(game.batch, mapGen, startCol, endCol, row, camera);
        }

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

        hudCamera.update();
        game.batch.setProjectionMatrix(hudCamera.combined);
        lighting.renderLightmap(game.batch, dayNight, hudCamera.viewportWidth / 2f, hudCamera.viewportHeight / 2f);

        game.batch.begin();
        lighting.drawLightmap(game.batch, 0, 0, hudCamera.viewportWidth, hudCamera.viewportHeight);
        game.batch.end();

        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();
        snowPS.drawScreenSnow(game.batch, 0, 0);
        game.batch.end();

        hud.render(game.batch, player, isPaused, showDeathScreen, showRespawnScreen, saveFeedbackTimer, animTime,
                showInventory, this);
    }

    public void setupGame() {
        assetSetter.setMonster();
    }

    public void update() {
        updateGame(Gdx.graphics != null ? Gdx.graphics.getDeltaTime() : 1f / 60f);
    }

    public void update(float delta) {
        updateGame(delta);
    }

    private void drawPlayer() {
        int footCol = (int) ((player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int footRow = (int) ((player.worldY + Player.HIT_OFFSET_Y + Player.HIT_H / 2f) / TileManager.TILE_SIZE);
        int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);

        if (waterLevel > 0) {
            int cutOff = player.getWaterCutOff(waterLevel);
            int yShift = player.getWaterYShift(waterLevel);

            float visibleWorldH = TileManager.TILE_SIZE - cutOff;

            float clipWorldY = player.worldY;

            Vector3 topLeft = camera.project(new Vector3(player.worldX, clipWorldY, 0));
            Vector3 bottomRight = camera
                    .project(new Vector3(player.worldX + TileManager.TILE_SIZE, clipWorldY + visibleWorldH, 0));

            int sx = (int) Math.min(topLeft.x, bottomRight.x);
            int sy = (int) Math.min(topLeft.y, bottomRight.y);
            int sw = (int) Math.abs(bottomRight.x - topLeft.x);
            int sh = (int) Math.abs(bottomRight.y - topLeft.y);

            game.batch.flush();
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(sx, sy, Math.max(1, sw), Math.max(1, sh));

            player.draw(game.batch, waterLevel, null);

            game.batch.flush();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

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

        if (info.hasPlayerData) {
            player.worldX = info.playerWorldX;
            player.worldY = info.playerWorldY;
            player.direction = info.playerDirection;
            player.health = info.playerHealth;
            player.lives = info.playerLives;
        }

        if (info.hasInventoryData) {
            player.inventory.clear();
            for (String itemId : info.inventoryItems) {
                Item item = createItem(itemId);
                if (item != null)
                    player.addItem(item);
            }

            if (!player.hasDevSword()) {
                player.addItem(new devsword());
            }
        }

        if (info.hasMonsterData) {
            for (int i = 0; i < monster.length; i++) {
                if (monster[i] != null) {
                    monster[i].dispose();
                    monster[i] = null;
                }
            }

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

        if (info.hasDayNightData && info.dayNightTicks >= 0) {
            dayNight.setTimeTicks(info.dayNightTicks);
        }
    }

    public void showNotification(String message) {
        if (hud != null) {
            hud.setNotification(message, 3.5f);
        }
    }

    private Item createItem(String id) {
        if (id == null)
            return null;
        switch (id) {
            case "Sword":
                return new Sword();
            case "devsword":
                return new devsword();
            case "boat":
                return new boat();
            case "OddMushroom":
                return new OddMushroom();
            case "FrogEggs":
                return new FrogEggs();
            case "IceGem":
                return new IceGem();
            case "ItemPotion":
                return new ItemPotion();
            case "BoatPiece1":
                return new BoatPiece1();
            case "BoatPiece2":
                return new BoatPiece2();
            case "BoatPiece3":
                return new BoatPiece3();
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
                return new MON_Ghost(this);
            case "MON_zombie":
                return new MON_zombie(this);
            case "MON_bat":
                return new MON_bat(this);
            case "MON_snowGhost":
                return new MON_snowGhost(this);
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
        if (disposed)
            return;
        disposed = true;

        Gdx.app.log("GameScreen", "dispose() iniciado" + (netManager != null ? " [multijugador]" : ""));
        try {
            saveProgress();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "saveProgress falló", t);
        }
        try {
            tileM.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "tileM.dispose falló", t);
        }
        try {
            player.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "player.dispose falló", t);
        }
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] != null) {
                try {
                    monster[i].dispose();
                } catch (Throwable ignored) {
                }
                monster[i] = null;
            }
        }

        if (netManager != null && !transitioningToEndGame) {
            netManager.setListener(null);
            final NetworkManager nm = netManager;
            Thread disconnectThread = new Thread(() -> {
                try {
                    nm.disconnect();
                } catch (Throwable ignored) {
                }
            });
            disconnectThread.setDaemon(true);
            disconnectThread.setName("net-disconnect");
            disconnectThread.start();
        }
        try {
            footprintM.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "footprintM.dispose falló", t);
        }
        try {
            snowPS.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "snowPS.dispose falló", t);
        }
        try {
            lighting.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "lighting.dispose falló", t);
        }
        try {
            audio.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "audio.dispose falló", t);
        }
        try {
            hud.dispose();
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "hud.dispose falló", t);
        }
        Gdx.app.log("GameScreen", "dispose() completado");
    }

    private void spawnNightGhosts() {
        if (mapGen == null || player == null)
            return;

        int nearbyGhosts = 0;
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost && monster[i].distanceTo(player) < 35 * tileSize) {
                nearbyGhosts++;
            }
        }
        int toSpawn = MAX_NIGHT_GHOSTS - nearbyGhosts;
        if (toSpawn <= 0)
            return;

        int playerCol = (int) (player.worldX / tileSize);
        int playerRow = (int) (player.worldY / tileSize);

        float halfW = (camera.viewportWidth * camera.zoom) / 2f;
        float halfH = (camera.viewportHeight * camera.zoom) / 2f;
        int minRadius = (int) (Math.max(halfW, halfH) / tileSize) + 2;
        int maxRadius = minRadius + 4;

        java.util.Random rand = new java.util.Random();
        int spawned = 0;

        for (int attempt = 0; attempt < 60 && spawned < toSpawn; attempt++) {
            int angle = rand.nextInt(360);
            int radius = minRadius + rand.nextInt(maxRadius - minRadius);
            int col = playerCol + (int) (Math.cos(Math.toRadians(angle)) * radius);
            int row = playerRow + (int) (Math.sin(Math.toRadians(angle)) * radius);

            if (col < 1 || col >= mapGen.mapCols - 1 || row < 1 || row >= mapGen.mapRows - 1)
                continue;

            if (mapGen.isDeepWater[col][row])
                continue;
            if (tileM.collidesWithTree(mapGen, col * tileSize, row * tileSize, tileSize, tileSize))
                continue;

            int slotIndex = -1;
            for (int i = 0; i < monster.length; i++) {
                if (monster[i] == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex == -1)
                break;

            MON_Ghost ghost = new MON_Ghost(this);
            ghost.worldX = col * tileSize;
            ghost.worldY = row * tileSize;
            monster[slotIndex] = ghost;
            spawned++;

            Gdx.app.log("GameScreen", "Ghost spawned at (" + col + "," + row + ") slot " + slotIndex);
        }
    }

    private void spawnNightZombies() {
        if (mapGen == null || player == null)
            return;

        int nearbyZombies = 0;
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_zombie && monster[i].distanceTo(player) < 35 * tileSize) {
                nearbyZombies++;
            }
        }
        int toSpawnZ = MAX_NIGHT_ZOMBIES - nearbyZombies;
        if (toSpawnZ <= 0)
            return;

        int playerCol = (int) (player.worldX / tileSize);
        int playerRow = (int) (player.worldY / tileSize);

        float halfW = (camera.viewportWidth * camera.zoom) / 2f;
        float halfH = (camera.viewportHeight * camera.zoom) / 2f;
        int minRadius = (int) (Math.max(halfW, halfH) / tileSize) + 2;
        int maxRadius = minRadius + 4;

        java.util.Random rand = new java.util.Random();
        int spawned = 0;

        for (int attempt = 0; attempt < 60 && spawned < toSpawnZ; attempt++) {
            int angle = rand.nextInt(360);
            int radius = minRadius + rand.nextInt(maxRadius - minRadius);
            int col = playerCol + (int) (Math.cos(Math.toRadians(angle)) * radius);
            int row = playerRow + (int) (Math.sin(Math.toRadians(angle)) * radius);

            if (col < 1 || col >= mapGen.mapCols - 1 || row < 1 || row >= mapGen.mapRows - 1)
                continue;

            if (mapGen.isDeepWater[col][row])
                continue;
            if (tileM.collidesWithTree(mapGen, col * tileSize, row * tileSize, tileSize, tileSize))
                continue;

            int slotIndex = -1;
            for (int i = 0; i < monster.length; i++) {
                if (monster[i] == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex == -1)
                break;

            MON_zombie zombie = new MON_zombie(this);
            zombie.worldX = col * tileSize;
            zombie.worldY = row * tileSize;
            monster[slotIndex] = zombie;
            spawned++;

            Gdx.app.log("GameScreen", "Zombie spawned at (" + col + "," + row + ") slot " + slotIndex);
        }
    }

    private void despawnNightGhosts() {
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_Ghost) {
                ((MON_Ghost) monster[i]).takeDamage(9999, 0, 0);
            }
        }
    }

    private void despawnNightZombies() {
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_zombie) {
                ((MON_zombie) monster[i]).takeDamage(9999, 0, 0);
            }
        }
    }

    private void spawnNightBats() {
        if (mapGen == null || player == null)
            return;

        int nearbyBats = 0;
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_bat && monster[i].distanceTo(player) < 35 * tileSize) {
                nearbyBats++;
            }
        }
        int toSpawnB = MAX_NIGHT_BATS - nearbyBats;
        if (toSpawnB <= 0)
            return;

        int playerCol = (int) (player.worldX / tileSize);
        int playerRow = (int) (player.worldY / tileSize);

        float halfW = (camera.viewportWidth * camera.zoom) / 2f;
        float halfH = (camera.viewportHeight * camera.zoom) / 2f;
        int minRadius = (int) (Math.max(halfW, halfH) / tileSize) + 2;
        int maxRadius = minRadius + 4;

        java.util.Random rand = new java.util.Random();
        int spawned = 0;

        for (int attempt = 0; attempt < 60 && spawned < toSpawnB; attempt++) {
            int angle = rand.nextInt(360);
            int radius = minRadius + rand.nextInt(maxRadius - minRadius);
            int col = playerCol + (int) (Math.cos(Math.toRadians(angle)) * radius);
            int row = playerRow + (int) (Math.sin(Math.toRadians(angle)) * radius);

            if (col < 1 || col >= mapGen.mapCols - 1 || row < 1 || row >= mapGen.mapRows - 1)
                continue;

            if (mapGen.isDeepWater[col][row])
                continue;
            if (tileM.collidesWithTree(mapGen, col * tileSize, row * tileSize, tileSize, tileSize))
                continue;

            int slotIndex = -1;
            for (int i = 0; i < monster.length; i++) {
                if (monster[i] == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex == -1)
                break;

            MON_bat bat = new MON_bat(this);
            bat.worldX = col * tileSize;
            bat.worldY = row * tileSize;
            monster[slotIndex] = bat;
            spawned++;

            Gdx.app.log("GameScreen", "Bat spawned at (" + col + "," + row + ") slot " + slotIndex);
        }
    }

    private void despawnNightBats() {
        for (int i = 0; i < monster.length; i++) {
            if (monster[i] instanceof MON_bat) {
                ((MON_bat) monster[i]).takeDamage(9999, 0, 0);
            }
        }
    }
}
