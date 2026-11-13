package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class BoatPiece2 extends Item {
    public BoatPiece2() {
        super(
            "Pieza de bote 2",
            "Mastil y vela impermeable.",
            loadIcon("items/boat_piece2.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 2 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false;
    }
}
