package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_frogger;

public class FrogEggs extends Item {
    public FrogEggs() {
        super("Huevos de rana", "Son extrañamente\nGrandes...", loadIcon("items/frog_eggs.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player.gp != null){
            boolean summoned = MON_frogger.summon(player.gp);
            if (summoned){
                return true;
            }
        }
        return false;
    }
}
