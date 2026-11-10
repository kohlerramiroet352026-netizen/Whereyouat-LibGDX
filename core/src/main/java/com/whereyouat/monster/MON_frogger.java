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
 * Frogger monster entity for Where you at (LibGDX Edition).
 *
 * Implements:
 * - Random 4-direction AI movement (picks a new direction every ~2 seconds)
 * - 2-frame walk animation per direction
 * - Proper LibGDX texture loading via Gdx.files.internal
 * - Y-down TextureRegion flipping (consistent with Player/Bird)
 * - Disposable texture lifecycle
 */
public class MON_frogger extends Entity {

    public GameScreen gp;
    public String name;

    //estados
    public static final int STATE_IDLE = 0;
    public static final int STATE_RUN = 1;
    public static final int STATE_ATTACK = 2;
    public static final int STATE_HIT = 3;

    private static final int SIDE_LEFT = 0;
    private static final int SIDE_RIGHT = 1;

    public static final float SCALE = 2.0f;
    public static final float BODY_SIZE = 128f * SCALE;
    public static final float SHEET_WIDTH = 384 * SCALE;
    public static final float SHEET_HEIGHT = 128f * SCALE;
    public static final float DRAW_WIDTH = 350f;
    public static final float DRAW_HEIGHT = 280f;


    public float attackCooldown = 0f;
    public static final float ATTACK_COOLDOWN = 2.0f;

    public int state = STATE_IDLE;
    private int previousState = STATE_IDLE;

    //control de si esta atacando o no
    private boolean isAttacking = false;
    private boolean attackDamageDealt = false;

    private final List<Texture> textures = new ArrayList<>();
    private TextureRegion[][] idleFrames;
    private TextureRegion[][] moveFrames;
    private TextureRegion[][] tongueFrames;
    private TextureRegion[][] hurtFrames;

    private float knockbackDx = 0f;
    private float knockbackDy = 0f;
    private String lastHorizontal = "right";


    public MON_frogger() {
        this((GameScreen) null);
    }

    public MON_frogger(GameScreen gp) {
        super(0, 0, 75f);
        this.gp = gp;

        name = "Rey Frogger";
        maxLife = 10;
        life = maxLife;

        //hitbox
        solidArea.set(38f * SCALE, 96f * SCALE, 52f * SCALE, 26f * SCALE);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        loadSprites();
    }

    @Override
    public boolean dealsContactDamage(){
        return false;            //hace daño con su lengua
    }

    public MON_frogger(float worldX, float worldY) {
        this((GameScreen) null);
        this.worldX = worldX;
        this.worldY = worldY;
    }

    /**
     * Loads the frogger sprite sheets from the assets folder.
     * Uses the same pattern as Player.loadRegion(): Gdx.files.internal,
     * Nearest filtering, and Y-flip for the Y-down coordinate system.
     *
     * Expected assets:
     *   monster/frogger_move1.png  (idle / frame 1)
     *   monster/frogger_move2.png  (walk  / frame 2)
     */
    private void loadSprites() {
        idleFrames = loadFrames("monster/frogger_idle.png", 128);
        moveFrames = loadFrames("monster/frogger_move.png", 384);
        tongueFrames = loadFrames("monster/frogger_tongue.png", 384);
        hurtFrames = loadFrames("monster/frogger_hurt.png", 384);
    }

    private TextureRegion[][] loadFrames(String path, int frameWidth){
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        textures.add(texture);

        int count = texture.getWidth() / frameWidth;
        TextureRegion[][] sheet = TextureRegion.split(texture, frameWidth, 128);
        TextureRegion[][] frames = new TextureRegion[2][count];

        for (int i = 0; i < count; i++){
            //el sprite original mira hacia la derecha
            TextureRegion right = new TextureRegion(sheet[0][i]);
            right.flip(false, true);

            //darlo vuelta hacia la derecha
            TextureRegion left = new TextureRegion(sheet[0][i]);
            left.flip(true, true);

            frames[SIDE_RIGHT][i] = right;
            frames[SIDE_LEFT][i] = left;
        }
        return frames;
    }

    

    @Override
    public void update(float delta) {
       if (attackCooldown > 0f){
        attackCooldown -= delta;
       } 
       if (state == STATE_HIT) {
        float knockbackSpeed = 140f;
        float moveX = knockbackDx * knockbackSpeed * delta;
        float moveY = knockbackDy * knockbackSpeed * delta;

        if (!collides(worldX + moveX, worldY)) worldX += moveX;
        if (!collides(worldX, worldY + moveY)) worldY += moveY;

        updateAnimation(delta);
        return;

       }
       //si el jugador no existe o esta muerto se queda en idle
       if (gp == null || gp.player == null || gp.player.dead){
        state = STATE_IDLE;
        isAttacking = false;
       } else {
        float frogCenterX = worldX + 64f;
        float frogCenterY = worldY + 96f;
        float playerCenterX = gp.player.worldX + TileManager.TILE_SIZE / 2f;
        float playerCenterY = gp.player.worldY + TileManager.TILE_SIZE / 2f;

        float deltaX = playerCenterX - frogCenterX;
        float deltaY = playerCenterY - frogCenterY;
        float distanceToPlayer = (float) Math.hypot(deltaX, deltaY);

        //orientacion segun donde este el jugador
        if (!isAttacking && deltaX != 0){
            lastHorizontal = (deltaX < 0) ? "left" : "right";
        }

        if (isAttacking){
            state = STATE_ATTACK;

            //daño con la lengua, cada 4 frames (osea cuando se sale por completo)
            if (spriteNum == 4 && !attackDamageDealt){
                //si el jugador esa dentro del rango y en la direccion correcta
                boolean inFront = ("right".equals(lastHorizontal) && deltaX > -20f) || ("left".equals(lastHorizontal) && deltaX < 20f);

                if (distanceToPlayer <= 210f && inFront) {
                    gp.player.contactMonster();
                }
                attackDamageDealt = true;

            }
        } else {
            //rango de la legua, ataca desde 190 pixeles
            if(distanceToPlayer <= 220f && attackCooldown <= 0f){
                isAttacking = true;
                attackDamageDealt = false;
                state = STATE_ATTACK;
                spriteNum = 1;
                spriteCounter = 0;
            } else if (distanceToPlayer <= 200f){
                state = STATE_IDLE;
            } else if(distanceToPlayer <= 1200f){
                state = STATE_RUN;

            
                float moveX = (deltaX / distanceToPlayer) * speed * delta;
                float moveY = (deltaY / distanceToPlayer) * speed * delta;

                boolean movedX = false;
                boolean movedY = false;

                if (!collides(worldX + moveX, worldY)){
                    worldX += moveX;
                    movedX = true;
                }
                if (!collides(worldX, worldY + moveY)){
                    worldY += moveY;
                    movedY = true;
                }

                //evasion de arboles
                if (!movedX && !movedY){
                    float slide = speed * delta;
                    if (Math.abs(deltaY) >= Math.abs(deltaX)){
                        if (!collides(worldX + slide, worldY)) worldX += slide;
                        else if (!collides(worldX - slide, worldY)) worldX -= slide;
                        
                    } else {
                        if (!collides(worldX, worldY + slide)) worldY += slide;
                        else if (!collides(worldX, worldY - slide)) worldY -= slide;
                    }
                }
            } else {
                state = STATE_IDLE;
            }
        }
       }

       updateAnimation(delta);
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

    private boolean collides(float x, float y){
        if (gp == null || gp.tileM == null || gp.mapGen == null) return false;
        return gp.tileM.collidesWithTree(gp.mapGen, x + solidArea.x, y + solidArea.y, solidArea.width, solidArea.height);
    }

    private void updateAnimation(float delta){
        if (state != previousState){
            previousState = state;
            spriteNum = 1;
            spriteCounter = 0;
        }

        int maxFrames;
        float animSpeed = 7f;

        switch (state){
            case STATE_ATTACK:
                maxFrames = 8;
                animSpeed = 6f;
                break;
            case STATE_HIT:
                maxFrames = 4;
                animSpeed = 5f;
                break;
            case STATE_RUN:
                maxFrames = 8;
                animSpeed = 6f;
                break;
            default: //idle
                maxFrames = 5;
                animSpeed = 10f;
                break;
        }

        spriteCounter += delta *60f;
        if (spriteCounter >= animSpeed) {
            spriteNum++;
            if (spriteNum > maxFrames){
                if (state == STATE_ATTACK){
                    isAttacking = false;
                    attackDamageDealt = false;
                    attackCooldown = ATTACK_COOLDOWN;
                    state = STATE_RUN;
                    spriteNum = 1;
                } else if (state == STATE_HIT){
                    state = STATE_RUN;
                }
                spriteNum = 1;
            }
            spriteCounter = 0;
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        int side = "left".equals(lastHorizontal) ? SIDE_LEFT : SIDE_RIGHT;
        TextureRegion[][] sheet;
        int maxFrames;
        float drawW, drawH = SHEET_HEIGHT;
        float drawX = worldX - BODY_SIZE;
        float drawY = worldY;

        switch (state){
            case STATE_ATTACK:
                sheet = tongueFrames;
                maxFrames = 8;
                drawW = SHEET_WIDTH;
                drawX = worldX - BODY_SIZE;
                break;
            case STATE_HIT:
                sheet = hurtFrames;
                maxFrames = 4;
                drawW = SHEET_WIDTH;
                drawX = worldX - BODY_SIZE;
                break;
            case STATE_RUN:
                sheet = moveFrames;
                maxFrames = 8;
                drawW = SHEET_WIDTH;
                drawX = worldX - BODY_SIZE;
                break;
            default: //idle
                sheet = idleFrames;
                maxFrames = 5;
                drawW = BODY_SIZE;
                drawX = worldX;
                break;
        }

        int frameIndex = Math.max(0, Math.min(maxFrames -1, spriteNum - 1));
        TextureRegion region = sheet[side][frameIndex];
        if (region == null) return;

        int waterLevel = 0;
        if (gp != null && gp.tileM != null && gp.mapGen != null){
            float footX = worldX + solidArea.x + solidArea.width / 2f;
            float footY = worldY + solidArea.y + solidArea.height / 2f;
            int col = (int) (footX / TileManager.TILE_SIZE);
            int row = (int) (footY / TileManager.TILE_SIZE);
            waterLevel = gp.tileM.getWaterLevel(gp.mapGen, col, row);
        }
            if (waterLevel > 0 && gp != null & gp.camera != null){
                float cutOff = (waterLevel == 1) ? 20f * SCALE : 35f * SCALE;
                float yShift = (waterLevel == 1) ? 8f * SCALE : 16f * SCALE;
                float submergedY = drawY + yShift;
                float visibleH = drawH - cutOff;

                Vector3 topLeft = gp.camera.project(new Vector3(drawX, submergedY, 0));
                Vector3 bottomRight = gp.camera.project(new Vector3(drawX + drawW, submergedY + visibleH, 0));

                int sx = (int) Math.min(topLeft.x, bottomRight.x);
                int sy = (int) Math.min(topLeft.y, bottomRight.y);
                int sw = (int) Math.abs(bottomRight.x - topLeft.x);
                int sh = (int) Math.abs(bottomRight.y - topLeft.y);

                batch.flush();
                Gdx.gl.glEnable((GL20.GL_SCISSOR_TEST));
                Gdx.gl.glScissor(sx, sy, Math.max(1, sw), Math.max(1, sh));

                batch.draw(region, drawX, submergedY, drawW, drawH);

                batch.flush();
                Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            } else {
             batch.draw(region, drawX, drawY, drawW, drawH);
            } 
    }
    

    public void takeDamage(int damage, float playerX, float playerY) {
        if (life <= 0) return;

        this.life -= damage;
        Gdx.app.log("Frogger", "Vida restante: " + life + "/" + maxLife);

        if (this.life <= 0) {
            deleteFrogger();
        } else {
            state = STATE_HIT;
            spriteNum = 1;
            spriteCounter = 0;
            isAttacking = false;

            // Retroceso (knockback) alejándose del jugador
            float centerX = worldX + 64f;
            float centerY = worldY + 96f;
            float dx = centerX - playerX;
            float dy = centerY - playerY;
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

        public static boolean summon(GameScreen gp) {
        if (gp == null || gp.camera == null || gp.monster == null) return false;

        //obtener la posicion del jugador en el mapa
        int col = (int) ((gp.player.worldX + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);
        int row = (int) ((gp.player.worldY + TileManager.TILE_SIZE / 2f) / TileManager.TILE_SIZE);

        //comprobar si esta en el pantano

        if (col < 0 || col >= gp.mapGen.mapCols || row < 0 || row >= gp.mapGen.mapRows) {
            return false;
        }
        // Permitir invocar en pantano
        if (!gp.mapGen.isSwampBiome[col][row]) {
            Gdx.app.log("Frogger", "No se puede invocar a Frogger fuera del pantano");
            return false;
        }

        //evitar invocar dos veces si ya hay un Rey Hongo vivo en el mapa
        for (Entity m : gp.monster) {
            if (m instanceof MON_frogger) {
                return false;
            }
        }

        //buscar un espacio libre en el array de monstruos
        int slotIndex = -1;
        for (int i = 0; i < gp.monster.length; i++) {
            if (gp.monster[i] == null) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) return false; // Array lleno

        //obtener el área visible real de la cámara (funciona en fullscreen y minimizada)
        float halfW = (gp.camera.viewportWidth * gp.camera.zoom) / 2f;
        float halfH = (gp.camera.viewportHeight * gp.camera.zoom) / 2f;
        float centerX = gp.camera.position.x;
        float centerY = gp.camera.position.y;

        float spawnX, spawnY;
        float margin = 60f; //margen para que no aparezca asomado en el borde

        // 4. Elegir al azar uno de los 4 lados (0: Arriba, 1: Abajo, 2: Izquierda, 3: Derecha)
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

        //instanciar al jefe y ubicarlo
        MON_frogger boss = new MON_frogger(gp);
        boss.worldX = spawnX;
        boss.worldY = spawnY;
        boss.clampToBounds(); //evita que quede fuera de los límites del mapa del mundo

        //ponerlo en el juego
        gp.monster[slotIndex] = boss;
        return true;
    }

    private void deleteFrogger() {
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
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
    }
}
