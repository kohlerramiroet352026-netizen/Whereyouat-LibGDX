package com.whereyouat.items;

import com.whereyouat.entity.Player;

//espada basica
public class Sword extends Item {

    //crear espada
    public Sword() {
        super("Espada de hierro", "Chiquita pero peligrosa", loadIcon("items/iron_sword.png"));
    }

    //atacar con espada
    @Override
    public boolean use(Player player) {
        //hacer ataque
        player.attack(player.direction, 2);
        return false;
    }
}
