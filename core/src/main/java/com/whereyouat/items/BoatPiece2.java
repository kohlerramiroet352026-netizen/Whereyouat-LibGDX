package com.whereyouat.items;

import com.whereyouat.entity.Player;

//pieza 2 del barco
public class BoatPiece2 extends Item {

    //crear pieza
    public BoatPiece2() {
        super(
            "Pieza de bote 2",
            "Mastil y vela impermeable.",
            loadIcon("items/boat_piece2.png")
        );
    }

    //usar pieza
    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 2 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false; //no consumir
    }
}
