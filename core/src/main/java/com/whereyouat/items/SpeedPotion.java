package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class SpeedPotion extends Item {
    public SpeedPotion() {
        super("Pocion de velocidad", "Soy... veloz...", loadIcon("items/speed_potion.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player == null) return false;
        player.applySpeedBoost(200f, 30f);
        return true;
    }
}

