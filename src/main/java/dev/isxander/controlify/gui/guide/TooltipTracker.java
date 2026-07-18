package dev.isxander.controlify.gui.guide;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Tracks final tooltip rectangles from both the current and most recently completed frame. */
public final class TooltipTracker {
    private static Screen currentScreen;
    private static int frameMouseX;
    private static int frameMouseY;
    private static int previousRetentionFrames;
    private static final List<TrackedTooltip> currentBounds = new ArrayList<>();
    private static final List<TrackedTooltip> previousBounds = new ArrayList<>();

    private TooltipTracker() {}

    public static void beginFrame(Screen screen, int mouseX, int mouseY) {
        if (screen != currentScreen) {
            previousBounds.clear();
            currentBounds.clear();
            previousRetentionFrames = 0;
        } else if (!currentBounds.isEmpty()) {
            previousBounds.clear();
            previousBounds.addAll(currentBounds);
            currentBounds.clear();
            previousRetentionFrames = 2;
        } else if (previousRetentionFrames > 0) {
            previousRetentionFrames--;
        } else {
            previousBounds.clear();
        }
        currentScreen = screen;
        frameMouseX = mouseX;
        frameMouseY = mouseY;
    }

    public static void record(Screen screen, int mouseX, int mouseY,
                              int x, int y, int width, int height) {
        if (screen == null || screen != currentScreen) return;
        currentBounds.add(new TrackedTooltip(
                new GuideRenderer.Bounds(x - 4, y - 4, x + width + 4, y + height + 4),
                mouseX,
                mouseY
        ));
    }

    public static List<GuideRenderer.Bounds> boundsFor(Screen screen) {
        if (screen != currentScreen) return List.of();
        List<GuideRenderer.Bounds> bounds = new ArrayList<>(currentBounds.size() + previousBounds.size());
        currentBounds.stream().map(TrackedTooltip::bounds).forEach(bounds::add);
        previousBounds.stream().map(tooltip -> tooltip.bounds().translate(
                frameMouseX - tooltip.mouseX(),
                frameMouseY - tooltip.mouseY()
        )).forEach(bounds::add);
        return List.copyOf(bounds);
    }

    private record TrackedTooltip(GuideRenderer.Bounds bounds, int mouseX, int mouseY) {}
}
