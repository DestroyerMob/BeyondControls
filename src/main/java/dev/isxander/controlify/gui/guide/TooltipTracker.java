package dev.isxander.controlify.gui.guide;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /**
     * Finds the tooltip rendered for this exact cursor position. Current-frame
     * bounds always win; the previous frame is only used to bridge render-order
     * differences in overlays which draw after the screen itself.
     */
    public static Optional<GuideRenderer.Bounds> boundsForCursor(Screen screen, int mouseX, int mouseY) {
        if (screen != currentScreen) return Optional.empty();

        Optional<GuideRenderer.Bounds> current = matchingBounds(currentBounds, mouseX, mouseY, false);
        if (current.isPresent()) return current;
        return matchingBounds(previousBounds, mouseX, mouseY, true);
    }

    private static Optional<GuideRenderer.Bounds> matchingBounds(List<TrackedTooltip> tooltips,
                                                                  int mouseX, int mouseY,
                                                                  boolean translate) {
        for (int i = tooltips.size() - 1; i >= 0; i--) {
            TrackedTooltip tooltip = tooltips.get(i);
            // EMI clamps the Y passed to its positioner to at least 16.
            boolean sameCursor = Math.abs(tooltip.mouseX() - mouseX) <= 2
                    && Math.abs(tooltip.mouseY() - Math.max(16, mouseY)) <= 2;
            if (!sameCursor) continue;
            return Optional.of(translate
                    ? tooltip.bounds().translate(mouseX - tooltip.mouseX(), mouseY - tooltip.mouseY())
                    : tooltip.bounds());
        }
        return Optional.empty();
    }

    private record TrackedTooltip(GuideRenderer.Bounds bounds, int mouseX, int mouseY) {}
}
