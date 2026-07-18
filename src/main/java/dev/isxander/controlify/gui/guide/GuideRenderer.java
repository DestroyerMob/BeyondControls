package dev.isxander.controlify.gui.guide;

import com.google.common.collect.Lists;
import dev.isxander.controlify.utils.render.Blit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GuideRenderer {
    private static final List<BoundsProvider> BOUNDS_PROVIDERS = new CopyOnWriteArrayList<>();

    private GuideRenderer() {}

    public static void registerBoundsProvider(BoundsProvider provider) {
        BOUNDS_PROVIDERS.add(provider);
    }

    public static Bounds resolveBounds(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        Bounds bounds = new Bounds(0, 0, width, height);
        for (BoundsProvider provider : BOUNDS_PROVIDERS) {
            try {
                Optional<Bounds> provided = provider.get(minecraft.screen, width, height);
                if (provided.isPresent()) bounds = bounds.intersect(provided.get());
            } catch (Throwable ignored) {
            }
        }
        return bounds;
    }

    public static void render(GuiGraphics graphics, GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast) {
        Bounds bounds = resolveBounds(minecraft);

        Blit.batchDraw(graphics, () -> {
            renderLines(graphics, domain.leftGuides(), minecraft.font, bounds, bottomAligned, false, textContrast);
            renderLines(graphics, domain.rightGuides(), minecraft.font, bounds, bottomAligned, true, textContrast);
        });
    }

    private static void renderLines(GuiGraphics graphics, PrecomputedLines lines, Font font, Bounds bounds, boolean bottomAligned, boolean rightAligned, boolean textContrast) {
        int safeAreaX = 2;
        int safeAreaY = 5;
        int betweenLines = 2;

        int allLinesHeight = lines.height() + (lines.lines().size() - 1) * betweenLines;

        int x = rightAligned ? (bounds.right() - safeAreaX) : (bounds.left() + safeAreaX);
        int y = bottomAligned ? (bounds.bottom() - allLinesHeight - safeAreaY) : (bounds.top() + safeAreaY);

        var list = bottomAligned ? Lists.reverse(lines.lines()) : lines.lines();
        for (PrecomputedLines.PrecomputedLine line : list) {
            int lineX = rightAligned ? (x - line.width()) : x;

            if (textContrast) {
                graphics.fill(
                        lineX + line.backgroundLeft() - 1, y - 1,
                        lineX + line.backgroundRight() + 1, y + font.lineHeight + 1, // use font.lineHeight for the height of the line since we're just contrasting the regular text
                        0x80000000
                );
            }

            graphics.drawString(font, line.text(), lineX, y, 0xFFFFFFFF, !textContrast);

            y += line.height() + betweenLines;
        }
    }

    public record Bounds(int left, int top, int right, int bottom) {
        public Bounds intersect(Bounds other) {
            int newLeft = Math.max(left, other.left);
            int newTop = Math.max(top, other.top);
            int newRight = Math.min(right, other.right);
            int newBottom = Math.min(bottom, other.bottom);
            if (newRight <= newLeft || newBottom <= newTop) return this;
            return new Bounds(newLeft, newTop, newRight, newBottom);
        }
    }

    @FunctionalInterface
    public interface BoundsProvider {
        Optional<Bounds> get(net.minecraft.client.gui.screens.Screen screen, int width, int height);
    }

    public static class Renderable implements net.minecraft.client.gui.components.Renderable {
        private final GuideDomain<?> domain;
        private final Minecraft minecraft;
        private boolean bottomAligned;
        private boolean textContrast;

        public Renderable(GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast) {
            this.domain = domain;
            this.minecraft = minecraft;
            this.bottomAligned = bottomAligned;
            this.textContrast = textContrast;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            GuideRenderer.render(guiGraphics, domain, minecraft, bottomAligned, textContrast);
        }

        public void setBottomAligned(boolean bottomAligned) {
            this.bottomAligned = bottomAligned;
        }

        public void setTextContrast(boolean textContrast) {
            this.textContrast = textContrast;
        }
    }
}
