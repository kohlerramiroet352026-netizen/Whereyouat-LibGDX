package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class CopperHelmet extends Item {
    public CopperHelmet() {
        super("Casco de cobre", "Casco forjado\nen cobre", loadIcon("items/copper_helmet.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player == null) return false;
        Item old = player.helmetItem;
        player.helmetItem = this;
        if (old != null) {
            player.addItem(old);
        }
        return true;
    }
}

