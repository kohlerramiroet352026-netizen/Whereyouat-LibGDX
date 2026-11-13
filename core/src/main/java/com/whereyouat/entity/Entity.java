package com.whereyouat.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

// clase base abstracta para entidades del juego
public abstract class Entity {

    // posición en coordenadas de mundo (píxeles)
    public float worldX;
    public float worldY;
    public float speed;

    // dirección de movimiento
    public String direction = "down";

    // contador y número de frame de animación
    public float spriteCounter = 0;
    public int spriteNum = 1;

    // hitbox para colisiones (relativo a worldX, worldY)
    public Rectangle solidArea = new Rectangle(0, 0, 48, 48);
    public float solidAreaDefaultX = 0f;
    public float solidAreaDefaultY = 0f;
    public boolean collisionOn = false;

    // atributos de combate e invencibilidad
    public int maxLife;
    public int life;
    public boolean invincible = false;
    public int invincibleCounter = 0;

    // indica si el contacto causa daño al jugador
    public boolean dealsContactDamage() {
        return true;
    }

    public Entity() {
    }

    public Entity(float worldX, float worldY, float speed) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.speed = speed;
    }

    // actualiza lógica y movimiento cada frame
    public abstract void update(float delta);

    // actualiza usando delta de LibGDX
    public void update() {
        update(com.badlogic.gdx.Gdx.graphics != null ? com.badlogic.gdx.Gdx.graphics.getDeltaTime() : 1f / 60f);
    }

    // libera recursos gráficos y de audio
    public void dispose() {
    }

    // dibuja la entidad con el SpriteBatch activo
    public abstract void draw(SpriteBatch batch);

    public float getWorldX() {
        return worldX;
    }

    public float getWorldY() {
        return worldY;
    }

    public void setPosition(float x, float y) {
        this.worldX = x;
        this.worldY = y;
    }

    // verifica colisión entre áreas sólidas en mundo
    public boolean intersects(Entity other) {
        if (other == null) return false;
        float ax = this.worldX + this.solidArea.x;
        float ay = this.worldY + this.solidArea.y;
        float aw = this.solidArea.width;
        float ah = this.solidArea.height;

        float bx = other.worldX + other.solidArea.x;
        float by = other.worldY + other.solidArea.y;
        float bw = other.solidArea.width;
        float bh = other.solidArea.height;

        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    // distancia euclidiana a otra entidad
    public float distanceTo(Entity other) {
        if (other == null) return Float.MAX_VALUE;
        return distanceTo(other.worldX, other.worldY);
    }

    // distancia euclidiana a un punto del mundo
    public float distanceTo(float targetX, float targetY) {
        float deltaX = this.worldX - targetX;
        float deltaY = this.worldY - targetY;
        return (float) Math.hypot(deltaX, deltaY);
    }

}
