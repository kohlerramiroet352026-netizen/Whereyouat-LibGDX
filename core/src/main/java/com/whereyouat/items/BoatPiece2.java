package com.whereyouat.items;

import com.whereyouat.entity.Player;

/**
 * Recompensa única obtenida al derrotar al Rey Frogger en el bioma del pantano.
 * Segunda parte del bote requerida para escapar de la isla.
 */
public class BoatPiece2 extends Item {

    public BoatPiece2() {
        super(
            "Pieza de bote 2",
            "Mástil y vela impermeable.\nEntregado por el Rey Frogger.",
            loadIcon("items/boat_piece2.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 2 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false; // Es un objeto de misión, no se consume al usarlo
    }
}
