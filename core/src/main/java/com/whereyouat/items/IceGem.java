package com.whereyouat.items;

import com.whereyouat.entity.Player;
import com.whereyouat.monster.MON_snowGhost;

/**
 * Objeto de invocación para el Jefe del Bioma de Nieve (Espectro de las Nieves).
 * Solo se consume si la invocación se realiza con éxito en un lugar válido del bioma nevado.
 */
public class IceGem extends Item {
    
    public IceGem() {
        super(
            "Gema de Hielo",
            "Gema helada que irradia un frío gélido.\nInvoca al Espectro de las Nieves.",
            loadIcon("items/regeneration_potion.png")
        );
    }

    @Override
    public boolean use(Player player) {
        if (player != null && player.gp != null) {
            boolean summoned = MON_snowGhost.summon(player.gp);
            if (summoned) {
                return true; // Se consume tras invocar con éxito
            }
        }
        return false; // No se consume si la invocación falló
    }
}
