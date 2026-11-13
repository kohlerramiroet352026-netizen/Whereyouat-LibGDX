package com.whereyouat.items;

import com.whereyouat.entity.Player;

//pocion curativa
public class ItemPotion extends Item {

    //crear pocion
    public ItemPotion() {
        super("Pocion de regeneración", "Restaura vida", loadIcon("items/regeneration_potion.png"));
    }

    //consumir y curar
    @Override
    public boolean use(Player player) {
        //curar jugador
        player.health = Math.min(player.maxHealth, player.health + 40);
        return true;
    }
}
