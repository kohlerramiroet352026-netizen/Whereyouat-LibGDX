package com.whereyouat.screens;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.whereyouat.WhereYouAtGame;
import com.whereyouat.audio.AudioManager;
import com.whereyouat.entity.Entity;
import com.whereyouat.entity.Player;
import com.whereyouat.items.Item;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_bat;
import com.whereyouat.monster.MON_frogger;
import com.whereyouat.monster.MON_Ghost;
import com.whereyouat.monster.MON_mushroom;
import com.whereyouat.monster.MON_snowGhost;
import com.whereyouat.monster.MON_zombie;

public class GameHUD {
    public interface HUDCallback {
        void onReturnToTitle();
        void onSaveProgress();
        void onRespawn();
    }

    private final WhereYouAtGame game;
    private final OrthographicCamera hudCamera;
    private final AudioManager audio;
    private final HUDCallback callback;

    private BitmapFont fontSmall;
    private BitmapFont fontMedium;
    private BitmapFont fontLarge;

    private Texture heartTexture;
    private TextureRegion heartRegion;
    private Texture whiteTex;
    private TextureRegion whitePixel;

    private Texture heartContainerSheet;
    private final TextureRegion[] heartContainerFrames = new TextureRegion[11];

    private Texture healthBarSheet;
    private final TextureRegion[] healthBarFrames = new TextureRegion[9];

    private Texture ironHeartTexture;
    private TextureRegion ironHeartRegion;

    private static final float MINIMAP_VIEW_TILES = 36f;
    private Texture minimapFrameTex;
    private TextureRegion minimapFrameRegion;
    private FrameBuffer minimapFbo;
    private TextureRegion minimapFboRegion;
    private OrthographicCamera minimapCam;
    private int lastMinimapTileCol = -9999;
    private int lastMinimapTileRow = -9999;
    private int lastMinimapWaterFrame = -1;

    private TextureRegion playerIconRegion;
    private TextureRegion batIconRegion;
    private TextureRegion froggerIconRegion;
    private TextureRegion frostIconRegion;
    private TextureRegion ghostIconRegion;
    private TextureRegion mushroomIconRegion;
    private TextureRegion zombIconRegion;

    private Texture buttonSlotTex;
    private TextureRegion buttonSlotRegion;
    private Texture backButtonTex;
    private TextureRegion backButtonRegion;

    public int slotCol = 0;
    public int slotRow = 0;

    public static class NotificationItem {
        public String text;
        public float timer;

        public NotificationItem(String text, float timer) {
            this.text = text;
            this.timer = timer;
        }
    }

    private final List<NotificationItem> notifications = new ArrayList<>();

    public void setNotification(String message, float duration) {
        if (message == null || message.trim().isEmpty()) return;
        notifications.add(new NotificationItem(message, duration));
        if (notifications.size() > 5) {
            notifications.remove(0);
        }
    }

    public void update(float delta) {
        for (int i = notifications.size() - 1; i >= 0; i--) {
            NotificationItem item = notifications.get(i);
            item.timer -= delta;
            if (item.timer <= 0f) {
                notifications.remove(i);
            }
        }
    }

    private final List<Texture> hudTextures = new ArrayList<>();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    public GameHUD(WhereYouAtGame game, OrthographicCamera hudCamera, AudioManager audio, HUDCallback callback) {
        this.game = game;
        this.hudCamera = hudCamera;
        this.audio = audio;
        this.callback = callback;
        loadAssets();
    }

    private void loadAssets() {
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.flip = true;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "áéíóúÁÉÍÓÚñÑ¿¡üÜ";
            parameter.size = 11;
            parameter.shadowOffsetX = 1;
            parameter.shadowOffsetY = 1;
            parameter.shadowColor = new Color(0f, 0f, 0f, 0.85f);
            fontSmall = generator.generateFont(parameter);
            fontSmall.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            parameter.size = 16;
            fontMedium = generator.generateFont(parameter);
            fontMedium.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            parameter.size = 28;
            parameter.shadowOffsetX = 2;
            parameter.shadowOffsetY = 2;
            fontLarge = generator.generateFont(parameter);
            fontLarge.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            generator.dispose();
        } else {
            fontSmall = new BitmapFont();
            fontMedium = new BitmapFont();
            fontLarge = new BitmapFont();
        }

        Pixmap pixelPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixelPixmap.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
        whiteTex = new Texture(pixelPixmap);
        pixelPixmap.dispose();
        hudTextures.add(whiteTex);
        whitePixel = new TextureRegion(whiteTex);

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

        if (Gdx.files.internal("player/heart.png").exists()) {
            heartTexture = new Texture(Gdx.files.internal("player/heart.png"));
            heartTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(heartTexture);
            heartRegion = new TextureRegion(heartTexture);
            heartRegion.flip(false, true);
        }

        if (Gdx.files.internal("player/heartcontainer.png").exists()) {
            heartContainerSheet = new Texture(Gdx.files.internal("player/heartcontainer.png"));
            heartContainerSheet.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(heartContainerSheet);
            final int frameWidth = 64, frameHeight = 16, columns = 2;
            for (int i = 0; i < heartContainerFrames.length; i++) {
                int col = i % columns;
                int row = i / columns;
                heartContainerFrames[i] = new TextureRegion(heartContainerSheet, col * frameWidth, row * frameHeight, frameWidth, frameHeight);
                heartContainerFrames[i].flip(false, true);
            }
        }

        if (Gdx.files.internal("player/healthbar.png").exists()) {
            healthBarSheet = new Texture(Gdx.files.internal("player/healthbar.png"));
            healthBarSheet.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(healthBarSheet);
            for (int i = 0; i < 9; i++) {
                healthBarFrames[i] = new TextureRegion(healthBarSheet, 0, i * 16, 70, 16);
                healthBarFrames[i].flip(false, true);
            }
        }

        if (Gdx.files.internal("player/iron_heart.png").exists()) {
            ironHeartTexture = new Texture(Gdx.files.internal("player/iron_heart.png"));
            ironHeartTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(ironHeartTexture);
            ironHeartRegion = new TextureRegion(ironHeartTexture);
            ironHeartRegion.flip(false, true);
        }

        if (Gdx.files.internal("player/minimap.png").exists()) {
            minimapFrameTex = new Texture(Gdx.files.internal("player/minimap.png"));
            minimapFrameTex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(minimapFrameTex);
            minimapFrameRegion = new TextureRegion(minimapFrameTex);
            minimapFrameRegion.flip(false, true);

            minimapFbo = new FrameBuffer(Pixmap.Format.RGBA8888, 216, 216, false);
            Texture fboTex = minimapFbo.getColorBufferTexture();
            fboTex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            minimapFboRegion = new TextureRegion(fboTex);
            minimapCam = new OrthographicCamera();
        }

        playerIconRegion = loadMapIcon("player/player_mapicon.png");
        batIconRegion = loadMapIcon("monster/bat_mapicon.png");
        froggerIconRegion = loadMapIcon("monster/frogger_mapicon.png");
        frostIconRegion = loadMapIcon("monster/frost_mapicon.png");
        ghostIconRegion = loadMapIcon("monster/ghost_mapicon.png");
        mushroomIconRegion = loadMapIcon("monster/mushroom_mapicon.png");
        zombIconRegion = loadMapIcon("monster/zomb_mapicon.png");
    }

    private TextureRegion loadMapIcon(String path) {
        if (Gdx.files.internal(path).exists()) {
            Texture tex = new Texture(Gdx.files.internal(path));
            tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(tex);
            TextureRegion tr = new TextureRegion(tex);
            tr.flip(false, true);
            return tr;
        }
        return null;
    }

    public void renderInventory(SpriteBatch batch, Player player, float screenW, float screenH) {
        float slotSize = 42f;
        float slotPadding = 8f;
        float padding = 16f;

        float gridW = (5 * slotSize) + (4 * slotPadding);
        float gridH = (4 * slotSize) + (3 * slotPadding);

        float frameW = gridW + (padding * 2f);
        float frameH = gridH + (padding * 2f);
        float frameX = screenW - frameW - 20f;
        float frameY = 24f;

        drawSubWindow(batch, frameX, frameY, frameW, frameH);

        float solitaryBoxW = slotSize + 10f;
        float solitaryBoxH = slotSize + 10f;
        float solitaryBoxX = frameX - solitaryBoxW - 10f;

        for (int r = 0; r < 4; r++) {
            float boxY = frameY + (r * 56f);
            drawSubWindow(batch, solitaryBoxX, boxY, solitaryBoxW, solitaryBoxH);

            float sSlotX = solitaryBoxX + (solitaryBoxW - slotSize) / 2f;
            float sSlotY = boxY + (solitaryBoxH - slotSize) / 2f;

            Item itemInSlot = null;
            if (r == 0) itemInSlot = player.offsetItem;
            else if (r == 1) itemInSlot = player.helmetItem;
            else if (r == 2) itemInSlot = player.chestplateItem;
            else if (r == 3) itemInSlot = player.leggingsItem;

            if (itemInSlot != null && itemInSlot.icon != null) {
                batch.draw(itemInSlot.icon, sSlotX + 3f, sSlotY + 3f, slotSize - 6f, slotSize - 6f);
            }
        }

        float startSlotsX = frameX + padding;
        float startSlotsY = frameY + padding;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float slotX = startSlotsX + (col * (slotSize + slotPadding));
                float slotY = startSlotsY + (row * (slotSize + slotPadding));

                int index = col + (row * 5);
                if (index < player.inventory.size()) {
                    Item item = player.inventory.get(index);
                    if (item != null && item.icon != null) {
                        batch.draw(item.icon, slotX + 3f, slotY + 3f, slotSize - 6f, slotSize - 6f);
                    } else if (item != null) {
                        batch.setColor(0.9f, 0.25f, 0.25f, 0.9f);
                        batch.draw(whitePixel, slotX + 8f, slotY + 8f, slotSize - 16f, slotSize - 16f);
                        batch.setColor(1f, 1f, 1f, 1f);
                    }
                }
            }
        }

        if (slotCol == -1) {
            float sSlotX = solitaryBoxX + (solitaryBoxW - slotSize) / 2f;
            float sSlotY = frameY + (slotRow * 56f) + (solitaryBoxH - slotSize) / 2f;
            drawCursor(batch, sSlotX, sSlotY, slotSize);
        } else {
            float cursorX = startSlotsX + (slotCol * (slotSize + slotPadding));
            float cursorY = startSlotsY + (slotRow * (slotSize + slotPadding));
            drawCursor(batch, cursorX, cursorY, slotSize);
        }

        float descX = frameX;
        float descY = frameY + frameH + 8f;
        float descW = frameW;
        float descH = 120f;
        drawSubWindow(batch, descX, descY, descW, descH);

        Item selectedItem = null;
        if (slotCol == -1) {
            if (slotRow == 0) {
                selectedItem = player.offsetItem;
                if (selectedItem == null && fontSmall != null) {
                    fontSmall.setColor(Color.GRAY);
                    fontSmall.draw(batch, "Offset (vacio)", descX + 16f, descY + 24f);
                    fontSmall.setColor(Color.WHITE);
                    fontSmall.draw(batch, "Apreta [F] sobre el", descX + 16f, descY + 48f);
                    fontSmall.draw(batch, "escudo para equiparlo", descX + 16f, descY + 66f);
                }
            } else if (slotRow == 1) {
                selectedItem = player.helmetItem;
                if (selectedItem == null && fontSmall != null) {
                    fontSmall.setColor(Color.GRAY);
                    fontSmall.draw(batch, "Casco (vacio)", descX + 16f, descY + 24f);
                    fontSmall.setColor(Color.WHITE);
                    fontSmall.draw(batch, "Equipa un casco", descX + 16f, descY + 48f);
                }
            } else if (slotRow == 2) {
                selectedItem = player.chestplateItem;
                if (selectedItem == null && fontSmall != null) {
                    fontSmall.setColor(Color.GRAY);
                    fontSmall.draw(batch, "Pechera (vacio)", descX + 16f, descY + 24f);
                    fontSmall.setColor(Color.WHITE);
                    fontSmall.draw(batch, "Equipa una pechera", descX + 16f, descY + 48f);
                }
            } else if (slotRow == 3) {
                selectedItem = player.leggingsItem;
                if (selectedItem == null && fontSmall != null) {
                    fontSmall.setColor(Color.GRAY);
                    fontSmall.draw(batch, "Pantalones (vacio)", descX + 16f, descY + 24f);
                    fontSmall.setColor(Color.WHITE);
                    fontSmall.draw(batch, "Equipa pantalones", descX + 16f, descY + 48f);
                }
            }
        } else {
            int selectedIndex = slotCol + (slotRow * 5);
            if (selectedIndex < player.inventory.size()) {
                selectedItem = player.inventory.get(selectedIndex);
            }
        }

        if (selectedItem != null && fontSmall != null) {
            fontSmall.setColor(Color.GOLD);
            fontSmall.draw(batch, selectedItem.name, descX + 16f, descY + 24f);

            fontSmall.setColor(Color.WHITE);
            float lineY = descY + 48f;
            for (String line : selectedItem.description.split("\n")) {
                fontSmall.draw(batch, line, descX + 16f, lineY);
                lineY += 18f;
            }
        }

        float guideX = frameX;
        float guideY = descY + descH + 8f;
        float guideW = frameW;
        float guideH = 104f;
        drawSubWindow(batch, guideX, guideY, guideW, guideH);

        if (fontSmall != null) {
            float textX = guideX + 14f;
            fontSmall.setColor(Color.GOLD);
            fontSmall.draw(batch, "Guía de inventario", textX, guideY + 20f);

            fontSmall.setColor(Color.WHITE);
            fontSmall.draw(batch, "[Enter] Usar / Equipar", textX, guideY + 38f);
            fontSmall.draw(batch, "[1-9] Mover ítem", textX, guideY + 54f);
            fontSmall.draw(batch, "[Q] Tirar ítem", textX, guideY + 70f);
            fontSmall.draw(batch, "[F] Equipar en slot", textX, guideY + 86f);
        }
    }

    public void drawSubWindow(SpriteBatch batch, float x, float y, float width, float height){
        if (whitePixel == null) return;

        batch.setColor(0f, 0f, 0f, 0.82f);
        batch.draw(whitePixel, x + 5f, y, width - 10f, height);
        batch.draw(whitePixel, x, y +5f, width, height - 10f);
        batch.draw(whitePixel, x + 2f, y + 2f, width - 4f, height - 4f);

        batch.setColor(1f, 1f, 1f, 1f);
        float b = 3f;

        batch.draw(whitePixel, x + 6f, y, width - 12f, b);
        batch.draw(whitePixel, x + 6f, y + height - b, width -12f, b);
        batch.draw(whitePixel, x, y + 6f, b, height - 12f);
        batch.draw(whitePixel, x + width - b, y + 6f, b, height - 12f);

        batch.draw(whitePixel, x + 3f, y + 1f, 3f, b);
        batch.draw(whitePixel, x + 1f, y + 3f, b, 3f);

        batch.draw(whitePixel, x + width - 6f, y + 1f, 3f, b);
        batch.draw(whitePixel, x + width - 1f - b, y + 3f, b, 3f);

        batch.draw(whitePixel, x + 3f, y + height - 1f - b, 3f, b);
        batch.draw(whitePixel, x + 1f, y + height - 6f, b, 3f);

        batch.draw(whitePixel, x + width - 6f, y + height - 1f - b, 3f, b);
        batch.draw(whitePixel, x + width - 1f - b, y + height - 6f, b, 3f);

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void drawCursor(SpriteBatch batch, float x, float y, float size){
        if (whitePixel == null) return;

        batch.setColor(1f, 1f, 1f, 0.95f);
        float b = 2.5f;

        batch.draw(whitePixel, x + 4f, y, size - 8f, b);
        batch.draw(whitePixel, x + 4f, y + size - b, size - 8f, b);
        batch.draw(whitePixel, x, y + 4f, b, size - 8f);
        batch.draw(whitePixel, x + size - b, y + 4f, b, size - 8f);

        batch.draw(whitePixel, x + 2f, y + 1f, 2f, 2f);
        batch.draw(whitePixel, x + size - 4f, y + 1f, 2f, 2f);
        batch.draw(whitePixel, x + 2f, y + size - 3f, 2f, 2f);
        batch.draw(whitePixel, x + size - 4f, y + size - 3f, 2f, 2f);

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void render(SpriteBatch batch, Player player, boolean isPaused, boolean showDeathScreen, boolean showRespawnScreen, float saveFeedbackTimer, float animTime, boolean showInventory) {
        render(batch, player, isPaused, showDeathScreen, showRespawnScreen, saveFeedbackTimer, animTime, showInventory, null);
    }

    public void render(SpriteBatch batch, Player player, boolean isPaused, boolean showDeathScreen, boolean showRespawnScreen, float saveFeedbackTimer, float animTime, boolean showInventory, GameScreen gameScreen) {
        if (gameScreen != null && gameScreen.showMinimap && minimapFbo != null && player != null && gameScreen.tileM != null && gameScreen.mapGen != null) {
            renderMinimapFbo(batch, player, gameScreen);
        }

        hudCamera.update();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();

        float screenWidth = hudCamera.viewportWidth;
        float screenHeight = hudCamera.viewportHeight;

        float hcScale;
        if (screenHeight >= 1300f) {
            hcScale = 4.0f;
        } else if (screenHeight >= 900f) {
            hcScale = 3.0f;
        } else if (screenHeight >= 500f) {
            hcScale = 2.0f;
        } else {
            hcScale = 1.5f;
        }

        float hbScale = hcScale * 0.82f;
        float gap = Math.round(5f * hcScale);
        float startX = Math.round(10f * hcScale);
        float startY = Math.round(9f * hcScale);

        int hcFrame = (int) (animTime * 8f) % 11;
        float hcW = Math.round(64f * hcScale);
        float hcH = Math.round(16f * hcScale);
        float hcX = startX;
        float hcY = startY;

        int[] slotXCoords = { 10, 25, 40 };
        float heartW = (heartRegion != null)
                ? Math.round(heartRegion.getRegionWidth() * hcScale)
                : Math.round(13f * hcScale);
        float heartH = (heartRegion != null)
                ? Math.round(heartRegion.getRegionHeight() * hcScale)
                : Math.round(12f * hcScale);

        for (int i = 0; i < player.maxLives; i++) {
            float slotX = hcX + Math.round(slotXCoords[i] * hcScale);
            float slotY = hcY + Math.round(2f * hcScale);
            if (i < player.lives) {
                batch.setColor(1f, 1f, 1f, 1f);
            } else {
                batch.setColor(0.18f, 0.08f, 0.08f, 0.75f);
            }
            if (heartRegion != null) {
                batch.draw(heartRegion, slotX, slotY, heartW, heartH);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);

        if (heartContainerFrames[hcFrame] != null) {
            batch.draw(heartContainerFrames[hcFrame], hcX, hcY, hcW, hcH);
        }

        int hbFrame = (int) (animTime * 8f) % 9;
        float hbW = Math.round(70f * hbScale);
        float hbH = Math.round(16f * hbScale);
        float hcCenter = hcX + Math.round(31f * hcScale);
        float hbX = hcCenter - Math.round(34f * hbScale);
        float hbY = hcY + hcH + gap;

        float innerX = hbX + Math.round(3f * hbScale);
        float innerY = hbY + Math.round(2f * hbScale);
        float maxInnerW = Math.round(63f * hbScale);
        float innerH = Math.round(6f * hbScale);
        float curInnerW = Math.round(maxInnerW * player.getHealthRatio());

        if (whitePixel != null) {
            batch.setColor(0.14f, 0.05f, 0.05f, 0.86f);
            batch.draw(whitePixel, innerX, innerY, maxInnerW, innerH);

            if (curInnerW > 0) {
                float topH = Math.max(1f, Math.round(1f * hbScale));
                float botH = Math.max(1f, Math.round(1f * hbScale));
                float midH = Math.max(1f, innerH - topH - botH);

                batch.setColor(0.92f, 0.20f, 0.20f, 1f);
                batch.draw(whitePixel, innerX, innerY, curInnerW, topH);
                batch.setColor(0.80f, 0.12f, 0.12f, 1f);
                batch.draw(whitePixel, innerX, innerY + topH, curInnerW, midH);
                batch.setColor(0.57f, 0.06f, 0.06f, 1f);
                batch.draw(whitePixel, innerX, innerY + topH + midH, curInnerW, botH);
            }
            batch.setColor(1f, 1f, 1f, 1f);
        }

        if (healthBarFrames[hbFrame] != null) {
            batch.draw(healthBarFrames[hbFrame], hbX, hbY, hbW, hbH);
        }

        float ironHeartW = Math.round(11f * hbScale);
        float ironHeartH = Math.round(9f * hbScale);
        float ironHeartY = hbY + hbH + gap;
        float ironSpacing = (maxInnerW - (5 * ironHeartW)) / 4f;

        int filledIronHearts = (player != null) ? player.getIronHearts() : 0;
        for (int i = 0; i < 5; i++) {
            float slotX = innerX + i * (ironHeartW + ironSpacing);
            if (i < filledIronHearts) {
                batch.setColor(1f, 1f, 1f, 1f);
            } else {
                batch.setColor(0.2f, 0.2f, 0.25f, 0.55f);
            }
            if (ironHeartRegion != null) {
                batch.draw(ironHeartRegion, slotX, ironHeartY, ironHeartW, ironHeartH);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);

        if (gameScreen != null && gameScreen.showMinimap && minimapFrameRegion != null) {
            float minimapScale = hcScale * 1.4f;
            float minimapW = Math.round(64f * minimapScale);
            float minimapH = Math.round(64f * minimapScale);
            float minimapX = startX;
            float minimapY = ironHeartY + ironHeartH + gap;

            float innerMapX = minimapX + Math.round(5f * minimapScale);
            float innerMapY = minimapY + Math.round(5f * minimapScale);
            float innerMapW = Math.round(54f * minimapScale);
            float innerMapH = Math.round(54f * minimapScale);

            if (minimapFboRegion != null) {
                batch.draw(minimapFboRegion, innerMapX, innerMapY, innerMapW, innerMapH);
            }

            renderMinimapIcons(batch, player, gameScreen, innerMapX, innerMapY, innerMapW, innerMapH, minimapScale);

            batch.draw(minimapFrameRegion, minimapX, minimapY, minimapW, minimapH);
        }

        if (isPaused) {
            renderPauseMenu(batch, screenWidth, screenHeight, saveFeedbackTimer, gameScreen);
        }

        if (showDeathScreen) {
            renderDeathScreen(batch, screenWidth, screenHeight);
        }

        if (showRespawnScreen) {
            renderRespawnScreen(batch, screenWidth, screenHeight, player.lives);
        }

        if(showInventory){
            renderInventory(batch, player, screenWidth, screenHeight);
        }

        if (gameScreen != null && gameScreen.netManager != null) {
            renderChat(batch, gameScreen, screenWidth, screenHeight);
        }

        if (gameScreen != null && gameScreen.showSailHint && !showInventory && !isPaused && fontSmall != null) {
            String hint = "Apreta B para zarpar";
            glyphLayout.setText(fontSmall, hint);
            float hintW = glyphLayout.width + 32f;
            float hintH = glyphLayout.height + 20f;
            float hintX = (screenWidth - hintW) / 2f;
            float totalNotifH = notifications.isEmpty() ? 0f : (notifications.size() * 38f);
            float hintY = screenHeight - hintH - 25f - totalNotifH;
            drawSubWindow(batch, hintX, hintY, hintW, hintH);
            fontSmall.setColor(Color.CYAN);
            fontSmall.draw(batch, hint, hintX + 16f, hintY + 14f);
            fontSmall.setColor(Color.WHITE);
        }

        if (!notifications.isEmpty() && fontSmall != null) {
            float currentBottomY = screenHeight - 25f;
            for (int i = 0; i < notifications.size(); i++) {
                NotificationItem n = notifications.get(i);
                glyphLayout.setText(fontSmall, n.text);
                float boxW = glyphLayout.width + 32f;
                float boxH = glyphLayout.height + 20f;
                float boxX = (screenWidth - boxW) / 2f;
                float boxY = currentBottomY - boxH;
                drawSubWindow(batch, boxX, boxY, boxW, boxH);
                fontSmall.setColor(Color.YELLOW);
                fontSmall.draw(batch, n.text, boxX + 16f, boxY + 14f);
                fontSmall.setColor(Color.WHITE);
                currentBottomY = boxY - 8f;
            }
        }

        batch.end();

        if (pendingAction != null) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }
    }

    private void renderChat(SpriteBatch batch, GameScreen gs, float screenW, float screenH) {
        if (fontSmall == null || whitePixel == null) return;

        final float chatX    = 10f;
        final float chatW    = 290f;
        final float lineH    = 18f;
        final float fontH    = 11f;
        final float chatPad  = 5f;
        final float marginBottom = 72f;

        int count = Math.min(gs.chatMessages.size(), GameScreen.CHAT_MAX_VISIBLE);
        boolean inputOpen = gs.isChatOpen;

        if (count == 0 && !inputOpen) return;

        float anchorY = screenH - marginBottom - fontH;

        if (count > 0) {
            float bgTop    = anchorY - (count - 1) * lineH - chatPad;
            float bgBottom = anchorY + fontH + chatPad;
            batch.setColor(0f, 0f, 0f, 0.48f);
            batch.draw(whitePixel, chatX - chatPad, bgTop, chatW, bgBottom - bgTop);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        java.util.List<GameScreen.ChatMessage> msgs = new java.util.ArrayList<>(gs.chatMessages);
        for (int i = 0; i < count; i++) {
            GameScreen.ChatMessage cm = msgs.get(i);
            float alpha = Math.min(1f, cm.timer / 1.5f);
            float msgY = anchorY - i * lineH;
            fontSmall.setColor(cm.color.r, cm.color.g, cm.color.b, alpha);
            fontSmall.draw(batch, cm.text, chatX, msgY);
        }

        if (inputOpen) {
            handleChatKeyInput(gs);

            float inputTop    = anchorY + fontH + chatPad + 2f;
            float inputBottom = inputTop + fontH + chatPad * 2f;
            batch.setColor(0f, 0f, 0f, 0.72f);
            batch.draw(whitePixel, chatX - chatPad, inputTop, chatW, inputBottom - inputTop);

            batch.setColor(0.7f, 0.7f, 0.7f, 0.9f);
            batch.draw(whitePixel, chatX - chatPad, inputTop, chatW, 1f);
            batch.setColor(1f, 1f, 1f, 1f);

            String cursor = ((System.currentTimeMillis() / 450) % 2 == 0) ? "_" : "";
            fontSmall.setColor(Color.WHITE);
            fontSmall.draw(batch, "> " + gs.chatInput + cursor, chatX, inputTop + chatPad);
        }

        fontSmall.setColor(Color.WHITE);
    }

    private void handleChatKeyInput(GameScreen gs) {
        if (Gdx.input.isKeyJustPressed(Keys.BACKSPACE) && gs.chatInput.length() > 0) {
            gs.chatInput = gs.chatInput.substring(0, gs.chatInput.length() - 1);
        }

        for (int k = Keys.A; k <= Keys.Z; k++) {
            if (Gdx.input.isKeyJustPressed(k)) {
                boolean shift = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT);
                char c = (char) ('a' + (k - Keys.A));
                if (shift) c = Character.toUpperCase(c);
                if (gs.chatInput.length() < 60) gs.chatInput += c;
            }
        }
        for (int k = Keys.NUM_0; k <= Keys.NUM_9; k++) {
            if (Gdx.input.isKeyJustPressed(k) && gs.chatInput.length() < 60) {
                gs.chatInput += (char) ('0' + (k - Keys.NUM_0));
            }
        }
        if (Gdx.input.isKeyJustPressed(Keys.SPACE) && gs.chatInput.length() < 60) {
            gs.chatInput += ' ';
        }
        if (Gdx.input.isKeyJustPressed(Keys.COMMA) && gs.chatInput.length() < 60) gs.chatInput += ',';
        if (Gdx.input.isKeyJustPressed(Keys.PERIOD) && gs.chatInput.length() < 60) gs.chatInput += '.';
        if (Gdx.input.isKeyJustPressed(Keys.APOSTROPHE) && gs.chatInput.length() < 60) gs.chatInput += '!';
        if (Gdx.input.isKeyJustPressed(Keys.SLASH) && gs.chatInput.length() < 60) gs.chatInput += '?';
    }

    private Runnable pendingAction = null;

    private float roomCodeCopiedTimer = 0f;

    private void renderPauseMenu(SpriteBatch batch, float screenW, float screenH, float saveFeedbackTimer, GameScreen gameScreen) {
        if (roomCodeCopiedTimer > 0f) roomCodeCopiedTimer -= Gdx.graphics.getDeltaTime();

        if (whitePixel != null) {
            batch.setColor(0f, 0f, 0f, 0.65f);
            batch.draw(whitePixel, 0, 0, screenW, screenH);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        if (fontMedium != null) {
            fontMedium.setColor(Color.GOLD);
            glyphLayout.setText(fontMedium, "Juego en pausa");
            fontMedium.draw(batch, "Juego en pausa",
                    (screenW - glyphLayout.width) / 2f, 200f);
        }

        float pauseButtonScale = 1.6f;
        float buttonWidth = (backButtonRegion != null) ? (backButtonRegion.getRegionWidth() * pauseButtonScale) : 224f;
        float buttonHeight = (backButtonRegion != null) ? (backButtonRegion.getRegionHeight() * pauseButtonScale) : 58f;
        float buttonX = (screenW - buttonWidth) / 2f;
        float menuButtonY = 250f;
        float saveButtonY = menuButtonY + buttonHeight + 16f;

        boolean showCodeBtn = (gameScreen != null && gameScreen.netManager != null
                && gameScreen.netManager.isHost && gameScreen.roomCode != null
                && !gameScreen.roomCode.isEmpty());
        float codeButtonY = saveButtonY + buttonHeight + 16f;

        float mouseX = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
        float mouseY = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
        boolean hoverBack = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= menuButtonY && mouseY <= menuButtonY + buttonHeight;
        boolean hoverSave = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= saveButtonY && mouseY <= saveButtonY + buttonHeight;
        boolean hoverCode = showCodeBtn && mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= codeButtonY && mouseY <= codeButtonY + buttonHeight;

        if (backButtonRegion != null) {
            batch.setColor(hoverBack ? 0.85f : 1f, hoverBack ? 0.85f : 1f, hoverBack ? 0.85f : 1f, 1f);
            batch.draw(backButtonRegion, buttonX, menuButtonY, buttonWidth, buttonHeight);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontSmall != null) {
            fontSmall.setColor(hoverBack ? Color.GOLD : Color.WHITE);
            String menuText = "Volver al men\u00fa";
            glyphLayout.setText(fontSmall, menuText);
            fontSmall.draw(batch, menuText,
                    buttonX + (buttonWidth - glyphLayout.width) / 2f,
                    menuButtonY + (buttonHeight - glyphLayout.height) / 2f);
        }

        if (backButtonRegion != null) {
            batch.setColor(hoverSave ? 0.85f : 1f, hoverSave ? 0.85f : 1f, hoverSave ? 0.85f : 1f, 1f);
            batch.draw(backButtonRegion, buttonX, saveButtonY, buttonWidth, buttonHeight);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontSmall != null) {
            fontSmall.setColor(hoverSave ? Color.GOLD : Color.WHITE);
            String saveLabel = (saveFeedbackTimer > 0f) ? "\u00a1Guardado!" : "Guardar progreso";
            glyphLayout.setText(fontSmall, saveLabel);
            fontSmall.draw(batch, saveLabel,
                    buttonX + (buttonWidth - glyphLayout.width) / 2f,
                    saveButtonY + (buttonHeight - glyphLayout.height) / 2f);
        }

        if (showCodeBtn) {
            if (backButtonRegion != null) {
                batch.setColor(hoverCode ? 0.85f : 1f, hoverCode ? 0.85f : 1f, hoverCode ? 0.85f : 1f, 1f);
                batch.draw(backButtonRegion, buttonX, codeButtonY, buttonWidth, buttonHeight);
                batch.setColor(1f, 1f, 1f, 1f);
            }
            if (fontSmall != null) {
                String codeLabel = (roomCodeCopiedTimer > 0f) ? "\u00a1Copiado!" : "Copiar codigo de sala";
                fontSmall.setColor(roomCodeCopiedTimer > 0f ? Color.YELLOW : (hoverCode ? Color.GOLD : Color.WHITE));
                glyphLayout.setText(fontSmall, codeLabel);
                fontSmall.draw(batch, codeLabel,
                        buttonX + (buttonWidth - glyphLayout.width) / 2f,
                        codeButtonY + (buttonHeight - glyphLayout.height) / 2f);
            }
        }

        if (Gdx.input.isButtonJustPressed(0)) {
            if (hoverBack) {
                audio.playButtonClick();
                pendingAction = () -> callback.onReturnToTitle();
            } else if (hoverSave) {
                audio.playButtonClick();
                callback.onSaveProgress();
            } else if (hoverCode && gameScreen != null) {
                audio.playButtonClick();
                try { Gdx.app.getClipboard().setContents(gameScreen.roomCode); } catch (Throwable ignored) {}
                roomCodeCopiedTimer = 2f;
            }
        }
    }

    private void renderDeathScreen(SpriteBatch batch, float screenW, float screenH) {
        if (whitePixel != null) {
            batch.setColor(0f, 0f, 0f, 0.78f);
            batch.draw(whitePixel, 0, 0, screenW, screenH);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        if (fontLarge != null) {
            fontLarge.setColor(new Color(0.85f, 0.1f, 0.1f, 1f));
            glyphLayout.setText(fontLarge, "MORISTE");
            fontLarge.draw(batch, "MORISTE",
                    (screenW - glyphLayout.width) / 2f, 220f);
        }

        float buttonWidth = (buttonSlotRegion != null) ? buttonSlotRegion.getRegionWidth() : 576f;
        float buttonHeight = (buttonSlotRegion != null) ? buttonSlotRegion.getRegionHeight() : 48f;
        float buttonX = (screenW - buttonWidth) / 2f;
        float buttonY = 290f;
        float mouseX = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
        float mouseY = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
        boolean hover = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;

        if (buttonSlotRegion != null) {
            batch.setColor(hover ? 0.85f : 1f, hover ? 0.85f : 1f, hover ? 0.85f : 1f, 1f);
            batch.draw(buttonSlotRegion, buttonX, buttonY, buttonWidth, buttonHeight);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontMedium != null) {
            fontMedium.setColor(hover ? Color.GOLD : Color.WHITE);
            glyphLayout.setText(fontMedium, "Volver al men\u00fa");
            fontMedium.draw(batch, "Volver al men\u00fa",
                    buttonX + (buttonWidth - glyphLayout.width) / 2f,
                    buttonY + (buttonHeight - glyphLayout.height) / 2f);
        }

        if (Gdx.input.isButtonJustPressed(0) && hover) {
            audio.playButtonClick();
            pendingAction = () -> callback.onReturnToTitle();
        }
    }

    private void renderRespawnScreen(SpriteBatch batch, float screenW, float screenH, int lives) {
        if (whitePixel != null) {
            batch.setColor(0f, 0f, 0f, 0.72f);
            batch.draw(whitePixel, 0, 0, screenW, screenH);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        String title = "Moriste! te quedan " + lives + (lives == 1 ? " vida" : " vidas");
        if (fontMedium != null) {
            fontMedium.setColor(new Color(0.95f, 0.25f, 0.2f, 1f));
            glyphLayout.setText(fontMedium, title);
            fontMedium.draw(batch, title,
                    (screenW - glyphLayout.width) / 2f, 200f);
        }

        float respawnButtonScale = 1.6f;
        float buttonWidth = (backButtonRegion != null) ? (backButtonRegion.getRegionWidth() * respawnButtonScale) : 224f;
        float buttonHeight = (backButtonRegion != null) ? (backButtonRegion.getRegionHeight() * respawnButtonScale) : 58f;
        float buttonX = (screenW - buttonWidth) / 2f;
        float respawnButtonY = 242f;
        float menuButtonY = respawnButtonY + buttonHeight + 14f;

        float mouseX = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
        float mouseY = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
        boolean hoverRespawn = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= respawnButtonY && mouseY <= respawnButtonY + buttonHeight;
        boolean hoverMenu    = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= menuButtonY && mouseY <= menuButtonY + buttonHeight;

        if (backButtonRegion != null) {
            batch.setColor(hoverRespawn ? 0.85f : 1f, hoverRespawn ? 0.85f : 1f, hoverRespawn ? 0.85f : 1f, 1f);
            batch.draw(backButtonRegion, buttonX, respawnButtonY, buttonWidth, buttonHeight);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontSmall != null) {
            fontSmall.setColor(hoverRespawn ? Color.GOLD : Color.WHITE);
            glyphLayout.setText(fontSmall, "Reaparecer");
            fontSmall.draw(batch, "Reaparecer",
                    buttonX + (buttonWidth - glyphLayout.width) / 2f,
                    respawnButtonY + (buttonHeight - glyphLayout.height) / 2f);
        }

        if (backButtonRegion != null) {
            batch.setColor(hoverMenu ? 0.85f : 1f, hoverMenu ? 0.85f : 1f, hoverMenu ? 0.85f : 1f, 1f);
            batch.draw(backButtonRegion, buttonX, menuButtonY, buttonWidth, buttonHeight);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        if (fontSmall != null) {
            fontSmall.setColor(hoverMenu ? Color.GOLD : Color.WHITE);
            String menuText = "Volver al men\u00fa";
            glyphLayout.setText(fontSmall, menuText);
            fontSmall.draw(batch, menuText,
                    buttonX + (buttonWidth - glyphLayout.width) / 2f,
                    menuButtonY + (buttonHeight - glyphLayout.height) / 2f);
        }

        if (Gdx.input.isButtonJustPressed(0)) {
            if (hoverRespawn) {
                audio.playButtonClick();
                callback.onRespawn();
            } else if (hoverMenu) {
                audio.playButtonClick();
                pendingAction = () -> callback.onReturnToTitle();
            }
        } else if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            audio.playButtonClick();
            callback.onRespawn();
        }
    }

    private void renderMinimapFbo(SpriteBatch batch, Player player, GameScreen gameScreen) {
        if (minimapFbo == null || gameScreen == null || gameScreen.tileM == null || gameScreen.mapGen == null || player == null) return;

        int centerTileCol = (int) ((player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int centerTileRow = (int) ((player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int currentWaterFrame = gameScreen.tileM.getWaterFrame();

        if (centerTileCol == lastMinimapTileCol && centerTileRow == lastMinimapTileRow && currentWaterFrame == lastMinimapWaterFrame) {
            return;
        }

        lastMinimapTileCol = centerTileCol;
        lastMinimapTileRow = centerTileRow;
        lastMinimapWaterFrame = currentWaterFrame;

        float minimapWorldWidth = MINIMAP_VIEW_TILES * TileManager.TILE_SIZE;
        float minimapWorldHeight = MINIMAP_VIEW_TILES * TileManager.TILE_SIZE;

        float camX = centerTileCol * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f;
        float camY = centerTileRow * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f;

        minimapCam.setToOrtho(true, minimapWorldWidth, minimapWorldHeight);
        minimapCam.position.set(camX, camY, 0);
        minimapCam.update();

        minimapFbo.begin();
        Gdx.gl.glClearColor(0.06f, 0.11f, 0.25f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(minimapCam.combined);
        batch.begin();

        float minX = camX - minimapWorldWidth / 2f;
        float maxX = camX + minimapWorldWidth / 2f;
        float minY = camY - minimapWorldHeight / 2f;
        float maxY = camY + minimapWorldHeight / 2f;

        gameScreen.tileM.drawGround(batch, gameScreen.mapGen, minX, minY, maxX, maxY);
        gameScreen.tileM.drawAllDecorations(batch, gameScreen.mapGen, minX, minY, maxX, maxY);

        batch.end();
        minimapFbo.end();
    }

    private TextureRegion getMonsterIcon(Entity m) {
        if (m instanceof MON_mushroom) return mushroomIconRegion;
        if (m instanceof MON_frogger) return froggerIconRegion;
        if (m instanceof MON_snowGhost) return frostIconRegion;
        if (m instanceof MON_Ghost) return ghostIconRegion;
        if (m instanceof MON_zombie) return zombIconRegion;
        if (m instanceof MON_bat) return batIconRegion;
        return null;
    }

    private void renderMinimapIcons(SpriteBatch batch, Player player, GameScreen gameScreen,
                                    float innerMapX, float innerMapY, float innerMapW, float innerMapH,
                                    float minimapScale) {
        float minimapWorldWidth = MINIMAP_VIEW_TILES * TileManager.TILE_SIZE;
        float minimapWorldHeight = MINIMAP_VIEW_TILES * TileManager.TILE_SIZE;

        float mapCenterX = innerMapX + innerMapW / 2f;
        float mapCenterY = innerMapY + innerMapH / 2f;

        int centerTileCol = (int) ((player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int centerTileRow = (int) ((player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        float camX = centerTileCol * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f;
        float camY = centerTileRow * TileManager.TILE_SIZE + TileManager.TILE_SIZE / 2f;

        if (gameScreen.monster != null) {
            for (int i = 0; i < gameScreen.monster.length; i++) {
                Entity m = gameScreen.monster[i];
                if (m != null && m.life > 0) {
                    TextureRegion icon = getMonsterIcon(m);
                    if (icon == null) continue;

                    boolean isBoss = (m instanceof MON_mushroom || m instanceof MON_frogger || m instanceof MON_snowGhost);
                    float targetSize = isBoss ? Math.round(7.0f * minimapScale) : Math.round(5.5f * minimapScale);
                    float iconAspect = (float) icon.getRegionWidth() / (float) icon.getRegionHeight();
                    float drawW = (iconAspect >= 1f) ? targetSize : targetSize * iconAspect;
                    float drawH = (iconAspect >= 1f) ? targetSize / iconAspect : targetSize;

                    float mWorldCenterX = m.worldX + m.solidArea.x + m.solidArea.width / 2f;
                    float mWorldCenterY = m.worldY + m.solidArea.y + m.solidArea.height / 2f;

                    float diffX = mWorldCenterX - camX;
                    float diffY = mWorldCenterY - camY;

                    float iconCenterX = mapCenterX + (diffX / minimapWorldWidth) * innerMapW;
                    float iconCenterY = mapCenterY + (diffY / minimapWorldHeight) * innerMapH;

                    if (iconCenterX >= innerMapX && iconCenterX <= innerMapX + innerMapW
                            && iconCenterY >= innerMapY && iconCenterY <= innerMapY + innerMapH) {
                        batch.draw(icon, iconCenterX - drawW / 2f, iconCenterY - drawH / 2f, drawW, drawH);
                    }
                }
            }
        }

        if (playerIconRegion != null) {
            float playerTargetSize = Math.round(5.5f * minimapScale);
            float playerAspect = (float) playerIconRegion.getRegionWidth() / (float) playerIconRegion.getRegionHeight();
            float pDrawW = (playerAspect >= 1f) ? playerTargetSize : playerTargetSize * playerAspect;
            float pDrawH = (playerAspect >= 1f) ? playerTargetSize / playerAspect : playerTargetSize;

            float pWorldCenterX = player.worldX + TileManager.TILE_SIZE / 2f;
            float pWorldCenterY = player.worldY + TileManager.TILE_SIZE / 2f;
            float diffX = pWorldCenterX - camX;
            float diffY = pWorldCenterY - camY;

            float playerIconX = mapCenterX + (diffX / minimapWorldWidth) * innerMapW;
            float playerIconY = mapCenterY + (diffY / minimapWorldHeight) * innerMapH;

            batch.draw(playerIconRegion, playerIconX - pDrawW / 2f, playerIconY - pDrawH / 2f, pDrawW, pDrawH);
        }
    }

    public void dispose() {
        if (fontSmall != null) fontSmall.dispose();
        if (fontMedium != null) fontMedium.dispose();
        if (fontLarge != null) fontLarge.dispose();

        if (minimapFbo != null) {
            minimapFbo.dispose();
            minimapFbo = null;
        }

        for (Texture tex : hudTextures) {
            tex.dispose();
        }
        hudTextures.clear();
    }
}
