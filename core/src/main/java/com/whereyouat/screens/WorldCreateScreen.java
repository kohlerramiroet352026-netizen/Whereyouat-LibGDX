package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
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

import java.util.Random;

/**
 * WorldCreateScreen for LibGDX.
 * Interactive text input for world name and seed,
 * with random seed generation, input validation, and direct launch into GameScreen.
 */
public class WorldCreateScreen extends ScreenAdapter {

    private final WhereYouAtGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final Vector3 mousePos = new Vector3();

    private String nameInput = "Nuevo Mundo";
    private String seedInput = "";
    private int activeField = 0; // 0=name, 1=seed
    private final Random rand = new Random();

    private Texture buttonTexture;
    private TextureRegion buttonRegion;
    private Texture backBtnTexture;
    private TextureRegion backBtnRegion;
    private Texture slotTexture;
    private TextureRegion slotRegion;
    private Texture whiteTex;
    private TextureRegion whitePixel;
    private Sound buttonSound;

    private BitmapFont fontTitle;
    private BitmapFont fontLabel;
    private BitmapFont fontInput;
    private BitmapFont fontBtn;
    private final GlyphLayout layout = new GlyphLayout();

    public WorldCreateScreen(WhereYouAtGame game) {
        this.game = game;

        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new FitViewport(768, 576, camera);

        // Generate default random seed
        seedInput = String.valueOf(Math.abs(rand.nextLong() % 1_000_000_000L));

        loadAssets();
        setupInput();
    }

    private void loadAssets() {
        String btnPath = Gdx.files.internal("buttons/button1.png").exists() ? "buttons/button1.png" : "button1.png";
        if (Gdx.files.internal(btnPath).exists()) {
            buttonTexture = new Texture(Gdx.files.internal(btnPath));
            buttonTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            buttonRegion = new TextureRegion(buttonTexture);
            buttonRegion.flip(false, true);
        }
        if (Gdx.files.internal("back_button.png").exists()) {
            backBtnTexture = new Texture(Gdx.files.internal("back_button.png"));
            backBtnTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            backBtnRegion = new TextureRegion(backBtnTexture);
            backBtnRegion.flip(false, true);
        }
        if (Gdx.files.internal("sounds/button_sound.mp3").exists()) {
            buttonSound = Gdx.audio.newSound(Gdx.files.internal("sounds/button_sound.mp3"));
        }

        // button_slot.png texture used as background for the name and seed input fields
        String slotPath = Gdx.files.internal("buttons/button_slot.png").exists() ? "buttons/button_slot.png" : "button_slot.png";
        if (Gdx.files.internal(slotPath).exists()) {
            slotTexture = new Texture(Gdx.files.internal(slotPath));
            slotTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            slotRegion = new TextureRegion(slotTexture);
            slotRegion.flip(false, true);
        }

        // White pixel for drawing plain input field backgrounds (fallback if slot texture missing)
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
        whiteTex = new Texture(pm);
        pm.dispose();
        whitePixel = new TextureRegion(whiteTex);

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
            fontLabel = gen.generateFont(param);
            fontLabel.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            param.size = 14;
            param.shadowOffsetX = 1;
            param.shadowOffsetY = 1;
            fontInput = gen.generateFont(param);
            fontInput.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            param.size = 12;
            param.shadowOffsetX = 1;
            param.shadowOffsetY = 1;
            fontBtn = gen.generateFont(param);
            fontBtn.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            gen.dispose();
        } else {
            fontTitle = new BitmapFont();
            fontLabel = new BitmapFont();
            fontInput = new BitmapFont();
            fontBtn = new BitmapFont();
        }
    }

    private void setupInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
                if (character == '\b') { // Backspace
                    if (activeField == 0 && nameInput.length() > 0) {
                        nameInput = nameInput.substring(0, nameInput.length() - 1);
                    } else if (activeField == 1 && seedInput.length() > 0) {
                        seedInput = seedInput.substring(0, seedInput.length() - 1);
                    }
                    return true;
                } else if (character >= 32 && character < 127) { // Printable characters
                    if (activeField == 0 && nameInput.length() < 24) {
                        nameInput += character;
                    } else if (activeField == 1 && seedInput.length() < 18) {
                        seedInput += character;
                    }
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(mousePos);

        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            if (buttonSound != null) buttonSound.play(0.32f);
            game.setScreen(new WorldSelectScreen(game));
            return;
        }

        float formX = (768f - 480f) / 2f;
        float nameBoxY = 140f;
        float seedBoxY = 240f;
        float boxW = 480f;
        float boxH = 44f;

        // Button dimensions
        float randBtnX = formX + boxW - 140f;
        float randBtnY = seedBoxY + 54f;
        float randBtnW = 140f;
        float randBtnH = 34f;

        float createBtnX = (768f - 240f) / 2f;
        float createBtnY = 380f;
        float createBtnW = 240f;
        float createBtnH = 48f;

        float backX = formX;
        float backY = 500f;
        float backW = 140f;
        float backH = 40f;

        boolean hoverName = (mousePos.x >= formX && mousePos.x <= formX + boxW &&
                             mousePos.y >= nameBoxY && mousePos.y <= nameBoxY + boxH);
        boolean hoverSeed = (mousePos.x >= formX && mousePos.x <= formX + boxW &&
                             mousePos.y >= seedBoxY && mousePos.y <= seedBoxY + boxH);

        boolean hoverRandom = (mousePos.x >= randBtnX && mousePos.x <= randBtnX + randBtnW &&
                               mousePos.y >= randBtnY && mousePos.y <= randBtnY + randBtnH);
        boolean hoverCreate = (mousePos.x >= createBtnX && mousePos.x <= createBtnX + createBtnW &&
                               mousePos.y >= createBtnY && mousePos.y <= createBtnY + createBtnH);
        boolean hoverBack = (mousePos.x >= backX && mousePos.x <= backX + backW &&
                             mousePos.y >= backY && mousePos.y <= backY + backH);

        if (Gdx.input.isButtonJustPressed(0)) {
            if (hoverName) {
                activeField = 0;
            } else if (hoverSeed) {
                activeField = 1;
            } else if (hoverRandom) {
                if (buttonSound != null) buttonSound.play(0.32f);
                seedInput = String.valueOf(Math.abs(rand.nextLong() % 1_000_000_000L));
            } else if (hoverCreate) {
                if (buttonSound != null) buttonSound.play(0.32f);
                createWorldAndPlay();
                return;
            } else if (hoverBack) {
                if (buttonSound != null) buttonSound.play(0.32f);
                game.setScreen(new WorldSelectScreen(game));
                return;
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
        layout.setText(fontTitle, "CREAR NUEVO MUNDO");
        fontTitle.setColor(Color.GOLD);
        fontTitle.draw(game.batch, "CREAR NUEVO MUNDO", (768f - layout.width) / 2f, 70f);

        // 2. Name field
        fontLabel.setColor(new Color(0.85f, 0.85f, 0.85f, 1f));
        fontLabel.draw(game.batch, "Nombre del Mundo:", formX, nameBoxY - 20f);

        drawInputBox(formX, nameBoxY, boxW, boxH, nameInput, activeField == 0);

        // 3. Seed field
        fontLabel.draw(game.batch, "Semilla (Texto o Número):", formX, seedBoxY - 20f);
        drawInputBox(formX, seedBoxY, boxW, boxH, seedInput, activeField == 1);

        // Random Seed button
        drawSmallButton("ALEATORIO", randBtnX, randBtnY, randBtnW, randBtnH, hoverRandom);

        // 4. Create World Button
        drawLargeButton("CREAR MUNDO", createBtnX, createBtnY, createBtnW, createBtnH, hoverCreate);

        // 5. Back Button
        if (backBtnRegion != null) {
            game.batch.setColor(hoverBack ? 1.15f : 1f, hoverBack ? 1.15f : 1f, hoverBack ? 1.15f : 1f, 1f);
            game.batch.draw(backBtnRegion, backX, backY, backW, backH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontBtn, "VOLVER");
        fontBtn.setColor(hoverBack ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, "VOLVER", backX + (backW - layout.width) / 2f, backY + (backH - layout.height) / 2f);

        game.batch.end();
    }

    private void drawInputBox(float x, float y, float w, float h, String text, boolean focused) {
        // Draw button_slot.png at its native pixel dimensions, centered over the hitbox.
        // The texture is 576x48 — no stretching to preserve pixel art quality.
        // If the texture is unavailable, fall back to a plain dark rectangle.
        if (slotRegion != null) {
            float slotW = slotRegion.getRegionWidth();
            float slotH = slotRegion.getRegionHeight();
            float drawX = x + (w - slotW) / 2f;
            float drawY = y + (h - slotH) / 2f;
            game.batch.setColor(focused ? new Color(1.1f, 1.1f, 1.1f, 1f) : new Color(1f, 1f, 1f, 1f));
            game.batch.draw(slotRegion, drawX, drawY, slotW, slotH);
            game.batch.setColor(1f, 1f, 1f, 1f);
        } else if (whitePixel != null) {
            game.batch.setColor(focused ? new Color(0.55f, 0.55f, 0.60f, 1f) : new Color(0.35f, 0.35f, 0.38f, 1f));
            game.batch.draw(whitePixel, x - 1f, y - 1f, w + 2f, h + 2f);
            game.batch.setColor(focused ? new Color(0.20f, 0.20f, 0.24f, 1f) : new Color(0.12f, 0.12f, 0.14f, 1f));
            game.batch.draw(whitePixel, x, y, w, h);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        String disp = text + (focused && (System.currentTimeMillis() / 450) % 2 == 0 ? "_" : "");
        layout.setText(fontInput, disp.isEmpty() ? " " : disp);
        fontInput.setColor(focused ? Color.WHITE : new Color(0.8f, 0.8f, 0.8f, 1f));
        fontInput.draw(game.batch, disp, x + 16f, y + (h - layout.height) / 2f);
    }

    private void drawSmallButton(String text, float x, float y, float w, float h, boolean hover) {
        if (buttonRegion != null) {
            game.batch.setColor(hover ? 1.15f : 1f, hover ? 1.15f : 1f, hover ? 1.15f : 1f, 1f);
            game.batch.draw(buttonRegion, x, y, w, h);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontBtn, text);
        fontBtn.setColor(hover ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, text, x + (w - layout.width) / 2f, y + (h - layout.height) / 2f);
    }

    private void drawLargeButton(String text, float x, float y, float w, float h, boolean hover) {
        float hoverScale = hover ? 1.05f : 1.0f;
        float bw = w * hoverScale;
        float bh = h * hoverScale;
        float bx = x - (bw - w) / 2f;
        float by = y - (bh - h) / 2f;
        if (buttonRegion != null) {
            game.batch.setColor(hover ? 1.15f : 1f, hover ? 1.15f : 1f, hover ? 1.15f : 1f, 1f);
            game.batch.draw(buttonRegion, bx, by, bw, bh);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontBtn, text);
        fontBtn.setColor(hover ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, text, bx + (bw - layout.width) / 2f, by + (bh - layout.height) / 2f);
    }

    private void createWorldAndPlay() {
        String finalName = nameInput.trim().isEmpty() ? "Mundo" : nameInput.trim();
        long numericSeed;
        try {
            numericSeed = Long.parseLong(seedInput.trim());
        } catch (NumberFormatException e) {
            numericSeed = (long) seedInput.trim().hashCode();
        }

        WorldInfo saved = WorldSaveManager.saveWorld(finalName, numericSeed);
        String fileName = (saved != null) ? saved.fileName : null;

        game.setScreen(new GameScreen(game, numericSeed, finalName, fileName));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        Gdx.input.setInputProcessor(null);
        if (whiteTex != null) whiteTex.dispose();
        if (slotTexture != null) slotTexture.dispose();
        if (buttonTexture != null) buttonTexture.dispose();
        if (backBtnTexture != null) backBtnTexture.dispose();
        if (buttonSound != null) buttonSound.dispose();
        if (fontTitle != null) fontTitle.dispose();
        if (fontLabel != null) fontLabel.dispose();
        if (fontInput != null) fontInput.dispose();
        if (fontBtn != null) fontBtn.dispose();
    }
}