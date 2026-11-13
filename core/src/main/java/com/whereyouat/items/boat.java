package com.whereyouat.items;

import com.whereyouat.entity.Player;

//item del bote
public class boat extends Item {

    //crear bote
    public boat() {
        super(
            "Bote",
            "Presiona B cerca\ndel oceano para\nzarpar",
            loadIcon("items/boat.png")
        );
    }

    //usar bote
    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Anda al oceano y apreta B para zarpar");
        }
        return false; //no consumir
    }
}
