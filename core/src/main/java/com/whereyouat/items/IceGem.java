package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_snowGhost;

//invocador espectro nieves
public class IceGem extends Item {
    
    //crear gema
    public IceGem() {
        super(
            "Gema de Hielo",
            "Brillante, que hará?",
            loadIcon("items/ice_gem.png")
        );
    }

    //invocar jefe nieve
    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            boolean summoned = MON_snowGhost.summon(player.gp);
            if (summoned) {
                return true;
            }
        }
        return false;
    }
}
