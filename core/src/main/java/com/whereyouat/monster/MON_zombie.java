package com.whereyouat.monster;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.whereyouat.entity.Entity;
import com.whereyouat.screens.GameScreen;

public class MON_zombie extends Entity {

	public GameScreen gp;
	public String name;

	// Escala: 16x16 × 3 = 48px (exactamente 1 tile)
	private static final float FRAME_W_NATIVE = 16f;
	private static final float FRAME_H_NATIVE = 16f;
	private static final float SCALE = 3.0f; // ×4.5 → 72×144 px en pantalla
	public static final float DRAW_WIDTH = FRAME_W_NATIVE * SCALE; // 72
	public static final float DRAW_HEIGHT = FRAME_H_NATIVE * SCALE; // 144

	// --- Estados (igual que Mushroom y Frogger) ---
	public static final int STATE_WALK = 0;
	public static final int STATE_HIT = 1;
	public static final int STATE_DIE = 2;

	private static final int FRAMES_IDLE = 4;
	private static final int FRAMES_WALK = 4;
	private static final int FRAMES_DIE = 4;
	private static final int FRAMES_DOWN = 4;

	public int state = STATE_WALK;
	private int previousState = STATE_WALK;

	// Knockback (igual que Mushroom)
	private float knockbackDx = 0f;
	private float knockbackDy = 0f;

	// Texturas (para liberar memoria en dispose)
	private final List<Texture> textures = new ArrayList<>();

	// Frames por dirección (8 frames cada uno, tira vertical)
	private TextureRegion[] downFrames;
	private TextureRegion[] upFrames;
	private TextureRegion[] leftFrames;
	private TextureRegion[] rightFrames;
	private TextureRegion[] dieFrames; // ← nuevo: gohst_die.png
	private TextureRegion[] idleFrames;

	// Contador para el deambular aleatorio (cuando el jugador está lejos)
	private int actionLockCounter = 0;

	// -------------------------------------------------------
	// CONSTRUCTOR
	// -------------------------------------------------------
	public MON_zombie(GameScreen gp) {
		super(0, 0, 80f);
		this.gp = gp;

		name = "Zombie";
		maxLife = 4;
		life = maxLife;
		direction = "down";

		// Hitbox centrada dentro del tile 48x48
		float hitW = DRAW_WIDTH * 0.55f;
		float hitH = DRAW_HEIGHT * 0.22f;
		solidArea.set(
				(DRAW_WIDTH - hitW) / 2f,
				DRAW_HEIGHT - hitH - 4f,
				hitW,
				hitH);
		solidAreaDefaultX = solidArea.x;
		solidAreaDefaultY = solidArea.y;

		loadSprites();
	}

	// -------------------------------------------------------
	// CARGA DE SPRITES
	// Sin cambios respecto a antes, solo se agrega dieFrames
	// -------------------------------------------------------
	private void loadSprites() {
		downFrames = loadColumn("monster/zomb_walk_down.png", FRAMES_DOWN);
		idleFrames = loadColumn("monster/zomb_idle.png", FRAMES_IDLE);
		leftFrames = loadColumn("monster/zomb_walk_left.png", FRAMES_WALK);
		rightFrames = loadColumn("monster/zomb_walk_right.png", FRAMES_WALK);
		upFrames = loadColumn("monster/zomb_walk_up.png", FRAMES_WALK);
		dieFrames = loadColumn("monster/zomb_die.png", FRAMES_DIE);
	}

	/**
	 * Carga un spritesheet de 1 columna y N filas.
	 * Cada frame mide FRAME_W_NATIVE × FRAME_H_NATIVE px.
	 * Las regiones se voltean verticalmente para el sistema Y-down de LibGDX.
	 */
	private TextureRegion[] loadColumn(String path, int frameCount) {
		Texture tex = new Texture(Gdx.files.internal(path));
		tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		textures.add(tex);

		TextureRegion[] frames = new TextureRegion[frameCount];
		int fw = (int) FRAME_W_NATIVE;
		int fh = (int) FRAME_H_NATIVE;

		for (int i = 0; i < frameCount; i++) {
			TextureRegion r = new TextureRegion(tex, 0, i * fh, fw, fh);
			r.flip(false, true); // Y-down → Y-up para LibGDX
			frames[i] = r;
		}
		return frames;
	}

	// -------------------------------------------------------
	// RECIBIR DAÑO — igual que Mushroom.takeDamage()
	// Lo llama Player.attack() cuando el ataque conecta
	// -------------------------------------------------------
	public void takeDamage(int damage, float playerX, float playerY) {
		if (state == STATE_DIE)
			return; // ya muerto, ignorar

		life -= damage;
		Gdx.app.log("Zombie", "Vida restante: " + life + "/" + maxLife);

		if (life <= 0) {
			// Pasa a animación de muerte
			state = STATE_DIE;
			spriteNum = 1;
			spriteCounter = 0;
		} else {
			// Pasa a estado golpeado con knockback
			state = STATE_HIT;
			spriteNum = 1;
			spriteCounter = 0;

			// Dirección del knockback: empuja al Ghost LEJOS del jugador
			float ghostCX = worldX + solidArea.x + solidArea.width / 2f;
			float ghostCY = worldY + solidArea.y + solidArea.height / 2f;
			float dx = ghostCX - playerX;
			float dy = ghostCY - playerY;
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

	// -------------------------------------------------------
	// UPDATE — máquina de estados
	// -------------------------------------------------------
	@Override
	public void update(float delta) {

		// DIE: solo reproduce la animación y espera que updateAnimation lo elimine
		if (state == STATE_DIE) {
			updateAnimation(delta);
			return;
		}

		// HIT: retrocede con knockback, luego vuelve a WALK
		if (state == STATE_HIT) {
			float knockbackSpeed = 100f;
			float moveX = knockbackDx * knockbackSpeed * delta;
			float moveY = knockbackDy * knockbackSpeed * delta;
			if (!collides(worldX + moveX, worldY))
				worldX += moveX;
			if (!collides(worldX, worldY + moveY))
				worldY += moveY;
			updateAnimation(delta);
			return;
		}

		// WALK: persigue al jugador si está cerca, deambula si está lejos
		if (gp != null && gp.player != null && !gp.player.dead) {
			float ghostCX = worldX + solidArea.x + solidArea.width / 2f;
			float ghostCY = worldY + solidArea.y + solidArea.height / 2f;
			float playerCX = gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2f;
			float playerCY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2f;

			float dx = playerCX - ghostCX;
			float dy = playerCY - ghostCY;
			float dist = (float) Math.hypot(dx, dy);

			if (dist <= 1200f && !gp.player.isHiddenInBush) {
				// --- PERSECUCIÓN ---
				// Mueve al Ghost hacia el jugador
				if (dist > 1f) {
					float moveX = (dx / dist) * speed * delta;
					float moveY = (dy / dist) * speed * delta;
					if (!collides(worldX + moveX, worldY))
						worldX += moveX;
					if (!collides(worldX, worldY + moveY))
						worldY += moveY;
				}

				// La dirección visual sigue al eje dominante
				if (Math.abs(dx) >= Math.abs(dy)) {
					direction = dx > 0 ? "right" : "left";
				} else {
					direction = dy > 0 ? "down" : "up";
				}
			} else {
				// --- DEAMBULAR (igual que antes) ---
				setRandomAction(delta);
			}
		} else {
			setRandomAction(delta);
		}

		updateAnimation(delta);
	}

	// Lógica de movimiento aleatorio (sacada del update original)
	private void setRandomAction(float delta) {
		actionLockCounter++;
		if (actionLockCounter >= 120) {
			int i = new Random().nextInt(100) + 1;
			if (i <= 25)
				direction = "up";
			else if (i <= 50)
				direction = "down";
			else if (i <= 75)
				direction = "left";
			else
				direction = "right";
			actionLockCounter = 0;
		}

		float moveX = 0f, moveY = 0f;
		switch (direction) {
			case "up":
				moveY -= speed * delta;
				break;
			case "down":
				moveY += speed * delta;
				break;
			case "left":
				moveX -= speed * delta;
				break;
			case "right":
				moveX += speed * delta;
				break;
		}
		if (moveX != 0f && !collides(worldX + moveX, worldY))
			worldX += moveX;
		if (moveY != 0f && !collides(worldX, worldY + moveY))
			worldY += moveY;
	}

	private boolean collides(float x, float y) {
		if (gp == null || gp.tileM == null || gp.mapGen == null)
			return false;
		return gp.tileM.collidesWithTree(gp.mapGen, x + solidArea.x, y + solidArea.y, solidArea.width,
				solidArea.height);
	}

	// -------------------------------------------------------
	// ANIMACIÓN — igual que Mushroom.updateAnimation()
	// -------------------------------------------------------
	private void updateAnimation(float delta) {
		// Resetear al cambiar de estado
		if (state != previousState) {
			previousState = state;
			spriteNum = 1;
			spriteCounter = 0;
		}

		float animSpeed;
		int maxFrames = 4; // los spritesheets de zombie tienen 4 frames

		switch (state) {
			case STATE_HIT:
				animSpeed = 5f;
				break;
			case STATE_DIE:
				animSpeed = 7f;
				break;
			default:
				animSpeed = 6f;
				break; // WALK
		}

		spriteCounter += delta * 60f;
		if (spriteCounter >= animSpeed) {
			spriteNum++;
			if (spriteNum > maxFrames) {
				if (state == STATE_DIE) {
					spriteNum = maxFrames; // congela en el último frame
					deleteGhost(); // se elimina del mundo
					return;
				} else if (state == STATE_HIT) {
					state = STATE_WALK; // vuelve a caminar
				}
				spriteNum = 1;
			}
			spriteCounter = 0;
		}
	}

	// Se elimina a sí mismo del array de monstruos de GameScreen
	// (igual que deleteBoss en Mushroom)
	private void deleteGhost() {
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

	// -------------------------------------------------------
	// DIBUJADO
	// -------------------------------------------------------
	@Override
	public void draw(SpriteBatch batch) {
		TextureRegion[] frames;

		// En muerte usa dieFrames sin importar la dirección
		if (state == STATE_DIE) {
			frames = dieFrames;
		} else {
			switch (direction) {
				case "down":
					frames = downFrames;
					break;
					
					es;
					break;
				case "left":
					frames = leftFrames;
					break;
				case "right":
					frames = rightFrames;
					break;
				default:
					frames = idleFrames != null ? idleFrames : downFrames;
					break;
			}
		}

		if (frames == null)
			return;

		int frameIndex = Math.max(0, Math.min(frames.length - 1, spriteNum - 1));
		TextureRegion region = frames[frameIndex];
		if (region == null)
			return;

		// Parpadeo cuando recibe daño (igual que Player con invincibleCounter)
		if (state == STATE_HIT && (spriteNum / 2) % 2 == 0) {
			batch.setColor(1f, 1f, 1f, 0.3f);
		}

		batch.draw(region, worldX, worldY, DRAW_WIDTH, DRAW_HEIGHT);
		batch.setColor(1f, 1f, 1f, 1f); // siempre restaurar el color
	}

	// -------------------------------------------------------
	// DISPOSE
	// -------------------------------------------------------
	@Override
	public void dispose() {
		for (Texture tex : textures) {
			tex.dispose();
		}
		textures.clear();
	}
}