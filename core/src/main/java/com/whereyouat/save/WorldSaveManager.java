package com.whereyouat.save;

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

import com.whereyouat.entity.Entity;
import com.whereyouat.entity.Player;
import com.whereyouat.items.Item;
import com.whereyouat.lighting.DayNightCycle;

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

        // datos de inventario
        public boolean hasInventoryData = false;
        public List<String> inventoryItems = new ArrayList<>();

        //estructura para guardar mounstros
        public static class SavedMonster{
            public String id;
            public float x, y;
            public int life;

            public SavedMonster(String id, float x, float y, int life){
                this.id = id;
                this.x = x;
                this.y = y;
                this.life = life;
            }
        }

        public boolean hasMonsterData = false;
        public List<SavedMonster> savedMonsters = new ArrayList<>();

        @Override
        public int compareTo(WorldInfo other) {
            // Sort newest / most recently played first
            long timeThis = this.lastPlayed > 0 ? this.lastPlayed : this.created;
            long timeOther = other.lastPlayed > 0 ? other.lastPlayed : other.created;
            return Long.compare(timeOther, timeThis);
        }
    }

    /** Returns sorted list (most recently played first) of all saved worlds. */
    public static List<WorldInfo> listWorlds() {
        List<WorldInfo> worlds = new ArrayList<>();
        File dir = new File(SAVES_DIR);
        if (!dir.exists()) return worlds;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".properties"));
        if (files == null) return worlds;

        for (File file : files) {
            WorldInfo info = loadInfo(file);
            if (info != null) worlds.add(info);
        }
        Collections.sort(worlds);
        return worlds;
    }

    /** Loads a world by its filename (without extension). */
    public static WorldInfo loadWorld(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;
        File file = new File(SAVES_DIR, fileName + ".properties");
        if (!file.exists()) return null;
        return loadInfo(file);
    }

    private static WorldInfo loadInfo(File file) {
        
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties properties = new Properties();
            properties.load(fis);
            WorldInfo info   = new WorldInfo();
            info.name        = properties.getProperty("world.name",       "Unknown");
            info.seed        = Long.parseLong(properties.getProperty("world.seed",       "12345"));
            info.created     = Long.parseLong(properties.getProperty("world.created",    "0"));
            info.lastPlayed  = Long.parseLong(properties.getProperty("world.lastPlayed", "0"));
            String fileName  = file.getName();
            info.fileName    = fileName.substring(0, fileName.lastIndexOf('.'));


            // Load player data if present
            if (properties.containsKey("player.worldX") && properties.containsKey("player.worldY")) {
                try {
                    info.playerWorldX    = Float.parseFloat(properties.getProperty("player.worldX"));
                    info.playerWorldY    = Float.parseFloat(properties.getProperty("player.worldY"));
                    info.playerDirection = properties.getProperty("player.direction", "down");
                    info.playerHealth    = Integer.parseInt(properties.getProperty("player.health", "100"));
                    info.playerLives     = Integer.parseInt(properties.getProperty("player.lives", "3"));
                    info.isDead          = Boolean.parseBoolean(properties.getProperty("player.dead", "false"));
                    if (info.playerLives <= 0) {
                        info.isDead = true;
                    }
                    info.hasPlayerData   = true;
                } catch (Exception ex) {
                    info.hasPlayerData = false;
                }
            } else if (properties.containsKey("player.dead")) {
                info.isDead = Boolean.parseBoolean(properties.getProperty("player.dead", "false"));
            }

            // Load DayNightCycle data if present
            if (properties.containsKey("daynight.ticks")) {
                try {
                    info.dayNightTicks   = Long.parseLong(properties.getProperty("daynight.ticks"));
                    info.hasDayNightData = true;
                } catch (Exception ex) {
                    info.hasDayNightData = false;
                }
            }

            //cargar inventario si existe
        if (properties.containsKey("player.inventory")){
            info.hasInventoryData = true;
            info.inventoryItems = new ArrayList<>();
            String invStr = properties.getProperty("player.inventory");

            if (!invStr.trim().isEmpty()){
                for (String itemId : invStr.split(",")){
                    String trimmed = itemId.trim();
                    if (!trimmed.isEmpty()) info.inventoryItems.add(trimmed);
                }
            }
        }

        //cargar mounstros si existen
        if (properties.containsKey("monster.count")){
            try {
                int count = Integer.parseInt(properties.getProperty("monster.count" , "0"));
                info.hasMonsterData = true;
                info.savedMonsters = new ArrayList<>();
                for (int i = 0; i < count; i++){
                    String id = properties.getProperty("monster." + i + ".id");
                    float mx = Float.parseFloat(properties.getProperty("monster." + i + ".x", "0"));
                    float my = Float.parseFloat(properties.getProperty("monster." + i + ".y", "0"));
                    int mLife = Integer.parseInt(properties.getProperty("monster." + i + ".life", "1"));

                    if (id != null && !id.isEmpty()){
                        info.savedMonsters.add(new WorldInfo.SavedMonster(id, mx, my, mLife));
                    }
                }
            } catch (Exception ignored) {
                info.hasMonsterData = false;
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

        Properties properties = new Properties();
        properties.setProperty("world.name",       name);
        properties.setProperty("world.seed",       String.valueOf(seed));
        properties.setProperty("world.created",    String.valueOf(now));
        properties.setProperty("world.lastPlayed", String.valueOf(now));

        try (FileOutputStream fos = new FileOutputStream(file)) {
            properties.store(fos, "WhereYouAt - World Save");
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
                                        Player player, DayNightCycle dayNight, Entity[] monsters) {
        if (fileName == null || fileName.trim().isEmpty()) return;
        File dir = new File(SAVES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName + ".properties");

        Properties properties = new Properties();
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (Exception ignored) {}
        }

        properties.setProperty("world.name", worldName);
        properties.setProperty("world.seed", String.valueOf(seed));
        if (!properties.containsKey("world.created")) {
            properties.setProperty("world.created", String.valueOf(created > 0 ? created : System.currentTimeMillis()));
        }
        properties.setProperty("world.lastPlayed", String.valueOf(System.currentTimeMillis()));

        //guardar jugador e inventario

        if (player != null) {
            properties.setProperty("player.worldX", String.valueOf(player.worldX));
            properties.setProperty("player.worldY", String.valueOf(player.worldY));
            properties.setProperty("player.direction", player.direction != null ? player.direction : "down");
            properties.setProperty("player.health", String.valueOf(player.health));
            properties.setProperty("player.lives", String.valueOf(player.lives));
            properties.setProperty("player.dead", String.valueOf(player.dead));

            if (player.inventory != null){
                List<String> ids = new ArrayList<>();
                for (Item item : player.inventory){
                    if (item != null) ids.add(item.getClass().getSimpleName());
                }
                properties.setProperty("player.inventory", String.join(",", ids));
            }
        }

        if (monsters != null) {
            // Limpiar claves viejas por si antes había más monstruos
            int oldCount = 0;
            if (properties.containsKey("monster.count")) {
                try { oldCount = Integer.parseInt(properties.getProperty("monster.count", "0")); } catch (Exception ignored) {}
            }
            for (int i = 0; i < oldCount + 20; i++) {
                properties.remove("monster." + i + ".id");
                properties.remove("monster." + i + ".x");
                properties.remove("monster." + i + ".y");
                properties.remove("monster." + i + ".life");
            }
            int savedCount = 0;
            for (int i = 0; i < monsters.length; i++) {
                Entity m = monsters[i];
                if (m != null && m.life > 0) {
                    properties.setProperty("monster." + savedCount + ".id", m.getClass().getSimpleName());
                    properties.setProperty("monster." + savedCount + ".x", String.valueOf(m.worldX));
                    properties.setProperty("monster." + savedCount + ".y", String.valueOf(m.worldY));
                    properties.setProperty("monster." + savedCount + ".life", String.valueOf(m.life));
                    savedCount++;
                }
            }
            properties.setProperty("monster.count", String.valueOf(savedCount));
        }

        if (dayNight != null) {
            properties.setProperty("daynight.ticks", String.valueOf(dayNight.getTimeTicks()));
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            properties.store(fos, "WhereYouAt - World Save");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes a saved world by its fileName (without extension).
     */
    public static void deleteWorld(String fileName) {
        File file = new File(SAVES_DIR, fileName + ".properties");
        if (file.exists() && !file.delete()) {
            System.err.println("WorldSaveManager: could not delete " + file.getAbsolutePath());
        }
    }

    /** Formats a timestamp nicely for display. */
    public static String formatDate(long millis) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm");
        return dateFormat.format(new Date(millis));
    }
}
