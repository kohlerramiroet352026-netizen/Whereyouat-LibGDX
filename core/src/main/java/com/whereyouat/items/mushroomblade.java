package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class mushroomblade extends Item {
    public mushroomblade() {
        super("Sable esporoso", "Esporosamente\npeligroso", loadIcon("items/mushroom_blade.png"));
    }

    @Override
    public boolean use(Player player) {
        player.attack(player.direction, 4);
        return false;
    }
}
