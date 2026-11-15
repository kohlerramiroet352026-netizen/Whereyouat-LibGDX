package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class FrostHelmet extends Item {
    public FrostHelmet() {
        super("Casco helado", "Esta fresquito", loadIcon("items/frost_helmet.png"));
        this.ironHearts = 2;
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

