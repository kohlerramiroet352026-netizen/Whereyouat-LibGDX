package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class BoatPiece3 extends Item {
    public BoatPiece3() {
        super(
            "Pieza de bote 3",
            "Timon y quilla congelada.",
            loadIcon("items/boat_piece3.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 3 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false;
    }
}
