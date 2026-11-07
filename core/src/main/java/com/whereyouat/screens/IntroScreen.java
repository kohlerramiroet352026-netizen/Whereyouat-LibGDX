package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
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
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.whereyouat.WhereYouAtGame;

/**
 * IntroScreen - Animated author splash screen played on game startup.
 *
 * Sequence:
 * 1. Initial black screen: 1.5s
 * 2. "RAZVN" logo fade-in: 1.2s (menu music begins fading in here)
 * 3. Logo hold: 3.0s
 * 4. "Presenta..." text fade-in: 1.0s (placed ~22px below logo)
 * 5. Both hold: 1.5s
 * 6. Smooth Terraria-style cross-fade: TitleScreen renders in background
 *    with title, floating logo, and buttons already active while the black
 *    splash curtain fades away over 1.8s to seamlessly reveal the main menu.
 */
public class IntroScreen extends ScreenAdapter {

    private final WhereYouAtGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;

    private Texture razvnTex;
    private TextureRegion razvnRegion;
    private Texture whiteTex;
    private TextureRegion whitePixel;
    private BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();

    private TitleScreen titleScreen;

    private float timer = 0f;
    private boolean musicStarted = false;
    private boolean transitioning = false;

    // Timeline markers in seconds
    private static final float BLACK_HOLD       = 1.5f;
    private static final float LOGO_FADE_IN    = 1.2f;
    private static final float LOGO_HOLD       = 3.0f;
    private static final float TEXT_FADE_IN    = 1.0f;
    private static final float BOTH_HOLD       = 1.5f;
    private static final float FADE_OUT        = 1.8f;

    // Computed milestone timestamps
    private static final float T_LOGO_START    = BLACK_HOLD;
    private static final float T_LOGO_FULL     = T_LOGO_START + LOGO_FADE_IN;
    private static final float T_TEXT_START    = T_LOGO_FULL + LOGO_HOLD;
    private static final float T_TEXT_FULL     = T_TEXT_START + TEXT_FADE_IN;
    private static final float T_FADE_OUT      = T_TEXT_FULL + BOTH_HOLD;
    private static final float T_END           = T_FADE_OUT + FADE_OUT;

    public IntroScreen(WhereYouAtGame game) {
        this.game = game;

        camera = new OrthographicCamera();
        camera.setToOrtho(true, 768, 576);
        viewport = new FitViewport(768, 576, camera);
        viewport.apply();

        // Initialize TitleScreen immediately so its viewport, textures, and fonts are ready
        titleScreen = new TitleScreen(game);
        titleScreen.inputEnabled = false;
        titleScreen.bgAlpha = 0f;
        titleScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // 1x1 white pixel for fade curtain
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
        whiteTex = new Texture(pixmap);
        pixmap.dispose();
        whitePixel = new TextureRegion(whiteTex);

        // Load RAZVN logo texture
        if (Gdx.files.internal("RAZVN.png").exists()) {
            razvnTex = new Texture(Gdx.files.internal("RAZVN.png"));
            razvnTex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            razvnRegion = new TextureRegion(razvnTex);
            razvnRegion.flip(false, true); // Camera is Y-down
        }

        // Load font for "Presenta..."
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter fontParameter = new FreeTypeFontParameter();
            fontParameter.flip = true;
            fontParameter.size = 14;
            fontParameter.shadowOffsetX = 1;
            fontParameter.shadowOffsetY = 1;
            fontParameter.shadowColor = new Color(0f, 0f, 0f, 0.85f);
            font = fontGenerator.generateFont(fontParameter);
            font.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            fontGenerator.dispose();
        } else {
            font = new BitmapFont();
        }
    }

    @Override
    public void render(float delta) {
        float deltaTime = Math.min(delta, 0.1f);
        timer += deltaTime;

        // Start menu music right as RAZVN begins fading in
        if (timer >= T_LOGO_START && !musicStarted) {
            musicStarted = true;
            game.startMenuMusic(true);
        }



        // When intro timeline finishes, transition cleanly to TitleScreen
        if (timer >= T_END && !transitioning) {
            transitioning = true;
            titleScreen.inputEnabled = true;
            titleScreen.bgAlpha = 1f;
            game.isIntroActive = false;
            game.setScreen(titleScreen);
            return;
        }

        // Compute element alphas
        float logoAlpha = 0f;
        float textAlpha = 0f;
        float curtainAlpha = 1f;

        if (timer < T_LOGO_START) {
            logoAlpha = 0f;
            textAlpha = 0f;
            curtainAlpha = 1f;
        } else if (timer < T_LOGO_FULL) {
            logoAlpha = Math.min(1f, (timer - T_LOGO_START) / LOGO_FADE_IN);
            textAlpha = 0f;
            curtainAlpha = 1f;
        } else if (timer < T_TEXT_START) {
            logoAlpha = 1f;
            textAlpha = 0f;
            curtainAlpha = 1f;
        } else if (timer < T_TEXT_FULL) {
            logoAlpha = 1f;
            textAlpha = Math.min(1f, (timer - T_TEXT_START) / TEXT_FADE_IN);
            curtainAlpha = 1f;
        } else if (timer < T_FADE_OUT) {
            logoAlpha = 1f;
            textAlpha = 1f;
            curtainAlpha = 1f;
        } else {
            // Terraria-style cross-fade: TitleScreen renders in background while splash fades away
            float fadeProgress = Math.min(1f, (timer - T_FADE_OUT) / FADE_OUT);
            curtainAlpha = Math.max(0f, 1f - fadeProgress);
            logoAlpha = curtainAlpha;
            textAlpha = curtainAlpha;
        }

        // 1. If in cross-fade phase, render TitleScreen in the background first
        if (timer >= T_FADE_OUT && titleScreen != null) {
            float fadeProgress = Math.min(1f, (timer - T_FADE_OUT) / FADE_OUT);
            titleScreen.bgAlpha = fadeProgress;
            titleScreen.render(deltaTime);
        } else {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        // 2. Render intro splash overlay on top
        viewport.apply();
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // Dark curtain over background during cross-fade (solid black before T_FADE_OUT)
        if (timer >= T_FADE_OUT && whitePixel != null && curtainAlpha > 0f) {
            game.batch.setColor(0f, 0f, 0f, curtainAlpha);
            game.batch.draw(whitePixel, 0, 0, 768f, 576f);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        // Logo positioning
        float logoHeight = 0f;
        float logoY = 0f;
        if (razvnRegion != null && logoAlpha > 0f) {
            float scale = 3.0f;
            float logoWidth = razvnRegion.getRegionWidth() * scale;
            logoHeight = razvnRegion.getRegionHeight() * scale;
            float logoX = (768f - logoWidth) / 2f;
            logoY = (576f - logoHeight) / 2f - 20f;

            game.batch.setColor(1f, 1f, 1f, logoAlpha);
            game.batch.draw(razvnRegion, logoX, logoY, logoWidth, logoHeight);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        // "Presenta..." text positioned 22px below the logo (~14px lower than original)
        if (font != null && textAlpha > 0f && razvnRegion != null) {
            String text = "Presenta...";
            glyphLayout.setText(font, text);
            float textX = (768f - glyphLayout.width) / 2f;
            float textY = logoY + logoHeight + 22f;

            font.setColor(1f, 1f, 1f, textAlpha);
            font.draw(game.batch, text, textX, textY);
            font.setColor(1f, 1f, 1f, 1f);
        }

        game.batch.end();
    }



    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        if (titleScreen != null) {
            titleScreen.resize(width, height);
        }
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (razvnTex != null) {
            razvnTex.dispose();
            razvnTex = null;
        }
        if (whiteTex != null) {
            whiteTex.dispose();
            whiteTex = null;
        }
        if (font != null) {
            font.dispose();
            font = null;
        }
    }
}
