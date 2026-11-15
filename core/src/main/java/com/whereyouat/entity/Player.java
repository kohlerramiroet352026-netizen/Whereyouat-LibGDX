package com.whereyouat.entity;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.items.BoatPiece1;
import com.whereyouat.items.BoatPiece2;
import com.whereyouat.items.BoatPiece3;
import com.whereyouat.items.CopperChestplate;
import com.whereyouat.items.CopperHelmet;
import com.whereyouat.items.CopperLeggings;
import com.whereyouat.items.FrogBellyShield;
import com.whereyouat.items.FrogEggs;
import com.whereyouat.items.FrostHelmet;
import com.whereyouat.items.IceGem;
import com.whereyouat.items.Item;
import com.whereyouat.items.ItemPotion;
import com.whereyouat.items.OddMushroom;
import com.whereyouat.items.SpeedPotion;
import com.whereyouat.items.Sword;
import com.whereyouat.items.boat;
import com.whereyouat.items.devsword;
import com.whereyouat.items.mushroomblade;
import com.whereyouat.map.MapGenerator;
import com.whereyouat.map.TileManager;
import com.whereyouat.monster.MON_Ghost;
import com.whereyouat.monster.MON_bat;
import com.whereyouat.monster.MON_frogger;
import com.whereyouat.monster.MON_mushroom;
import com.whereyouat.monster.MON_snowGhost;
import com.whereyouat.monster.MON_zombie;
import com.whereyouat.screens.GameScreen;

public class Player extends Entity {
    public interface PlayerStepListener {
        void onStep(float footX, float footY, double angle, boolean isLeftFoot, int footCol, int footRow);

        void onBushRustle();
    }

    private PlayerStepListener stepListener;

    private final List<Texture> textures = new ArrayList<>();
    private final TextureRegion[] downWalk = new TextureRegion[13];
    private final TextureRegion[] upWalk = new TextureRegion[13];
    private final TextureRegion[] leftWalk = new TextureRegion[8];
    private final TextureRegion[] rightWalk = new TextureRegion[8];
    private final TextureRegion[] idleAnim = new TextureRegion[9];

    private long lastStepMs = 0;
    private boolean isLeftFootStep = true;
    private static final long STEP_INTERVAL_MS = 400;
    private static final long STEP_INTERVAL_SHALLOW_WATER_MS = 640;
    private static final long STEP_INTERVAL_DEEP_WATER_MS = 950;

    private boolean wasInBush = false;
    private boolean wasInTallGrass = false;

    public boolean moving = false;
    public boolean isHiddenInBush = false;

    public int maxLives = 3;
    public int lives = 3;
    public int maxHealth = 100;
    public int health = 100;
    public boolean dead = false;

    private int healCooldown = 0;
    private int regenTick = 0;

    private int drownCounter = 0;
    public static final int DROWN_TICKS_DEEP = 180;

    public static final int INVINCIBLE_DURATION = 120;

    public static final int MONSTER_DAMAGE = 20;

    private static final float BASE_SPEED = 240f;
    private static final float BUSH_SPEED = 180f;
    private static final float SHALLOW_SPEED = 120f;
    private static final float DEEP_SPEED = 60f;

    public static final float HIT_OFFSET_X = 14f;
    public static final float HIT_OFFSET_Y = 34f;
    public static final float HIT_W = 20f;
    public static final float HIT_H = 12f;

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
    public Item offsetItem = null;
    public Item helmetItem = null;
    public Item chestplateItem = null;
    public Item leggingsItem = null;
    public boolean isShielding = false;
    public float shieldTimer = 0f;
    public float shieldCooldown = 0f;
    public static final float SHIELD_DURATION = 0.85f;
    public static final float SHIELD_COOLDOWN = 5.0f;
    public static final float SHIELD_COOLDOWN_MISS = 1.0f;
    public float knockbackDx = 0f;
    public float knockbackDy = 0f;
    public float knockbackTimer = 0f;
    public float speedBoostTimer = 0f;
    public float speedBoostAmount = 0f;
    private TextureRegion shieldFrontRegion;
    private TextureRegion shieldSideRegion;
    private TextureRegion copperHelmetWearRegion;
    private TextureRegion copperHelmetWearUpRegion;
    private TextureRegion copperChestplateWearRegion;
    private TextureRegion copperLeggingsWearRegion;
    private TextureRegion frostHelmetWearRegion;
    private TextureRegion frostHelmetWearUpRegion;
    private TextureRegion frostHelmetWearRightLeftRegion;

    public Player() {
        super(0, 0, BASE_SPEED);

        solidArea.set(HIT_OFFSET_X, HIT_OFFSET_Y, HIT_W, HIT_H);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();

        addItem(new Sword());
        addItem(new ItemPotion());
        addItem(new OddMushroom());
        addItem(new FrogBellyShield());
        addItem(new SpeedPotion());
        addItem(new CopperHelmet());
        addItem(new CopperChestplate());
        addItem(new CopperLeggings());
        addItem(new FrogEggs());
        addItem(new IceGem());
        addItem(new FrostHelmet());

    }

    public boolean hasSword() {
        for (Item item : inventory) {
            if (item == null) continue;
            if (item instanceof Sword || item instanceof devsword || item instanceof mushroomblade) {
                return true;
            }
            if (item.name != null && (item.name.toLowerCase().contains("espada") || item.name.toLowerCase().contains("sword"))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasMushroomBlade() {
        for (Item item : inventory) {
            if (item == null) continue;
            if (item instanceof mushroomblade) return true;
            if (item.name != null && item.name.toLowerCase().contains("esporosa")) return true;
        }
        return false;
    }

    public boolean hasDevSword() {
        for (Item item : inventory) {
            if (item == null) continue;
            if (item instanceof devsword) return true;
            if (item.name != null && item.name.toLowerCase().contains("developer")) return true;
        }
        return false;
    }

    public Item getEquippedSword() {
        for (Item item : inventory) {
            if (item != null && (item instanceof devsword || (item.name != null && item.name.toLowerCase().contains("developer")))) {
                return item;
            }
        }
        for (Item item : inventory) {
            if (item != null && (item instanceof mushroomblade || (item.name != null && item.name.toLowerCase().contains("esporosa")))) {
                return item;
            }
        }
        for (Item item : inventory) {
            if (item != null && (item instanceof Sword || (item.name != null && (item.name.toLowerCase().contains("espada") || item.name.toLowerCase().contains("sword"))))) {
                return item;
            }
        }
        return null;
    }

    public int getAttackDamage() {
        if (hasDevSword()) {
            return 999;
        } else if (hasMushroomBlade()){
            return 4;
        }
        return 2;
    }

    public void attack(String targetDir, int damage) {
        if (!hasSword())
            return;
        if (attackCooldown > 0f)
            return;

        this.attackCooldown = hasDevSword() ? 0.25f : ATTACK_COOLDOWN;
        this.direction = targetDir;
        this.attacking = true;
        this.attackTimer = ATTACK_DURATION;

        if (currentMonsters == null)
            return;

        float attackW = 24f;
        float attackH = 24f;
        float attackX = worldX;
        float attackY = worldY;

        switch (direction) {
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

        for (int i = 0; i < currentMonsters.length; i++) {
            Entity m = currentMonsters[i];
            if (m != null && m.life > 0) {
                float mX = (m instanceof MON_mushroom) ? m.worldX + 130f : m.worldX + m.solidArea.x;
                float mY = (m instanceof MON_mushroom) ? m.worldY + 160f : m.worldY + m.solidArea.y;
                float mW = (m instanceof MON_mushroom) ? 90f : m.solidArea.width;
                float mH = (m instanceof MON_mushroom) ? 80f : m.solidArea.height;

                if (attackX < mX + mW && attackX + attackW > mX && attackY < mY + mH && attackY + attackH > mY) {
                    if (m instanceof MON_mushroom) {
                        ((MON_mushroom) m).takeDamage(damage, worldX, worldY);
                    } else if (m instanceof MON_frogger) {
                        ((MON_frogger) m).takeDamage(damage, worldX, worldY);
                    } else if (m instanceof MON_Ghost) {
                        ((MON_Ghost) m).takeDamage(damage, worldX, worldY);
                    } else if (m instanceof MON_zombie) {
                        ((MON_zombie) m).takeDamage(damage, worldX, worldY);
                    } else if (m instanceof MON_bat) {
                        ((MON_bat) m).takeDamage(damage, worldX, worldY);
                    } else if (m instanceof MON_snowGhost) {
                        ((MON_snowGhost) m).takeDamage(damage, worldX, worldY);
                    } else {
                        m.life -= damage;
                        if (m.life <= 0) {
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
            upWalk[i - 1] = loadRegion("player/up" + i + ".png");
        }
        for (int i = 1; i <= 8; i++) {
            leftWalk[i - 1] = loadRegion("player/left" + i + ".png");
            rightWalk[i - 1] = loadRegion("player/right" + i + ".png");
        }
        for (int i = 0; i <= 8; i++) {
            idleAnim[i] = loadRegion("player/idle" + i + ".png");
        }

        attackUp = loadRegion("player/attack_up.png");
        attackDown = loadRegion("player/attack_down.png");
        attackLeft = loadRegion("player/attack_left.png");
        attackRight = loadRegion("player/attack_right.png");
        shieldFrontRegion = loadRegion("items/frog_belly_shield.png");
        shieldSideRegion = loadRegion("items/frog_belly_sides.png");
        copperHelmetWearRegion = loadRegion("items/copper_helmet_wear.png");
        copperHelmetWearUpRegion = loadRegion("items/copper_helmet_wear_up.png");
        copperChestplateWearRegion = loadRegion("items/copper_chestplate_wear.png");
        copperLeggingsWearRegion = loadRegion("items/copper_leggings_wear.png");
        frostHelmetWearRegion = loadRegion("items/frost_helmet_wear.png");
        frostHelmetWearUpRegion = loadRegion("items/frost_helmet_wear_up.png");
        frostHelmetWearRightLeftRegion = loadRegion("items/frost_helmet_wear_right_left.png");
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
                    if (checkCol >= 10 && checkCol < mapGen.mapCols - 10 && checkRow >= 10
                            && checkRow < mapGen.mapRows - 10) {
                        if (!mapGen.isDeepWater[checkCol][checkRow] && !mapGen.isShallowWater[checkCol][checkRow]
                                && mapGen.mapTreeNum[checkCol][checkRow] == 0) {
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
    }

    public void update(float delta, MapGenerator mapGen, TileManager tileM) {
        update(delta, mapGen, tileM, null);
    }

    public void update(float delta, MapGenerator mapGen, TileManager tileM, Entity[] monsters) {
        if (dead)
            return;

        this.currentMonsters = monsters;

        if (attackCooldown > 0f) {
            attackCooldown -= delta;
        }

        if (shieldCooldown > 0f) {
            shieldCooldown -= delta;
            if (shieldCooldown < 0f) shieldCooldown = 0f;
        }

        if (attacking) {
            attackTimer -= delta;
            if (attackTimer <= 0f) {
                attacking = false;
            }
        }

        if (isShielding) {
            shieldTimer -= delta;
            if (shieldTimer <= 0f) {
                isShielding = false;
                shieldCooldown = SHIELD_COOLDOWN_MISS;
            }
        }

        if (speedBoostTimer > 0f) {
            speedBoostTimer -= delta;
            if (speedBoostTimer <= 0f) {
                speedBoostTimer = 0f;
                speedBoostAmount = 0f;
            }
        }

        int footCol = (int) ((worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int footRow = (int) ((worldY + HIT_OFFSET_Y + HIT_H / 2f) / TileManager.TILE_SIZE);
        int waterLevel = tileM.getWaterLevel(mapGen, footCol, footRow);

        boolean inBush = tileM.isPlayerInBush(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H);
        isHiddenInBush = inBush;

        if (waterLevel == 2) {
            speed = DEEP_SPEED + (speedBoostTimer > 0f ? speedBoostAmount * 0.5f : 0f);
        } else if (waterLevel == 1) {
            speed = SHALLOW_SPEED + (speedBoostTimer > 0f ? speedBoostAmount * 0.75f : 0f);
        } else if (inBush) {
            speed = BUSH_SPEED + (speedBoostTimer > 0f ? speedBoostAmount * 0.75f : 0f);
        } else {
            speed = BASE_SPEED + (speedBoostTimer > 0f ? speedBoostAmount : 0f);
        }

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

        if (monsters != null) {
            int monsterIndex = checkMonster(monsters);
            contactMonster(monsterIndex);
        }

        if (knockbackTimer > 0f) {
            knockbackTimer -= delta;
            float kbSpeed = 340f;
            float kbX = knockbackDx * kbSpeed * delta;
            float kbY = knockbackDy * kbSpeed * delta;
            if (kbX != 0f) {
                float targetX = worldX + kbX;
                if (!tileM.collidesWithTree(mapGen, targetX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H)) {
                    worldX = targetX;
                }
            }
            if (kbY != 0f) {
                float targetY = worldY + kbY;
                if (!tileM.collidesWithTree(mapGen, worldX + HIT_OFFSET_X, targetY + HIT_OFFSET_Y, HIT_W, HIT_H)) {
                    worldY = targetY;
                }
            }
        }

        float inputX = 0f;
        float inputY = 0f;
        if (Gdx.input.isKeyPressed(Keys.W))
            inputY -= 1f;
        if (Gdx.input.isKeyPressed(Keys.S))
            inputY += 1f;
        if (Gdx.input.isKeyPressed(Keys.A))
            inputX -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D))
            inputX += 1f;

        if (knockbackTimer <= 0f && (inputX != 0f || inputY != 0f)) {
            moving = true;

            if (!attacking && !isShielding) {
                if (inputY < 0f)
                    direction = "up";
                else if (inputY > 0f)
                    direction = "down";
                else if (inputX < 0f)
                    direction = "left";
                else if (inputX > 0f)
                    direction = "right";
            }

            float inputLength = (float) Math.hypot(inputX, inputY);
            float moveX = (inputX / inputLength) * speed * delta;
            float moveY = (inputY / inputLength) * speed * delta;

            float prevX = worldX;
            float prevY = worldY;

            if (inputX != 0f) {
                float targetX = worldX + moveX;
                float hitBoxX = targetX + HIT_OFFSET_X;
                float hitBoxY = worldY + HIT_OFFSET_Y;
                if (!tileM.collidesWithTree(mapGen, hitBoxX, hitBoxY, HIT_W, HIT_H)) {
                    worldX = targetX;
                }
            }

            if (inputY != 0f) {
                float targetY = worldY + moveY;
                float hitBoxX = worldX + HIT_OFFSET_X;
                float hitBoxY = targetY + HIT_OFFSET_Y;
                if (!tileM.collidesWithTree(mapGen, hitBoxX, hitBoxY, HIT_W, HIT_H)) {
                    worldY = targetY;
                }
            }

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

                boolean nowInBush = tileM.isPlayerInBush(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W,
                        HIT_H);
                boolean nowInTallGrass = tileM.isPlayerInTallGrass(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y,
                        HIT_W, HIT_H);

                if ((!wasInBush && nowInBush) || (wasInBush && !nowInBush) || (!wasInTallGrass && nowInTallGrass)) {
                    if (stepListener != null) {
                        stepListener.onBushRustle();
                    }
                }
                wasInBush = nowInBush;
                wasInTallGrass = nowInTallGrass;
            }

            tileM.checkBushInteraction(mapGen, worldX + HIT_OFFSET_X, worldY + HIT_OFFSET_Y, HIT_W, HIT_H, moveX,
                    moveY);

            spriteCounter += delta * 60f;
            if (direction.equals("up") || direction.equals("down")) {
                if (spriteCounter >= 2.5f) {
                    spriteNum++;
                    if (spriteNum > 13)
                        spriteNum = 1;
                    spriteCounter = 0;
                }
            } else {
                if (spriteCounter >= 5f) {
                    spriteNum++;
                    if (spriteNum > 8)
                        spriteNum = 1;
                    spriteCounter = 0;
                }
            }
        } else {
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
                    if (spriteNum > 9)
                        spriteNum = 1;
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

    public int checkMonster(Entity[] monsters) {
        if (monsters == null)
            return 999;
        for (int i = 0; i < monsters.length; i++) {
            if (monsters[i] != null && monsters[i].dealsContactDamage() && intersects(monsters[i])) {
                return i;
            }
        }
        return 999;
    }

    public void contactMonster(int i) {
        if (i != 999 && i != -1) {
            if (currentMonsters != null && i < currentMonsters.length && currentMonsters[i] != null) {
                contactMonster(currentMonsters[i]);
            } else {
                contactMonster();
            }
        }
    }

    public void contactMonster(Entity monster) {
        if (monster != null) {
            if (isShielding) {
                triggerShieldBlock(monster);
                return;
            }
            if (knockbackTimer > 0f) {
                return;
            }
            contactMonster();
        }
    }

    public void contactMonster() {
        if (invincibleCounter > 0 || dead || knockbackTimer > 0f)
            return;

        if (isShielding) {
            triggerShieldBlock(null);
            return;
        }

        damage(MONSTER_DAMAGE);

        if (!dead) {
            invincibleCounter = INVINCIBLE_DURATION;
            invincible = true;
        }
    }

    public void damage(int amount) {
        if (invincibleCounter > 0 || dead || knockbackTimer > 0f)
            return;

        if (isShielding) {
            triggerShieldBlock(null);
            return;
        }

        int finalAmount = amount;
        int ironHearts = getIronHearts();
        if (ironHearts > 0 && amount > 1) {
            float reduction = ironHearts * 0.15f;
            finalAmount = Math.max(1, Math.round(amount * (1.0f - reduction)));
        }

        health -= finalAmount;
        healCooldown = 300;
        if (health <= 0) {
            health = 0;
            loseLife();
        }
    }

    public int getIronHearts() {
        int total = 0;
        int copperPieces = 0;
        if (helmetItem != null) {
            if (helmetItem instanceof CopperHelmet) copperPieces++;
            else total += helmetItem.ironHearts;
        }
        if (chestplateItem != null) {
            if (chestplateItem instanceof CopperChestplate) copperPieces++;
            else total += chestplateItem.ironHearts;
        }
        if (leggingsItem != null) {
            if (leggingsItem instanceof CopperLeggings) copperPieces++;
            else total += leggingsItem.ironHearts;
        }

        if (copperPieces == 3) {
            total += 2;
        } else if (copperPieces >= 1) {
            total += 1;
        }

        return Math.max(0, Math.min(5, total));
    }

    public void triggerShieldBlock(Entity source) {
        if (source != null) {
            float dx = (worldX + HIT_OFFSET_X + HIT_W / 2f) - (source.worldX + source.solidArea.x + source.solidArea.width / 2f);
            float dy = (worldY + HIT_OFFSET_Y + HIT_H / 2f) - (source.worldY + source.solidArea.y + source.solidArea.height / 2f);
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 0.001f) {
                knockbackDx = dx / dist;
                knockbackDy = dy / dist;
            } else {
                knockbackDx = "left".equals(direction) ? 1f : -1f;
                knockbackDy = 0f;
            }
        } else {
            float kx = 0f;
            float ky = 0f;
            switch (direction) {
                case "up": ky = 1f; break;
                case "down": ky = -1f; break;
                case "left": kx = 1f; break;
                case "right": kx = -1f; break;
            }
            knockbackDx = kx;
            knockbackDy = ky;
        }
        knockbackTimer = 0.25f;
        invincible = false;
        invincibleCounter = 0;
        isShielding = false;
        shieldTimer = 0f;
        shieldCooldown = SHIELD_COOLDOWN;
    }

    public boolean hasShieldEquipped() {
        return offsetItem != null && (offsetItem instanceof FrogBellyShield || (offsetItem.name != null && offsetItem.name.toLowerCase().contains("escudo")));
    }

    public void block(String dir) {
        if (!hasShieldEquipped() || shieldCooldown > 0f || isShielding)
            return;
        this.direction = dir;
        this.isShielding = true;
        this.shieldTimer = SHIELD_DURATION;
    }

    public void applySpeedBoost(float amount, float duration) {
        this.speedBoostAmount = amount;
        this.speedBoostTimer = Math.min(60f, this.speedBoostTimer + duration);
    }

    public void heal(int amount) {
        if (dead)
            return;
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
        if (drownCounter == 0)
            return 0f;
        return (float) drownCounter / DROWN_TICKS_DEEP;
    }

    public float getHealthRatio() {
        if (maxHealth <= 0)
            return 0f;
        return Math.max(0f, Math.min(1f, (float) health / maxHealth));
    }

    public TextureRegion getCurrentSprite() {
        if (attacking) {
            switch (direction) {
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

    public TextureRegion getShadowSprite() {
        switch (direction) {
            case "up":
                return upWalk[Math.max(0, Math.min(12, spriteNum - 1))];
            case "left":
                return leftWalk[Math.max(0, Math.min(7, spriteNum - 1))];
            case "right":
                return rightWalk[Math.max(0, Math.min(7, spriteNum - 1))];
            case "down":
            default:
                if (!moving) {
                    return idleAnim[Math.max(0, Math.min(8, spriteNum - 1))];
                } else {
                    return downWalk[Math.max(0, Math.min(12, spriteNum - 1))];
                }
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        draw(batch, 0, null);
    }

    public int getWaterYShift(int waterLevel) {
        if (waterLevel == 1)
            return 4;
        if (waterLevel == 2)
            return 4 + (int) (getDrownProgress() * 8);
        return 0;
    }

    public int getWaterCutOff(int waterLevel) {
        if (waterLevel == 1)
            return 16;
        if (waterLevel == 2)
            return 16 + (int) (getDrownProgress() * 16);
        return 0;
    }

    public void draw(SpriteBatch batch, int waterLevel, TextureRegion foamRegion) {
        TextureRegion current = getCurrentSprite();
        if (current == null)
            return;

        if (invincibleCounter > 0 && (invincibleCounter / 10) % 2 == 0) {
            batch.setColor(1f, 1f, 1f, 0.4f);
        } else {
            batch.setColor(1f, 1f, 1f, 1f);
        }

        float baseY = worldY + getWaterYShift(waterLevel);
        float drawY = baseY;

        float drawX = worldX;
        float drawW = current.getRegionWidth() * 3f;
        float drawH = current.getRegionHeight() * 3f;

        if (attacking) {
            if ("left".equals(direction)) {
                drawX = worldX - (drawW - TileManager.TILE_SIZE);
            } else if ("up".equals(direction)) {
                drawY = worldY - (drawH - TileManager.TILE_SIZE);
            }
        }

        if ((attacking && "up".equals(direction)) || (isShielding && "up".equals(direction))) {
            if (attacking && "up".equals(direction)) {
                drawAttackSword(batch, drawX, drawY);
            }
            if (isShielding && "up".equals(direction)) {
                drawShield(batch, worldX, baseY);
            }
        }

        batch.draw(current, drawX, drawY, drawW, drawH);

        drawArmor(batch, worldX, baseY);

        if (attacking && !"up".equals(direction)) {
            drawAttackSword(batch, drawX, drawY);
        }
        if (isShielding && !"up".equals(direction)) {
            drawShield(batch, worldX, baseY);
        }

        if (waterLevel > 0 && foamRegion != null) {
            int cutOff = getWaterCutOff(waterLevel);
            float foamY = drawY + TileManager.TILE_SIZE - cutOff - 3f;
            batch.setColor(1f, 1f, 1f, 0.9f);
            batch.draw(foamRegion, worldX, foamY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawArmor(SpriteBatch batch, float baseX, float baseY) {
        float scaleX = "right".equals(direction) ? -1f : 1f;

        if (leggingsItem != null && copperLeggingsWearRegion != null) {
            float legX = baseX + 12f;
            float legY = baseY + 39f;
            float legW = 24f;
            float legH = 6f;
            batch.draw(copperLeggingsWearRegion, legX, legY, legW / 2f, legH / 2f, legW, legH, scaleX, 1f, 0f);
        }

        if (chestplateItem != null && copperChestplateWearRegion != null) {
            float chestX = baseX + 6f;
            float chestY = baseY + 27f;
            float chestW = 36f;
            float chestH = 15f;
            batch.draw(copperChestplateWearRegion, chestX, chestY, chestW / 2f, chestH / 2f, chestW, chestH, scaleX, 1f, 0f);
        }

        if (helmetItem instanceof FrostHelmet) {
            if ("up".equals(direction) && frostHelmetWearUpRegion != null) {
                float helmX = baseX + 3f;
                float helmY = baseY + 3f;
                float helmW = 42f;
                float helmH = 30f;
                batch.draw(frostHelmetWearUpRegion, helmX, helmY, helmW, helmH);
            } else if (("left".equals(direction) || "right".equals(direction)) && frostHelmetWearRightLeftRegion != null) {
                float helmX = "left".equals(direction) ? (baseX + 6f) : (baseX + 3f);
                float helmY = baseY + 3f;
                float helmW = 39f;
                float helmH = 30f;
                float helmScaleX = "left".equals(direction) ? -1f : 1f;
                batch.draw(frostHelmetWearRightLeftRegion, helmX, helmY, helmW / 2f, helmH / 2f, helmW, helmH, helmScaleX, 1f, 0f);
            } else if (frostHelmetWearRegion != null) {
                float helmX = baseX + 3f;
                float helmY = baseY + 3f;
                float helmW = 42f;
                float helmH = 30f;
                batch.draw(frostHelmetWearRegion, helmX, helmY, helmW, helmH);
            }
        } else if (helmetItem != null) {
            if ("up".equals(direction) && copperHelmetWearUpRegion != null) {
                float helmX = baseX + 9f;
                float helmY = baseY + 0f;
                float helmW = 30f;
                float helmH = 21f;
                batch.draw(copperHelmetWearUpRegion, helmX, helmY, helmW, helmH);
            } else if (copperHelmetWearRegion != null) {
                float helmX = baseX + 9f;
                float helmY = baseY + 0f;
                float helmW = 30f;
                float helmH = 15f;
                batch.draw(copperHelmetWearRegion, helmX, helmY, helmW / 2f, helmH / 2f, helmW, helmH, scaleX, 1f, 0f);
            }
        }
    }

    private void drawAttackSword(SpriteBatch batch, float drawX, float drawY) {
        Item sword = getEquippedSword();
        if (sword == null || sword.icon == null) return;

        TextureRegion icon = sword.icon;
        float swordW = icon.getRegionWidth() * 3f;
        float swordH = icon.getRegionHeight() * 3f;

        if (swordW >= 36f || swordH >= 36f) {
            float maxDim = Math.max(swordW, swordH);
            float scale = 26f / maxDim;
            swordW *= scale;
            swordH *= scale;
        }

        float swordX = drawX;
        float swordY = drawY;
        float scaleX = 1f;
        float scaleY = 1f;
        float rotation = 0f;
        float halfDiag = 0.7071f * swordW;

        switch (direction) {
            case "right":
                scaleX = -1f;
                scaleY = 1f;
                rotation = 0f;
                swordX = drawX + 39f;
                swordY = drawY + 33f - swordH;
                break;
            case "left":
                scaleX = 1f;
                scaleY = 1f;
                rotation = 0f;
                swordX = drawX + 34f - swordW;
                swordY = drawY + 33f - swordH;
                break;
            case "down":
                scaleX = 1f;
                scaleY = 1f;
                rotation = -135f;
                float worldOriginXDown = drawX + 9f;
                float worldOriginYDown = drawY + 38f + halfDiag;
                swordX = worldOriginXDown - swordW / 2f;
                swordY = worldOriginYDown - swordH / 2f;
                break;
            case "up":
                scaleX = 1f;
                scaleY = 1f;
                rotation = 45f;
                float worldOriginXUp = drawX + 40f;
                float worldOriginYUp = drawY + 25f - halfDiag;
                swordX = worldOriginXUp - swordW / 2f;
                swordY = worldOriginYUp - swordH / 2f;
                break;
        }

        batch.draw(icon, swordX, swordY, swordW / 2f, swordH / 2f, swordW, swordH, scaleX, scaleY, rotation);
    }

    private void drawShield(SpriteBatch batch, float baseX, float baseY) {
        if (!hasShieldEquipped()) return;

        float sW = 38f;
        float sH = 38f;

        switch (direction) {
            case "down":
                if (shieldFrontRegion != null) {
                    float sX = baseX + (TileManager.TILE_SIZE - sW) / 2f;
                    float sY = baseY + 12f;
                    batch.draw(shieldFrontRegion, sX, sY, sW, sH);
                }
                break;
            case "up":
                if (shieldFrontRegion != null) {
                    float sX = baseX + (TileManager.TILE_SIZE - sW) / 2f;
                    float sY = baseY + 8f;
                    batch.draw(shieldFrontRegion, sX, sY, sW, sH);
                }
                break;
            case "left":
                if (shieldSideRegion != null) {
                    float sX = baseX - 1f;
                    float sY = baseY + 10f;
                    batch.draw(shieldSideRegion, sX, sY, sW, sH);
                }
                break;
            case "right":
                if (shieldSideRegion != null) {
                    float sX = baseX + 11f;
                    float sY = baseY + 10f;
                    batch.draw(shieldSideRegion, sX, sY, sW / 2f, sH / 2f, sW, sH, -1f, 1f, 0f);
                }
                break;
        }
    }

    public final ArrayList<Item> inventory = new ArrayList<>();
    public final int MAX_INVENTORY_SIZE = 20;

    public boolean addItem(Item item) {
        if (item == null) return false;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i) == null) {
                inventory.set(i, item);
                return true;
            }
        }
        if (inventory.size() < MAX_INVENTORY_SIZE) {
            inventory.add(item);
            return true;
        }
        return false;
    }

    public boolean tryAssembleBoat() {
        boolean hasPiece1 = false, hasPiece2 = false, hasPiece3 = false;
        for (Item it : inventory) {
            if (it == null) continue;
            if (it instanceof BoatPiece1) hasPiece1 = true;
            if (it instanceof BoatPiece2) hasPiece2 = true;
            if (it instanceof BoatPiece3) hasPiece3 = true;
        }
        if (!hasPiece1 || !hasPiece2 || !hasPiece3) return false;

        inventory.removeIf(it -> it != null && (it instanceof BoatPiece1 || it instanceof BoatPiece2 || it instanceof BoatPiece3));
        addItem(new boat());
        return true;
    }

    public boolean hasBoat() {
        for (Item it : inventory) {
            if (it == null) continue;
            if (it instanceof boat) return true;
            if (it.name != null && (it.name.equalsIgnoreCase("Barco") || it.name.equalsIgnoreCase("Bote") || it.name.equalsIgnoreCase("boat"))) {
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
