package com.whereyouat.items;

import com.whereyouat.entity.Player;

/**
 * Recompensa única obtenida al derrotar al Espectro de las Nieves en el bioma de nieve.
 * Tercera parte del bote requerida para escapar de la isla.
 */
public class BoatPiece3 extends Item {

    public BoatPiece3() {
        super(
            "Pieza de bote 3",
            "Timón y quilla congelada.\nEntregado por el Espectro de Nieve.",
            loadIcon("items/boat_piece3.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            player.gp.showNotification("Pieza 3 del bote. Necesitas las 3 para reparar el barco.");
        }
        return false; // Es un objeto de misión, no se consume al usarlo
    }
}
