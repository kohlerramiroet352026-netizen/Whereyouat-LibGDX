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

/**
 * Entidad del Jefe Hongo Gigante (Mushroom Boss).
 * 
 * Implementa máquina de estados simple (Reposo, Persecución, Ataque),
 * animación de sprites con orientación horizontal y evasión de obstáculos.
 */
public class MON_mushroom extends Entity {

    public GameScreen gp;
    public String name;

    // Dimensiones proporcionales 80:64 (5:4). Tamaño de jefe (~tamaño de un árbol)
    public static final float DRAW_WIDTH = 350f;
    public static final float DRAW_HEIGHT = 280f;

    // Constantes de estados de animación e IA
    public static final int STATE_IDLE = 0;
    public static final int STATE_RUN = 1;
    public static final int STATE_ATTACK = 2;
    public static final int STATE_STUN = 3;
    public static final int STATE_HIT = 4;
    public static final int STATE_DIE = 5;

    // Constantes de orientación horizontal
    private static final int SIDE_LEFT = 0;
    private static final int SIDE_RIGHT = 1;

    public int state = STATE_IDLE;
    private int previousState = STATE_IDLE;

    // Control del ciclo de ataque
    private boolean isAttacking = false;
    private boolean attackDamageDealt = false;

    // Colección de texturas para liberar recursos en dispose()
    private final List<Texture> textures = new ArrayList<>();
    private TextureRegion[][] idleFrames;
    private TextureRegion[][] runFrames;
    private TextureRegion[][] attackFrames;
    private TextureRegion[][] stunFrames;
    private TextureRegion[][] hitFrames;
    private TextureRegion[][] dieFrames;

    private float knockbackDx = 0f;
    private float knockbackDy = 0f;

    private String lastHorizontal = "left";

    public MON_mushroom(GameScreen gp) {
        super(0, 0, 230f);
        this.gp = gp;

        name = "Rey Hongo";
        maxLife = 20;
        life = maxLife;

        // Hitbox compacta en la base de los pies (40x26 px) para permitir pasar entre
        // árboles
        solidArea.set(155f, 246f, 40f, 26f);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();
    }

    @Override
    public boolean dealsContactDamage() {
        // El hongo no daña por mero roce, daña mediante su ataque en el frame 7
        return false;
    }

    private void loadSprites() {
        idleFrames = loadFrames("monster/Mushroom-Idle.png", 7);
        runFrames = loadFrames("monster/Mushroom-Run.png", 8);
        attackFrames = loadFrames("monster/Mushroom-Attack.png", 10);
        stunFrames = loadFrames("monster/Mushroom-Stun.png", 18);
        hitFrames = loadFrames("monster/Mushroom-Hit.png", 5);
        dieFrames = loadFrames("monster/Mushroom-Die.png", 15);
    }

    private TextureRegion[][] loadFrames(String path, int count) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(texture);

        TextureRegion[][] sheet = TextureRegion.split(texture, 80, 64);
        TextureRegion[][] frames = new TextureRegion[2][count];
        for (int i = 0; i < count; i++) {
            TextureRegion left = new TextureRegion(sheet[0][i]);
            left.flip(false, true);

            TextureRegion right = new TextureRegion(sheet[0][i]);
            right.flip(true, true);

            frames[SIDE_LEFT][i] = left; // Mirando a la izquierda
            frames[SIDE_RIGHT][i] = right; // Mirando a la derecha
        }
        return frames;
    }

    @Override
    public void update(float delta) {
        // si esta muriendo, solo reproduce la animacion de muerte
        if (state == STATE_DIE) {
            updateAnimation(delta);
            return;
        }
        // si esta en stun, se queda quieto
        if (state == STATE_STUN) {
            updateAnimation(delta);
            return;
        }
        // si recibio un golpe, se va un poco para atras y muestra la animacion de hit
        if (state == STATE_HIT) {
            float knockbackSpeed = 120f;
            float moveX = knockbackDx * knockbackSpeed * delta;
            float moveY = knockbackDy * knockbackSpeed * delta;

            if (!collides(worldX + moveX, worldY))
                worldX += moveX;
            if (!collides(worldX, worldY + moveY))
                worldY += moveY;
            clampToBounds();

            updateAnimation(delta);
            return;
        }

        if (gp == null || gp.player == null || gp.player.dead) {
            state = STATE_IDLE;
            isAttacking = false;
        } else {
            // Posición de los pies del hongo y del jugador en coordenadas del mundo
            float mushroomFootX = worldX + solidArea.x + solidArea.width / 2f;
            float mushroomFootY = worldY + solidArea.y + solidArea.height / 2f;
            float playerFootX = gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2f;
            float playerFootY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2f;

            // Vector de distancia hacia el jugador
            float deltaX = playerFootX - mushroomFootX;
            float deltaY = playerFootY - mushroomFootY;
            float distanceToPlayer = (float) Math.hypot(deltaX, deltaY);

            // Orientación visual según la posición del jugador
            if (!isAttacking && deltaX != 0) {
                lastHorizontal = (deltaX < 0) ? "left" : "right";
            }

            // Máquina de estados: Ataque, Persecución o Reposo
            if (isAttacking) {
                state = STATE_ATTACK;

                // El daño ocurre exactamente en el frame 7 de la animación de ataque
                if (spriteNum >= 7 && !attackDamageDealt) {
                    if (distanceToPlayer <= 125f || intersects(gp.player)) {
                        gp.player.contactMonster();
                    }
                    attackDamageDealt = true;
                }
            } else {
                // Si está a rango de golpe, inicia ataque
                if (distanceToPlayer <= 110f || intersects(gp.player)) {
                    isAttacking = true;
                    attackDamageDealt = false;
                    state = STATE_ATTACK;
                    spriteNum = 1;
                    spriteCounter = 0;
                } else if (distanceToPlayer <= 1200f) {
                    // Si el jugador está dentro del rango de detección, lo persigue
                    state = STATE_RUN;

                    float currentSpeed = speed;
                    if (gp.tileM != null && gp.mapGen != null) {
                        int col = (int) (mushroomFootX / TileManager.TILE_SIZE);
                        int row = (int) (mushroomFootY / TileManager.TILE_SIZE);
                        int waterLevel = gp.tileM.getWaterLevel(gp.mapGen, col, row);
                        if (waterLevel == 2)
                            currentSpeed = speed * 0.45f;
                        else if (waterLevel == 1)
                            currentSpeed = speed * 0.75f;
                    }

                    float moveX = (deltaX / distanceToPlayer) * currentSpeed * delta;
                    float moveY = (deltaY / distanceToPlayer) * currentSpeed * delta;

                    boolean movedX = false;
                    boolean movedY = false;

                    // Moverse en X si no colisiona con un árbol o roca
                    float nextX = worldX + moveX;
                    if (!collides(nextX, worldY)) {
                        worldX = nextX;
                        movedX = true;
                    }

                    // Moverse en Y si no colisiona con un árbol o roca
                    float nextY = worldY + moveY;
                    if (!collides(worldX, nextY)) {
                        worldY = nextY;
                        movedY = true;
                    }

                    // Evasión: si se frena contra un árbol de frente, se desvía al costado
                    if (!movedX && !movedY) {
                        float slideStep = currentSpeed * delta;
                        if (Math.abs(deltaY) >= Math.abs(deltaX)) {
                            // Si intentaba moverse verticalmente, intenta deslizarse en horizontal
                            if (!collides(worldX + slideStep, worldY)) {
                                worldX += slideStep;
                            } else if (!collides(worldX - slideStep, worldY)) {
                                worldX -= slideStep;
                            }
                        } else {
                            // Si intentaba moverse horizontalmente, intenta deslizarse en vertical
                            if (!collides(worldX, worldY + slideStep)) {
                                worldY += slideStep;
                            } else if (!collides(worldX, worldY - slideStep)) {
                                worldY -= slideStep;
                            }
                        }
                    }

                    clampToBounds();
                } else {
                    // Si el jugador está fuera de rango, queda en reposo
                    state = STATE_IDLE;
                }
            }
        }

        updateAnimation(delta);
    }

    private boolean collides(float x, float y) {
        if (gp == null || gp.tileM == null || gp.mapGen == null)
            return false;
        return gp.tileM.collidesWithTree(gp.mapGen, x + solidArea.x, y + solidArea.y,
                solidArea.width, solidArea.height);
    }

    private void clampToBounds() {
        if (gp == null || gp.mapGen == null)
            return;
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
                maxFrames = 10;
                animSpeed = 7f;
                break;
            case STATE_STUN:
                maxFrames = 18;
                animSpeed = 6f;
                break;
            case STATE_HIT:
                maxFrames = 5;
                animSpeed = 5f;
                break;
            case STATE_DIE:
                maxFrames = 15;
                animSpeed = 7f;
                break;
            case STATE_RUN:
                maxFrames = 8;
                break;
            default:
                maxFrames = 7;
                break;
        }

        spriteCounter += delta * 60f;
        if (spriteCounter >= animSpeed) {
            spriteNum++;
            if (spriteNum > maxFrames) {
                if (state == STATE_ATTACK) {
                    isAttacking = false;
                    attackDamageDealt = false;
                    state = STATE_STUN;
                    spriteNum = 1;
                } else if (state == STATE_STUN) {
                    state = STATE_RUN;
                    spriteNum = 1;
                } else if (state == STATE_HIT) {
                    state = STATE_RUN;
                    spriteNum = 1;
                } else if (state == STATE_DIE) {
                    spriteNum = maxFrames;
                    deleteBoss();
                    return;
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
                maxFrames = 10;
                break;
            case STATE_STUN:
                sheet = stunFrames;
                maxFrames = 18;
                break;
            case STATE_HIT:
                sheet = hitFrames;
                maxFrames = 5;
                break;
            case STATE_DIE:
                sheet = dieFrames;
                maxFrames = 15;
                break;
            case STATE_RUN:
                sheet = runFrames;
                maxFrames = 8;
                break;
            default:
                sheet = idleFrames;
                maxFrames = 7;
                break;
        }

        int frameIndex = Math.max(0, Math.min(maxFrames - 1, spriteNum - 1));
        TextureRegion region = sheet[side][frameIndex];
        if (region == null)
            return;

        // Detección de nivel de agua para sumersión
        int waterLevel = 0;
        if (gp != null && gp.tileM != null && gp.mapGen != null) {
            float footX = worldX + solidArea.x + solidArea.width / 2f;
            float footY = worldY + solidArea.y + solidArea.height / 2f;
            int col = (int) (footX / TileManager.TILE_SIZE);
            int row = (int) (footY / TileManager.TILE_SIZE);
            waterLevel = gp.tileM.getWaterLevel(gp.mapGen, col, row);
        }

        // Sumersión en agua con OpenGL Scissor Test
        if (waterLevel > 0 && gp != null && gp.camera != null) {
            float cutOff = (waterLevel == 1) ? 28f : 56f;
            float yShift = (waterLevel == 1) ? 10f : 20f;
            float drawY = worldY + yShift;
            float visibleH = DRAW_HEIGHT - cutOff;

            Vector3 topLeft = gp.camera.project(new Vector3(worldX, drawY, 0));
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

            // Espuma en la línea de agua
            TextureRegion foam = gp.tileM.getFoamRegion();
            if (foam != null) {
                float foamY = drawY + DRAW_HEIGHT - cutOff - 4f;
                float foamX = worldX + (DRAW_WIDTH - TileManager.TILE_SIZE) / 2f;
                batch.setColor(1f, 1f, 1f, 0.9f);
                batch.draw(foam, foamX, foamY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);
                batch.setColor(1f, 1f, 1f, 1f);
            }
        } else {
            batch.draw(region, worldX, worldY, DRAW_WIDTH, DRAW_HEIGHT);
        }
    }

    public void takeDamage(int damage, float playerX, float playerY) {
        if (state == STATE_DIE)
            return;

        this.life -= damage;
        Gdx.app.log("Rey hongo", "Vida restante: " + life + "/" + maxLife);

        if (this.life <= 0) {
            // entra en la animacion final de muerte
            state = STATE_DIE;
            spriteNum = 1;
            spriteCounter = 0;
            isAttacking = false;
        } else {
            // entra en hit, si estana en stun, lo cancela
            state = STATE_HIT;
            spriteNum = 1;
            spriteCounter = 0;
            isAttacking = false;

            // calcula el empuje para salir despedido lejos del jugador
            float bossCenterX = worldX + solidArea.x + solidArea.width / 2f;
            float bossCenterY = worldY + solidArea.y + solidArea.height / 2f;
            float dx = bossCenterX - playerX;
            float dy = bossCenterY - playerY;
            float dist = (float) Math.hypot(dx, dy);

            if (dist > 0.001f) {
                knockbackDx = dx / dist;
                knockbackDy = dy / dist;
            } else {
                knockbackDx = "left".equals(lastHorizontal) ? 1f : -1f;
                knockbackDy = 0f;
            }
        }
    }

    // invocación del rey hongo para el item Odd Mushroom
    public static boolean summon(GameScreen gp) {
        if (gp == null || gp.camera == null || gp.monster == null)
            return false;

        // obtener la posicion del jugador en el mapa
        int col = (int) ((gp.player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int row = (int) ((gp.player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);

        // comprobar si esta en el bosque

        if (col < 0 || col >= gp.mapGen.mapCols || row < 0 || row >= gp.mapGen.mapRows) {
            return false;
        }
        // Permitir invocar en bosque y planicies comunes (rechazar nieve, pantano,
        // playa y agua)
        if (gp.mapGen.isSnowBiome[col][row]
                || gp.mapGen.isSwampBiome[col][row]
                || gp.mapGen.isBeachBiome[col][row]
                || gp.mapGen.isDeepWater[col][row]
                || gp.mapGen.isShallowWater[col][row]) {
            Gdx.app.log("Rey hongo", "No se puede invocar al rey hongo fuera del bosque y planicies");
            return false;
        }

        // evitar invocar dos veces si ya hay un Rey Hongo vivo en el mapa
        for (Entity m : gp.monster) {
            if (m instanceof MON_mushroom) {
                return false;
            }
        }

        // buscar un espacio libre en el array de monstruos
        int slotIndex = -1;
        for (int i = 0; i < gp.monster.length; i++) {
            if (gp.monster[i] == null) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1)
            return false; // Array lleno

        // obtener el área visible real de la cámara (funciona en fullscreen y
        // minimizada)
        float halfW = (gp.camera.viewportWidth * gp.camera.zoom) / 2f;
        float halfH = (gp.camera.viewportHeight * gp.camera.zoom) / 2f;
        float centerX = gp.camera.position.x;
        float centerY = gp.camera.position.y;

        float spawnX, spawnY;
        float margin = 60f; // margen para que no aparezca asomado en el borde

        // 4. Elegir al azar uno de los 4 lados (0: Arriba, 1: Abajo, 2: Izquierda, 3:
        // Derecha)
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

        // instanciar al jefe y ubicarlo
        MON_mushroom boss = new MON_mushroom(gp);
        boss.worldX = spawnX;
        boss.worldY = spawnY;
        boss.clampToBounds(); // evita que quede fuera de los límites del mapa del mundo

        // ponerlo en el juego
        gp.monster[slotIndex] = boss;
        return true;
    }

    private void deleteBoss() {
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
