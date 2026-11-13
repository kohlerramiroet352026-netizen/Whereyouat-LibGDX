package com.whereyouat.monster;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.whereyouat.entity.Entity;
import com.whereyouat.items.DropManager;
import com.whereyouat.map.TileManager;
import com.whereyouat.screens.GameScreen;

public class MON_zombie extends Entity {
	public GameScreen gp;
	public String name;

	private static final float FRAME_W_NATIVE = 16f;
	private static final float FRAME_H_NATIVE = 16f;
	private static final float SCALE = 3.0f;
	public static final float DRAW_WIDTH = FRAME_W_NATIVE * SCALE;
	public static final float DRAW_HEIGHT = FRAME_H_NATIVE * SCALE;

	public static final int STATE_WALK = 0;
	public static final int STATE_HIT = 1;
	public static final int STATE_DIE = 2;

	private static final int FRAMES_IDLE = 4;
	private static final int FRAMES_WALK = 4;
	private static final int FRAMES_DIE = 4;
	private static final int FRAMES_DOWN = 4;

	public int state = STATE_WALK;
	private int previousState = STATE_WALK;

	private float knockbackDx = 0f;
	private float knockbackDy = 0f;

	private boolean dropProcessed = false;
	private boolean killedByPlayer = false;

	private final List<Texture> textures = new ArrayList<>();

	private TextureRegion[] downFrames;
	private TextureRegion[] upFrames;
	private TextureRegion[] leftFrames;
	private TextureRegion[] rightFrames;
	private TextureRegion[] dieFrames;
	private TextureRegion[] idleFrames;

	private int actionLockCounter = 0;

	public MON_zombie(GameScreen gp) {
		super(0, 0, 80f);
		this.gp = gp;

		name = "Zombie";
		maxLife = 8;
		life = maxLife;
		direction = "down";

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

	private void loadSprites() {
		downFrames = loadColumn("monster/zomb_walk_down.png", FRAMES_DOWN);
		idleFrames = loadColumn("monster/zomb_idle.png", FRAMES_IDLE);
		leftFrames = loadColumn("monster/zomb_walk_left.png", FRAMES_WALK);
		rightFrames = loadColumn("monster/zomb_walk_right.png", FRAMES_WALK);
		upFrames = loadColumn("monster/zomb_walk_up.png", FRAMES_WALK);
		dieFrames = loadColumn("monster/zomb_die.png", FRAMES_DIE);
	}

	private TextureRegion[] loadColumn(String path, int frameCount) {
		Texture tex = new Texture(Gdx.files.internal(path));
		tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		textures.add(tex);

		TextureRegion[] frames = new TextureRegion[frameCount];
		int fw = (int) FRAME_W_NATIVE;
		int fh = (int) FRAME_H_NATIVE;

		for (int i = 0; i < frameCount; i++) {
			TextureRegion r = new TextureRegion(tex, 0, i * fh, fw, fh);
			r.flip(false, true);
			frames[i] = r;
		}
		return frames;
	}

	public void takeDamage(int damage, float playerX, float playerY) {
		if (state == STATE_DIE)
			return;

		if (damage < 9000) {
			killedByPlayer = true;
		}

		life -= damage;
		Gdx.app.log("Zombie", "Vida restante: " + life + "/" + maxLife);

		if (life <= 0) {
			state = STATE_DIE;
			spriteNum = 1;
			spriteCounter = 0;
		} else {
			state = STATE_HIT;
			spriteNum = 1;
			spriteCounter = 0;

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

	@Override
	public void update(float delta) {
		if (state == STATE_DIE) {
			updateAnimation(delta);
			return;
		}

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

		if (gp != null && gp.player != null && !gp.player.dead) {
			float ghostCX = worldX + solidArea.x + solidArea.width / 2f;
			float ghostCY = worldY + solidArea.y + solidArea.height / 2f;
			float playerCX = gp.player.worldX + gp.player.solidArea.x + gp.player.solidArea.width / 2f;
			float playerCY = gp.player.worldY + gp.player.solidArea.y + gp.player.solidArea.height / 2f;

			float dx = playerCX - ghostCX;
			float dy = playerCY - ghostCY;
			float dist = (float) Math.hypot(dx, dy);

			if (dist <= 1200f && !gp.player.isHiddenInBush) {
				if (dist > 1f) {
					float moveX = (dx / dist) * speed * delta;
					float moveY = (dy / dist) * speed * delta;
					if (!collides(worldX + moveX, worldY))
						worldX += moveX;
					if (!collides(worldX, worldY + moveY))
						worldY += moveY;
				}

				if (Math.abs(dx) >= Math.abs(dy)) {
					direction = dx > 0 ? "right" : "left";
				} else {
					direction = dy > 0 ? "down" : "up";
				}
			} else {
				setRandomAction(delta);
			}
		} else {
			setRandomAction(delta);
		}

		updateAnimation(delta);
	}

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

	private void updateAnimation(float delta) {
		if (state != previousState) {
			previousState = state;
			spriteNum = 1;
			spriteCounter = 0;
		}

		float animSpeed;
		int maxFrames = 4;

		switch (state) {
			case STATE_HIT:
				animSpeed = 5f;
				break;
			case STATE_DIE:
				animSpeed = 7f;
				break;
			default:
				animSpeed = 6f;
				break;
		}

		spriteCounter += delta * 60f;
		if (spriteCounter >= animSpeed) {
			spriteNum++;
			if (spriteNum > maxFrames) {
				if (state == STATE_DIE) {
					spriteNum = maxFrames;
					deleteGhost();
					return;
				} else if (state == STATE_HIT) {
					state = STATE_WALK;
				}
				spriteNum = 1;
			}
			spriteCounter = 0;
		}
	}

	private void deleteGhost() {
		if (killedByPlayer && !dropProcessed && gp != null) {
			dropProcessed = true;
			DropManager.processMonsterDrop(this, gp);
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
	public void draw(SpriteBatch batch) {
		TextureRegion[] frames;

		if (state == STATE_DIE) {
			frames = dieFrames;
		} else {
			switch (direction) {
				case "down":
					frames = downFrames;
					break;
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

		if (state == STATE_HIT && (spriteNum / 2) % 2 == 0) {
			batch.setColor(1f, 1f, 1f, 0.3f);
		} else {
			batch.setColor(1f, 1f, 1f, 1f);
		}

		int waterLevel = 0;
		if (gp != null && gp.tileM != null && gp.mapGen != null) {
			float footX = worldX + solidArea.x + solidArea.width / 2f;
			float footY = worldY + solidArea.y + solidArea.height / 2f;
			int col = (int) (footX / TileManager.TILE_SIZE);
			int row = (int) (footY / TileManager.TILE_SIZE);
			waterLevel = gp.tileM.getWaterLevel(gp.mapGen, col, row);
		}

		if (waterLevel > 0 && gp != null && gp.camera != null) {
			float cutOff = (waterLevel == 1) ? 16f : 26f;
			float yShift = (waterLevel == 1) ? 4f : 8f;
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

			TextureRegion foam = gp.tileM.getFoamRegion();
			if (foam != null) {
				float foamY = drawY + DRAW_HEIGHT - cutOff - 3f;
				float foamX = worldX + (DRAW_WIDTH - TileManager.TILE_SIZE) / 2f;
				batch.setColor(1f, 1f, 1f, 0.9f);
				batch.draw(foam, foamX, foamY, TileManager.TILE_SIZE, TileManager.TILE_SIZE);
			}
		} else {
			batch.draw(region, worldX, worldY, DRAW_WIDTH, DRAW_HEIGHT);
		}

		batch.setColor(1f, 1f, 1f, 1f);
	}

	@Override
	public void dispose() {
		for (Texture tex : textures) {
			tex.dispose();
		}
		textures.clear();
	}
}
