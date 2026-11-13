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

//jefe espectro de las nieves
public class MON_snowGhost extends Entity {

    public GameScreen gp;
    public String name;

    // Dimensiones del frame nativo y escala de renderizado
    private static final float FRAME_W_NATIVE = 192f;
    private static final float FRAME_H_NATIVE = 128f;
    private static final float SCALE = 3.0f; // Ajusta este valor para cambiar el tamaño en pantalla
    public static final float DRAW_WIDTH = FRAME_W_NATIVE * SCALE; // 576
    public static final float DRAW_HEIGHT = FRAME_H_NATIVE * SCALE; // 384

    // Estados de animación e IA
    public static final int STATE_IDLE = 0;
    public static final int STATE_RUN = 1;
    public static final int STATE_ATTACK = 2;
    public static final int STATE_HIT = 3;
    public static final int STATE_DIE = 4;

    private float knockbackDx = 0f;
    private float knockbackDy = 0f;
    private float hitTimer = 0f;
    private static final float HIT_DURATION = 0.25f; // Duración del retroceso

    private float attackCooldown = 0f;
    private static final float ATTACK_COOLDOWN = 2.5f;  // Enfriamiento entre ataques
    private static final float ATTACK_RANGE  = 220f;    // Distancia desde la que dispara el ataque
    private static final float KEEP_DISTANCE = 180f;    // Distancia mínima de seguridad (se queda parado)
    private static final int   DAMAGE_FRAME  = 7;       // Frame en que el golpe conecta (1-based)
    // ATTACK_DURATION se calcula dinámicamente para que cubra los 14 frames completos
    // animSpeed ataque = 5 ticks/frame → 14 frames × (5/60s) ≈ 1.167 s
    private static final float ATTACK_DURATION = 14 * (5f / 60f) + 0.05f; // ~1.22 s
    private float attackTimer = 0f;
    private boolean attackDamageDealt = false;

    // Control para evitar entrega múltiple de la recompensa del bote
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
        super(0, 0, 120f); // worldX, worldY, speed
        this.gp = gp;
        this.name = "Espectro de las Nieves";
        this.maxLife = 24;
        this.life = maxLife;

        // Hitbox sólida centrada en la base del sprite (escalada con SCALE)
        // solidArea.y + solidArea.height define el "pie" para el Y-sorting
        float hitW = 52f * SCALE;
        float hitH = 35f * SCALE;
        solidArea.set(
                (DRAW_WIDTH - hitW) / 2f,       // centrada horizontalmente
                DRAW_HEIGHT - hitH - 20f,        // pegada al borde inferior (pies)
                hitW,
                hitH);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();
    }

    @Override
    public boolean dealsContactDamage() {
        // Daña a través de su ataque cuerpo a cuerpo
        return false;
    }

    private void loadSprites() {
        Texture texture = new Texture(Gdx.files.internal("monster/frost_guardian_free_192x128_SpriteSheet.png"));
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(texture);

        // Corta la hoja de celdas de 192 x 128 px
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
            // Mirando a la derecha (sprite original con Y invertido para el sistema de coordenadas)
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
            updateAnimation(delta);
            return;
        }

        if (attackCooldown > 0f) {
            attackCooldown -= delta;
        }

        // --- Estado ATTACK: reproduce los 14 frames completos ---
        if (state == STATE_ATTACK) {
            attackTimer -= delta;

            // El daño conecta exactamente en el frame DAMAGE_FRAME (frame 7)
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

            // Vuelve a moverse solo cuando el timer cubre todos los frames
            if (attackTimer <= 0f) {
                state = STATE_RUN;
                attackCooldown = ATTACK_COOLDOWN;
            }

            updateAnimation(delta);
            return;
        }

        // --- Estado HIT: retroceso ---
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

        // --- Lógica de movimiento / idle ---
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

            // Orientar la mirada hacia el jugador
            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                direction = (deltaX < 0) ? "left" : "right";
            } else {
                direction = (deltaY < 0) ? "up" : "down";
            }
            if (deltaX != 0) {
                lastHorizontal = (deltaX < 0) ? "left" : "right";
            }

            if (distanceToPlayer > 1000f) {
                // Fuera del rango de detección: idle
                state = STATE_IDLE;

            } else if (distanceToPlayer <= ATTACK_RANGE && attackCooldown <= 0f) {
                // En rango de ataque y cooldown listo: disparar ataque
                state = STATE_ATTACK;
                attackTimer = ATTACK_DURATION;
                attackDamageDealt = false;
                spriteNum = 1;
                spriteCounter = 0;

            } else if (distanceToPlayer <= KEEP_DISTANCE) {
                // Demasiado cerca pero cooldown activo: se queda en idle (mantiene distancia)
                state = STATE_IDLE;

            } else {
                // Se acerca caminando
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

                // Evasión de obstáculos
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
                    deleteBoss(); // Fin de la animación de muerte
                    return;
                } else if (state == STATE_ATTACK) {
                    spriteNum = maxFrames; // Congela en el último frame hasta que el timer expire
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

        // Detección del nivel de agua bajo los pies del boss
        int waterLevel = 0;
        if (gp != null && gp.tileM != null && gp.mapGen != null) {
            float footX = worldX + solidArea.x + solidArea.width / 2f;
            float footY = worldY + solidArea.y + solidArea.height / 2f;
            int col = (int) (footX / TileManager.TILE_SIZE);
            int row = (int) (footY / TileManager.TILE_SIZE);
            waterLevel = gp.tileM.getWaterLevel(gp.mapGen, col, row);
        }

        if (waterLevel > 0 && gp != null && gp.camera != null) {
            // Sumersión: recortar la parte inferior del sprite con scissor test
            float cutOff = (waterLevel == 1) ? 20f * SCALE : 35f * SCALE;
            float yShift  = (waterLevel == 1) ?  8f * SCALE : 16f * SCALE;
            float drawY    = worldY + yShift;
            float visibleH = DRAW_HEIGHT - cutOff;

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

            // Espuma en la línea de flotación (misma lógica que frogger)
            TextureRegion foam = gp.tileM.getFoamRegion();
            if (foam != null) {
                float foamY = drawY + DRAW_HEIGHT - cutOff - 4f;
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

            // Al recibir daño, forzar cooldown de ataque para dar tiempo al jugador de alejarse
            attackCooldown = Math.max(attackCooldown, ATTACK_COOLDOWN);

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

    //invocar espectro con gema de hielo
    public static boolean summon(GameScreen gp) {
        if (gp == null || gp.camera == null || gp.monster == null || gp.player == null || gp.mapGen == null) {
            return false;
        }

        // 1. Obtener la posición del jugador en la cuadrícula de tiles
        int playerCol = (int) ((gp.player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int playerRow = (int) ((gp.player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);

        if (playerCol < 0 || playerCol >= gp.mapGen.mapCols || playerRow < 0 || playerRow >= gp.mapGen.mapRows) {
            return false;
        }

        // 2. Comprobar que el jugador se encuentre en el bioma de nieve
        if (!gp.mapGen.isSnowBiome[playerCol][playerRow] || gp.mapGen.isDeepWater[playerCol][playerRow]) {
            gp.showNotification("¡Debes estar en el bioma de nieve para usar la Gema de Hielo!");
            Gdx.app.log("Espectro de las Nieves", "Invocación rechazada: fuera del bioma de nieve.");
            return false;
        }

        // 3. Evitar invocar si ya existe un Espectro de las Nieves vivo
        for (Entity m : gp.monster) {
            if (m instanceof MON_snowGhost) {
                gp.showNotification("¡El Espectro de las Nieves ya ha sido invocado!");
                return false;
            }
        }

        // 4. Buscar espacio disponible en el array de monstruos
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

        // 5. Calcular posición de spawn fuera de la pantalla de la cámara
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
                case 0: // ARRIBA
                    spawnX = centerX + (float) ((Math.random() - 0.5) * halfW * 1.5);
                    spawnY = centerY - halfH - DRAW_HEIGHT - margin;
                    break;
                case 1: // ABAJO
                    spawnX = centerX + (float) ((Math.random() - 0.5) * halfW * 1.5);
                    spawnY = centerY + halfH + margin;
                    break;
                case 2: // IZQUIERDA
                    spawnX = centerX - halfW - DRAW_WIDTH - margin;
                    spawnY = centerY + (float) ((Math.random() - 0.5) * halfH * 1.5);
                    break;
                default: // DERECHA
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

        // Si los intentos exteriores cayeron en agua profunda, generar a distancia prudencial en tile de nieve
        if (!positionFound) {
            spawnX = gp.player.worldX + TileManager.TILE_SIZE * 4f;
            spawnY = gp.player.worldY;
        }

        // 6. Instanciar y posicionar al jefe
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
        // 1. Entregar la recompensa única (Pieza de bote 3) de forma protegida contra duplicados
        if (!rewardDelivered && gp != null) {
            rewardDelivered = true;
            DropManager.deliverBossReward("Espectro de las Nieves", new BoatPiece3(), gp);
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

        // 3. Liberar texturas
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
