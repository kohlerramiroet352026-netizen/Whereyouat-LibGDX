package com.whereyouat.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;
import com.whereyouat.WhereYouAtGame;
import com.whereyouat.net.NetworkManager;

public class EndGameScreen extends ScreenAdapter {
    private enum State {
        FADE_IN_BLACK,
        SHOW_TEXT,
        PLAYING_VIDEO,
        FADE_OUT_TO_MENU
    }

    private State state = State.FADE_IN_BLACK;

    private final WhereYouAtGame game;
    private final NetworkManager netManager;

    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final SpriteBatch batch;

    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private Texture blackPixel;

    private static final float FADE_SPEED = 0.40f;
    private float fadeProgress = 0f;

    private float textAlpha = 0f;
    private float textHoldTimer = 0f;
    private static final float TEXT_HOLD_DURATION = 3.8f;
    private boolean textFadingOut = false;

    private float fadeOutMenuProgress = 0f;

    private volatile boolean otherSailed = false;
    private final String otherPlayerName;

    private VideoPlayer videoPlayer;
    private volatile boolean videoFinished = false;
    private boolean videoFailed = false;

    public EndGameScreen(WhereYouAtGame game, NetworkManager netManager) {
        this(game, netManager, false);
    }

    public EndGameScreen(WhereYouAtGame game, NetworkManager netManager, boolean otherPlayerAlreadySailed) {
        this.game = game;
        this.netManager = netManager;
        this.otherSailed = otherPlayerAlreadySailed;

        this.camera = new OrthographicCamera();
        this.viewport = new FitViewport(768, 576, camera);
        this.batch = new SpriteBatch();

        loadFont();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.BLACK);
        pm.fill();
        this.blackPixel = new Texture(pm);
        pm.dispose();

        this.otherPlayerName = (netManager != null && netManager.isHost) ? "Jugador 2" : "Jugador 1";

        if (netManager != null) {
            netManager.send("SAILED");

            netManager.setListener(new NetworkManager.NetworkListener() {
                @Override
                public void onConnected() {}

                @Override
                public void onMessageReceived(String message) {
                    if ("SAILED".equals(message)) {
                        otherSailed = true;
                        netManager.send("SAILED");
                    }
                }

                @Override
                public void onDisconnected() {
                    otherSailed = true;
                }
            });
        }
    }

    private void loadFont() {
        if (Gdx.files.internal("fonts/PressStart2P-Regular.ttf").exists()) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/PressStart2P-Regular.ttf"));
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = 13;
            parameter.shadowOffsetX = 1;
            parameter.shadowOffsetY = 1;
            parameter.shadowColor = new Color(0f, 0f, 0f, 0.85f);
            font = generator.generateFont(parameter);
            font.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            generator.dispose();
        } else {
            font = new BitmapFont();
        }
    }

    private float smoothStep(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        switch (state) {
            case FADE_IN_BLACK:
                fadeProgress += FADE_SPEED * delta;
                drawBlackOverlay(smoothStep(fadeProgress));
                if (fadeProgress >= 1f) {
                    fadeProgress = 1f;
                    state = State.SHOW_TEXT;
                }
                break;

            case SHOW_TEXT:
                drawBlackOverlay(1f);

                String message;
                boolean canProceed;
                if (netManager == null) {
                    message = "Escapaste de la isla, felicitaciones";
                    canProceed = (textHoldTimer >= TEXT_HOLD_DURATION);
                } else {
                    if (otherSailed) {
                        message = "Ambos zarparon, escapando de la isla...";
                        canProceed = (textHoldTimer >= 2.0f);
                    } else {
                        message = "Esperando a que el " + otherPlayerName + " zarpe...";
                        canProceed = false;
                    }
                }

                if (!textFadingOut) {
                    textAlpha = Math.min(1f, textAlpha + delta * 0.75f);
                    if (textAlpha >= 1f) {
                        textHoldTimer += delta;
                        if (canProceed) {
                            textFadingOut = true;
                        }
                    }
                } else {
                    textAlpha = Math.max(0f, textAlpha - delta * 0.85f);
                    if (textAlpha <= 0f) {
                        if (netManager != null) {
                            startVideo();
                        } else {
                            fadeOutMenuProgress = 0f;
                            state = State.FADE_OUT_TO_MENU;
                        }
                    }
                }

                drawCenteredText(message, smoothStep(textAlpha));
                break;

            case PLAYING_VIDEO:
                if (videoFailed || videoPlayer == null) {
                    returnToMenu();
                    break;
                }

                videoPlayer.update();
                Texture frame = videoPlayer.getTexture();
                if (frame != null) {
                    renderVideoFrame(frame);
                }

                if (videoFinished || (!videoPlayer.isPlaying() && videoPlayer.getCurrentTimestamp() > 1000)) {
                    fadeOutMenuProgress = 0f;
                    state = State.FADE_OUT_TO_MENU;
                }
                break;

            case FADE_OUT_TO_MENU:
                if (videoPlayer != null && videoPlayer.getTexture() != null) {
                    renderVideoFrame(videoPlayer.getTexture());
                }
                fadeOutMenuProgress += FADE_SPEED * delta;
                drawBlackOverlay(smoothStep(fadeOutMenuProgress));
                if (fadeOutMenuProgress >= 1f) {
                    returnToMenu();
                }
                break;
        }

        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            returnToMenu();
        }
    }

    private void renderVideoFrame(Texture frame) {
        if (frame == null) return;

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();

        int vidW = (videoPlayer != null) ? videoPlayer.getVideoWidth() : 0;
        int vidH = (videoPlayer != null) ? videoPlayer.getVideoHeight() : 0;
        if (vidW <= 0) vidW = 1920;
        if (vidH <= 0) vidH = 1080;

        float videoAspect = (float) vidW / (float) vidH;
        float screenAspect = (float) screenW / (float) screenH;

        float drawW, drawH, drawX, drawY;

        if (screenAspect > videoAspect) {
            drawH = screenH;
            drawW = screenH * videoAspect;
            drawX = (screenW - drawW) / 2f;
            drawY = 0f;
        } else {
            drawW = screenW;
            drawH = screenW / videoAspect;
            drawX = 0f;
            drawY = (screenH - drawH) / 2f;
        }

        Gdx.gl.glViewport(0, 0, screenW, screenH);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, screenW, screenH);

        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(frame, drawX, drawY, drawW, drawH, 0, 0, vidW, vidH, false, false);
        batch.end();
    }

    private void drawBlackOverlay(float alpha) {
        if (alpha <= 0f || blackPixel == null) return;
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, screenW, screenH);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, screenW, screenH);
        batch.begin();
        batch.setColor(0f, 0f, 0f, Math.min(1f, alpha));
        batch.draw(blackPixel, 0, 0, screenW, screenH);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawCenteredText(String text, float alpha) {
        if (alpha <= 0f || font == null) return;
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, screenW, screenH);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, screenW, screenH);
        layout.setText(font, text);
        batch.begin();
        font.setColor(1f, 1f, 1f, alpha);
        font.draw(batch, layout,
                (screenW - layout.width) / 2f,
                (screenH + layout.height) / 2f);
        batch.end();
    }

    private void startVideo() {
        if (state == State.PLAYING_VIDEO) return;
        if (netManager == null) {
            returnToMenu();
            return;
        }

        try {
            videoPlayer = VideoPlayerCreator.createVideoPlayer();

            FileHandle fh = resolveVideoFile();
            if (fh == null || !fh.exists()) {
                Gdx.app.log("EndGameScreen", "No se encontro el archivo de video final, volviendo al menu.");
                videoFailed = true;
                returnToMenu();
                return;
            }

            videoPlayer.setOnCompletionListener(vp -> {
                videoFinished = true;
            });

            boolean success = videoPlayer.play(fh);
            if (!success) {
                Gdx.app.error("EndGameScreen", "VideoPlayer.play() devolvio false para: " + fh.path());
                videoFailed = true;
                returnToMenu();
                return;
            }

            videoPlayer.setVolume(0.12f);
            state = State.PLAYING_VIDEO;
        } catch (Throwable e) {
            Gdx.app.error("EndGameScreen", "Error al inicializar video: " + e.getMessage(), e);
            videoFailed = true;
            returnToMenu();
        }
    }

    private FileHandle resolveVideoFile() {
        String[] candidates = {
            "video/Video final del juego.webm",
            "Video final del juego.webm",
            "assets/video/Video final del juego.webm",
            "assets/Video final del juego.webm",
            "video/Video final del juego.mp4",
            "Video final del juego.mp4",
            "assets/video/Video final del juego.mp4",
            "assets/Video final del juego.mp4"
        };
        for (String c : candidates) {
            FileHandle fh = Gdx.files.internal(c);
            if (fh != null && fh.exists()) return fh;
            fh = Gdx.files.local(c);
            if (fh != null && fh.exists()) return fh;
        }
        return null;
    }

    private void returnToMenu() {
        dispose();
        game.returnToMenuFromWorld(null);
    }

    @Override
    public void dispose() {
        if (netManager != null) {
            netManager.setListener(null);
            final NetworkManager nm = netManager;
            new Thread(() -> {
                try {
                    nm.disconnect();
                } catch (Throwable ignored) {}
            }).start();
        }

        if (videoPlayer != null) {
            try {
                videoPlayer.dispose();
            } catch (Throwable ignored) {}
            videoPlayer = null;
        }

        if (blackPixel != null) {
            blackPixel.dispose();
            blackPixel = null;
        }

        if (font != null) {
            font.dispose();
            font = null;
        }

        if (batch != null) {
            batch.dispose();
        }
    }
}
