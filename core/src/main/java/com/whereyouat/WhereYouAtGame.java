package com.whereyouat;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.io.File;
import com.whereyouat.screens.GameScreen;
import com.whereyouat.map.TileManager;
import com.whereyouat.map.MapGenerator;

/**
 * Main game entry point for Where you at (LibGDX Edition).
 * Manages game screens, global shared SpriteBatch, and lifecycle events.
 *
 * menuMusic is shared across all menu screens (TitleScreen, WorldSelectScreen,
 * WorldCreateScreen) and fades out only when entering a world — matching the
 * original game behavior.
 */
public class WhereYouAtGame extends Game {

    public SpriteBatch batch;

    public static final String[] MENU_OST_TRACKS = {
        "ost/aviva.mp3", "ost/farlands.mp3", "ost/hope.mp3", "ost/modulo.mp3", "ost/TYWM.mp3"
    };

    /**
     * Shared menu music that persists across menu screens.
     * Owned here so all screens can access it without stopping/restarting it between menu transitions.
     */
    public Music menuMusic;
    private boolean menuMusicFading = false;
    private float menuMusicFadeTimer = 0f;
    private static final float MENU_FADE_DURATION = 2.5f;

    // Transition from world back to menu
    private Music outgoingWorldMusic = null;
    private float worldMusicFadeTimer = 0f;
    private static final float WORLD_FADE_OUT_DURATION = 1.5f;

    private float menuMusicDelayTimer = 0f;
    private boolean menuMusicFadeIn = false;
    private float menuMusicFadeInTimer = 0f;
    private static final float MENU_FADE_IN_DURATION = 3.0f;
    public boolean isIntroActive = false;

    private int autoFrame = 0;
    private boolean autoCapture = false;

    @Override
    public void create() {
        batch = new SpriteBatch();
        autoCapture = (System.getProperty("autoScreenshot") != null);
        Gdx.app.log("WhereYouAtGame", "LibGDX Game initialized successfully.");

        if (autoCapture) {
            setScreen(new com.whereyouat.screens.TitleScreen(this));
        } else {
            isIntroActive = true;
            setScreen(new com.whereyouat.screens.IntroScreen(this));
        }
    }

    /** Starts a fade-out of the shared menu music (call before entering a world). */
    public void fadeOutMenuMusic() {
        if (menuMusic != null && menuMusic.isPlaying()) {
            menuMusicFading = true;
            menuMusicFadeTimer = 0f;
        }
        menuMusicDelayTimer = 0f;
        menuMusicFadeIn = false;
    }

    /** Stops and disposes the shared menu music immediately. */
    public void stopMenuMusic() {
        if (menuMusic != null) {
            menuMusic.stop();
            menuMusic.dispose();
            menuMusic = null;
        }
        menuMusicFading = false;
        menuMusicFadeTimer = 0f;
        menuMusicDelayTimer = 0f;
        menuMusicFadeIn = false;
    }

    /**
     * Called when exiting the world to return to the title menu.
     * Takes ownership of active world music to fade it out, waits ~5 seconds of silence,
     * and then begins fading in the menu music.
     */
    public void returnToMenuFromWorld(Music activeWorldMusic) {
        stopMenuMusic();

        if (outgoingWorldMusic != null) {
            outgoingWorldMusic.stop();
            outgoingWorldMusic.dispose();
        }
        outgoingWorldMusic = activeWorldMusic;
        worldMusicFadeTimer = 0f;

        menuMusicDelayTimer = 5.0f;
        menuMusicFadeIn = false;
        menuMusicFadeInTimer = 0f;

        setScreen(new com.whereyouat.screens.TitleScreen(this));
    }

    public boolean isMenuMusicScheduled() {
        return menuMusicDelayTimer > 0f || menuMusicFadeIn;
    }

    public void playMenuMusicImmediately() {
        if (menuMusic == null && !isMenuMusicScheduled()) {
            startMenuMusic(false);
        }
    }

    public void startMenuMusic(boolean fadeIn) {
        try {
            java.util.Random random = new java.util.Random();
            int trackIndex = random.nextInt(MENU_OST_TRACKS.length);
            if (Gdx.files.internal(MENU_OST_TRACKS[trackIndex]).exists()) {
                menuMusic = Gdx.audio.newMusic(Gdx.files.internal(MENU_OST_TRACKS[trackIndex]));
                menuMusic.setLooping(false);
                if (fadeIn) {
                    menuMusic.setVolume(0.001f);
                    menuMusicFadeIn = true;
                    menuMusicFadeInTimer = 0f;
                } else {
                    menuMusic.setVolume(0.12f);
                    menuMusicFadeIn = false;
                }
                menuMusic.play();
            }
        } catch (Exception e) {
            Gdx.app.log("WhereYouAtGame", "Could not start menu music: " + e.getMessage());
        }
    }

    public void render() {
        // Clear screen with dark background
        if (!isIntroActive) {
            Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        super.render();

        float deltaTime = Gdx.graphics.getDeltaTime();

        // 1. Fade out outgoing world music
        if (outgoingWorldMusic != null) {
            worldMusicFadeTimer += deltaTime;
            float volume = Math.max(0f, 0.12f * (1f - worldMusicFadeTimer / WORLD_FADE_OUT_DURATION));
            outgoingWorldMusic.setVolume(volume);
            if (worldMusicFadeTimer >= WORLD_FADE_OUT_DURATION) {
                outgoingWorldMusic.stop();
                outgoingWorldMusic.dispose();
                outgoingWorldMusic = null;
            }
        }

        // 2. Countdown 5-second silence delay before starting menu music
        if (menuMusicDelayTimer > 0f) {
            menuMusicDelayTimer -= deltaTime;
            if (menuMusicDelayTimer <= 0f) {
                menuMusicDelayTimer = 0f;
                if (!(getScreen() instanceof com.whereyouat.screens.GameScreen)) {
                    startMenuMusic(true);
                }
            }
        }

        // 3. Fade in menu music
        if (menuMusicFadeIn && menuMusic != null) {
            menuMusicFadeInTimer += deltaTime;
            float fadeInVolume = Math.min(0.12f, 0.12f * (menuMusicFadeInTimer / MENU_FADE_IN_DURATION));
            menuMusic.setVolume(fadeInVolume);
            if (menuMusicFadeInTimer >= MENU_FADE_IN_DURATION) {
                menuMusic.setVolume(0.12f);
                menuMusicFadeIn = false;
            }
        }

        // 4. Update menu music fade-out (when entering world)
        if (menuMusicFading && menuMusic != null) {
            menuMusicFadeTimer += deltaTime;
            float volume = Math.max(0f, 0.12f * (1f - menuMusicFadeTimer / MENU_FADE_DURATION));
            menuMusic.setVolume(volume);
            if (menuMusicFadeTimer >= MENU_FADE_DURATION) {
                stopMenuMusic();
            }
        }

        // F12 Full Map Screenshot (renders entire world at 1:1 tile scale)
        if (Gdx.input.isKeyJustPressed(Keys.F12)) {
            if (getScreen() instanceof GameScreen) {
                takeFullMapScreenshot((GameScreen) getScreen());
            } else {
                takeScreenshot("screenshots/screenshot_" + System.currentTimeMillis() + ".png");
            }
        }

        // F11 Toggle Fullscreen
        if (Gdx.input.isKeyJustPressed(Keys.F11)) {
            if (Gdx.graphics.isFullscreen()) {
                Gdx.graphics.setWindowedMode(768, 576);
            } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
        }

        // Automated screenshot sequence for testing & verification
        if (autoCapture) {
            autoFrame++;
            if (autoFrame == 20) {
                takeScreenshot("screenshots/shot_title.png");
                setScreen(new com.whereyouat.screens.WorldSelectScreen(this));
            } else if (autoFrame == 50) {
                takeScreenshot("screenshots/shot_select.png");
                setScreen(new com.whereyouat.screens.WorldCreateScreen(this));
            } else if (autoFrame == 80) {
                takeScreenshot("screenshots/shot_create.png");
                setScreen(new com.whereyouat.screens.GameScreen(this, 987654321L, "Test World", null));
            } else if (autoFrame == 140) {
                takeScreenshot("screenshots/shot_game.png");
                Gdx.app.exit();
            }
        }
    }

    /**
     * Renders the entire world map to a PNG file, matching the original F12 behavior.
     * Renders in chunks to avoid exceeding GPU max texture size.
     * Final output: 512*16 = 8192 x 8192 pixels.
     */
    public void takeFullMapScreenshot(GameScreen gameScreen) {
        Gdx.app.log("WhereYouAtGame", "Generating full map screenshot, please wait...");
        try {
            MapGenerator map = gameScreen.mapGen;
            TileManager tileM = gameScreen.tileM;
            int tileSize = TileManager.TILE_SIZE; // 48 px (world coords)
            int origTileSize = TileManager.ORIGINAL_TILE_SIZE; // 16 px (1:1 output)
            int scale = tileSize / origTileSize; // 3
            int outputWidth = map.mapCols * origTileSize; // 512 * 16 = 8192
            int outputHeight = map.mapRows * origTileSize; // 512 * 16 = 8192

            // Safe, universally supported FBO chunk size (1024x1024)
            int fboSize = 1024;
            int chunkWidth = fboSize * scale; // 1024 * 3 = 3072 world px per chunk
            int chunkHeight = fboSize * scale; // 3072 world px
            int worldWidth = map.mapCols * tileSize; // 24576 px
            int worldHeight = map.mapRows * tileSize; // 24576 px

            // Final output pixmap at authentic 1:1 tile scale (8192 x 8192)
            Pixmap fullMap = new Pixmap(outputWidth, outputHeight, Pixmap.Format.RGBA8888);
            fullMap.setColor(0.04f, 0.04f, 0.06f, 1f);
            fullMap.fill();

            FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, fboSize, fboSize, false);
            OrthographicCamera mapCam = new OrthographicCamera();

            for (int currentY = 0; currentY < worldHeight; currentY += chunkHeight) {
                for (int currentX = 0; currentX < worldWidth; currentX += chunkWidth) {
                    int currentChunkWidth = Math.min(chunkWidth, worldWidth - currentX);
                    int currentChunkHeight = Math.min(chunkHeight, worldHeight - currentY);
                    int currentFboWidth = currentChunkWidth / scale;
                    int currentFboHeight = currentChunkHeight / scale;

                    fbo.begin();
                    Gdx.gl.glViewport(0, 0, currentFboWidth, currentFboHeight);
                    Gdx.gl.glClearColor(0.04f, 0.04f, 0.06f, 1f);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

                    // Camera covers chunk world bounds, rendered into FBO
                    mapCam.setToOrtho(true, currentChunkWidth, currentChunkHeight);
                    mapCam.position.set(currentX + currentChunkWidth / 2f, currentY + currentChunkHeight / 2f, 0);
                    mapCam.update();

                    batch.setProjectionMatrix(mapCam.combined);
                    batch.begin();

                    float minX = currentX;
                    float minY = currentY;
                    float maxX = currentX + currentChunkWidth;
                    float maxY = currentY + currentChunkHeight;

                    // Pass 1: Ground tiles, cliff depth borders, foam, and biome transitions
                    tileM.drawGround(batch, map, minX, minY, maxX, maxY);

                    // Pass 2: All trees, pines, palms, willows, mangroves, rocks, cattails, and lilipads
                    tileM.drawAllDecorations(batch, map, minX, minY, maxX, maxY);

                    batch.end();

                    // Read FBO pixels into chunk pixmap
                    // glReadPixels returns pixels bottom-up; flip Y to get top-down map orientation
                    Pixmap chunk = Pixmap.createFromFrameBuffer(0, 0, currentFboWidth, currentFboHeight);
                    fbo.end();

                    int destX = currentX / scale;
                    int destY = currentY / scale;
                    for (int y = 0; y < currentFboHeight; y++) {
                        // srcY: flip Y because OpenGL framebuffer is bottom-up
                        int srcY = currentFboHeight - 1 - y;
                        for (int x = 0; x < currentFboWidth; x++) {
                            int pixel = chunk.getPixel(x, srcY);
                            fullMap.drawPixel(destX + x, destY + y, pixel);
                        }
                    }
                    chunk.dispose();
                }
            }
            fbo.dispose();

            // Save output to screenshots folder
            String path = "screenshots/full_map_" + System.currentTimeMillis() + ".png";
            File outputFile = new File(path);
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            PixmapIO.writePNG(Gdx.files.local(path), fullMap, 5, true);
            fullMap.dispose();

            Gdx.app.log("WhereYouAtGame", "Full map screenshot saved: " + path + " (" + outputWidth + "x" + outputHeight + ")");
        } catch (Exception e) {
            Gdx.app.error("WhereYouAtGame", "Failed to capture full map screenshot", e);
        }
    }

    public void takeScreenshot(String relativePath) {
        try {
            int width = Gdx.graphics.getWidth();
            int height = Gdx.graphics.getHeight();
            Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
            File screenshotFile = new File(relativePath);
            File parent = screenshotFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            PixmapIO.writePNG(Gdx.files.local(relativePath), pixmap, 5, true);
            pixmap.dispose();
            Gdx.app.log("WhereYouAtGame", "Screenshot saved: " + relativePath);
        } catch (Exception e) {
            Gdx.app.error("WhereYouAtGame", "Failed to capture screenshot", e);
        }
    }

    @Override
    public void dispose() {
        stopMenuMusic();
        if (outgoingWorldMusic != null) { outgoingWorldMusic.stop(); outgoingWorldMusic.dispose(); outgoingWorldMusic = null; }
        if (batch != null) {
            batch.dispose();
        }
        super.dispose();
    }
}
