package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class CopperChestplate extends Item {
    public CopperChestplate() {
        super("Pechera de cobre", "Pechera forjada\nen cobre", loadIcon("items/copper_chestplate.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player == null) return false;
        Item old = player.chestplateItem;
        player.chestplateItem = this;
        if (old != null) {
            player.addItem(old);
        }
        return true;
    }
}

