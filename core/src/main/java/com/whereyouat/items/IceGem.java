package com.whereyouat.items;

import com.whereyouat.entity.Player;

public class IceGem extends Item {
    
    public IceGem() {
        super("Gema de Hielo", "Gema helada obtenida del Espectro de las Nieves. Irradia un frio revitalizante.", loadIcon("items/regeneration_potion.png"));
    }

    @Override
    public boolean use(Player player) {
        if (player.health < player.maxHealth) {
            player.health = Math.min(player.maxHealth, player.health + 60);
            return true; // Se consume
        }
        return false;
    }
}
