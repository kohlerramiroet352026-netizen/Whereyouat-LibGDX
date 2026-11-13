package com.whereyouat.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.whereyouat.lighting.DayNightCycle;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AudioManager {
    private final Random rand = new Random();

    public float masterVolume = 1.0f;
    public float musicVolume  = 0.12f;
    public float sfxVolume    = 1.0f;

    private Sound grassStep;
    private Sound sandStep;
    private Sound snowStep;
    private Sound dirtStep;
    private Sound waterStep;

    private Sound bushRustle;
    private Sound buttonClick;
    private Sound owlSound;
    private Sound birdShortChirp;

    private Music birdAmbience;
    private Music nightAmbience;
    private Music cricketsAmbience;

    private final List<String> playlist = new ArrayList<>();
    private final Map<String, Float> trackLengths = new HashMap<>();
    private int currentTrackIndex = 0;
    private Music currentMusic = null;
    private String currentTrackPath = null;
    private float trackPlayTime = 0f;
    private float currentTrackDuration = 200f;

    private static final float FADE_IN_DURATION = 3.1f;
    private static final float FADE_OUT_DURATION = 4.1f;
    private boolean isFadingOut = false;
    private float manualFadeOutTimer = 0f;

    private float musicPauseTimer = 10f;
    private boolean isPausedBetweenTracks = true;

    private float ambientSoundTimer = 20f;

    public AudioManager() {
        loadSounds();
        setupPlaylist();
    }

    private Sound safeLoadSound(String path) {
        if (Gdx.files.internal(path).exists()) {
            return Gdx.audio.newSound(Gdx.files.internal(path));
        }
        return null;
    }

    private Music safeLoadMusic(String path) {
        if (Gdx.files.internal(path).exists()) {
            return Gdx.audio.newMusic(Gdx.files.internal(path));
        }
        return null;
    }

    private void loadSounds() {
        grassStep      = safeLoadSound("sounds/walk_grass2.mp3");
        sandStep       = safeLoadSound("sounds/walk_sand1.mp3");
        if (sandStep == null) sandStep = safeLoadSound("sounds/sand_walk1.mp3");
        snowStep       = safeLoadSound("sounds/walk_snow.mp3");
        dirtStep       = safeLoadSound("sounds/walk_dirt.ogg");
        waterStep      = safeLoadSound("sounds/walk_water.mp3");

        bushRustle     = safeLoadSound("sounds/through_bush1.mp3");
        buttonClick    = safeLoadSound("sounds/button_sound.mp3");
        owlSound       = safeLoadSound("sounds/owl1.mp3");
        birdShortChirp = safeLoadSound("sounds/birds_chipping2.mp3");

        birdAmbience     = safeLoadMusic("sounds/birds_chipping1.mp3");
        nightAmbience    = safeLoadMusic("sounds/night_ambience1.mp3");
        cricketsAmbience = safeLoadMusic("sounds/crickets1.mp3");
    }

    private void setupPlaylist() {
        String[] tracks = {
            "ost/aviva.mp3",
            "ost/TYWM.mp3",
            "ost/hope.mp3",
            "ost/farlands.mp3",
            "ost/modulo.mp3"
        };
        float[] lengths = { 215f, 308f, 211f, 287f, 272f };

        for (int i = 0; i < tracks.length; i++) {
            if (Gdx.files.internal(tracks[i]).exists()) {
                playlist.add(tracks[i]);
                trackLengths.put(tracks[i], lengths[i]);
            }
        }
        Collections.shuffle(playlist, rand);
    }

    public void update(float delta, DayNightCycle cycle) {
        updateMusic(delta);
        updateAmbience(delta, cycle);
    }

    private void updateMusic(float delta) {
        if (playlist.isEmpty()) return;

        if (currentMusic != null && currentMusic.isPlaying()) {
            trackPlayTime += delta;

            float fadeMultiplier = 1.0f;
            if (trackPlayTime < FADE_IN_DURATION) {
                fadeMultiplier = Math.max(0.01f, trackPlayTime / FADE_IN_DURATION);
            } else if (trackPlayTime > (currentTrackDuration - FADE_OUT_DURATION)) {
                float remaining = Math.max(0f, currentTrackDuration - trackPlayTime);
                fadeMultiplier = Math.max(0.01f, remaining / FADE_OUT_DURATION);
            }

            if (isFadingOut) {
                manualFadeOutTimer -= delta;
                if (manualFadeOutTimer <= 0f) {
                    stopMusic();
                    resetSilenceTimer();
                    return;
                }
                fadeMultiplier = Math.min(fadeMultiplier, manualFadeOutTimer / 2.5f);
            }

            currentMusic.setVolume(musicVolume * masterVolume * fadeMultiplier);

            if (trackPlayTime >= currentTrackDuration) {
                currentMusic.stop();
                resetSilenceTimer();
            }
            return;
        }

        if (isPausedBetweenTracks) {
            musicPauseTimer -= delta;
            if (musicPauseTimer <= 0f) {
                isPausedBetweenTracks = false;
                playNextTrack();
            }
        } else {
            resetSilenceTimer();
        }
    }

    private void resetSilenceTimer() {
        isPausedBetweenTracks = true;

        musicPauseTimer = 300f + rand.nextFloat() * 120f;
        isFadingOut = false;
        if (currentMusic != null) {
            currentMusic.dispose();
            currentMusic = null;
        }
    }

    private void playNextTrack() {
        if (playlist.isEmpty()) return;
        if (currentTrackIndex >= playlist.size()) {
            currentTrackIndex = 0;
            Collections.shuffle(playlist, rand);
        }
        currentTrackPath = playlist.get(currentTrackIndex++);
        currentTrackDuration = trackLengths.getOrDefault(currentTrackPath, 220f);
        trackPlayTime = 0f;
        isFadingOut = false;

        currentMusic = safeLoadMusic(currentTrackPath);
        if (currentMusic != null) {
            currentMusic.setLooping(false);
            currentMusic.setVolume(0.001f);
            currentMusic.play();
        }
    }

    public void fadeOutAndStop(float duration) {
        if (currentMusic != null && currentMusic.isPlaying()) {
            isFadingOut = true;
            manualFadeOutTimer = duration;
        }
    }

    public Music releaseCurrentMusic() {
        Music releasedMusic = currentMusic;
        currentMusic = null;
        return releasedMusic;
    }
    private void updateAmbience(float delta, DayNightCycle cycle) {
        if (cycle == null) return;

        ambientSoundTimer -= delta;
        if (ambientSoundTimer <= 0f) {
            boolean isNight = cycle.isNight();
            if (isNight) {
                ambientSoundTimer = 15f + rand.nextFloat() * 20f;

                if (rand.nextBoolean() && owlSound != null) {
                    owlSound.play(0.12f * sfxVolume * masterVolume);
                } else if (nightAmbience != null && !nightAmbience.isPlaying()) {
                    nightAmbience.setVolume(0.08f * sfxVolume * masterVolume);
                    nightAmbience.play();
                }
            } else {
                ambientSoundTimer = 25f + rand.nextFloat() * 40f;

                if (rand.nextFloat() < 0.65f && birdShortChirp != null) {
                    float pitch = 0.95f + rand.nextFloat() * 0.1f;
                    birdShortChirp.play(0.28f * sfxVolume * masterVolume, pitch, 0f);
                } else if (birdAmbience != null && !birdAmbience.isPlaying()) {
                    birdAmbience.setVolume(0.10f * sfxVolume * masterVolume);
                    birdAmbience.play();
                }
            }
        }
    }

    public void playFootstep(MapGenerator map, TileManager tileM, int col, int row) {
        int waterLevel = tileM.getWaterLevel(map, col, row);
        Sound stepSound = grassStep;
        float volume = 0.048f;

        if (waterLevel > 0) {
            stepSound = waterStep;
            volume = 0.045f;
        } else if (col >= 0 && col < map.mapCols && row >= 0 && row < map.mapRows) {
            int groundTile = map.mapTileNum[col][row];

            if (groundTile == MapGenerator.TILE_SAND1 || groundTile == MapGenerator.TILE_SAND2 ||
                       groundTile == 24 || groundTile == 25 || groundTile == 26) {
                stepSound = sandStep;
                volume = 0.220f;
            } else if (groundTile == MapGenerator.TILE_DIRT1 || groundTile == MapGenerator.TILE_MUD) {
                stepSound = (dirtStep != null) ? dirtStep : grassStep;
                volume = 0.052f;
            } else if (map.isSwampBiome[col][row]) {
                stepSound = (dirtStep != null) ? dirtStep : grassStep;
                volume = 0.052f;
            } else if (map.isSnowBiome[col][row]) {
                stepSound = snowStep;
                volume = 0.044f;
            } else {
                stepSound = grassStep;
                volume = 0.048f;
            }
        }

        if (stepSound != null) {
            float pitch = 0.96f + rand.nextFloat() * 0.08f;
            stepSound.play(volume * sfxVolume * masterVolume, pitch, 0f);
        }
    }

    public void playBushRustle() {
        if (bushRustle != null) {
            float pitch = 0.94f + rand.nextFloat() * 0.12f;
            bushRustle.play(0.22f * sfxVolume * masterVolume, pitch, 0f);
        }
    }

    public void playButtonClick() {
        if (buttonClick != null) {
            buttonClick.play(0.32f * sfxVolume * masterVolume);
        }
    }

    public void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
        }
    }

    public void dispose() {
        stopMusic();
        if (currentMusic != null) currentMusic.dispose();
        if (birdAmbience != null) birdAmbience.dispose();
        if (nightAmbience != null) nightAmbience.dispose();
        if (cricketsAmbience != null) cricketsAmbience.dispose();

        Sound[] sounds = { grassStep, sandStep, snowStep, dirtStep, waterStep, bushRustle, buttonClick, owlSound, birdShortChirp };
        for (Sound sound : sounds) {
            if (sound != null) sound.dispose();
        }
    }
}
