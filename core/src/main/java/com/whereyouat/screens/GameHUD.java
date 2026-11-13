package com.whereyouat.screens;

import java.util.ArrayList;
import java.util.List;

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
import com.whereyouat.items.Item;

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

    public int slotCol = 0;
    public int slotRow = 0;

    // Mensaje de notificación flotante en pantalla
    private String notificationMessage = null;
    private float notificationTimer = 0f;

    public void setNotification(String message, float duration) {
        this.notificationMessage = message;
        this.notificationTimer = duration;
    }

    public void update(float delta) {
        if (notificationTimer > 0f) {
            notificationTimer -= delta;
            if (notificationTimer <= 0f) {
                notificationMessage = null;
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

    public void renderInventory(SpriteBatch batch, Player player, float screenW, float screenH) {
        // Dimensiones de la ventana de slots
        float slotSize = 42f;
        float slotPadding = 8f;
        float padding = 16f;

        // 5 columnas x 4 filas = 242px ancho x 192px alto de contenido
        float gridW = (5 * slotSize) + (4 * slotPadding);
        float gridH = (4 * slotSize) + (3 * slotPadding);

        float frameW = gridW + (padding * 2f); // 274 px
        float frameH = gridH + (padding * 2f); // 224 px
        float frameX = screenW - frameW - 20f; // Margen derecho
        float frameY = 24f;                    // Margen superior

        // 1. VENTANA PRINCIPAL (Grid de ítems)
        drawSubWindow(batch, frameX, frameY, frameW, frameH);

        // 2. DIBUJAR ÍTEMS (flotando sin cajas grises, como RyiSnow)
        float startSlotsX = frameX + padding;
        float startSlotsY = frameY + padding;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float slotX = startSlotsX + (col * (slotSize + slotPadding));
                float slotY = startSlotsY + (row * (slotSize + slotPadding));

                int index = col + (row * 5);
                if (index < player.inventory.size()) {
                    Item item = player.inventory.get(index);
                    if (item.icon != null) {
                        batch.draw(item.icon, slotX + 3f, slotY + 3f, slotSize - 6f, slotSize - 6f);
                    } else {
                        // Ícono provisorio si el ítem aún no tiene textura cargada
                        batch.setColor(0.9f, 0.25f, 0.25f, 0.9f);
                        batch.draw(whitePixel, slotX + 8f, slotY + 8f, slotSize - 16f, slotSize - 16f);
                        batch.setColor(1f, 1f, 1f, 1f);
                    }
                }
            }
        }

        // 3. CURSOR SELECTOR REDONDEADO
        float cursorX = startSlotsX + (slotCol * (slotSize + slotPadding));
        float cursorY = startSlotsY + (slotRow * (slotSize + slotPadding));
        drawCursor(batch, cursorX, cursorY, slotSize);

        // 4. VENTANA INFERIOR DE DESCRIPCIÓN (Siempre visible justo abajo)
        float descX = frameX;
        float descY = frameY + frameH + 8f; // 8px de separación
        float descW = frameW;
        float descH = 120f;
        drawSubWindow(batch, descX, descY, descW, descH);

        // Texto de descripción del ítem seleccionado
        int selectedIndex = slotCol + (slotRow * 5);
        if (selectedIndex < player.inventory.size()) {
            Item selectedItem = player.inventory.get(selectedIndex);

            if (fontSmall != null) {
                // Nombre en dorado / amarillo
                fontSmall.setColor(Color.GOLD);
                fontSmall.draw(batch, selectedItem.name, descX + 16f, descY + 24f);

                // Descripción en blanco con soporte multilínea (\n)
                fontSmall.setColor(Color.WHITE);
                float lineY = descY + 48f;
                for (String line : selectedItem.description.split("\n")) {
                    fontSmall.draw(batch, line, descX + 16f, lineY);
                    lineY += 18f;
                }
            }
        }
    }

    public void drawSubWindow(SpriteBatch batch, float x, float y, float width, float height){
        if (whitePixel == null) return;

        //fondo semitransparente
        batch.setColor(0f, 0f, 0f, 0.82f);
        batch.draw(whitePixel, x + 5f, y, width - 10f, height);
        batch.draw(whitePixel, x, y +5f, width, height - 10f);
        batch.draw(whitePixel, x + 2f, y + 2f, width - 4f, height - 4f);

        batch.setColor(1f, 1f, 1f, 1f);
        float b = 3f;

        //tramos rectos
        batch.draw(whitePixel, x + 6f, y, width - 12f, b);
        batch.draw(whitePixel, x + 6f, y + height - b, width -12f, b);
        batch.draw(whitePixel, x, y + 6f, b, height - 12f);
        batch.draw(whitePixel, x + width - b, y + 6f, b, height - 12f);

        //puntos de redondeo
        //superior izquierda
        batch.draw(whitePixel, x + 3f, y + 1f, 3f, b);
        batch.draw(whitePixel, x + 1f, y + 3f, b, 3f);
        //superior derecha
        batch.draw(whitePixel, x + width - 6f, y + 1f, 3f, b);
        batch.draw(whitePixel, x + width - 1f - b, y + 3f, b, 3f);
        //inferior izquierda
        batch.draw(whitePixel, x + 3f, y + height - 1f - b, 3f, b);
        batch.draw(whitePixel, x + 1f, y + height - 6f, b, 3f);
        //inferior derecha
        batch.draw(whitePixel, x + width - 6f, y + height - 1f - b, 3f, b);
        batch.draw(whitePixel, x + width - 1f - b, y + height - 6f, b, 3f);

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public void drawCursor(SpriteBatch batch, float x, float y, float size){
        if (whitePixel == null) return;

        batch.setColor(1f, 1f, 1f, 0.95f);
        float b = 2.5f;

        //bordes rectos
        batch.draw(whitePixel, x + 4f, y, size - 8f, b);
        batch.draw(whitePixel, x + 4f, y + size - b, size - 8f, b);
        batch.draw(whitePixel, x, y + 4f, b, size - 8f);
        batch.draw(whitePixel, x + size - b, y + 4f, b, size - 8f);

        //esquinas redondeadas
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
            renderPauseMenu(batch, screenWidth, screenHeight, saveFeedbackTimer, gameScreen);
        }

        // 4. Pantalla de muerte definitiva (0 vidas)
        if (showDeathScreen) {
            renderDeathScreen(batch, screenWidth, screenHeight);
        }

        // 5. Pantalla de reaparición (perdió vida pero aún tiene restantes)
        if (showRespawnScreen) {
            renderRespawnScreen(batch, screenWidth, screenHeight, player.lives);
        }

        //renderizar inventario
        if(showInventory){
            renderInventory(batch, player, screenWidth, screenHeight);
        }

        // 6. CHAT (siempre por encima, incluso en pausa)
        if (gameScreen != null && gameScreen.netManager != null) {
            renderChat(batch, gameScreen, screenWidth, screenHeight);
        }

        // Renderizar notificación en pantalla si existe
        if (notificationMessage != null && notificationTimer > 0f && fontSmall != null) {
            glyphLayout.setText(fontSmall, notificationMessage);
            float boxW = glyphLayout.width + 32f;
            float boxH = glyphLayout.height + 20f;
            float boxX = (screenWidth - boxW) / 2f;
            float boxY = screenHeight - boxH - 25f;
            drawSubWindow(batch, boxX, boxY, boxW, boxH);
            fontSmall.setColor(Color.YELLOW);
            fontSmall.draw(batch, notificationMessage, boxX + 16f, boxY + 14f);
            fontSmall.setColor(Color.WHITE);
        }

        batch.end();

        // IMPORTANTE: ejecutar acciones que cambian de pantalla DESPUÉS de batch.end().
        // Si se llamaran dentro de batch.begin/end, LibGDX llamaría hide()→dispose()
        // con el batch aún activo, crasheando el render.
        if (pendingAction != null) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }
    }

    private void renderChat(SpriteBatch batch, GameScreen gs, float screenW, float screenH) {
        if (fontSmall == null || whitePixel == null) return;

        // Con camera.setToOrtho(true): Y=0 es arriba, Y=screenH es abajo.
        // fontSmall.draw(batch, text, x, y) dibuja con y = esquina SUPERIOR-izquierda del texto.
        // batch.draw(tex, x, y, w, h) dibuja con y = esquina SUPERIOR-izquierda del rectángulo.
        //
        // Layout del chat (de abajo hacia arriba):
        //   [input box]      ← solo si está abierto (más abajo)
        //   [mensaje 0]      ← más reciente, más abajo
        //   [mensaje 1]
        //   [mensaje 2]      ← más arriba

        final float chatX    = 10f;
        final float chatW    = 290f;
        final float lineH    = 18f;   // separación entre líneas
        final float fontH    = 11f;   // altura aproximada del glyph
        final float chatPad  = 5f;
        final float marginBottom = 72f; // margen desde el borde inferior de pantalla

        int count = Math.min(gs.chatMessages.size(), GameScreen.CHAT_MAX_VISIBLE);
        boolean inputOpen = gs.isChatOpen;

        if (count == 0 && !inputOpen) return;

        // anchorY = Y del mensaje más reciente (índice 0).
        // En Y-down, "más abajo" = mayor Y → el más reciente está en mayor Y.
        float anchorY = screenH - marginBottom - fontH;

        // ── Fondo de mensajes ──────────────────────────────────────────────────────
        if (count > 0) {
            // El mensaje más viejo está en anchorY - (count-1)*lineH
            float bgTop    = anchorY - (count - 1) * lineH - chatPad;
            float bgBottom = anchorY + fontH + chatPad;
            batch.setColor(0f, 0f, 0f, 0.48f);
            batch.draw(whitePixel, chatX - chatPad, bgTop, chatW, bgBottom - bgTop);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        // ── Mensajes ──────────────────────────────────────────────────────────────
        java.util.List<GameScreen.ChatMessage> msgs = new java.util.ArrayList<>(gs.chatMessages);
        for (int i = 0; i < count; i++) {
            GameScreen.ChatMessage cm = msgs.get(i);
            float alpha = Math.min(1f, cm.timer / 1.5f);
            float msgY = anchorY - i * lineH;  // i=0 abajo, i>0 sube
            fontSmall.setColor(cm.color.r, cm.color.g, cm.color.b, alpha);
            fontSmall.draw(batch, cm.text, chatX, msgY);
        }

        // ── Caja de entrada ───────────────────────────────────────────────────────
        if (inputOpen) {
            handleChatKeyInput(gs);

            // La caja va DEBAJO del mensaje más reciente
            float inputTop    = anchorY + fontH + chatPad + 2f;
            float inputBottom = inputTop + fontH + chatPad * 2f;
            batch.setColor(0f, 0f, 0f, 0.72f);
            batch.draw(whitePixel, chatX - chatPad, inputTop, chatW, inputBottom - inputTop);
            // borde blanco superior de la caja
            batch.setColor(0.7f, 0.7f, 0.7f, 0.9f);
            batch.draw(whitePixel, chatX - chatPad, inputTop, chatW, 1f);
            batch.setColor(1f, 1f, 1f, 1f);

            String cursor = ((System.currentTimeMillis() / 450) % 2 == 0) ? "_" : "";
            fontSmall.setColor(Color.WHITE);
            fontSmall.draw(batch, "> " + gs.chatInput + cursor, chatX, inputTop + chatPad);
        }

        fontSmall.setColor(Color.WHITE);
    }

    /** Captura las teclas de escritura del chat directamente desde el polling de LibGDX. */
    private void handleChatKeyInput(GameScreen gs) {
        // Backspace
        if (Gdx.input.isKeyJustPressed(Keys.BACKSPACE) && gs.chatInput.length() > 0) {
            gs.chatInput = gs.chatInput.substring(0, gs.chatInput.length() - 1);
        }
        // Letras y caracteres imprimibles (A-Z, 0-9, espacio, signos)
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

    // Acción pendiente diferida: se ejecuta DESPUÉS de batch.end() para evitar
    // el crash causado por cambiar de pantalla mientras el SpriteBatch está activo.
    private Runnable pendingAction = null;

    // Guardamos el roomCode copiado recientemente para feedback visual
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
        // Botón de código de sala: solo visible si es host y tiene código
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

        // Botón "Copiar código de sala" (solo host)
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

