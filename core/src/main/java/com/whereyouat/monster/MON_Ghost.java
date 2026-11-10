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

public class MON_Ghost extends Entity {

	public GameScreen gp;
	public String name;

	// Escala: 16x16 × 3 = 48px (exactamente 1 tile)
	public static final float SCALE = 3.0f;
	public static final float DRAW_SIZE = 16f * SCALE; // 48px

	// --- Estados (igual que Mushroom y Frogger) ---
	public static final int STATE_WALK = 0;
	public static final int STATE_HIT = 1;
	public static final int STATE_DIE = 2;

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

	// Contador para el deambular aleatorio (cuando el jugador está lejos)
	private int actionLockCounter = 0;

	// -------------------------------------------------------
	// CONSTRUCTOR
	// -------------------------------------------------------
	public MON_Ghost(GameScreen gp) {
		super(0, 0, 80f);
		this.gp = gp;

		name = "Fantasma";
		maxLife = 4;
		life = maxLife;
		direction = "down";

		// Hitbox centrada dentro del tile 48x48
		solidArea.set(10f, 10f, 28f, 28f);
		solidAreaDefaultX = solidArea.x;
		solidAreaDefaultY = solidArea.y;

		loadSprites();
	}

	// -------------------------------------------------------
	// CARGA DE SPRITES
	// Sin cambios respecto a antes, solo se agrega dieFrames
	// -------------------------------------------------------
	private void loadSprites() {
		downFrames = loadFrames("monster/gohst_idle_walk_down.png", 8);
		upFrames = loadFrames("monster/gohst_walk_up.png", 8);
		leftFrames = loadFrames("monster/gohst_walk_left.png", 8);
		rightFrames = loadFrames("monster/gohst_walk_right.png", 8);
		dieFrames = loadFrames("monster/gohst_die.png", 8); // ← nuevo
	}

	// Corta una tira VERTICAL de 16×128 en 8 frames de 16×16
	// (sheet[fila][columna] → tomamos columna 0 de cada fila)
	private TextureRegion[] loadFrames(String path, int count) {
		Texture texture = new Texture(Gdx.files.internal(path));
		texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		textures.add(texture);

		TextureRegion[][] sheet = TextureRegion.split(texture, 16, 16);
		TextureRegion[] frames = new TextureRegion[count];

		for (int i = 0; i < count; i++) {
			TextureRegion frame = new TextureRegion(sheet[i][0]);
			frame.flip(false, true); // corrección Y-down
			frames[i] = frame;
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
		Gdx.app.log("Ghost", "Vida restante: " + life + "/" + maxLife);

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

			if (dist <= 600f && !gp.player.isHiddenInBush) {
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
		int maxFrames = 8; // todos los estados del Ghost tienen 8 frames

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
				case "up":
					frames = upFrames;
					break;
				case "left":
					frames = leftFrames;
					break;
				case "right":
					frames = rightFrames;
					break;
				default:
					frames = downFrames;
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

		batch.draw(region, worldX, worldY, DRAW_SIZE, DRAW_SIZE);
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