package com.whereyouat.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Player;

public class Sword extends Item {

    public Sword(TextureRegion icon) {
        super("Espada de hierro", "Chiquita pero peligrosa", loadIcon("items/iron_sword.png"));
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
        player.attack(player.direction, 2);
        return false;
    }
}

