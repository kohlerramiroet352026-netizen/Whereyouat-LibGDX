package com.whereyouat.items;

import com.whereyouat.entity.Player;

//pieza 3 del barco
public class BoatPiece3 extends Item {

    //crear pieza
    public BoatPiece3() {
        super(
            "Pieza de bote 3",
            "Timon y quilla congelada.",
            loadIcon("items/boat_piece3.png")
        );
    }

    //usar pieza
    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 3 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false; //no consumir
    }
}
