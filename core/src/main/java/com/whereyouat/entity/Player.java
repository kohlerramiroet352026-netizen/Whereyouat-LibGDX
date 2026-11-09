package com.whereyouat.entity;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.items.Item;
import com.whereyouat.items.ItemPotion;
import com.whereyouat.items.OddMushroom;
import com.whereyouat.items.Sword;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_frogger;
import com.whereyouat.monster.MON_mushroom;
import com.whereyouat.screens.GameScreen;

/**
 * Player entity for Where you at (LibGDX Edition).
 *
 * Implements:
 * - 4-direction walk cycles (up1..13, down1..13, left1..8, right1..8)
 * - Idle looking-around cycle (idle0..8)
 * - Obstacle and tree trunk collision
 * - Water wading and drowning mechanics with depth clipping and foam
 * - Step cadence and vegetation interaction callbacks
 * - Health, lives, damage, and regeneration systems
 */
public class Player extends Entity {

    // Listener interface for decoupled audio and particle/footprint triggers
    public interface PlayerStepListener {
        void onStep(float footX, float footY, double angle, boolean isLeftFoot, int footCol, int footRow);
        void onBushRustle();
    }

    private PlayerStepListener stepListener;

    // Sprite textures and regions
    private final List<Texture> textures = new ArrayList<>();
    private final TextureRegion[] downWalk = new TextureRegion[13];
    private final TextureRegion[] upWalk   = new TextureRegion[13];
    private final TextureRegion[] leftWalk = new TextureRegion[8];
    private final TextureRegion[] rightWalk= new TextureRegion[8];
    private final TextureRegion[] idleAnim = new TextureRegion[9];

    // Footstep pacing
    private long lastStepMs = 0;
    private boolean isLeftFootStep = true;
    private static final long STEP_INTERVAL_MS = 400;
    private static final long STEP_INTERVAL_SHALLOW_WATER_MS = 640;
    private static final long STEP_INTERVAL_DEEP_WATER_MS = 950;

    // Vegetation state
    private boolean wasInBush = false;
    private boolean wasInTallGrass = false;

    // Movement state
    public boolean moving = false;

    // Life and health system
    public int maxLives = 3;
    public int lives = 3;
    public int maxHealth = 100;
    public int health = 100;
    public boolean dead = false;

    private int healCooldown = 0;
    private int regenTick = 0;

    // Drowning mechanic
    private int drownCounter = 0;
    public static final int DROWN_TICKS_DEEP = 180; // 3 seconds at 60fps

    // Invincibility
    public static final int INVINCIBLE_DURATION = 120; // 2 seconds at 60fps

    // Monster contact damage
    public static final int MONSTER_DAMAGE = 20;

    // Movement speeds (pixels per second)
    private static final float BASE_SPEED = 1000f; //240
    private static final float BUSH_SPEED = 180f;
    private static final float SHALLOW_SPEED = 120f;
    private static final float DEEP_SPEED = 60f;

    // Foot hitbox offsets (relative to worldX, worldY)
    public static final float HIT_OFFSET_X = 14f;
    public static final float HIT_OFFSET_Y = 34f;
    public static final float HIT_W = 20f;
    public static final float HIT_H = 12f;

    //deteción de ataque y mounstros
    public GameScreen gp;
    public Entity[] currentMonsters;
    public boolean attacking = false;
    public float attackTimer = 0f;
    public float attackCooldown = 0f;
    public static final float ATTACK_COOLDOWN = 2.0f;
    public static final float ATTACK_DURATION = 0.2f;
    private TextureRegion attackUp;
    private TextureRegion attackDown;
    private TextureRegion attackRight;
    private TextureRegion attackLeft;
    

    public Player() {
        super(0, 0, BASE_SPEED);

        solidArea.set(HIT_OFFSET_X, HIT_OFFSET_Y, HIT_W, HIT_H);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();

        addItem(new OddMushroom());
        addItem(new Sword());
        addItem(new ItemPotion());

    }

    public void attack(String targetDir, int damage){
        if (!hasSword()) return;
        if (attackCooldown > 0f) return;

        this.attackCooldown = ATTACK_COOLDOWN;
        this.direction = targetDir;
        this.attacking = true;
        this.attackTimer = ATTACK_DURATION;

        if (currentMonsters == null) return;

        //area de impacto (24x24px en frente del jugador);
        float attackW = 24f;
        float attackH = 24f;
        float attackX = worldX;
        float attackY = worldY;

        switch (direction){
            case "up":
                attackX = worldX + (TileManager.TILE_SIZE - attackW) / 2f;
                attackY = worldY - attackH;
                break;
            case "down":
                attackX = worldX + (TileManager.TILE_SIZE - attackW) / 2f;
                attackY = worldY + TileManager.TILE_SIZE;
                break;
            case "left":
                attackX = worldX - attackW;
                attackY = worldY + (TileManager.TILE_SIZE - attackH) / 2f;
                break;
            case "right":
                attackX = worldX + TileManager.TILE_SIZE;
                attackY = worldY + (TileManager.TILE_SIZE - attackH) / 2f;
                break;
        }

        //revisar si le pega a algun mounstro

        for (int i = 0; i < currentMonsters.length; i++){
            Entity m = currentMonsters[i];
            if (m != null && m.life > 0){
                //hitbox del mounstro
                float mX = (m instanceof MON_mushroom) ? m.worldX + 130f : m.worldX + m.solidArea.x;
                float mY = (m instanceof MON_mushroom) ? m.worldY + 160f : m.worldY + m.solidArea.y;
                float mW = (m instanceof MON_mushroom) ? 90f : m.solidArea.width;
                float mH = (m instanceof MON_mushroom) ? 80f : m.solidArea.height;

                //colision entre el ataque y el mounstro
                if (attackX < mX + mW && attackX + attackW > mX && attackY < mY + mH && attackY + attackH > mY){
                    if (m instanceof MON_mushroom) {
                        ((MON_mushroom) m).takeDamage(damage, worldX, worldY);
                    
                    } else if (m instanceof MON_frogger) {
                        ((MON_frogger) m).takeDamage(damage, worldX, worldY);
                    } else {
                        m.life -= damage;
                        if (m.life <=0){
                            currentMonsters[i] = null;
                        }
                    }
                    break;
                }
            }
        }
    }

    public void setStepListener(PlayerStepListener listener) {
        this.stepListener = listener;
    }

    private TextureRegion loadRegion(String path) {
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(tex);
        TextureRegion tr = new TextureRegion(tex);
        tr.flip(false, true);
        return tr;
    }

    private void loadSprites() {
        for (int i = 1; i <= 13; i++) {
            downWalk[i - 1] = loadRegion("player/down" + i + ".png");
            upWalk[i - 1]   = loadRegion("player/up" + i + ".png");
        }
        for (int i = 1; i <= 8; i++) {
            leftWalk[i - 1]  = loadRegion("player/left" + i + ".png");
            rightWalk[i - 1] = loadRegion("player/right" + i + ".png");
        }
        for (int i = 0; i <= 8; i++) {
            idleAnim[i] = loadRegion("player/idle" + i + ".png");
        }

        //cargar sprites de ataque
        attackUp = loadRegion("player/attack_up.png");
        attackDown = loadRegion("player/attack_down.png");
        attackLeft = loadRegion("player/attack_left.png");
        attackRight = loadRegion("player/attack_right.png");
    }

    public void initSpawn(MapGenerator mapGen) {
        lives = maxLives;
        health = maxHealth;
        dead = false;
        drownCounter = 0;
        invincibleCounter = 0;
        invincible = false;
        direction = "down";

        java.util.Random rand = new java.util.Random(mapGen.seed);
        int startCol = 40 + rand.nextInt(mapGen.mapCols - 80);
        int startRow = 40 + rand.nextInt(mapGen.mapRows - 80);

        for (int radius = 0; radius < 50; radius++) {
            for (int deltaCol = -radius; deltaCol <= radius; deltaCol++) {
                for (int deltaRow = -radius; deltaRow <= radius; deltaRow++) {
                    int checkCol = startCol + deltaCol;
                    int checkRow = startRow + deltaRow;
                    if (checkCol >= 10 && checkCol < mapGen.mapCols - 10 && checkRow >= 10 && checkRow < mapGen.mapRows - 10) {
                        if (!mapGen.isDeepWater[checkCol][checkRow] && !mapGen.isShallowWater[checkCol][checkRow] && mapGen.mapTreeNum[checkCol][checkRow] == 0) {
                            worldX = checkCol * TileManager.TILE_SIZE;
                            worldY = checkRow * TileManager.TILE_SIZE;
                            return;
                        }
                    }
                }
            }
        }
        worldX = 256 * TileManager.TILE_SIZE;
        worldY = 256 * TileManager.TILE_SIZE;
    }

    /**
     * Respawns the player at a safe random land position on the map.
     */
    public void randomSpawn(MapGenerator mapGen) {
        health = maxHealth;
        dead = false;
        drownCounter = 0;
        healCooldown = 0;
        regenTick = 0;
        invincibleCounter = INVINCIBLE_DURATION;
        invincible = true;
        direction = "down";
        speed = BASE_SPEED;
        moving = false;

        java.util.Random rand = new java.util.Random();
        for (int attempts = 0; attempts < 500; attempts++) {
            int col = 35 + rand.nextInt(mapGen.mapCols - 70);
            int row = 35 + rand.nextInt(mapGen.mapRows - 70);
            if (!mapGen.isDeepWater[col][row] && !mapGen.isShallowWater[col][row]
                    && mapGen.mapTileNum[col][row] != MapGenerator.TILE_STONE) {
                int treeTile = mapGen.mapTreeNum[col][row];
                if (((treeTile < 12 || treeTile > 22)
                        && (treeTile < MapGenerator.TILE_WILLOW1 || treeTile > MapGenerator.TILE_MANGROVE))
                        && treeTile != MapGenerator.TILE_ROCK1
                        && treeTile != MapGenerator.TILE_SNOWY_ROCK1
                        && treeTile != MapGenerator.TILE_SANDSTONE_ROCK
                        && treeTile != MapGenerator.TILE_LOG1 && treeTile != MapGenerator.TILE_LOG2) {
                    worldX = col * TileManager.TILE_SIZE;
                    worldY = row * TileManager.TILE_SIZE;
                    return;
                }
            }
        }
        worldX = 256 * TileManager.TILE_SIZE;
        worldY = 256 * TileManager.TILE_SIZE;
    }

    @Override
    public void update(float delta) {
        // Concrete method called when MapGenerator context is not provided
    }

    /**
     * Main tick update with world map collision, water depth, and foliage reaction.
     * Backwards compatible overload without monster array.
     */
    public void update(float delta, MapGenerator mapGen, TileManager tileM) {
        update(delta, mapGen, tileM, null);
    }

    /**
     * Main tick update with world map collision, monster contact checking, water depth, and foliage reaction.
     */
    public void update(float delta, MapGenerator mapGen, TileManager tileM, Entity[] monsters) {
        if (dead) return;

        this.currentMonsters = monsters;
        // cooldown de 2 segundos
        if (attackCooldown > 0f){
            attackCooldown -= delta;
        }
        //la animacion de ataque dura 0.2 segundos
        if (attacking){
            attackTimer -= delta;
            if (attackTimer <= 0f){
                attacking = false;
            }
        }

        // Current tile under player feet
        int footCol = (int) ((worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int footRow = (int) ((worldY + HIT_OFFSET_Y + HIT_H / 2f) / TileManager.TILE_SIZE);
        int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);

        boolean inBush = tileM.isPlayerInBush(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);

        // Adjust speed according to terrain
        if (waterLevel == 2) {
            speed = DEEP_SPEED;
        } else if (waterLevel == 1) {
            speed = SHALLOW_SPEED;
        } else if (inBush) {
            speed = BUSH_SPEED;
        } else {
            speed = BASE_SPEED;
        }

        // Deep water drowning
        if (waterLevel == 2 && invincibleCounter == 0) {
            drownCounter++;
            if (drownCounter > 10) {
                damage(1);
            }
        } else {
            if (drownCounter > 0) {
                drownCounter = Math.max(0, drownCounter - 3);
            }
        }

        // Natural health regeneration
        if (health > 0 && health < maxHealth && !dead) {
            if (healCooldown > 0) {
                healCooldown--;
            } else {
                regenTick++;
                if (regenTick >= 90) {
                    health = Math.min(maxHealth, health + 1);
                    regenTick = 0;
                }
            }
        } else {
            regenTick = 0;
        }

        if (invincibleCounter > 0) {
            invincibleCounter--;
        }
        invincible = (invincibleCounter > 0);

        // Check collision with monsters
        if (monsters != null) {
            int monsterIndex = checkMonster(monsters);
            contactMonster(monsterIndex);
        }

        // Read movement input
        float inputX = 0f;
        float inputY = 0f;
        if (Gdx.input.isKeyPressed(Keys.W)) inputY -= 1f;
        if (Gdx.input.isKeyPressed(Keys.S)) inputY += 1f;
        if (Gdx.input.isKeyPressed(Keys.A)) inputX -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D)) inputX += 1f;

        if (inputX != 0f || inputY != 0f) {
            moving = true;

            if (!attacking) {
                if (inputY < 0f) direction = "up";
                else if (inputY > 0f) direction = "down";
                else if (inputX < 0f) direction = "left";
                else if (inputX > 0f) direction = "right";
            }

            // Diagonal normalization
            float inputLength = (float) Math.hypot(inputX, inputY);
            float moveX = (inputX / inputLength) * speed * delta;
            float moveY = (inputY / inputLength) * speed * delta;

            float prevX = worldX;
            float prevY = worldY;

            // X axis movement with collision check
            if (inputX != 0f) {
                float targetX = worldX + moveX;
                float hitBoxX = targetX + HIT_OFFSET_X;
                float hitBoxY = worldY + HIT_OFFSET_Y;
                if (!tileM.collidesWithTree(mapGen, hitBoxX, hitBoxY, HIT_W, HIT_H)) {
                    worldX = targetX;
                }
            }

            // Y axis movement with collision check
            if (inputY != 0f) {
                float targetY = worldY + moveY;
                float hitBoxX = worldX + HIT_OFFSET_X;
                float hitBoxY = targetY + HIT_OFFSET_Y;
                if (!tileM.collidesWithTree(mapGen, hitBoxX, hitBoxY, HIT_W, HIT_H)) {
                    worldY = targetY;
                }
            }

            // Keep within map boundaries
            int margin = 30;
            float minBoundX = margin * TileManager.TILE_SIZE;
            float minBoundY = margin * TileManager.TILE_SIZE;
            float maxBoundX = (mapGen.mapCols - margin) * TileManager.TILE_SIZE - TileManager.TILE_SIZE;
            float maxBoundY = (mapGen.mapRows - margin) * TileManager.TILE_SIZE - TileManager.TILE_SIZE;

            worldX = Math.max(minBoundX, Math.min(maxBoundX, worldX));
            worldY = Math.max(minBoundY, Math.min(maxBoundY, worldY));

            float movedX = worldX - prevX;
            float movedY = worldY - prevY;

            if (movedX != 0f || movedY != 0f) {
                long currentTimeMs = System.currentTimeMillis();
                long stepInterval = STEP_INTERVAL_MS;
                if (waterLevel == 2) {
                    stepInterval = STEP_INTERVAL_DEEP_WATER_MS;
                } else if (waterLevel == 1) {
                    stepInterval = STEP_INTERVAL_SHALLOW_WATER_MS;
                }

                if (currentTimeMs - lastStepMs >= stepInterval) {
                    lastStepMs = currentTimeMs;
                    triggerStep(movedX, movedY, waterLevel);
                }

                // Vegetation transitions
                boolean nowInBush = tileM.isPlayerInBush(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);
                boolean nowInTallGrass = tileM.isPlayerInTallGrass(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);

                if ((!wasInBush && nowInBush) || (wasInBush && !nowInBush) || (!wasInTallGrass && nowInTallGrass)) {
                    if (stepListener != null) {
                        stepListener.onBushRustle();
                    }
                }
                wasInBush = nowInBush;
                wasInTallGrass = nowInTallGrass;
            }

            // Trigger bush sway
            tileM.checkBushInteraction(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H, moveX, moveY);

            // Walk animation
            spriteCounter += delta * 60f;
            if (direction.equals("up") || direction.equals("down")) {
                if (spriteCounter >= 2.5f) {
                    spriteNum++;
                    if (spriteNum > 13) spriteNum = 1;
                    spriteCounter = 0;
                }
            } else {
                if (spriteCounter >= 5f) {
                    spriteNum++;
                    if (spriteNum > 8) spriteNum = 1;
                    spriteCounter = 0;
                }
            }

        } else {
            // Idle state
            if (moving) {
                spriteNum = 1;
                spriteCounter = 0;
            }
            moving = false;
            lastStepMs = 0;

            if (direction.equals("down")) {
                spriteCounter += delta * 60f;
                if (spriteCounter >= 60f) {
                    spriteNum++;
                    if (spriteNum > 9) spriteNum = 1;
                    spriteCounter = 0;
                }
            } else {
                spriteNum = 1;
                spriteCounter = 0;
            }
        }
    }

    private void triggerStep(float moveDx, float moveDy, int waterLevel) {
        float footCenterX = worldX + HIT_OFFSET_X + HIT_W / 2f;
        float footCenterY = worldY + HIT_OFFSET_Y + HIT_H / 2f;

        float moveDistance = (float) Math.hypot(moveDx, moveDy);
        float perpendicularX = 0f;
        float perpendicularY = 0f;
        if (moveDistance > 0.0001f) {
            perpendicularX = -moveDy / moveDistance;
            perpendicularY = moveDx / moveDistance;
        }

        float footSideOffset = isLeftFootStep ? -4.5f : 4.5f;
        float footX = footCenterX + perpendicularX * footSideOffset;
        float footY = footCenterY + perpendicularY * footSideOffset;

        double angle = Math.atan2(moveDy, moveDx);
        int footCol = (int) (footX / TileManager.TILE_SIZE);
        int footRow = (int) (footY / TileManager.TILE_SIZE);

        if (stepListener != null) {
            stepListener.onStep(footX, footY, angle + Math.PI / 2.0, isLeftFootStep, footCol, footRow);
        }

        isLeftFootStep = !isLeftFootStep;
    }

    /**
     * Checks collision with an array of monster entities using solidArea hitboxes.
     *
     * @param monsters Array of monsters
     * @return Index of contacted monster, or 999 if no collision
     */
    public int checkMonster(Entity[] monsters) {
        if (monsters == null) return 999;
        for (int i = 0; i < monsters.length; i++) {
            if (monsters[i] != null && monsters[i].dealsContactDamage() && intersects(monsters[i])) {
                return i;
            }
        }
        return 999;
    }

    /**
     * Handles contact damage with a monster by index.
     *
     * @param i Index of contacted monster in monsters array
     */
    public void contactMonster(int i) {
        if (i != 999 && i != -1) {
            contactMonster();
        }
    }

    /**
     * Handles contact damage with a specific monster entity.
     *
     * @param monster Monster entity
     */
    public void contactMonster(Entity monster) {
        if (monster != null) {
            contactMonster();
        }
    }

    /**
     * Handles contact damage from monsters using the player's health system.
     * Activates invincibility to prevent taking damage repeatedly or from
     * multiple monsters in the same instant.
     */
    public void contactMonster() {
        if (invincibleCounter > 0 || dead) return;

        damage(MONSTER_DAMAGE);

        if (!dead) {
            invincibleCounter = INVINCIBLE_DURATION;
            invincible = true;
        }
    }

    public void damage(int amount) {
        if (invincibleCounter > 0 || dead) return;
        health -= amount;
        healCooldown = 300;
        if (health <= 0) {
            health = 0;
            loseLife();
        }
    }

    public void heal(int amount) {
        if (dead) return;
        health = Math.min(maxHealth, health + amount);
    }

    public void loseLife() {
        lives--;
        drownCounter = 0;
        healCooldown = 0;
        regenTick = 0;
        if (lives <= 0) {
            lives = 0;
            health = 0;
            dead = true;
            invincible = false;
            invincibleCounter = 0;
        } else {
            health = maxHealth;
            invincibleCounter = INVINCIBLE_DURATION;
            invincible = true;
        }
    }

    public float getDrownProgress() {
        if (drownCounter == 0) return 0f;
        return (float) drownCounter / DROWN_TICKS_DEEP;
    }

    public float getHealthRatio() {
        if (maxHealth <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) health / maxHealth));
    }

    public TextureRegion getCurrentSprite() {
        //si el jugador esta atacando, devuelve el sprite de ataque hacia su dirección
        if (attacking) {
            switch (direction){
                case "up":
                    return attackUp != null ? attackUp : upWalk[0];
                case "left":
                    return attackLeft != null ? attackLeft : leftWalk[0];
                case "right":
                    return attackRight != null ? attackRight : rightWalk[0];
                case "down":
                    return attackDown != null ? attackDown : downWalk[0];
            }
        }

        //si no esta atacando continua con la animacion normal
        switch (direction) {
            case "up":
                int upFrameIndex = Math.max(0, Math.min(12, spriteNum - 1));
                return upWalk[upFrameIndex];
            case "left":
                int leftFrameIndex = Math.max(0, Math.min(7, spriteNum - 1));
                return leftWalk[leftFrameIndex];
            case "right":
                int rightFrameIndex = Math.max(0, Math.min(7, spriteNum - 1));
                return rightWalk[rightFrameIndex];
            case "down":
            default:
                if (!moving) {
                    int idleFrameIndex = Math.max(0, Math.min(8, spriteNum - 1));
                    return idleAnim[idleFrameIndex];
                } else {
                    int downFrameIndex = Math.max(0, Math.min(12, spriteNum - 1));
                    return downWalk[downFrameIndex];
                }
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        draw(batch, 0, null);
    }

    /**
     * Returns how many world-pixels the sprite should shift DOWN when in water.
     * Mirrors the original yShift logic from v0.3.
     */
    public int getWaterYShift(int waterLevel) {
        if (waterLevel == 1) return 4;
        if (waterLevel == 2) return 4 + (int) (getDrownProgress() * 8);
        return 0;
    }

    /**
     * Returns how many screen-pixels to clip from the BOTTOM of the sprite for water submersion.
     * Mirrors the original cutOff logic from v0.3 (tileSize = 48).
     */
    public int getWaterCutOff(int waterLevel) {
        if (waterLevel == 1) return 16;
        if (waterLevel == 2) return 16 + (int) (getDrownProgress() * 16);
        return 0;
    }

    /**
     * Draws the full player sprite at the given world position (no clipping applied here).
     * Water submersion clipping is handled by the caller (GameScreen) via GL scissor test,
     * matching the original g2.clipRect approach from v0.3.
     */
    public void draw(SpriteBatch batch, int waterLevel, TextureRegion foamRegion) {
        TextureRegion current = getCurrentSprite();
        if (current == null) return;

        // Invincibility blink
        if (invincibleCounter > 0 && (invincibleCounter / 10) % 2 == 0) {
            batch.setColor(1f, 1f, 1f, 0.4f);
        } else {
            batch.setColor(1f, 1f, 1f, 1f);
        }

        // yShift sinks the sprite slightly into water so feet meet the waterline
        float drawY = worldY + getWaterYShift(waterLevel);

        // Draw full sprite — clipping of the submerged portion is done in GameScreen with GL scissor
        

        float drawX = worldX;
        float drawW = current.getRegionWidth() * 3f;
        float drawH = current.getRegionHeight() * 3f;

        //si ataca a la izquierda o arriba desplaza el origen para que la espada sobresalga
        if (attacking){
            if ("left".equals(direction)){
                drawX = worldX - (drawW - TileManager.TILE_SIZE);
            } else if ("up".equals(direction)) {
                drawY = worldY - (drawH - TileManager.TILE_SIZE);
            }
        }

        batch.draw(current, drawX, drawY, drawW, drawH);

        // Foam overlay at the waist line (positioned at: drawY + tileSize - cutOff - 3)
        if (waterLevel > 0 && foamRegion != null) {
            int cutOff = getWaterCutOff(waterLevel);
            float foamY = drawY + TileManager.TILE_SIZE - cutOff - 3f;
            batch.setColor(1f, 1f, 1f, 0.9f);
            batch.draw(foamRegion, worldX, foamY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public final ArrayList<Item> inventory = new ArrayList<>();
    public final int MAX_INVENTORY_SIZE = 20;

    public boolean addItem(Item item){
        if (inventory.size() < MAX_INVENTORY_SIZE){
            inventory.add(item);
            return true;
        }
        return false;
    }

    public boolean hasSword(){
        for (Item item : inventory){
            if (item instanceof  Sword){
                return true;
            }
        }
        return false;
    }


    public void dispose() {
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
    }
}
