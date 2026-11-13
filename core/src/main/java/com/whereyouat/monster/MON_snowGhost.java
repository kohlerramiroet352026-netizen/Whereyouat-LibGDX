package com.whereyouat.monster;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.whereyouat.entity.Entity;
import com.whereyouat.items.BoatPiece3;
import com.whereyouat.items.DropManager;
import com.whereyouat.map.TileManager;
import com.whereyouat.screens.GameScreen;

public class MON_snowGhost extends Entity {
    public GameScreen gp;
    public String name;

    private static final float FRAME_W_NATIVE = 192f;
    private static final float FRAME_H_NATIVE = 128f;
    private static final float SCALE = 3.0f;
    public static final float DRAW_WIDTH = FRAME_W_NATIVE * SCALE;
    public static final float DRAW_HEIGHT = FRAME_H_NATIVE * SCALE;
    private static final float FEET_Y = 109f * SCALE;

    public static final int STATE_IDLE = 0;
    public static final int STATE_RUN = 1;
    public static final int STATE_ATTACK = 2;
    public static final int STATE_HIT = 3;
    public static final int STATE_DIE = 4;

    private float knockbackDx = 0f;
    private float knockbackDy = 0f;
    private float hitTimer = 0f;
    private static final float HIT_DURATION = 0.25f;

    private float attackCooldown = 0f;
    private static final float ATTACK_COOLDOWN = 2.5f;
    private static final float ATTACK_RANGE  = 220f;
    private static final float KEEP_DISTANCE = 180f;
    private static final int   DAMAGE_FRAME  = 7;

    private static final float ATTACK_DURATION = 14 * (5f / 60f) + 0.05f;
    private float attackTimer = 0f;
    private boolean attackDamageDealt = false;

    private boolean rewardDelivered = false;

    public int state = STATE_IDLE;
    public int previousState = STATE_IDLE;

    private final List<Texture> textures = new ArrayList<>();

    private static final int SIDE_LEFT = 0;
    private static final int SIDE_RIGHT = 1;
    private String lastHorizontal = "right";

    private TextureRegion[][] idleFrames;
    private TextureRegion[][] runFrames;
    private TextureRegion[][] attackFrames;
    private TextureRegion[][] hitFrames;
    private TextureRegion[][] dieFrames;

    public MON_snowGhost(GameScreen gp) {
        super(0, 0, 120f);
        this.gp = gp;
        this.name = "Espectro de las Nieves";
        this.maxLife = 24;
        this.life = maxLife;

        float hitW = 52f * SCALE;
        float hitH = 35f * SCALE;
        solidArea.set(
                (DRAW_WIDTH - hitW) / 2f,
                FEET_Y - hitH,
                hitW,
                hitH);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();
    }

    @Override
    public boolean dealsContactDamage() {
        return false;
    }

    private void loadSprites() {
        Texture texture = new Texture(Gdx.files.internal("monster/frost_guardian_free_192x128_SpriteSheet.png"));
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(texture);

        TextureRegion[][] sheet = TextureRegion.split(texture, 192, 128);

        idleFrames = extractRow(sheet, 0, 6);

        runFrames = extractRow(sheet, 1, 10);

        attackFrames = extractRow(sheet, 2, 14);

        hitFrames = extractRow(sheet, 3, 7);

        dieFrames = extractRow(sheet, 4, 16);
    }

    private TextureRegion[][] extractRow(TextureRegion[][] sheet, int row, int count) {
        TextureRegion[][] frames = new TextureRegion[2][count];
        for (int i = 0; i < count; i++) {
            TextureRegion right = new TextureRegion(sheet[row][i]);
            right.flip(true, true);

            TextureRegion left = new TextureRegion(sheet[row][i]);
            left.flip(false, true);

            frames[SIDE_RIGHT][i] = right;
            frames[SIDE_LEFT][i] = left;
        }
        return frames;
    }

    @Override
    public void update(float delta) {
        if (state == STATE_DIE) {
            updateAnimation(delta);
            return;
        }

        if (attackCooldown > 0f) {
            attackCooldown -= delta;
        }

        if (state == STATE_ATTACK) {
            attackTimer -= delta;

            if (!attackDamageDealt && spriteNum >= DAMAGE_FRAME && gp != null && gp.player != null) {
                float ghostCenterX = worldX + solidArea.x + solidArea.width / 2f;
                float ghostCenterY = worldY + solidArea.y + solidArea.height / 2f;
                float playerCenterX = gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2f;
                float playerCenterY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2f;
                float dist = (float) Math.hypot(playerCenterX - ghostCenterX, playerCenterY - ghostCenterY);

                if (dist <= ATTACK_RANGE || intersects(gp.player)) {
                    gp.player.contactMonster();
                    attackDamageDealt = true;
                }
            }

            if (attackTimer <= 0f) {
                state = STATE_RUN;
                attackCooldown = ATTACK_COOLDOWN;
            }

            updateAnimation(delta);
            return;
        }

        if (state == STATE_HIT) {
            float knockbackSpeed = 140f;
            float moveX = knockbackDx * knockbackSpeed * delta;
            float moveY = knockbackDy * knockbackSpeed * delta;

            if (!collides(worldX + moveX, worldY)) worldX += moveX;
            if (!collides(worldX, worldY + moveY)) worldY += moveY;
            clampToBounds();

            hitTimer -= delta;
            if (hitTimer <= 0f) {
                state = STATE_RUN;
            }

            updateAnimation(delta);
            return;
        }

        if (gp == null || gp.player == null || gp.player.dead) {
            state = STATE_IDLE;
        } else {
            float ghostCenterX = worldX + solidArea.x + solidArea.width / 2f;
            float ghostCenterY = worldY + solidArea.y + solidArea.height / 2f;

            float playerCenterX = gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2f;
            float playerCenterY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2f;

            float deltaX = playerCenterX - ghostCenterX;
            float deltaY = playerCenterY - ghostCenterY;
            float distanceToPlayer = (float) Math.hypot(deltaX, deltaY);

            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                direction = (deltaX < 0) ? "left" : "right";
            } else {
                direction = (deltaY < 0) ? "up" : "down";
            }
            if (deltaX != 0) {
                lastHorizontal = (deltaX < 0) ? "left" : "right";
            }

            if (distanceToPlayer > 1000f) {
                state = STATE_IDLE;
            } else if (distanceToPlayer <= ATTACK_RANGE && attackCooldown <= 0f) {
                state = STATE_ATTACK;
                attackTimer = ATTACK_DURATION;
                attackDamageDealt = false;
                spriteNum = 1;
                spriteCounter = 0;
            } else if (distanceToPlayer <= KEEP_DISTANCE) {
                state = STATE_IDLE;
            } else {
                state = STATE_RUN;

                float moveX = (deltaX / distanceToPlayer) * speed * delta;
                float moveY = (deltaY / distanceToPlayer) * speed * delta;

                boolean movedX = false;
                boolean movedY = false;

                if (!collides(worldX + moveX, worldY)) {
                    worldX += moveX;
                    movedX = true;
                }
                if (!collides(worldX, worldY + moveY)) {
                    worldY += moveY;
                    movedY = true;
                }

                if (!movedX && !movedY) {
                    float slideStep = speed * delta;
                    if (Math.abs(deltaY) >= Math.abs(deltaX)) {
                        if (!collides(worldX + slideStep, worldY)) worldX += slideStep;
                        else if (!collides(worldX - slideStep, worldY)) worldX -= slideStep;
                    } else {
                        if (!collides(worldX, worldY + slideStep)) worldY += slideStep;
                        else if (!collides(worldX, worldY - slideStep)) worldY -= slideStep;
                    }
                }

                clampToBounds();
            }
        }

        updateAnimation(delta);
    }

    private boolean collides(float x, float y) {
        if (gp == null || gp.tileM == null || gp.mapGen == null) return false;
        return gp.tileM.collidesWithTree(gp.mapGen, x + solidArea.x, y + solidArea.y, solidArea.width, solidArea.height);
    }

    private void clampToBounds() {
        if (gp == null || gp.mapGen == null) return;
        int margin = 30;
        float minX = margin * TileManager.TILE_SIZE;
        float minY = margin * TileManager.TILE_SIZE;
        float maxX = (gp.mapGen.mapCols - margin) * TileManager.TILE_SIZE - DRAW_WIDTH;
        float maxY = (gp.mapGen.mapRows - margin) * TileManager.TILE_SIZE - DRAW_HEIGHT;
        worldX = Math.max(minX, Math.min(maxX, worldX));
        worldY = Math.max(minY, Math.min(maxY, worldY));
    }

    private void updateAnimation(float delta) {
        if (state != previousState) {
            previousState = state;
            spriteNum = 1;
            spriteCounter = 0;
        }

        int maxFrames;
        float animSpeed = 6f;

        switch (state) {
            case STATE_ATTACK:
                maxFrames = 14;
                animSpeed = 5f;
                break;
            case STATE_HIT:
                maxFrames = 7;
                animSpeed = 4f;
                break;
            case STATE_DIE:
                maxFrames = 16;
                animSpeed = 7f;
                break;
            case STATE_RUN:
                maxFrames = 10;
                animSpeed = 6f;
                break;
            default:
                maxFrames = 6;
                animSpeed = 6f;
                break;
        }

        spriteCounter += delta * 60f;
        if (spriteCounter >= animSpeed) {
            spriteNum++;
            if (spriteNum > maxFrames) {
                if (state == STATE_DIE) {
                    spriteNum = maxFrames;
                    deleteBoss();
                    return;
                } else if (state == STATE_ATTACK) {
                    spriteNum = maxFrames;
                } else {
                    spriteNum = 1;
                }
            }
            spriteCounter = 0;
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        int side = "right".equals(lastHorizontal) ? SIDE_RIGHT : SIDE_LEFT;
        TextureRegion[][] sheet;
        int maxFrames;

        switch (state) {
            case STATE_ATTACK:
                sheet = attackFrames;
                maxFrames = 14;
                break;
            case STATE_HIT:
                sheet = hitFrames;
                maxFrames = 7;
                break;
            case STATE_DIE:
                sheet = dieFrames;
                maxFrames = 16;
                break;
            case STATE_RUN:
                sheet = runFrames;
                maxFrames = 10;
                break;
            default:
                sheet = idleFrames;
                maxFrames = 6;
                break;
        }

        if (sheet == null) return;

        int frameIndex = Math.max(0, Math.min(maxFrames - 1, spriteNum - 1));
        TextureRegion region = sheet[side][frameIndex];
        if (region == null) return;

        int waterLevel = 0;
        if (gp != null && gp.tileM != null && gp.mapGen != null) {
            float footX = worldX + solidArea.x + solidArea.width / 2f;
            float footY = worldY + FEET_Y;
            int col = (int) (footX / TileManager.TILE_SIZE);
            int row = (int) (footY / TileManager.TILE_SIZE);
            waterLevel = gp.tileM.getWaterLevel(gp.mapGen, col, row);
        }

        if (waterLevel > 0 && gp != null && gp.camera != null) {
            float cutOff = (waterLevel == 1) ? 20f * SCALE : 38f * SCALE;
            float yShift  = (waterLevel == 1) ?  6f * SCALE : 14f * SCALE;
            float drawY    = worldY + yShift;
            float visibleH = FEET_Y - cutOff;

            Vector3 topLeft     = gp.camera.project(new Vector3(worldX, drawY, 0));
            Vector3 bottomRight = gp.camera.project(new Vector3(worldX + DRAW_WIDTH, drawY + visibleH, 0));

            int sx = (int) Math.min(topLeft.x, bottomRight.x);
            int sy = (int) Math.min(topLeft.y, bottomRight.y);
            int sw = (int) Math.abs(bottomRight.x - topLeft.x);
            int sh = (int) Math.abs(bottomRight.y - topLeft.y);

            batch.flush();
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(sx, sy, Math.max(1, sw), Math.max(1, sh));

            batch.draw(region, worldX, drawY, DRAW_WIDTH, DRAW_HEIGHT);

            batch.flush();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

            TextureRegion foam = gp.tileM.getFoamRegion();
            if (foam != null) {
                float foamY = drawY + visibleH - 4f;
                float bodyCenterX = worldX + solidArea.x + solidArea.width / 2f;
                int tileCount = 4;
                float foamTileSize = TileManager.TILE_SIZE;
                float startX = bodyCenterX - (tileCount * foamTileSize) / 2f;

                batch.setColor(1f, 1f, 1f, 0.9f);
                for (int i = 0; i < tileCount; i++) {
                    batch.draw(foam, startX + (i * foamTileSize), foamY, foamTileSize, foamTileSize);
                }
                batch.setColor(1f, 1f, 1f, 1f);
            }
        } else {
            batch.draw(region, worldX, worldY, DRAW_WIDTH, DRAW_HEIGHT);
        }
    }

    public void takeDamage(int damage, float playerX, float playerY) {
        if (state == STATE_DIE || life <= 0) return;

        this.life -= damage;
        Gdx.app.log("Espectro de las Nieves", "Vida restante: " + life + "/" + maxLife);

        if (this.life <= 0) {
            state = STATE_DIE;
            spriteNum = 1;
            spriteCounter = 0;
        } else {
            state = STATE_HIT;
            spriteNum = 1;
            spriteCounter = 0;
            hitTimer = HIT_DURATION;

            attackCooldown = Math.max(attackCooldown, ATTACK_COOLDOWN);

            float ghostCenterX = worldX + solidArea.x + solidArea.width / 2f;
            float ghostCenterY = worldY + solidArea.y + solidArea.height / 2f;
            float dx = ghostCenterX - playerX;
            float dy = ghostCenterY - playerY;
            float dist = (float) Math.hypot(dx, dy);

            if (dist > 0.001f) {
                knockbackDx = dx / dist;
                knockbackDy = dy / dist;
            } else {
                knockbackDx = 1f;
                knockbackDy = 0f;
            }
        }
    }

    public static boolean summon(GameScreen gp) {
        if (gp == null || gp.camera == null || gp.monster == null || gp.player == null || gp.mapGen == null) {
            return false;
        }

        int playerCol = (int) ((gp.player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int playerRow = (int) ((gp.player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);

        if (playerCol < 0 || playerCol >= gp.mapGen.mapCols || playerRow < 0 || playerRow >= gp.mapGen.mapRows) {
            return false;
        }

        if (!gp.mapGen.isSnowBiome[playerCol][playerRow] || gp.mapGen.isDeepWater[playerCol][playerRow]) {
            gp.showNotification("¡Debes estar en el bioma de nieve para usar la Gema de Hielo!");
            Gdx.app.log("Espectro de las Nieves", "Invocación rechazada: fuera del bioma de nieve.");
            return false;
        }

        for (Entity m : gp.monster) {
            if (m instanceof MON_snowGhost) {
                gp.showNotification("¡El Espectro de las Nieves ya ha sido invocado!");
                return false;
            }
        }

        int slotIndex = -1;
        for (int i = 0; i < gp.monster.length; i++) {
            if (gp.monster[i] == null) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) {
            gp.showNotification("No hay espacio en el mundo para invocar al jefe.");
            return false;
        }

        float halfW = (gp.camera.viewportWidth * gp.camera.zoom) / 2f;
        float halfH = (gp.camera.viewportHeight * gp.camera.zoom) / 2f;
        float centerX = gp.camera.position.x;
        float centerY = gp.camera.position.y;
        float margin = 70f;

        float spawnX = 0f;
        float spawnY = 0f;
        boolean positionFound = false;

        for (int attempt = 0; attempt < 8; attempt++) {
            int side = (int) (Math.random() * 4);
            switch (side) {
                case 0:
                    spawnX = centerX + (float) ((Math.random() - 0.5) * halfW * 1.5);
                    spawnY = centerY - halfH - DRAW_HEIGHT - margin;
                    break;
                case 1:
                    spawnX = centerX + (float) ((Math.random() - 0.5) * halfW * 1.5);
                    spawnY = centerY + halfH + margin;
                    break;
                case 2:
                    spawnX = centerX - halfW - DRAW_WIDTH - margin;
                    spawnY = centerY + (float) ((Math.random() - 0.5) * halfH * 1.5);
                    break;
                default:
                    spawnX = centerX + halfW + margin;
                    spawnY = centerY + (float) ((Math.random() - 0.5) * halfH * 1.5);
                    break;
            }

            int spawnCol = (int) ((spawnX + DRAW_WIDTH / 2f) / TileManager.TILE_SIZE);
            int spawnRow = (int) ((spawnY + DRAW_HEIGHT / 2f) / TileManager.TILE_SIZE);

            if (spawnCol >= 10 && spawnCol < gp.mapGen.mapCols - 10 && spawnRow >= 10 && spawnRow < gp.mapGen.mapRows - 10) {
                if (gp.mapGen.isSnowBiome[spawnCol][spawnRow] && !gp.mapGen.isDeepWater[spawnCol][spawnRow]) {
                    positionFound = true;
                    break;
                }
            }
        }

        if (!positionFound) {
            spawnX = gp.player.worldX + TileManager.TILE_SIZE * 4f;
            spawnY = gp.player.worldY;
        }

        MON_snowGhost boss = new MON_snowGhost(gp);
        boss.worldX = spawnX;
        boss.worldY = spawnY;
        boss.clampToBounds();

        gp.monster[slotIndex] = boss;
        gp.showNotification("Sentis un escalofrio recorrer tu espalda, El Espectro ha aparecido...");
        Gdx.app.log("Espectro de las Nieves", "Invocado exitosamente en slot " + slotIndex);
        return true;
    }

    private void deleteBoss() {
        if (!rewardDelivered && gp != null) {
            rewardDelivered = true;
            DropManager.deliverBossReward("Espectro de las Nieves", new BoatPiece3(), gp);
        }

        if (gp != null && gp.monster != null) {
            for (int i = 0; i < gp.monster.length; i++) {
                if (gp.monster[i] == this) {
                    gp.monster[i] = null;
                    break;
                }
            }
        }

        dispose();
    }

    @Override
    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        textures.clear();
    }
}
