package io.github.duckysmacky.combatdrones.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Shared tooltip layout for mod items: an optional one-line summary ({@code <key>.tooltip}), then either a
 * "hold SHIFT" hint or the detail lines {@code <key>.details.1}, {@code .2}, ... Lang entries are optional; items
 * without them just show nothing extra.
 */
@SideOnly(Side.CLIENT)
public final class Tooltips {
    private Tooltips() {
    }

    public static void addSummary(String translationKey, List<String> tooltip) {
        String key = translationKey + ".tooltip";
        if (I18n.hasKey(key)) {
            tooltip.add(TextFormatting.GRAY + I18n.format(key));
        }
    }

    /** Adds the SHIFT hint, or the detail lines when SHIFT is held. Returns true if the details are showing. */
    public static boolean addDetails(String translationKey, List<String> tooltip, Object... args) {
        if (!GuiScreen.isShiftKeyDown()) {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.combat_drones.hold_shift"));
            return false;
        }
        for (int line = 1; I18n.hasKey(translationKey + ".details." + line); line++) {
            tooltip.add(TextFormatting.GRAY + I18n.format(translationKey + ".details." + line, args));
        }
        return true;
    }

    /** Display name of a key binding, for "press G"-style hints that follow the player's rebinds. */
    public static String keyName(KeyBinding binding) {
        return binding.getDisplayName();
    }
}
