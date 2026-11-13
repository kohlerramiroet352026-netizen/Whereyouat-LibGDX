package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class boat extends Item {
    public boat() {
        super(
            "Bote",
            "Presiona B cerca\ndel oceano para\nzarpar",
            loadIcon("items/boat.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Anda al oceano y apreta B para zarpar");
        }
        return false;
    }
}
