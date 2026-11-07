package com.whereyouat.video;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * MenuBackgroundPlayer for LibGDX.
 * Ported directly from original Java Swing MenuBackgroundPlayer.
 * Plays Motion-JPEG MP4 background video seamlessly across menu screens.
 */
public class MenuBackgroundPlayer implements Runnable {

    private RandomAccessFile raf;
    private int[] frameSizes;
    private long[] frameOffsets;
    private int totalFrames = 0;

    private byte[] pendingJpegData = null;
    private int pendingJpegSize = 0;
    private boolean hasNewFrame = false;

    private Texture videoTexture = null;
    private TextureRegion videoRegion = null;
    private Texture overlayTexture = null;

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private Thread workerThread = null;

    private byte[] readBuffer = new byte[768 * 1024];
    private long playbackStartTime = 0;
    private long pausedElapsedTime = 0;

    public MenuBackgroundPlayer() {
        locateAndInitialize();
    }

    private void locateAndInitialize() {
        File[] candidates = new File[] {
            new File("background.mp4"),
            new File("../background.mp4"),
            new File("../../background.mp4"),
            new File("assets/background.mp4"),
            new File("../assets/background.mp4"),
            new File("res/background.mp4"),
            new File("../res/background.mp4"),
            new File("../Whereyouat - LibGDX/background.mp4"),
            new File("Whereyouat - LibGDX/background.mp4"),
            new File("C:/Users/Noxie-PC/Desktop/Where you at - Versions archive/Whereyouat - LibGDX/background.mp4"),
            new File("C:/Users/Noxie-PC/Desktop/Where you at - Versions archive/Whereyouat - personal v0.3/background.mp4")
        };

        File found = null;
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile() && candidate.length() > 0) {
                found = candidate;
                break;
            }
        }

        if (found != null) {
            init(found);
        } else {
            Gdx.app.log("MenuBackgroundPlayer", "background.mp4 not found, fallback background will be used.");
        }
    }

    public synchronized boolean init(File file) {
        try {
            raf = new RandomAccessFile(file, "r");
            long length = raf.length();
            long pos = 0;
            long moovPos = -1;
            long moovSize = -1;

            while (pos < length) {
                raf.seek(pos);
                if (pos + 8 > length) break;
                long size = Integer.toUnsignedLong(raf.readInt());
                byte[] typeBytes = new byte[4];
                raf.readFully(typeBytes);
                String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
                long headerSize = 8;
                if (size == 1) {
                    size = raf.readLong();
                    headerSize = 16;
                } else if (size == 0) {
                    size = length - pos;
                }

                if (type.equals("moov")) {
                    moovPos = pos + headerSize;
                    moovSize = size - headerSize;
                    break;
                }
                pos += size;
                if (size <= 0) break;
            }

            if (moovPos < 0) {
                Gdx.app.error("MenuBackgroundPlayer", "moov atom not found in " + file.getName());
                return false;
            }

            findSampleTables(moovPos, moovSize);

            if (frameSizes == null || frameOffsets == null) {
                Gdx.app.error("MenuBackgroundPlayer", "sample tables not found in " + file.getName());
                return false;
            }

            totalFrames = Math.min(frameSizes.length, frameOffsets.length);
            Gdx.app.log("MenuBackgroundPlayer", "initialized " + file.getName() + " (" + totalFrames + " frames).");

            decodeFrame(0);
            return true;
        } catch (Exception e) {
            Gdx.app.error("MenuBackgroundPlayer", "failed to initialize video: " + e.getMessage());
            return false;
        }
    }

    private void findSampleTables(long start, long length) throws Exception {
        long end = start + length;
        long pos = start;

        while (pos < end) {
            raf.seek(pos);
            if (pos + 8 > end) break;
            long size = Integer.toUnsignedLong(raf.readInt());
            byte[] typeBytes = new byte[4];
            raf.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
            long headerSize = 8;
            if (size == 1) {
                size = raf.readLong();
                headerSize = 16;
            } else if (size == 0) {
                size = end - pos;
            }

            if (type.equals("trak") || type.equals("mdia") || type.equals("minf") || type.equals("stbl")) {
                findSampleTables(pos + headerSize, size - headerSize);
            } else if (type.equals("stsz")) {
                raf.seek(pos + headerSize + 4);
                int sampleSize = raf.readInt();
                int count = raf.readInt();
                frameSizes = new int[count];
                if (sampleSize == 0) {
                    for (int i = 0; i < count; i++) {
                        frameSizes[i] = raf.readInt();
                    }
                } else {
                    for (int i = 0; i < count; i++) {
                        frameSizes[i] = sampleSize;
                    }
                }
            } else if (type.equals("stco")) {
                raf.seek(pos + headerSize + 4);
                int count = raf.readInt();
                frameOffsets = new long[count];
                for (int i = 0; i < count; i++) {
                    frameOffsets[i] = Integer.toUnsignedLong(raf.readInt());
                }
            } else if (type.equals("co64")) {
                raf.seek(pos + headerSize + 4);
                int count = raf.readInt();
                frameOffsets = new long[count];
                for (int i = 0; i < count; i++) {
                    frameOffsets[i] = raf.readLong();
                }
            }
            pos += size;
            if (size <= 0) break;
        }
    }

    private void decodeFrame(int index) {
        if (index < 0 || index >= totalFrames || raf == null) return;
        try {
            long offset = frameOffsets[index];
            int size = frameSizes[index];
            if (size > readBuffer.length) {
                readBuffer = new byte[size + 65536];
            }
            synchronized (raf) {
                raf.seek(offset);
                raf.readFully(readBuffer, 0, size);
            }
            synchronized (this) {
                if (pendingJpegData == null || pendingJpegData.length < size) {
                    pendingJpegData = new byte[size + 65536];
                }
                System.arraycopy(readBuffer, 0, pendingJpegData, 0, size);
                pendingJpegSize = size;
                hasNewFrame = true;
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void start() {
        if (isRunning || totalFrames <= 0) return;
        isRunning = true;
        isPaused = false;
        playbackStartTime = System.currentTimeMillis();
        workerThread = new Thread(this, "Menu-Background-Video-Player");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public synchronized void pause() {
        if (!isPaused && isRunning) {
            isPaused = true;
            pausedElapsedTime = System.currentTimeMillis() - playbackStartTime;
        }
    }

    public synchronized void resume() {
        if (isPaused && isRunning) {
            isPaused = false;
            playbackStartTime = System.currentTimeMillis() - pausedElapsedTime;
            notifyAll();
        }
    }

    public synchronized void stop() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        try {
            if (raf != null) {
                raf.close();
                raf = null;
            }
        } catch (Exception ignored) {}
    }

    /**
     * Renders video frame center-cropped across screen and applies a soft transparent black tint (120 alpha).
     * Must be called on LibGDX render thread while batch is begun.
     */
    public boolean draw(SpriteBatch batch, float screenWidth, float screenHeight, float tintAlpha) {
        // Upload new frame if ready
        byte[] dataToUpload = null;
        int sizeToUpload = 0;
        synchronized (this) {
            if (hasNewFrame && pendingJpegData != null && pendingJpegSize > 0) {
                dataToUpload = new byte[pendingJpegSize];
                System.arraycopy(pendingJpegData, 0, dataToUpload, 0, pendingJpegSize);
                sizeToUpload = pendingJpegSize;
                hasNewFrame = false;
            }
        }

        if (dataToUpload != null) {
            Pixmap framePixmap = null;
            try {
                framePixmap = new Pixmap(dataToUpload, 0, sizeToUpload);
            } catch (Throwable t) {
                // Fallback to ImageIO if stb_image doesn't recognize header
                try {
                    java.awt.image.BufferedImage img = ImageIO.read(new ByteArrayInputStream(dataToUpload, 0, sizeToUpload));
                    if (img != null) {
                        int imageWidth = img.getWidth();
                        int imageHeight = img.getHeight();
                        framePixmap = new Pixmap(imageWidth, imageHeight, Pixmap.Format.RGBA8888);
                        int[] rgb = img.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
                        for (int y = 0; y < imageHeight; y++) {
                            int rowOff = y * imageWidth;
                            for (int x = 0; x < imageWidth; x++) {
                                int pixelColor = rgb[rowOff + x];
                                int red = (pixelColor >> 16) & 0xff;
                                int green = (pixelColor >> 8) & 0xff;
                                int blue = pixelColor & 0xff;
                                framePixmap.drawPixel(x, y, (red << 24) | (green << 16) | (blue << 8) | 0xff);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (framePixmap != null) {
                try {
                    if (videoTexture == null || videoTexture.getWidth() != framePixmap.getWidth() || videoTexture.getHeight() != framePixmap.getHeight()) {
                        if (videoTexture != null) videoTexture.dispose();
                        videoTexture = new Texture(framePixmap);
                        videoTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                        videoRegion = new TextureRegion(videoTexture);
                        videoRegion.flip(false, true);
                    } else {
                        videoTexture.draw(framePixmap, 0, 0);
                    }
                } catch (Throwable ignored) {
                } finally {
                    framePixmap.dispose();
                }
            }
        }

        if (videoRegion == null) return false;

        float frameWidth = videoRegion.getRegionWidth();
        float frameHeight = videoRegion.getRegionHeight();
        if (frameWidth <= 0 || frameHeight <= 0) return false;

        float screenAspect = screenWidth / screenHeight;
        float videoAspect = frameWidth / frameHeight;
        float drawX = 0f, drawY = 0f, drawWidth = screenWidth, drawHeight = screenHeight;

        if (screenAspect > videoAspect) {
            drawWidth = screenWidth;
            drawHeight = screenWidth / videoAspect;
            drawY = (screenHeight - drawHeight) / 2f;
        } else {
            drawHeight = screenHeight;
            drawWidth = screenHeight * videoAspect;
            drawX = (screenWidth - drawWidth) / 2f;
        }

        // Draw video frame
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(videoRegion, drawX, drawY, drawWidth, drawHeight);

        // Soft transparent black tint overlay (alpha 120/255 = ~0.47f)
        if (overlayTexture == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.drawPixel(0, 0, Color.rgba8888(1f, 1f, 1f, 1f));
            overlayTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        batch.setColor(0f, 0f, 0f, Math.min(1f, Math.max(0f, tintAlpha)));
        batch.draw(overlayTexture, 0f, 0f, screenWidth, screenHeight);
        batch.setColor(1f, 1f, 1f, 1f);

        return true;
    }

    @Override
    public void run() {
        int lastDecodedIndex = -1;
        while (isRunning) {
            synchronized (this) {
                while (isPaused && isRunning) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }

            if (!isRunning) break;

            long elapsed = System.currentTimeMillis() - playbackStartTime;
            int targetFrame = (int) ((elapsed * 60 / 1000) % totalFrames);

            if (targetFrame != lastDecodedIndex) {
                decodeFrame(targetFrame);
                lastDecodedIndex = targetFrame;
            }

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public void dispose() {
        stop();
        if (videoTexture != null) {
            videoTexture.dispose();
            videoTexture = null;
        }
        if (overlayTexture != null) {
            overlayTexture.dispose();
            overlayTexture = null;
        }
    }
}
