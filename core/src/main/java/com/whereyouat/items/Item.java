package com.whereyouat.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Player;

//clase base items
public abstract class Item {
    public String name;
    public String description;
    public TextureRegion icon;

    //crear item
    public Item(String name, String description, TextureRegion icon) {
        this.name = name;
        this.description = description;
        this.icon = icon;
    }

    //cargar icono
    protected static TextureRegion loadIcon(String path){
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true);
        return region;
    }

    //usar item
    public abstract boolean use(Player player);
}
