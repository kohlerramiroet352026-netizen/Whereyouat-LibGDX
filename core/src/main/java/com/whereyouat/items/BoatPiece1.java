package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class BoatPiece1 extends Item {
    public BoatPiece1() {
        super(
            "Pieza de bote 1",
            "Casco reforzado de madera.",
            loadIcon("items/boat_piece1.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 1 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false;
    }
}
