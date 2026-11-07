package com.whereyouat.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

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
