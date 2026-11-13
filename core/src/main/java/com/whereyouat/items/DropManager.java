package com.whereyouat.items;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.whereyouat.entity.Entity;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_Ghost;
import com.whereyouat.screens.GameScreen;

/**
 * Gestor centralizado de tablas de drops y recompensas de monstruos y jefes.
 * Contiene probabilidades configurables y métodos desacoplados para la entrega segura de objetos.
 */
public class DropManager {

    // Probabilidades configurables de drops para monstruos comunes (0.0f a 1.0f)
    public static final float CHANCE_SNOW_SUMMON_DROP = 0.35f;    // 35% de probabilidad de soltar Gema de Hielo
    public static final float CHANCE_SWAMP_SUMMON_DROP = 0.35f;   // 35% de probabilidad de soltar Huevos de Rana
    public static final float CHANCE_FOREST_SUMMON_DROP = 0.35f;  // 35% de probabilidad de soltar Hongo Extraño
    public static final float CHANCE_POTION_DROP = 0.25f;         // 25% de probabilidad de soltar Poción si no cayó invocación

    private static final Random random = new Random();

    /**
     * Procesa la probabilidad de drop cuando un monstruo común es derrotado.
     * Determina el objeto según el bioma y tipo de criatura, entregándolo al jugador de forma segura.
     *
     * @param monster Entidad del monstruo derrotado
     * @param gp Instancia de GameScreen
     */
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

        // 1. Determinar si suelta un objeto de invocación según el bioma donde fue derrotado
        if (gp.mapGen.isSnowBiome[tileCol][tileRow]) {
            if (roll < CHANCE_SNOW_SUMMON_DROP) {
                dropItem = new IceGem();
            }
        } else if (gp.mapGen.isSwampBiome[tileCol][tileRow]) {
            if (roll < CHANCE_SWAMP_SUMMON_DROP) {
                dropItem = new FrogEggs();
            }
        } else {
            // Bosque, planicies o praderas
            if (roll < CHANCE_FOREST_SUMMON_DROP) {
                dropItem = new OddMushroom();
            }
        }

        // 2. Si no cayó objeto de invocación, comprobar si suelta una poción de vida como recompensa alternativa
        if (dropItem == null) {
            float potionRoll = random.nextFloat();
            if (potionRoll < CHANCE_POTION_DROP) {
                dropItem = new ItemPotion();
            }
        }

        // 3. Entregar el objeto si se generó alguno
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

    /**
     * Entrega de forma segura y garantizada la pieza del bote otorgada por un jefe al morir.
     *
     * @param bossName Nombre descriptivo del jefe
     * @param boatPiece Objeto correspondiente a la pieza del bote
     * @param gp Instancia de GameScreen
     */
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
            // Si el inventario estuviera saturado a 20 items, se notifica claramente
            String fullMsg = "¡Inventario lleno! Libera espacio para recibir: " + boatPiece.name;
            gp.showNotification(fullMsg);
            Gdx.app.log("DropManager", fullMsg);
        }
    }
}
