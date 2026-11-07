package com.whereyouat.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

/**
 * Base abstract class for all in-game entities (Player, Bird, future NPCs/mobs)
 * for the LibGDX version of Where You At.
 *
 * Encapsulates position in world coordinates, movement speed, facing direction,
 * animation frames, distance computations, and lifecycle methods.
 *
 * Adheres strictly to OOP principles (SOLID, DRY, KISS, YAGNI).
 */
public abstract class Entity {

    // World coordinate positions (pixels)
    public float worldX;
    public float worldY;
    public float speed;

    // Facing / movement direction
    public String direction = "down";

    // Animation frame tracking
    public float spriteCounter = 0;
    public int spriteNum = 1;

    // Hitbox / solid area for collisions (relative to worldX, worldY)
    public Rectangle solidArea = new Rectangle(0, 0, 48, 48);
    public float solidAreaDefaultX = 0f;
    public float solidAreaDefaultY = 0f;
    public boolean collisionOn = false;

    // Combat attributes and invincibility state
    public int maxLife;
    public int life;
    public boolean invincible = false;
    public int invincibleCounter = 0;

    /**
     * Determines whether touching this entity deals contact damage to the player.
     * Entities that deal damage via attack animations can override to return false.
     */
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

    /**
     * Updates entity motion, logic, and state each frame.
     *
     * @param delta Frame delta time in seconds
     */
    public abstract void update(float delta);

    /**
     * Parameterless update delegating to update(delta) using LibGDX frame delta time.
     */
    public void update() {
        update(com.badlogic.gdx.Gdx.graphics != null ? com.badlogic.gdx.Gdx.graphics.getDeltaTime() : 1f / 60f);
    }

    /**
     * Frees any graphical or audio resources held by the entity.
     * Default implementation is a no-op; subclasses can override.
     */
    public void dispose() {
    }

    /**
     * Renders the entity using the active GPU SpriteBatch.
     *
     * @param batch Active LibGDX SpriteBatch
     */
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

    /**
     * Checks if this entity's solid area intersects with another entity's solid area in world coordinates.
     */
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

    /**
     * Computes Euclidean distance to another entity in world pixels.
     */
    public float distanceTo(Entity other) {
        if (other == null) return Float.MAX_VALUE;
        return distanceTo(other.worldX, other.worldY);
    }

    /**
     * Computes Euclidean distance to target point in world pixels.
     */
    public float distanceTo(float targetX, float targetY) {
        float deltaX = this.worldX - targetX;
        float deltaY = this.worldY - targetY;
        return (float) Math.hypot(deltaX, deltaY);
    }
}
