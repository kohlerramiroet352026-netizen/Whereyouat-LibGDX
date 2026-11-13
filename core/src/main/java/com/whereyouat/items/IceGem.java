package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_snowGhost;

public class IceGem extends Item {
    public IceGem() {
        super(
            "Gema de Hielo",
            "Brillante, que hará?",
            loadIcon("items/ice_gem.png")
        );
    }

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
