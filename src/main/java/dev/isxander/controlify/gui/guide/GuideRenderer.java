package dev.isxander.controlify.gui.guide;

import com.google.common.collect.Lists;
import dev.isxander.controlify.utils.render.Blit;
import dev.isxander.controlify.mixins.feature.guide.screen.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.function.Supplier;

public final class GuideRenderer {
    private GuideRenderer() {}

    private static Bounds screenBounds(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        return new Bounds(0, 0, width, height);
    }

    public static void render(GuiGraphics graphics, GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast) {
        render(graphics, domain, minecraft, bottomAligned, textContrast, screenBounds(minecraft));
    }

    public static void render(GuiGraphics graphics, GuideDomain<?> domain, Minecraft minecraft,
                              boolean bottomAligned, boolean textContrast, Bounds bounds) {

        Blit.batchDraw(graphics, () -> {
            renderLines(graphics, domain.leftGuides(), minecraft.font, bounds, bottomAligned, false, textContrast);
            renderLines(graphics, domain.rightGuides(), minecraft.font, bounds, bottomAligned, true, textContrast);
        });
    }

    public static Bounds belowContainer(AbstractContainerScreen<?> screen) {
        var accessor = (AbstractContainerScreenAccessor) screen;
        int bottom = accessor.getTopPos() + accessor.getImageHeight();
        // Creative tabs protrude below the nominal container texture.
        if (screen instanceof CreativeModeInventoryScreen) bottom += 28;
        return new Bounds(
                accessor.getLeftPos(),
                Math.min(bottom, screen.height),
                accessor.getLeftPos() + accessor.getImageWidth(),
                screen.height
        );
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
            if (!bottomAligned && y + line.height() > bounds.bottom() - safeAreaY) break;
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
    }

    public static class Renderable implements net.minecraft.client.gui.components.Renderable {
        private final GuideDomain<?> domain;
        private final Minecraft minecraft;
        private boolean bottomAligned;
        private boolean textContrast;
        private final Supplier<Bounds> boundsSupplier;

        public Renderable(GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast) {
            this(domain, minecraft, bottomAligned, textContrast, null);
        }

        public Renderable(GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast,
                          Supplier<Bounds> boundsSupplier) {
            this.domain = domain;
            this.minecraft = minecraft;
            this.bottomAligned = bottomAligned;
            this.textContrast = textContrast;
            this.boundsSupplier = boundsSupplier;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (boundsSupplier == null) {
                GuideRenderer.render(guiGraphics, domain, minecraft, bottomAligned, textContrast);
            } else {
                GuideRenderer.render(guiGraphics, domain, minecraft, bottomAligned, textContrast, boundsSupplier.get());
            }
        }

        public void setBottomAligned(boolean bottomAligned) {
            this.bottomAligned = bottomAligned;
        }

        public void setTextContrast(boolean textContrast) {
            this.textContrast = textContrast;
        }
    }
}
