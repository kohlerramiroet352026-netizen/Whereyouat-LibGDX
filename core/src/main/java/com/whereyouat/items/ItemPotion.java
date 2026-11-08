package com.whereyouat.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Player;

public class ItemPotion extends Item {

    public ItemPotion(TextureRegion icon) {
        super("Pocion de regeneración", "Restaura vida", loadIcon("items/regeneration_potion.png"));
    }

    private static TextureRegion loadIcon(String path){
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true);
        return region;
    }

    @Override
    public boolean use(Player player) {
        if (player.health < player.maxHealth) {
            player.health = Math.min(player.maxHealth, player.health + 40);
            return true;
        }
        return false;
    }
}

