package com.whereyouat.screens;

import java.util.Random;

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
import com.whereyouat.net.NetworkManager;
import com.whereyouat.save.WorldSaveManager;
import com.whereyouat.save.WorldSaveManager.WorldInfo;

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

    private boolean isMultiplayer = false; // Modo Host
    private boolean isJoiningMode = false; // Modo Cliente (unirse)
    private String joinCodeInput = "";     // Código pegado por el jugador 2
    private String connectionError = "";   // Mensaje de error si falla

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
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
        whiteTex = new Texture(pixmap);
        pixmap.dispose();
        whitePixel = new TextureRegion(whiteTex);

        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter fontParameter = new FreeTypeFontParameter();
            fontParameter.flip = true;

            fontParameter.size = 18;
            fontParameter.shadowOffsetX = 2;
            fontParameter.shadowOffsetY = 2;
            fontParameter.shadowColor = new Color(0f, 0f, 0f, 0.8f);
            fontTitle = fontGenerator.generateFont(fontParameter);
            fontTitle.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            fontParameter.size = 12;
            fontParameter.shadowOffsetX = 1;
            fontParameter.shadowOffsetY = 1;
            fontLabel = fontGenerator.generateFont(fontParameter);
            fontLabel.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            fontParameter.size = 14;
            fontParameter.shadowOffsetX = 1;
            fontParameter.shadowOffsetY = 1;
            fontInput = fontGenerator.generateFont(fontParameter);
            fontInput.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            fontParameter.size = 12;
            fontParameter.shadowOffsetX = 1;
            fontParameter.shadowOffsetY = 1;
            fontBtn = fontGenerator.generateFont(fontParameter);
            fontBtn.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            fontGenerator.dispose();
        } else {
            fontTitle = new BitmapFont();
            fontLabel = new BitmapFont();
            fontInput = new BitmapFont();
            fontBtn = new BitmapFont();
        }
    }

    private void pasteFromClipboard() {
        try {
            String clip = Gdx.app.getClipboard().getContents();
            if (clip != null) {
                // Solo mantener caracteres válidos de Base64-URL (A-Za-z0-9 _ - =)
                // Esto evita que basura del portapapeles (stacktraces, espacios, etc.)
                // llegue al campo y produzca IPs corruptas al decodificar.
                String sanitized = clip.trim().replaceAll("[^A-Za-z0-9_=-]", "");
                if (sanitized.length() > 32) {
                    sanitized = sanitized.substring(0, 32);
                }
                if (!sanitized.isEmpty()) {
                    joinCodeInput = sanitized;
                    connectionError = "";
                }
            }
        } catch (Throwable ignored) {
            // Previene errores en Windows si el portapapeles no está en formato string
        }
    }

    private void setupInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Keys.V && (Gdx.input.isKeyPressed(Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Keys.CONTROL_RIGHT))) {
                    if (isJoiningMode || activeField == 2) {
                        pasteFromClipboard();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean keyTyped(char character) {
                if (character == '\b') { // Backspace
                    if (activeField == 0 && nameInput.length() > 0) {
                        nameInput = nameInput.substring(0, nameInput.length() - 1);
                    } else if (activeField == 1 && seedInput.length() > 0) {
                        seedInput = seedInput.substring(0, seedInput.length() - 1);
                    } else if (activeField == 2 && joinCodeInput.length() > 0){
                        joinCodeInput = joinCodeInput.substring(0, joinCodeInput.length() - 1);
                    }
                    return true;
                } else if (character == '\r' || character == '\n') {
                    if (isJoiningMode) {
                        createWorldAndPlay();
                    }
                    return true;
                } else if (character >= 32 && character < 127) { // Printable characters
                    if (activeField == 0 && nameInput.length() < 24) {
                        nameInput += character;
                    } else if (activeField == 1 && seedInput.length() < 18) {
                        seedInput += character;
                    } else if (activeField == 2 && joinCodeInput.length() < 32 && !Character.isWhitespace(character)){
                        joinCodeInput += character;
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
        float boxWidth = 480f;
        float boxHeight = 44f;

        // Cajas de texto en modo normal
        float nameBoxY = 132f;
        float seedBoxY = 214f;

        // Boton ALEATORIO justo debajo de la semilla
        float randomButtonWidth = 140f;
        float randomButtonHeight = 32f;
        float randomButtonX = formX + boxWidth - randomButtonWidth;
        float randomButtonY = seedBoxY + boxHeight + 8f; // 266f, termina en 298f

        // Botones de multijugador bien separados hacia abajo (terminan en 383f)
        float multiBtnX = formX;
        float multiBtnY = 345f;
        float multiBtnW = 230f;
        float multiBtnH = 38f;

        // Boton para cambiar de modo (Unirse a sala o Volver a crear)
        float joinModeBtnX = isJoiningMode ? ((768f - 240f) / 2f) : (formX + 250f);
        float joinModeBtnY = isJoiningMode ? 375f : 345f;
        float joinModeBtnW = isJoiningMode ? 240f : 230f;
        float joinModeBtnH = 38f;

        // Boton PEGAR en modo unirse
        float pasteBtnX = (768f - 240f) / 2f;
        float pasteBtnY = 215f;
        float pasteBtnW = 240f;
        float pasteBtnH = 36f;

        // Boton de accion (Crear Mundo o Conectar)
        float createButtonX = (768f - 240f) / 2f;
        float createButtonY = isJoiningMode ? 305f : 415f;
        float createButtonWidth = 240f;
        float createButtonHeight = 48f;

        // Boton Volver
        float backButtonX = formX;
        float backButtonY = 495f;
        float backButtonWidth = 140f;
        float backButtonHeight = 40f;

        boolean hoverMulti = !isJoiningMode && (mousePos.x >= multiBtnX && mousePos.x <= multiBtnX + multiBtnW && mousePos.y >= multiBtnY && mousePos.y <= multiBtnY + multiBtnH);

        boolean hoverJoinMode = (mousePos.x >= joinModeBtnX && mousePos.x <= joinModeBtnX + joinModeBtnW && mousePos.y >= joinModeBtnY && mousePos.y <= joinModeBtnY + joinModeBtnH);

        boolean hoverPaste = isJoiningMode && (mousePos.x >= pasteBtnX && mousePos.x <= pasteBtnX + pasteBtnW && mousePos.y >= pasteBtnY && mousePos.y <= pasteBtnY + pasteBtnH);

        boolean hoverName = (mousePos.x >= formX && mousePos.x <= formX + boxWidth &&
                             mousePos.y >= (isJoiningMode ? 155f : nameBoxY) && mousePos.y <= (isJoiningMode ? 155f : nameBoxY) + boxHeight);
        boolean hoverSeed = !isJoiningMode && (mousePos.x >= formX && mousePos.x <= formX + boxWidth &&
                             mousePos.y >= seedBoxY && mousePos.y <= seedBoxY + boxHeight);

        boolean hoverRandom = !isJoiningMode && (mousePos.x >= randomButtonX && mousePos.x <= randomButtonX + randomButtonWidth &&
                               mousePos.y >= randomButtonY && mousePos.y <= randomButtonY + randomButtonHeight);
        boolean hoverCreate = (mousePos.x >= createButtonX && mousePos.x <= createButtonX + createButtonWidth &&
                               mousePos.y >= createButtonY && mousePos.y <= createButtonY + createButtonHeight);
        boolean hoverBack = (mousePos.x >= backButtonX && mousePos.x <= backButtonX + backButtonWidth &&
                             mousePos.y >= backButtonY && mousePos.y <= backButtonY + backButtonHeight);

        if (Gdx.input.isButtonJustPressed(0)) {
            if (hoverName) {
                activeField = isJoiningMode ? 2 : 0;
            } else if (hoverSeed) {
                activeField = 1;
            } else if (hoverRandom) {
                if (buttonSound != null) buttonSound.play(0.32f);
                seedInput = String.valueOf(Math.abs(rand.nextLong() % 1_000_000_000L));
            } else if (hoverPaste) {
                if (buttonSound != null) buttonSound.play(0.32f);
                pasteFromClipboard();
            } else if (hoverCreate) {
                if (buttonSound != null) buttonSound.play(0.32f);
                createWorldAndPlay();
                return;
            } else if (hoverBack) {
                if (buttonSound != null) buttonSound.play(0.32f);
                game.setScreen(new WorldSelectScreen(game));
                return;
            } else if (hoverMulti) {
                if (buttonSound != null) buttonSound.play(0.32f);
                isMultiplayer = !isMultiplayer;
            } else if (hoverJoinMode) {
                if (buttonSound != null) buttonSound.play(0.32f);
                isJoiningMode = !isJoiningMode;
                connectionError = "";
                if (isJoiningMode) {
                    isMultiplayer = false;
                    activeField = 2;
                } else {
                    activeField = 0;
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
        String screenTitle = isJoiningMode ? "UNIRSE A SALA" : "CREAR NUEVO MUNDO";
        layout.setText(fontTitle, screenTitle);
        fontTitle.setColor(Color.GOLD);
        fontTitle.draw(game.batch, screenTitle, (768f - layout.width) / 2f, 65f);

        // 2. Form fields
        if (isJoiningMode) {
            fontLabel.setColor(new Color(0.85f, 0.85f, 0.85f, 1f));
            fontLabel.draw(game.batch, "Pegar Codigo de Sala:", formX, 135f);
            drawInputBox(formX, 155f, boxWidth, boxHeight, joinCodeInput, activeField == 2);

            // Boton para pegar comodamente desde el portapapeles
            drawSmallButton("PEGAR CODIGO", pasteBtnX, pasteBtnY, pasteBtnW, pasteBtnH, hoverPaste);

            if (!connectionError.isEmpty()) {
                fontLabel.setColor(new Color(1f, 0.35f, 0.35f, 1f));
                layout.setText(fontLabel, connectionError);
                fontLabel.draw(game.batch, connectionError, (768f - layout.width) / 2f, 280f);
            }
        } else {
            // Name field
            fontLabel.setColor(new Color(0.85f, 0.85f, 0.85f, 1f));
            fontLabel.draw(game.batch, "Nombre del Mundo:", formX, nameBoxY - 18f);
            drawInputBox(formX, nameBoxY, boxWidth, boxHeight, nameInput, activeField == 0);

            // Seed field
            fontLabel.draw(game.batch, "Semilla (Texto o Número):", formX, seedBoxY - 18f);
            drawInputBox(formX, seedBoxY, boxWidth, boxHeight, seedInput, activeField == 1);

            // Random Seed button
            drawSmallButton("ALEATORIO", randomButtonX, randomButtonY, randomButtonWidth, randomButtonHeight, hoverRandom);

            // Multiplayer toggle button
            String multiText = isMultiplayer ? "MULTI: [SI]" : "MULTI: [NO]";
            drawSmallButton(multiText, multiBtnX, multiBtnY, multiBtnW, multiBtnH, hoverMulti);
        }

        // 3. Toggle Mode button ("UNIRSE A SALA" or "VOLVER A CREAR")
        String joinText = isJoiningMode ? "VOLVER A CREAR" : "UNIRSE A SALA";
        drawSmallButton(joinText, joinModeBtnX, joinModeBtnY, joinModeBtnW, joinModeBtnH, hoverJoinMode);

        // 4. Action Button (Create World or Connect)
        String actionBtnText = isJoiningMode ? "CONECTAR" : "CREAR MUNDO";
        drawLargeButton(actionBtnText, createButtonX, createButtonY, createButtonWidth, createButtonHeight, hoverCreate);

        // 5. Back Button
        if (backBtnRegion != null) {
            game.batch.setColor(hoverBack ? 1.15f : 1f, hoverBack ? 1.15f : 1f, hoverBack ? 1.15f : 1f, 1f);
            game.batch.draw(backBtnRegion, backButtonX, backButtonY, backButtonWidth, backButtonHeight);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontBtn, "VOLVER");
        fontBtn.setColor(hoverBack ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, "VOLVER", backButtonX + (backButtonWidth - layout.width) / 2f, backButtonY + (backButtonHeight - layout.height) / 2f);

        game.batch.end();
    }

    private void drawInputBox(float x, float y, float width, float height, String text, boolean focused) {
        // Draw button_slot.png at its native pixel dimensions, centered over the hitbox.
        // The texture is 576x48 — no stretching to preserve pixel art quality.
        // If the texture is unavailable, fall back to a plain dark rectangle.
        if (slotRegion != null) {
            float slotWidth = slotRegion.getRegionWidth();
            float slotHeight = slotRegion.getRegionHeight();
            float drawX = x + (width - slotWidth) / 2f;
            float drawY = y + (height - slotHeight) / 2f;
            game.batch.setColor(focused ? new Color(1.1f, 1.1f, 1.1f, 1f) : new Color(1f, 1f, 1f, 1f));
            game.batch.draw(slotRegion, drawX, drawY, slotWidth, slotHeight);
            game.batch.setColor(1f, 1f, 1f, 1f);
        } else if (whitePixel != null) {
            game.batch.setColor(focused ? new Color(0.55f, 0.55f, 0.60f, 1f) : new Color(0.35f, 0.35f, 0.38f, 1f));
            game.batch.draw(whitePixel, x - 1f, y - 1f, width + 2f, height + 2f);
            game.batch.setColor(focused ? new Color(0.20f, 0.20f, 0.24f, 1f) : new Color(0.12f, 0.12f, 0.14f, 1f));
            game.batch.draw(whitePixel, x, y, width, height);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }

        String displayText = text + (focused && (System.currentTimeMillis() / 450) % 2 == 0 ? "_" : "");
        layout.setText(fontInput, displayText.isEmpty() ? " " : displayText);
        fontInput.setColor(focused ? Color.WHITE : new Color(0.8f, 0.8f, 0.8f, 1f));
        fontInput.draw(game.batch, displayText, x + 16f, y + (height - layout.height) / 2f);
    }

    private void drawSmallButton(String text, float x, float y, float width, float height, boolean hover) {
        if (buttonRegion != null) {
            game.batch.setColor(hover ? 1.15f : 1f, hover ? 1.15f : 1f, hover ? 1.15f : 1f, 1f);
            game.batch.draw(buttonRegion, x, y, width, height);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontBtn, text);
        fontBtn.setColor(hover ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, text, x + (width - layout.width) / 2f, y + (height - layout.height) / 2f);
    }

    private void drawLargeButton(String text, float x, float y, float width, float height, boolean hover) {
        float hoverScale = hover ? 1.05f : 1.0f;
        float buttonWidth = width * hoverScale;
        float buttonHeight = height * hoverScale;
        float buttonX = x - (buttonWidth - width) / 2f;
        float buttonY = y - (buttonHeight - height) / 2f;
        if (buttonRegion != null) {
            game.batch.setColor(hover ? 1.15f : 1f, hover ? 1.15f : 1f, hover ? 1.15f : 1f, 1f);
            game.batch.draw(buttonRegion, buttonX, buttonY, buttonWidth, buttonHeight);
            game.batch.setColor(1f, 1f, 1f, 1f);
        }
        layout.setText(fontBtn, text);
        fontBtn.setColor(hover ? Color.GOLD : Color.WHITE);
        fontBtn.draw(game.batch, text, buttonX + (buttonWidth - layout.width) / 2f, buttonY + (buttonHeight - layout.height) / 2f);
    }

    private void createWorldAndPlay() {
    connectionError = "";

    // CASO A: EL JUGADOR 2 SE ESTÁ UNIENDO CON CÓDIGO
    if (isJoiningMode) {
        if (joinCodeInput.trim().isEmpty()) {
            connectionError = "Ingresa o pega un codigo de sala.";
            return;
        }

        NetworkManager net = new NetworkManager();
        boolean connected = net.connectAsClient(joinCodeInput.trim());

        if (!connected) {
            connectionError = "Error: Sala no encontrada o no esta creada.";
            return;
        }

        // Conectado con éxito: lanza el juego como Cliente (Isla 2)
        // La semilla 12345 la sincronizarán por red
        game.setScreen(new GameScreen(game, 12345L, "Mundo Conectado", null, net));
        return;
    }

    // CASO B: EL JUGADOR 1 ESTÁ CREANDO EL MUNDO (HOST O SOLO)
    String finalName = nameInput.trim().isEmpty() ? "Mundo" : nameInput.trim();
    long numericSeed;
    try {
        numericSeed = Long.parseLong(seedInput.trim());
    } catch (NumberFormatException e) {
        numericSeed = (long) seedInput.trim().hashCode();
    }

        WorldInfo saved = WorldSaveManager.saveWorld(finalName, numericSeed);
        String fileName = (saved != null) ? saved.fileName : null;

        NetworkManager net = null;
        String hostRoomCode = "";
        if (isMultiplayer) {
            net = new NetworkManager();
            hostRoomCode = net.startHost(); // Inicia el servidor
            if (hostRoomCode != null) {
                try {
                    Gdx.app.getClipboard().setContents(hostRoomCode);
                } catch (Throwable ignored) {}
                Gdx.app.log("Multijugador", "Sala creada. Codigo copiado al portapapeles: " + hostRoomCode);
            }
            // Guardar la bandera de multijugador en el archivo de guardado recién creado
            if (fileName != null) {
                WorldSaveManager.saveGameProgress(fileName, finalName, numericSeed, System.currentTimeMillis(),
                        null, null, null, true);
            }
        }

        GameScreen screen = new GameScreen(game, numericSeed, finalName, fileName, net);
        if (net != null && hostRoomCode != null) {
            screen.roomCode = hostRoomCode;
        }
        game.setScreen(screen);
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