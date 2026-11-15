package com.whereyouat.items;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.whereyouat.entity.Entity;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_snowGhost;
import com.whereyouat.monster.MON_zombie;
import com.whereyouat.screens.GameScreen;

public class DropManager {
    private static final float CHANCE_SUMMON = 0.1625f;
    private static final float CHANCE_SPEED_POTION = 0.35f;
    private static final float CHANCE_POTION = 0.55f;

    private static final Random random = new Random();

    public static void processMonsterDrop(Entity monster, GameScreen gp) {
        if (gp == null || gp.player == null || gp.mapGen == null) {
            return;
        }

        int tileCol = (int) (monster.worldX / TileManager.TILE_SIZE);
        int tileRow = (int) (monster.worldY / TileManager.TILE_SIZE);

        if (tileCol < 0 || tileCol >= gp.mapGen.mapCols || tileRow < 0 || tileRow >= gp.mapGen.mapRows) {
            return;
        }

        Item dropItem = null;
        if (monster instanceof MON_snowGhost && random.nextFloat() < 0.20f) {
            dropItem = new FrostHelmet();
        } else if (monster instanceof MON_zombie && random.nextFloat() < 0.08f) {
            int pieceRoll = random.nextInt(3);
            if (pieceRoll == 0) {
                dropItem = new CopperHelmet();
            } else if (pieceRoll == 1) {
                dropItem = new CopperChestplate();
            } else {
                dropItem = new CopperLeggings();
            }
        } else {
            float roll = random.nextFloat();
            if (roll < CHANCE_SUMMON) {
                if (gp.mapGen.isSnowBiome[tileCol][tileRow]) {
                    dropItem = new IceGem();
                } else if (gp.mapGen.isSwampBiome[tileCol][tileRow]) {
                    dropItem = new FrogEggs();
                } else {
                    dropItem = new OddMushroom();
                }
            } else if (roll < CHANCE_SPEED_POTION) {
                dropItem = new SpeedPotion();
            } else if (roll < CHANCE_POTION) {
                dropItem = new ItemPotion();
            }
        }

        if (dropItem != null) {
            boolean added = gp.player.addItem(dropItem);
            if (added) {
                String message = "¡Obtuviste " + dropItem.name + "!";
                gp.showNotification(message);
                Gdx.app.log("DropManager", message + " de " + monster.getClass().getSimpleName());
            } else {
                gp.showNotification("¡Inventario lleno! No pudiste recoger " + dropItem.name);
                Gdx.app.log("DropManager", "Inventario lleno al intentar entregar drop: " + dropItem.name);
            }
        }
    }

    public static void deliverBossReward(String bossName, Item boatPiece, GameScreen gp) {
        processBossDrop(bossName, gp);
    }

    public static void processBossDrop(String bossName, GameScreen gp) {
        if (gp == null || gp.player == null) {
            return;
        }

        Item dropItem = null;
        float roll = random.nextFloat();

        if ("Rey Hongo".equals(bossName)) {
            gp.mushroomDefeated = true;
            if (roll < 0.25f) {
                dropItem = new BoatPiece1();
            } else if (roll < 0.45f) {
                dropItem = new mushroomblade();
            } else if (roll < 0.60f) {
                dropItem = new OddMushroom();
            }
        } else if ("Rey Frogger".equals(bossName)) {
            gp.froggerDefeated = true;
            if (roll < 0.30f) {
                dropItem = new BoatPiece2();
            } else if (roll < 0.55f) {
                dropItem = new FrogBellyShield();
            } else if (roll < 0.75f) {
                dropItem = new FrogEggs();
            }
        } else if ("Espectro de las Nieves".equals(bossName)) {
            gp.snowGhostDefeated = true;
            if (roll < 0.35f) {
                dropItem = new BoatPiece3();
            } else if (roll < 0.55f) {
                dropItem = new FrostHelmet();
            } else if (roll < 0.85f) {
                dropItem = new IceGem();
            }
        }

        if (dropItem != null) {
            boolean added = gp.player.addItem(dropItem);
            if (added) {
                String message = "¡" + bossName + " derrotado! Has obtenido: " + dropItem.name;
                gp.showNotification(message);
                Gdx.app.log("DropManager", message);
            } else {
                String fullMsg = "¡Inventario lleno! Libera espacio para recibir: " + dropItem.name;
                gp.showNotification(fullMsg);
                Gdx.app.log("DropManager", fullMsg);
            }
        } else {
            String message = "¡" + bossName + " derrotado!";
            gp.showNotification(message);
            Gdx.app.log("DropManager", message + " sin drop");
        }
    }
}
