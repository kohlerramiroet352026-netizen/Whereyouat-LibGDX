package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_mushroom;

public class OddMushroom extends Item {

    public OddMushroom() {
        super("Hongo extraño", "Que hara?...", loadIcon("items/OddMushroom.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player.gp != null){
            boolean summoned = MON_mushroom.summon(player.gp);
            if (summoned){
                return true;
            }
        }
        return false;
    }
}
