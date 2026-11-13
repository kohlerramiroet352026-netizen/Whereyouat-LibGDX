package com.whereyouat.items;

import com.whereyouat.entity.Player;

//espada dev
public class devsword extends Item {

    //crear espada dev
    public devsword() {
        super("Espada de developer", "super OP", loadIcon("items/dev_sword.png"));
    }

    //atacar con devsword
    @Override
    public boolean use(Player player) {
        player.attack(player.direction, 999);
        return false;
    }
}
