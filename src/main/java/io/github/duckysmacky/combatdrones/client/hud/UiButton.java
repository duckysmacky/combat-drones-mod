package io.github.duckysmacky.combatdrones.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** A vanilla button drawn in the mod's style: a flat dark key with a lit frame, brighter under the pointer. */
public class UiButton extends GuiButton {
    private static final int FILL = 0xFF333942;
    private static final int FILL_HOVER = 0xFF454C56;
    private static final int FILL_DISABLED = 0xFF212428;

    public UiButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void playPressSound(SoundHandler soundHandler) {
        UiSounds.play(UiSounds.CLICK);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        boolean lit = enabled && hovered;
        Ui.rect(x, y, x + width, y + height, !enabled ? FILL_DISABLED : lit ? FILL_HOVER : FILL);
        Ui.frame(x, y, width, height);
        if (lit) {
            Ui.rect(x + 1, y + height - 2, x + width - 1, y + height - 1, Ui.SELECT);
        }
        drawCenteredString(minecraft.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, !enabled ? Ui.FAINT : lit ? Ui.SELECT : Ui.TEXT);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
