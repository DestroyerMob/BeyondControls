package dev.isxander.controlify.gui.guide;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Tracks final tooltip rectangles for the screen frame currently being rendered. */
public final class TooltipTracker {
    private static Screen currentScreen;
    private static final List<GuideRenderer.Bounds> bounds = new ArrayList<>();

    private TooltipTracker() {}

    public static void beginFrame(Screen screen) {
        currentScreen = screen;
        bounds.clear();
    }

    public static void record(Screen screen, int x, int y, int width, int height) {
        if (screen == null || screen != currentScreen) return;
        bounds.add(new GuideRenderer.Bounds(x - 4, y - 4, x + width + 4, y + height + 4));
    }

    public static List<GuideRenderer.Bounds> boundsFor(Screen screen) {
        return screen == currentScreen ? List.copyOf(bounds) : List.of();
    }
}
