package com.whereyouat.save;

import com.whereyouat.entity.Player;
import com.whereyouat.lighting.DayNightCycle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * WorldSaveManager for LibGDX.
 * Handles saving and loading world metadata, player position, health/lives,
 * and DayNightCycle simulation time.
 * Saves are stored as .properties files inside the "saves/" directory.
 */
public class WorldSaveManager {

    public static final String SAVES_DIR = "saves";

    public static class WorldInfo implements Comparable<WorldInfo> {
        public String name;
        public long   seed;
        public long   created;    // epoch millis
        public long   lastPlayed; // epoch millis
        public String fileName;   // without extension

        // Player state
        public boolean hasPlayerData = false;
        public float   playerWorldX;
        public float   playerWorldY;
        public String  playerDirection = "down";
        public int     playerHealth    = 100;
        public int     playerLives     = 3;
        public boolean isDead          = false;

        // Day/Night cycle state
        public boolean hasDayNightData = false;
        public long    dayNightTicks   = -1;

        @Override
        public int compareTo(WorldInfo other) {
            // Sort newest / most recently played first
            long tThis = this.lastPlayed > 0 ? this.lastPlayed : this.created;
            long tOther = other.lastPlayed > 0 ? other.lastPlayed : other.created;
            return Long.compare(tOther, tThis);
        }
    }

    /** Returns sorted list (most recently played first) of all saved worlds. */
    public static List<WorldInfo> listWorlds() {
        List<WorldInfo> worlds = new ArrayList<>();
        File dir = new File(SAVES_DIR);
        if (!dir.exists()) return worlds;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".properties"));
        if (files == null) return worlds;

        for (File f : files) {
            WorldInfo info = loadInfo(f);
            if (info != null) worlds.add(info);
        }
        Collections.sort(worlds);
        return worlds;
    }

    /** Loads a world by its filename (without extension). */
    public static WorldInfo loadWorld(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;
        File f = new File(SAVES_DIR, fileName + ".properties");
        if (!f.exists()) return null;
        return loadInfo(f);
    }

    private static WorldInfo loadInfo(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            Properties p = new Properties();
            p.load(fis);
            WorldInfo info   = new WorldInfo();
            info.name        = p.getProperty("world.name",       "Unknown");
            info.seed        = Long.parseLong(p.getProperty("world.seed",       "12345"));
            info.created     = Long.parseLong(p.getProperty("world.created",    "0"));
            info.lastPlayed  = Long.parseLong(p.getProperty("world.lastPlayed", "0"));
            String fname     = f.getName();
            info.fileName    = fname.substring(0, fname.lastIndexOf('.'));

            // Load player data if present
            if (p.containsKey("player.worldX") && p.containsKey("player.worldY")) {
                try {
                    info.playerWorldX    = Float.parseFloat(p.getProperty("player.worldX"));
                    info.playerWorldY    = Float.parseFloat(p.getProperty("player.worldY"));
                    info.playerDirection = p.getProperty("player.direction", "down");
                    info.playerHealth    = Integer.parseInt(p.getProperty("player.health", "100"));
                    info.playerLives     = Integer.parseInt(p.getProperty("player.lives", "3"));
                    info.isDead          = Boolean.parseBoolean(p.getProperty("player.dead", "false"));
                    if (info.playerLives <= 0) {
                        info.isDead = true;
                    }
                    info.hasPlayerData   = true;
                } catch (Exception ex) {
                    info.hasPlayerData = false;
                }
            } else if (p.containsKey("player.dead")) {
                info.isDead = Boolean.parseBoolean(p.getProperty("player.dead", "false"));
            }

            // Load DayNightCycle data if present
            if (p.containsKey("daynight.ticks")) {
                try {
                    info.dayNightTicks   = Long.parseLong(p.getProperty("daynight.ticks"));
                    info.hasDayNightData = true;
                } catch (Exception ex) {
                    info.hasDayNightData = false;
                }
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Saves a new world.
     * @param name Display name for the world.
     * @param seed The seed.
     * @return the WorldInfo that was saved, or null on failure.
     */
    public static WorldInfo saveWorld(String name, long seed) {
        File dir = new File(SAVES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        long now = System.currentTimeMillis();
        String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safeName.isEmpty()) safeName = "world";
        String fileName = safeName + "_" + now;
        File file = new File(dir, fileName + ".properties");

        Properties p = new Properties();
        p.setProperty("world.name",       name);
        p.setProperty("world.seed",       String.valueOf(seed));
        p.setProperty("world.created",    String.valueOf(now));
        p.setProperty("world.lastPlayed", String.valueOf(now));

        try (FileOutputStream fos = new FileOutputStream(file)) {
            p.store(fos, "WhereYouAt - World Save");
            WorldInfo info = new WorldInfo();
            info.name       = name;
            info.seed       = seed;
            info.created    = now;
            info.lastPlayed = now;
            info.fileName   = fileName;
            return info;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves live player position and day/night cycle progress to world save file.
     */
    public static void saveGameProgress(String fileName, String worldName, long seed, long created,
                                        Player player, DayNightCycle dayNight) {
        if (fileName == null || fileName.trim().isEmpty()) return;
        File dir = new File(SAVES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName + ".properties");

        Properties p = new Properties();
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                p.load(fis);
            } catch (Exception ignored) {}
        }

        p.setProperty("world.name", worldName);
        p.setProperty("world.seed", String.valueOf(seed));
        if (!p.containsKey("world.created")) {
            p.setProperty("world.created", String.valueOf(created > 0 ? created : System.currentTimeMillis()));
        }
        p.setProperty("world.lastPlayed", String.valueOf(System.currentTimeMillis()));

        if (player != null) {
            p.setProperty("player.worldX", String.valueOf(player.worldX));
            p.setProperty("player.worldY", String.valueOf(player.worldY));
            p.setProperty("player.direction", player.direction != null ? player.direction : "down");
            p.setProperty("player.health", String.valueOf(player.health));
            p.setProperty("player.lives", String.valueOf(player.lives));
            p.setProperty("player.dead", String.valueOf(player.dead));
        }

        if (dayNight != null) {
            p.setProperty("daynight.ticks", String.valueOf(dayNight.getTimeTicks()));
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            p.store(fos, "WhereYouAt - World Save");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes a saved world by its fileName (without extension).
     */
    public static void deleteWorld(String fileName) {
        File f = new File(SAVES_DIR, fileName + ".properties");
        if (f.exists() && !f.delete()) {
            System.err.println("WorldSaveManager: could not delete " + f.getAbsolutePath());
        }
    }

    /** Formats a timestamp nicely for display. */
    public static String formatDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm");
        return sdf.format(new Date(millis));
    }
}
