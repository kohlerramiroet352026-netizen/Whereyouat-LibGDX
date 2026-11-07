package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
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
import com.whereyouat.WhereYouAtGame;
import com.whereyouat.audio.AudioManager;
import com.whereyouat.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * GameHUD - Responsable de la carga y renderizado de la interfaz de usuario en el juego (HUD):
 * contenedor de corazones, barra de salud, textos informativos, menú de pausa,
 * pantalla de muerte y pantalla de reaparición.
 */
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

    // Fuentes tipográficas
    private BitmapFont fontSmall;
    private BitmapFont fontMedium;
    private BitmapFont fontLarge;

    // Texturas de la interfaz
    private Texture heartTexture;
    private TextureRegion heartRegion;
    private Texture whiteTex;
    private TextureRegion whitePixel;

    private Texture heartContainerSheet;
    private final TextureRegion[] heartContainerFrames = new TextureRegion[11];

    private Texture healthBarSheet;
    private final TextureRegion[] healthBarFrames = new TextureRegion[9];

    private Texture buttonSlotTex;
    private TextureRegion buttonSlotRegion;
    private Texture backButtonTex;
    private TextureRegion backButtonRegion;

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
        // Cargar fuente pixel art TrueType
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.flip = true;
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

        // Píxel blanco para fondos geométricos limpios
        Pixmap pixelPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixelPixmap.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
        whiteTex = new Texture(pixelPixmap);
        pixelPixmap.dispose();
        hudTextures.add(whiteTex);
        whitePixel = new TextureRegion(whiteTex);

        // Texturas de botones
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

        // Sprite de corazón (13x12)
        if (Gdx.files.internal("player/heart.png").exists()) {
            heartTexture = new Texture(Gdx.files.internal("player/heart.png"));
            heartTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            hudTextures.add(heartTexture);
            heartRegion = new TextureRegion(heartTexture);
            heartRegion.flip(false, true);
        }

        // Hoja de frames del contenedor de corazones (128x96, 2 cols x 6 rows, cada frame 64x16)
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

        // Hoja de frames de la barra de salud (70x144, 9 frames de 70x16)
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

    public void render(SpriteBatch batch, Player player, boolean isPaused,
                       boolean showDeathScreen, boolean showRespawnScreen,
                       float saveFeedbackTimer, float animTime) {

        hudCamera.update();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();

        float screenWidth = hudCamera.viewportWidth;
        float screenHeight = hudCamera.viewportHeight;

        // Escala adaptativa según altura de pantalla
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

        // 1. Contenedor de corazones y vidas (Esquina superior izquierda)
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

        // 2. Barra de salud centrada debajo del contenedor
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
            // Fondo oscuro de la barra
            batch.setColor(0.14f, 0.05f, 0.05f, 0.86f);
            batch.draw(whitePixel, innerX, innerY, maxInnerW, innerH);

            // Relleno de salud con relieve de 3 capas
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

        // 3. Menú de pausa
        if (isPaused) {
            renderPauseMenu(batch, screenWidth, screenHeight, saveFeedbackTimer);
        }

        // 4. Pantalla de muerte definitiva (0 vidas)
        if (showDeathScreen) {
            renderDeathScreen(batch, screenWidth, screenHeight);
        }

        // 5. Pantalla de reaparición (perdió vida pero aún tiene restantes)
        if (showRespawnScreen) {
            renderRespawnScreen(batch, screenWidth, screenHeight, player.lives);
        }

        batch.end();
    }

    private void renderPauseMenu(SpriteBatch batch, float screenW, float screenH, float saveFeedbackTimer) {
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

        float mouseX = Gdx.input.getX() * (screenW / Gdx.graphics.getWidth());
        float mouseY = Gdx.input.getY() * (screenH / Gdx.graphics.getHeight());
        boolean hoverBack = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= menuButtonY && mouseY <= menuButtonY + buttonHeight;
        boolean hoverSave = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= saveButtonY && mouseY <= saveButtonY + buttonHeight;

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

        if (Gdx.input.isButtonJustPressed(0)) {
            if (hoverBack) {
                audio.playButtonClick();
                callback.onReturnToTitle();
            } else if (hoverSave) {
                audio.playButtonClick();
                callback.onSaveProgress();
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
            callback.onReturnToTitle();
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
                callback.onReturnToTitle();
            }
        } else if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            audio.playButtonClick();
            callback.onRespawn();
        }
    }

    public void dispose() {
        if (fontSmall != null) fontSmall.dispose();
        if (fontMedium != null) fontMedium.dispose();
        if (fontLarge != null) fontLarge.dispose();

        for (Texture tex : hudTextures) {
            tex.dispose();
        }
        hudTextures.clear();
    }
}

