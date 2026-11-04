package com.whereyouat.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Bird entity for Where you at (LibGDX Edition).
 * Extends base Entity class, encapsulating perched states, flying physics,
 * ground hopping parabolic trajectory, and wing animation.
 */
public class Bird extends Entity {

    public enum PerchType { GRASS, TREE_TOP, BUSH_TOP }
    public enum BirdState { PERCHED, FLYING }

    public int spriteIndex; // 0=bird1, 1=bird2, 2=bird3
    public BirdState state = BirdState.PERCHED;
    public PerchType perchType;
    public int perchCol, perchRow;
    public boolean facingLeft = true;
    public float velX, velY;
    public int flapTick = 0;
    public int flapFrame = 0; // 0 or 1

    // Ground AI & hopping physics
    public float groundBaseY;
    public boolean isHopping = false;
    public float hopStartX, hopStartY;
    public float hopTargetX, hopTargetY;
    public float hopHeight;
    public int hopDuration;
    public int hopTick = 0;
    public int hopsRemaining = 0;
    public int aiTimer = 60;
    public int idleFidgetTimer = 120;

    public Bird(float worldX, float worldY, int spriteIndex, PerchType perchType, int perchCol, int perchRow, boolean facingLeft) {
        super(worldX, worldY, 276f); // ~4.6 px/frame at 60fps = 276 px/s
        this.groundBaseY = worldY;
        this.spriteIndex = spriteIndex;
        this.perchType = perchType;
        this.perchCol = perchCol;
        this.perchRow = perchRow;
        this.facingLeft = facingLeft;
        this.direction = facingLeft ? "left" : "right";
        this.aiTimer = 40 + (int)(Math.random() * 80);
        this.idleFidgetTimer = 120 + (int)(Math.random() * 200);
    }

    @Override
    public void update(float delta) {
        // Individual update logic managed within BirdManager
    }

    @Override
    public void draw(SpriteBatch batch) {
        // Individual draw dispatched by BirdManager with depth passes
    }
}
