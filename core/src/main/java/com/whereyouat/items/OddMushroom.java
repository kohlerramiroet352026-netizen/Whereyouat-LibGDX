package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_mushroom;

//invocador rey hongo
public class OddMushroom extends Item {

    //crear hongo
    public OddMushroom() {
        super("Hongo extraño", "Que hara?...", loadIcon("items/OddMushroom.png"));
    }

    //invocar rey hongo
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
