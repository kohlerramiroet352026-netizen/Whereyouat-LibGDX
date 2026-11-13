package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class Sword extends Item {
    public Sword() {
        super("Espada de hierro", "Chiquita pero peligrosa", loadIcon("items/iron_sword.png"));
    }

    @Override
    public boolean use(Player player) {
        player.attack(player.direction, 2);
        return false;
    }
}
