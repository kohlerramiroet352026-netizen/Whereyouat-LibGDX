package com.whereyouat.items;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.whereyouat.entity.Entity;
import com.whereyouat.map.TileManager;
import com.whereyouat.screens.GameScreen;

public class DropManager {
    private static final float CHANCE_SUMMON = 0.1625f;
    private static final float CHANCE_POTION = 0.5125f;

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
        float roll = random.nextFloat();

        if (roll < CHANCE_SUMMON) {
            if (gp.mapGen.isSnowBiome[tileCol][tileRow]) {
                dropItem = new IceGem();
            } else if (gp.mapGen.isSwampBiome[tileCol][tileRow]) {
                dropItem = new FrogEggs();
            } else {
                dropItem = new OddMushroom();
            }
        } else if (roll < CHANCE_POTION) {
            dropItem = new ItemPotion();
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
        if (gp == null || gp.player == null || boatPiece == null) {
            return;
        }

        boolean added = gp.player.addItem(boatPiece);
        if (added) {
            String message = "¡" + bossName + " derrotado! Has obtenido: " + boatPiece.name;
            gp.showNotification(message);
            Gdx.app.log("DropManager", message);
        } else {
            String fullMsg = "¡Inventario lleno! Libera espacio para recibir: " + boatPiece.name;
            gp.showNotification(fullMsg);
            Gdx.app.log("DropManager", fullMsg);
        }
    }
}
