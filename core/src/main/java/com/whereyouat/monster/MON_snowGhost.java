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
import com.whereyouat.map.TileManager;
import com.whereyouat.screens.GameScreen;

public class MON_snowGhost extends Entity {
    
    public GameScreen gp;
    public String name;

    // Dimensiones para dibujarlo
    public static final float DRAW_WIDTH = 192f;
    public static final float DRAW_HEIGHT = 128f;

    // Estados
    public static final int STATE_IDLE = 0;
    public static final int STATE_RUN = 1;
    public static final int STATE_ATTACK = 2;
    public static final int STATE_HIT = 3;
    public static final int STATE_DIE = 4;

    private float knockbackDx = 0f;
    private float knockbackDy = 0f;
    private float hitTimer = 0f;
    private float deathTimer = 0.5f;
    private static final float HIT_DURATION = 0.25f; // Dura un cuarto de segundo el retroceso

    private float attackCooldown = 0f;
    private static final float ATTACK_COOLDOWN = 1.5f; // Tiempo de espera entre ataques (1.5 segundos)
    private static final float ATTACK_RANGE = 70f; // Rango de atque cuerpo a cuerpo (70 pixeles)
    private float attackTimer = 0f;
    private static final float ATTACK_DURATION = 0.4f; // Cuánto dura la animación de embestida
    private boolean attackDamageDealt = false;

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
        super(0, 0, 120f); // worldX, worldY, speed
        this.gp = gp;
        this.name = "Espectro de las Nieves";
        this.maxLife = 12;
        this.life = maxLife;

        // hitbox sólida
        solidArea.set(70f, 75f, 52f, 35f);
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

        // Corta la hoja de celdas de 192 x 128px
        TextureRegion[][] sheet = TextureRegion.split(texture, 192, 128);

        // Fila 0: Idle (6 frames)
        idleFrames = extractRow(sheet, 0, 6);

        // Fila 1: Walk (10 frames)
        runFrames = extractRow(sheet, 1, 10);

        // Fila 2: Attack (14 frames)
        attackFrames = extractRow(sheet, 2, 14);

        // Fila 3: Take Hit (7 frames)
        hitFrames = extractRow(sheet, 3, 7);

        // Fila 4: Death (16 frames)
        dieFrames = extractRow(sheet, 4, 16);
    }

    private TextureRegion[][] extractRow(TextureRegion[][] sheet, int row, int count) {
        TextureRegion[][] frames = new TextureRegion[2][count];
        for (int i = 0; i < count; i++) {
            // Mirando a la derecha (sprite original con Y invertido para el juego)
            TextureRegion right = new TextureRegion(sheet[row][i]);
            right.flip(true, true);

            // Mirando a la izquierda (volteado horizontalmente)
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
            deathTimer -= delta;
            if (deathTimer <= 0f) {
                deleteBoss();
            }
            updateAnimation(delta);
            return;
        }

        if (attackCooldown > 0f) {
            attackCooldown -= delta;
        }

        if (state == STATE_ATTACK) {
            attackTimer -= delta;

            // Si aún no infligió daño en este golpe
            if (!attackDamageDealt) {
                float ghostCenterX = worldX + solidArea.x + solidArea.width / 2f;
                float ghostCenterY = worldY + solidArea.y + solidArea.height / 2f;
                float playerCenterX = gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2f;
                float playerCenterY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2f;

                if (Math.hypot(playerCenterX - ghostCenterX, playerCenterY - ghostCenterY) <= ATTACK_RANGE + 20f || intersects(gp.player)) {
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

        // Si está en retroceso por recibir daño
        if (state == STATE_HIT) {
            float knockbackSpeed = 140f;
            float moveX = knockbackDx * knockbackSpeed * delta;
            float moveY = knockbackDy * knockbackSpeed * delta;

            if (!collides(worldX + moveX, worldY)) worldX += moveX;
            if (!collides(worldX, worldY + moveY)) worldY += moveY;
            clampToBounds();

            hitTimer -= delta;
            if (hitTimer <= 0f) {
                state = STATE_RUN; // Termina el retroceso y vuelve a perseguir
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
            float playerCenterY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height     / 2f;

            float deltaX = playerCenterX - ghostCenterX;
            float deltaY = playerCenterY - ghostCenterY;
            float distanceToPlayer = (float) Math.hypot(deltaX, deltaY);

            // 1. Orientar la mirada según la dirección dominante
            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                direction = (deltaX < 0) ? "left" : "right";
            } else {
                direction = (deltaY < 0) ? "up" : "down";
            }

            // Actualizar hacia qué lado horizontal debe mirar el cuerpo
            if (deltaX != 0) {
                lastHorizontal = (deltaX < 0) ? "left" : "right";
            }

            // 2. Si está en rango de detección (1000 px), lo persigue
            if (distanceToPlayer <= 1000f && distanceToPlayer > 1f) {
                state = STATE_RUN;

                if (distanceToPlayer <= ATTACK_RANGE && attackCooldown <= 0f) {
                    state = STATE_ATTACK;
                    attackTimer = ATTACK_DURATION;
                    attackDamageDealt = false;
                } else {
                    float moveX = (deltaX / distanceToPlayer) * speed * delta;
                    float moveY = (deltaY / distanceToPlayer) * speed * delta;

                    boolean movedX = false;
                    boolean movedY = false;

                    // Intenta mover en X
                    if (!collides(worldX + moveX, worldY)) {
                        worldX += moveX;
                        movedX = true;
                    }

                    // Intenta mover en Y
                    if (!collides(worldX, worldY + moveY)) {
                        worldY += moveY;
                        movedY = true;
                    }

                    // 3. Evasión de árboles/rocas: si choca de frente, se desliza lateralmente
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
                } else {
                    state = STATE_IDLE;
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
                    spriteNum = maxFrames; // Se queda en el último frame al morir
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

        // spriteNum va de 1 a 8, el índice del array va de 0 a 7
        int frameIndex = Math.max(0, Math.min(maxFrames - 1, spriteNum - 1));
        TextureRegion region = sheet[side][frameIndex];

        if (region != null) {
            batch.draw(region, worldX, worldY, DRAW_WIDTH, DRAW_HEIGHT);
        }
    }

    public void takeDamage(int damage, float playerX, float playerY) {
        if (state == STATE_DIE || life <= 0) return;

        this.life -= damage;
        Gdx.app.log("Espectro de las Nieves", "Vida restante: " + life + "/" + maxLife);

        if (this.life <= 0) {
            state = STATE_DIE;
        } else {
            state = STATE_HIT;
            hitTimer = HIT_DURATION;

            // Calcular dirección del empuje (alejándose del jugador)
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

    private void deleteBoss() {
            // 1. Entregar ek Drop al jugador
            if (gp != null && gp.player != null) {
                gp.player.addItem(new com.whereyouat.items.IceGem());
                Gdx.app.log("Espectro de las Nieves", "¡El jefe fue derrotado! Has obtenido una Gema de Hielo.");
            }

            // 2. Remover del array de monstruos del mundo
            if (gp != null && gp.monster != null) {
                for (int i = 0; i < gp.monster.length; i++) {
                    if (gp.monster[i] == this) {
                        gp.monster[i] = null;
                        break;
                    }
                }
            }

            // 3. Liberar recursos gráficos
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

