package com.whereyouat.items;


import com.whereyouat.entity.Player;

public class ItemPotion extends Item {

    public ItemPotion() {
        super("Pocion de regeneración", "Restaura vida", loadIcon("items/regeneration_potion.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player.health < player.maxHealth) {
            player.health = Math.min(player.maxHealth, player.health + 40);
            return true;
        }
        return false;
    }
}

