package com.whereyouat.monster;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Entity;
import com.whereyouat.map.TileManager;
import com.whereyouat.screens.GameScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MON_mushroom extends Entity {

    public GameScreen gp;
    public String name;

    // Tamaño de dibujado aumentado (80px en lugar de 48px para que se vea más grande e imponente)
    public static final float DRAW_SIZE = 80f;

    private final List<Texture> textures = new ArrayList<>();
    private final TextureRegion[][] walkFrames = new TextureRegion[4][4];
    private static final int DIR_UP    = 0;
    private static final int DIR_DOWN  = 1;
    private static final int DIR_LEFT  = 2;
    private static final int DIR_RIGHT = 3;

    private String lastHorizontal = "right";

    private int actionLockCounter = 0;
    private final Random random = new Random();

    public MON_mushroom(GameScreen gp) {
        super(0, 0, 40f); // Velocidad: 40 px/segundo
        this.gp = gp;
        
        name = "Mushroom";
        maxLife = 3;
        life = maxLife;

        // Hitbox de colisión proporcional al nuevo tamaño (80px)
        solidArea.set(19f, 37f, 39f, 40f);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();
    }
    private void loadSprites() {
        // Right-facing movement frames (1 to 4)
        TextureRegion frame1 = loadRegion("monster/Mushroom-Run5.png");
        TextureRegion frame2 = loadRegion("monster/Mushroom-Run6.png");
        TextureRegion frame3 = loadRegion("monster/Mushroom-Run7.png");
        TextureRegion frame4 = loadRegion("monster/Mushroom-Run8.png");

        // Left-facing movement frames (5 to 8)
        TextureRegion frame5 = loadRegion("monster/Mushroom-Run1.png");
        TextureRegion frame6 = loadRegion("monster/Mushroom-Run2.png");
        TextureRegion frame7 = loadRegion("monster/Mushroom-Run3.png");
        TextureRegion frame8 = loadRegion("monster/Mushroom-Run4.png");

        // DIR_RIGHT (3): frames 1-4
        walkFrames[DIR_RIGHT][0] = frame1;
        walkFrames[DIR_RIGHT][1] = frame2;
        walkFrames[DIR_RIGHT][2] = frame3;
        walkFrames[DIR_RIGHT][3] = frame4;

        // DIR_LEFT (2): frames 3-4
        walkFrames[DIR_LEFT][0] = frame5;
        walkFrames[DIR_LEFT][1] = frame6;
        walkFrames[DIR_LEFT][2] = frame7;
        walkFrames[DIR_LEFT][3] = frame8;

        // DIR_UP (0) and DIR_DOWN (1): default right frames
        walkFrames[DIR_UP][0] = frame1;
        walkFrames[DIR_UP][1] = frame2;
        walkFrames[DIR_UP][2] = frame3;
        walkFrames[DIR_UP][3] = frame4;

        walkFrames[DIR_DOWN][0] = frame1;
        walkFrames[DIR_DOWN][1] = frame2;
        walkFrames[DIR_DOWN][2] = frame3;
        walkFrames[DIR_DOWN][3] = frame4;
    }
    private TextureRegion loadRegion(String path) {
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true); // Y-down para LibGDX
        return tr;
    }
    @Override
    public void update(float delta) {
        // IA simple: cambia de dirección cada ~2 segundos
        actionLockCounter++;
        if (actionLockCounter >= 120) {
            int r = random.nextInt(4);
            if (r == 0) direction = "up";
            else if (r == 1) direction = "down";
            else if (r == 2) direction = "left";
            else direction = "right";
            actionLockCounter = 0;
        }
        // Movimiento
        switch (direction) {
            case "up":    worldY -= speed * delta; break;
            case "down":  worldY += speed * delta; break;
            case "left":  worldX -= speed * delta; lastHorizontal = "left"; break;
            case "right": worldX += speed * delta; lastHorizontal = "right"; break;
        }
        // Animación de 4 frames
        spriteCounter += delta * 60f;
        if (spriteCounter >= 12f) {
            spriteNum++;
            if (spriteNum > 4) {
                spriteNum = 1;
            }
            spriteCounter = 0;
        }
    }
    @Override
    public void draw(SpriteBatch batch) {
        int dirIndex;
        switch (direction) {
            case "left":
                dirIndex = DIR_LEFT;
                break;
            case "right":
                dirIndex = DIR_RIGHT;
                break;
            case "up":
            case "down":
            default:
                // Keep the last horizontal direction (left or right) when moving vertically
                dirIndex = "left".equals(lastHorizontal) ? DIR_LEFT : DIR_RIGHT;
                break;
        }

        int frameIndex = Math.max(0, Math.min(3, spriteNum - 1));
        TextureRegion region = walkFrames[dirIndex][frameIndex];

        if (region != null) {
            batch.draw(region, worldX, worldY, DRAW_SIZE, DRAW_SIZE);
        }
    }
    @Override
    public void dispose() {
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
    }
}
