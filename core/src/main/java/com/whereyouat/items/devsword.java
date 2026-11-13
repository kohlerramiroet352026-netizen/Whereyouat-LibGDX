package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class devsword extends Item {
    public devsword() {
        super("Espada de developer", "super OP", loadIcon("items/dev_sword.png"));
    }

    @Override
    public boolean use(Player player) {
        player.attack(player.direction, 999);
        return false;
    }
}
