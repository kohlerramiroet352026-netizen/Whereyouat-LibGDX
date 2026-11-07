package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
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

/**
 * TitleScreen for LibGDX.
 * Atmospheric start menu with gentle floating title logo, pixel-art buttons,
 * sound effects, and clean screen transitions.
 *
 * Menu music is started here and stored in game.menuMusic so it persists
 * across WorldSelectScreen and WorldCreateScreen without interruption.
 * It only fades out when the player enters a world.
 */
public class TitleScreen extends ScreenAdapter {

    private final WhereYouAtGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final Vector3 mousePos = new Vector3();

    private Texture titleTexture;
    private TextureRegion titleRegion;
    private Texture buttonTexture;
    private TextureRegion buttonRegion;

    private BitmapFont fontTitle;
    private BitmapFont fontBtn;
    private BitmapFont fontSmall;
    private final GlyphLayout layout = new GlyphLayout();

    private Sound buttonSound;

    private final String[] ostTracks = {
        "ost/aviva.mp3", "ost/farlands.mp3", "ost/hope.mp3", "ost/modulo.mp3", "ost/TYWM.mp3"
    };

    private float animTime = 0f;
    public boolean inputEnabled = true;
    public float bgAlpha = 1f;

    // Button geometry (768 x 576 virtual coords)
    private static final float BTN_WIDTH = 200f;
    private static final float BTN_HEIGHT = 44f;
    private static final float PLAY_Y = 320f;
    private static final float QUIT_Y = 380f;

    public TitleScreen(WhereYouAtGame game) {
        this.game = game;

        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new FitViewport(768, 576, camera);

        loadAssets();
    }

    private void loadAssets() {
        if (Gdx.files.internal("titles/title.png").exists()) {
            titleTexture = new Texture(Gdx.files.internal("titles/title.png"));
            titleTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            titleRegion = new TextureRegion(titleTexture);
            titleRegion.flip(false, true);
        }

        String btnPath = Gdx.files.internal("buttons/button1.png").exists() ? "buttons/button1.png" : "button1.png";
        if (Gdx.files.internal(btnPath).exists()) {
            buttonTexture = new Texture(Gdx.files.internal(btnPath));
            buttonTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            buttonRegion = new TextureRegion(buttonTexture);
            buttonRegion.flip(false, true);
        }

        if (Gdx.files.internal("sounds/button_sound.mp3").exists()) {
            buttonSound = Gdx.audio.newSound(Gdx.files.internal("sounds/button_sound.mp3"));
        }

        // Start menu music immediately on cold launch if not already playing or scheduled
        if (!game.isIntroActive && game.menuMusic == null && !game.isMenuMusicScheduled()) {
            game.playMenuMusicImmediately();
        }

        // Fonts
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter fontParameter = new FreeTypeFontParameter();
            fontParameter.flip = true;

            fontParameter.size = 20;
            fontParameter.shadowOffsetX = 2;
            fontParameter.shadowOffsetY = 2;
            fontParameter.shadowColor = new Color(0f, 0f, 0f, 0.8f);
            fontTitle = fontGenerator.generateFont(fontParameter);
            fontTitle.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            fontParameter.size = 14;
            fontParameter.shadowOffsetX = 1;
            fontParameter.shadowOffsetY = 1;
            fontParameter.shadowColor = new Color(0f, 0f, 0f, 0.8f);
            fontBtn = fontGenerator.generateFont(fontParameter);
            fontBtn.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            fontParameter.size = 10;
            fontParameter.shadowOffsetX = 1;
            fontParameter.shadowOffsetY = 1;
            fontSmall = fontGenerator.generateFont(fontParameter);
            fontSmall.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            fontGenerator.dispose();
        } else {
            fontTitle = new BitmapFont();
            fontBtn = new BitmapFont();
            fontSmall = new BitmapFont();
        }
    }

    @Override
    public void render(float delta) {
        animTime += delta;

        // Convert mouse coordinates to 768x576 virtual screen space
        mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(mousePos);

        float btnX = (768f - BTN_WIDTH) / 2f;
        boolean hoverPlay = (mousePos.x >= btnX && mousePos.x <= btnX + BTN_WIDTH &&
                             mousePos.y >= PLAY_Y && mousePos.y <= PLAY_Y + BTN_HEIGHT);
        boolean hoverQuit = (mousePos.x >= btnX && mousePos.x <= btnX + BTN_WIDTH &&
                             mousePos.y >= QUIT_Y && mousePos.y <= QUIT_Y + BTN_HEIGHT);

        if (inputEnabled && Gdx.input.isButtonJustPressed(0)) {
            if (hoverPlay) {
                if (buttonSound != null) buttonSound.play(0.32f);
                // Music continues — WorldSelectScreen uses game.menuMusic directly.
                // Do NOT stop or dispose it here.
                game.setScreen(new WorldSelectScreen(game));
                return;
            } else if (hoverQuit) {
                if (buttonSound != null) buttonSound.play(0.32f);
                Gdx.app.exit();
                return;
            }
        }

        // Clear background
        Gdx.gl.glClearColor(0.06f * bgAlpha, 0.08f * bgAlpha, 0.07f * bgAlpha, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // 1. Title logo with gentle floating bob
        float bob = (float) Math.sin(animTime * 2.5f) * 6f;
        float titleCenterY = 160f + bob;

        if (titleRegion != null) {
            float titleWidth = titleRegion.getRegionWidth() * 3f;
            float titleHeight = titleRegion.getRegionHeight() * 3f;
            game.batch.draw(titleRegion, (768f - titleWidth) / 2f, titleCenterY - titleHeight / 2f, titleWidth, titleHeight);
        } else {
            layout.setText(fontTitle, "WHERE YOU AT?");
            fontTitle.setColor(Color.GOLD);
            fontTitle.draw(game.batch, "WHERE YOU AT?", (768f - layout.width) / 2f, titleCenterY - layout.height / 2f);
        }

        // Subtitle (only shown if title graphic is missing)
        if (titleRegion == null) {
            layout.setText(fontSmall, "Somewhere Out There");
            fontSmall.setColor(new Color(0.65f, 0.88f, 0.55f, 0.9f));
            fontSmall.draw(game.batch, "Somewhere Out There", (768f - layout.width) / 2f, titleCenterY + 45f);
        }

        // 2. Play button
        drawMenuButton("JUGAR", btnX, PLAY_Y, hoverPlay);

        // 3. Quit button
        drawMenuButton("SALIR", btnX, QUIT_Y, hoverQuit);

        // 4. Footer instructions
        layout.setText(fontSmall, "Usa el MOUSE para navegar y seleccionar");
        fontSmall.setColor(new Color(0.7f, 0.75f, 0.7f, 0.8f));
        fontSmall.draw(game.batch, "Usa el MOUSE para navegar y seleccionar", (768f - layout.width) / 2f, 520f);

        layout.setText(fontSmall, "F11 - Pantalla Completa");
        fontSmall.setColor(new Color(0.55f, 0.6f, 0.55f, 0.7f));
        fontSmall.draw(game.batch, "F11 - Pantalla Completa", (768f - layout.width) / 2f, 545f);

        game.batch.end();
    }

    private void drawMenuButton(String text, float x, float y, boolean hover) {
        float scale = hover ? 1.05f : 1.0f;
        float buttonWidth = BTN_WIDTH * scale;
        float buttonHeight = BTN_HEIGHT * scale;
        float buttonX = x - (buttonWidth - BTN_WIDTH) / 2f;
        float buttonY = y - (buttonHeight - BTN_HEIGHT) / 2f;

        if (buttonRegion != null) {
            game.batch.setColor(hover ? 1.1f : 1f, hover ? 1.1f : 1f, hover ? 1.1f : 1f, 1f);
            game.batch.draw(buttonRegion, buttonX, buttonY, buttonWidth, buttonHeight);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        layout.setText(fontBtn, text);
        fontBtn.setColor(hover ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, text, buttonX + (buttonWidth - layout.width) / 2f, buttonY + (buttonHeight - layout.height) / 2f);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (titleTexture != null) titleTexture.dispose();
        if (buttonTexture != null) buttonTexture.dispose();
        if (buttonSound != null) buttonSound.dispose();
        // Note: game.menuMusic is NOT disposed here — it is owned by WhereYouAtGame
        // and shared with WorldSelectScreen/WorldCreateScreen.
        if (fontTitle != null) fontTitle.dispose();
        if (fontBtn != null) fontBtn.dispose();
        if (fontSmall != null) fontSmall.dispose();
    }
}
