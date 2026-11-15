package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class CopperLeggings extends Item {
    public CopperLeggings() {
        super("Pantalones de cobre", "Pantalones forjados\nen cobre", loadIcon("items/copper_leggings.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player == null) return false;
        Item old = player.leggingsItem;
        player.leggingsItem = this;
        if (old != null) {
            player.addItem(old);
        }
        return true;
    }
}

