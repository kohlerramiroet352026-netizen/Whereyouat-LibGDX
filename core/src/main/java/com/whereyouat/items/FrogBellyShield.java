package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class FrogBellyShield extends Item {
    public FrogBellyShield() {
        super("Escudo de piel de rana", "Tiene un olor\nhorrible", loadIcon("items/frog_belly_shield.png"));
    }

    @Override
    public boolean use(Player player) {
        return false;
    }
}

