package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.whereyouat.WhereYouAtGame;
import com.whereyouat.save.WorldSaveManager;
import com.whereyouat.save.WorldSaveManager.WorldInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * WorldSelectScreen for LibGDX.
 * Displays existing worlds in clickable wood-border slots,
 * with delete button icons and "+ CREAR MUNDO" button.
 */
public class WorldSelectScreen extends ScreenAdapter {

    private final WhereYouAtGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final Vector3 mousePos = new Vector3();

    private Texture slotTexture;
    private TextureRegion slotRegion;
    private Texture backBtnTexture;
    private TextureRegion backBtnRegion;
    private Texture deleteBtnTexture;
    private TextureRegion deleteBtnRegion;

    private BitmapFont fontTitle;
    private BitmapFont fontSlot;
    private BitmapFont fontSmall;
    private final GlyphLayout layout = new GlyphLayout();

    private Sound buttonSound;

    private List<WorldInfo> worldList = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int VISIBLE_SLOTS = 5;

    // Slot layout
    private static final float SLOT_W = 540f;
    private static final float SLOT_H = 64f;
    private static final float SLOT_GAP = 12f;
    private static final float START_Y = 100f;

    public WorldSelectScreen(WhereYouAtGame game) {
        this.game = game;

        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new FitViewport(768, 576, camera);

        loadAssets();
        refreshWorlds();
    }

    private void loadAssets() {
        if (Gdx.files.internal("button_slot.png").exists()) {
            slotTexture = new Texture(Gdx.files.internal("button_slot.png"));
            slotTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            slotRegion = new TextureRegion(slotTexture);
            slotRegion.flip(false, true);
        }
        if (Gdx.files.internal("back_button.png").exists()) {
            backBtnTexture = new Texture(Gdx.files.internal("back_button.png"));
            backBtnTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            backBtnRegion = new TextureRegion(backBtnTexture);
            backBtnRegion.flip(false, true);
        }
        if (Gdx.files.internal("delete_button.png").exists()) {
            deleteBtnTexture = new Texture(Gdx.files.internal("delete_button.png"));
            deleteBtnTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            deleteBtnRegion = new TextureRegion(deleteBtnTexture);
            deleteBtnRegion.flip(false, true);
        }
        if (Gdx.files.internal("sounds/button_sound.mp3").exists()) {
            buttonSound = Gdx.audio.newSound(Gdx.files.internal("sounds/button_sound.mp3"));
        }

        // Fonts
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter param = new FreeTypeFontParameter();
            param.flip = true;

            param.size = 18;
            param.shadowOffsetX = 2;
            param.shadowOffsetY = 2;
            param.shadowColor = new Color(0f, 0f, 0f, 0.8f);
            fontTitle = gen.generateFont(param);
            fontTitle.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            param.size = 12;
            param.shadowOffsetX = 1;
            param.shadowOffsetY = 1;
            fontSlot = gen.generateFont(param);
            fontSlot.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            param.size = 10;
            param.shadowOffsetX = 1;
            param.shadowOffsetY = 1;
            fontSmall = gen.generateFont(param);
            fontSmall.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            gen.dispose();
        } else {
            fontTitle = new BitmapFont();
            fontSlot = new BitmapFont();
            fontSmall = new BitmapFont();
        }
    }

    private void refreshWorlds() {
        worldList = WorldSaveManager.listWorlds();
        scrollOffset = 0;
    }

    @Override
    public void render(float delta) {
        mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(mousePos);

        // Escape key returns to title
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            if (buttonSound != null) buttonSound.play(0.32f);
            game.setScreen(new TitleScreen(game));
            return;
        }

        float panelX = (768f - SLOT_W) / 2f;
        float backX = panelX;
        float backY = 500f;
        float backW = 140f;
        float backH = 40f;
        boolean hoverBack = (mousePos.x >= backX && mousePos.x <= backX + backW &&
                             mousePos.y >= backY && mousePos.y <= backY + backH);

        boolean clicked = Gdx.input.isButtonJustPressed(0);

        if (clicked && hoverBack) {
            if (buttonSound != null) buttonSound.play(0.32f);
            game.setScreen(new TitleScreen(game));
            return;
        }

        // Total items = 1 ("+ CREAR MUNDO") + worldList.size()
        int totalItems = 1 + worldList.size();

        // Handle item clicks
        for (int i = 0; i < VISIBLE_SLOTS && (i + scrollOffset) < totalItems; i++) {
            int itemIndex = i + scrollOffset;
            float slotY = START_Y + i * (SLOT_H + SLOT_GAP);

            boolean hoverSlot = (mousePos.x >= panelX && mousePos.x <= panelX + SLOT_W &&
                                 mousePos.y >= slotY && mousePos.y <= slotY + SLOT_H);

            if (itemIndex == 0) {
                // "+ CREAR MUNDO" button
                if (clicked && hoverSlot) {
                    if (buttonSound != null) buttonSound.play(0.32f);
                    game.setScreen(new WorldCreateScreen(game));
                    return;
                }
            } else {
                int worldIdx = itemIndex - 1;
                WorldInfo info = worldList.get(worldIdx);

                // Check delete button on the right edge of slot
                float delX = panelX + SLOT_W - 40f;
                float delY = slotY + (SLOT_H - 24f) / 2f;
                boolean hoverDelete = (mousePos.x >= delX && mousePos.x <= delX + 24f &&
                                       mousePos.y >= delY && mousePos.y <= delY + 24f);

                if (clicked) {
                    if (hoverDelete) {
                        if (buttonSound != null) buttonSound.play(0.32f);
                        WorldSaveManager.deleteWorld(info.fileName);
                        refreshWorlds();
                        return;
                    } else if (hoverSlot) {
                        if (buttonSound != null) buttonSound.play(0.32f);
                        // Launch world
                        GameScreen gs = new GameScreen(game, info.seed, info.name, info.fileName);
                        if (info.hasPlayerData) {
                            gs.player.worldX = info.playerWorldX;
                            gs.player.worldY = info.playerWorldY;
                            gs.player.direction = info.playerDirection;
                            gs.player.health = info.playerHealth;
                            gs.player.lives = info.playerLives;
                        }
                        if (info.hasDayNightData && info.dayNightTicks >= 0) {
                            gs.dayNight.setTimeTicks(info.dayNightTicks);
                        }
                        game.setScreen(gs);
                        return;
                    }
                }
            }
        }

        // Draw screen
        Gdx.gl.glClearColor(0.06f, 0.08f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // 1. Title
        layout.setText(fontTitle, "SELECCIONAR MUNDO");
        fontTitle.setColor(Color.GOLD);
        fontTitle.draw(game.batch, "SELECCIONAR MUNDO", (768f - layout.width) / 2f, 60f);

        // 2. Slots
        for (int i = 0; i < VISIBLE_SLOTS && (i + scrollOffset) < totalItems; i++) {
            int itemIndex = i + scrollOffset;
            float slotY = START_Y + i * (SLOT_H + SLOT_GAP);

            boolean hoverSlot = (mousePos.x >= panelX && mousePos.x <= panelX + SLOT_W &&
                                 mousePos.y >= slotY && mousePos.y <= slotY + SLOT_H);

            if (slotRegion != null) {
                game.batch.setColor(hoverSlot ? 1.15f : 1f, hoverSlot ? 1.15f : 1f, hoverSlot ? 1.15f : 1f, 1f);
                game.batch.draw(slotRegion, panelX, slotY, SLOT_W, SLOT_H);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }

            if (itemIndex == 0) {
                // "+ CREAR NUEVO MUNDO"
                layout.setText(fontSlot, "+ CREAR NUEVO MUNDO");
                fontSlot.setColor(hoverSlot ? Color.GOLD : Color.WHITE);
                fontSlot.draw(game.batch, "+ CREAR NUEVO MUNDO",
                              panelX + (SLOT_W - layout.width) / 2f,
                              slotY + (SLOT_H - layout.height) / 2f);
            } else {
                int worldIdx = itemIndex - 1;
                WorldInfo info = worldList.get(worldIdx);

                // World Name
                fontSlot.setColor(hoverSlot ? Color.GOLD : Color.WHITE);
                fontSlot.draw(game.batch, info.name, panelX + 24f, slotY + 14f);

                // Date & Seed
                String sub = "Semilla: " + info.seed + " · " + WorldSaveManager.formatDate(info.lastPlayed > 0 ? info.lastPlayed : info.created);
                fontSmall.setColor(new Color(0.7f, 0.7f, 0.7f, 0.9f));
                fontSmall.draw(game.batch, sub, panelX + 24f, slotY + 38f);

                // Delete Button icon
                if (deleteBtnRegion != null) {
                    float delX = panelX + SLOT_W - 40f;
                    float delY = slotY + (SLOT_H - 24f) / 2f;
                    boolean hoverDelete = (mousePos.x >= delX && mousePos.x <= delX + 24f &&
                                           mousePos.y >= delY && mousePos.y <= delY + 24f);
                    // Only scale up on hover — no red tint (matches original)
                    float dScale = hoverDelete ? 1.2f : 1.0f;
                    float dW = 24f * dScale;
                    float dH = 24f * dScale;
                    float dDrawX = delX - (dW - 24f) / 2f;
                    float dDrawY = delY - (dH - 24f) / 2f;
                    game.batch.setColor(1f, 1f, 1f, 1f);
                    game.batch.draw(deleteBtnRegion, dDrawX, dDrawY, dW, dH);
                }
            }
        }

        // 3. Back Button
        if (backBtnRegion != null) {
            game.batch.setColor(hoverBack ? 1.15f : 1f, hoverBack ? 1.15f : 1f, hoverBack ? 1.15f : 1f, 1f);
            game.batch.draw(backBtnRegion, backX, backY, backW, backH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontSlot, "VOLVER");
        fontSlot.setColor(hoverBack ? Color.GOLD : Color.WHITE);
        fontSlot.draw(game.batch, "VOLVER", backX + (backW - layout.width) / 2f, backY + (backH - layout.height) / 2f);

        game.batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (slotTexture != null) slotTexture.dispose();
        if (backBtnTexture != null) backBtnTexture.dispose();
        if (deleteBtnTexture != null) deleteBtnTexture.dispose();
        if (buttonSound != null) buttonSound.dispose();
        if (fontTitle != null) fontTitle.dispose();
        if (fontSlot != null) fontSlot.dispose();
        if (fontSmall != null) fontSmall.dispose();
    }
}