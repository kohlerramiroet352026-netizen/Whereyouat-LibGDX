package com.whereyouat.items;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Player;

public abstract class Item {
    public String name;
    public String description;
    public TextureRegion icon;

    public Item(String name, String description, TextureRegion icon) {
        this.name = name;
        this.description = description;
        this.icon = icon;
    }

    public abstract boolean use(Player player);
}

