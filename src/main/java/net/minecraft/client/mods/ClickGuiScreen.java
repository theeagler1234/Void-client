package net.minecraft.client.mods;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

/**
 * Simple click GUI for the built-in client mods. Opened / closed with Right Shift.
 */
public class ClickGuiScreen extends Screen {

	private static final int RIGHT_SHIFT = 344;

	private static final int PANEL_W = 150;
	private static final int TITLE_H = 16;
	private static final int ROW_H = 14;
	private static final int COUNT = 6;

	private static final String[] NAMES = { "Fullbright", "No Particles", "No Hurt Cam", "Freecam", "ViaBlocks (rejoin)", "Below Y0 (rejoin)" };

	public ClickGuiScreen() {
		super(new StringTextComponent("Mods"));
	}

	private boolean get(int i) {
		switch (i) {
		case 0:
			return Mods.fullbright;
		case 1:
			return Mods.noParticles;
		case 2:
			return Mods.noHurtCam;
		case 4:
			return Mods.viaBlocks;
		case 5:
			return Mods.belowY0;
		default:
			return Mods.freecam;
		}
	}

	private void toggle(int i) {
		switch (i) {
		case 0:
			Mods.fullbright = !Mods.fullbright;
			break;
		case 1:
			Mods.noParticles = !Mods.noParticles;
			break;
		case 2:
			Mods.noHurtCam = !Mods.noHurtCam;
			break;
		case 4:
			Mods.viaBlocks = !Mods.viaBlocks;
			break;
		case 5:
			Mods.belowY0 = !Mods.belowY0;
			break;
		default:
			Mods.setFreecam(!Mods.freecam);
			break;
		}
	}

	private int panelX() {
		return (this.width - PANEL_W) / 2;
	}

	private int panelY() {
		return (this.height - (TITLE_H + ROW_H * COUNT)) / 2;
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTicks) {
		this.fillGradient(0, 0, this.width, this.height, 0x60000000, 0x60000000);
		int x = panelX();
		int y = panelY();
		fill(x, y, x + PANEL_W, y + TITLE_H, 0xFF202020);
		this.drawCenteredString(this.font, "Mods", x + PANEL_W / 2, y + 4, 0xFFFFFF);
		for (int i = 0; i < COUNT; ++i) {
			int ry = y + TITLE_H + i * ROW_H;
			boolean on = get(i);
			boolean hover = mouseX >= x && mouseX < x + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H;
			int bg = on ? (hover ? 0xFF2E9E43 : 0xFF248A38) : (hover ? 0xFF4A4A4A : 0xFF353535);
			fill(x, ry, x + PANEL_W, ry + ROW_H, bg);
			this.drawString(this.font, NAMES[i], x + 4, ry + 3, 0xFFFFFF);
			String state = on ? "ON" : "OFF";
			this.drawString(this.font, state, x + PANEL_W - 4 - this.font.getStringWidth(state), ry + 3,
					on ? 0xFFFFFF : 0xAAAAAA);
		}
		super.render(mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int x = panelX();
			int y = panelY() + TITLE_H;
			if (mouseX >= x && mouseX < x + PANEL_W && mouseY >= y && mouseY < y + ROW_H * COUNT) {
				toggle((int) (mouseY - y) / ROW_H);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == RIGHT_SHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
