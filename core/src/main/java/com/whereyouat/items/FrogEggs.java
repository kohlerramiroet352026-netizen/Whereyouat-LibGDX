package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_frogger;

//invocador rey frogger
public class FrogEggs extends Item {

    //crear item
    public FrogEggs() {
        super("Huevos de rana", "Son extrañamente\nGrandes...", loadIcon("items/frog_eggs.png"));
    }

    //invocar rey frogger
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
