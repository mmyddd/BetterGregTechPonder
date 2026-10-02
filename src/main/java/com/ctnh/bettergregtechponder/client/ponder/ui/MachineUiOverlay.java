// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把 {@link MachineUiPanel} 画进屏幕：面板是 Ponder 的 speech box，指针尖落在锚点上，越界就整体推回屏内；
 * tooltip 也在这里画，因为 LDLib 自己的 {@code drawInForeground} 会去找不存在的 {@code ModularUIGuiContainer}。
 */
final class MachineUiOverlay {

    /** 叠在场景之上、文本框之下。 */
    private static final float Z = 250f;
    /** 面板内容与 speech box 边框之间的留白。 */
    private static final int PADDING = 3;
    /** speech box 的指针占位：PonderUI#renderSpeechBox 里的 divotSize(8) + 1 + distance(1)。 */
    private static final int DIVOT_SPAN = 10;
    /** 前景层在这个环境里能不能用，只报一次。 */
    private static boolean foregroundFailed;

    /** 面板离屏幕边缘至少留出的像素。 */
    private static final int MARGIN = 6;
    /** 红框的边框粗细（面板像素，跟着面板一起缩）。 */
    private static final int BOX_THICKNESS = 2;
    /** 红框最淡时的透明度。 */
    private static final float BOX_ALPHA_MIN = 0.30f;
    /** 红框最浓时的透明度；呼吸就在这两者之间来回。 */
    private static final float BOX_ALPHA_MAX = 0.70f;
    /** 编辑模式下贴在槽位 tooltip 首行的序号，参数是机器里的真实槽位序号。 */
    private static final String SLOT_INDEX_KEY = "bettergregtechponder.tooltip.slot_index";
    /** 同上，储罐的序号。 */
    private static final String TANK_INDEX_KEY = "bettergregtechponder.tooltip.tank_index";
    /** 同上，机器页按钮/开关的序号。 */
    private static final String BUTTON_INDEX_KEY = "bettergregtechponder.tooltip.button_index";

    private MachineUiOverlay() {}

    static void render(PonderScene scene, GuiGraphics graphics, PonderUI screen, MachineUiElement owner,
                       MachineUiPanel panel, Vec3 anchor, Pointing pointing, float partialTicks, float fade,
                       float scale, List<Box> boxes, float pulse, boolean full) {
        Vec2 projected = scene.getTransform().sceneToScreen(anchor, partialTicks);
        int width = Math.round(panel.width() * scale) + PADDING * 2;
        int height = Math.round(panel.height() * scale) + PADDING * 2;

        // speech box 实际占用的矩形（与 PonderUI#renderSpeechBox 的布局一致）：越界就整体推回屏内。
        float boxX = switch (pointing) {
            case LEFT -> projected.x + DIVOT_SPAN;
            case RIGHT -> projected.x - width - DIVOT_SPAN;
            default -> projected.x - width / 2f;
        };
        float boxY = switch (pointing) {
            case UP -> projected.y + DIVOT_SPAN;
            case LEFT, RIGHT -> projected.y - height / 2f;
            default -> projected.y - height - DIVOT_SPAN;
        };
        float dx = clampOffset(boxX, width, screen.width);
        float dy = clampOffset(boxY, height, screen.height);

        // 与 InputWindowElement 一致的淡入位移：面板从指向点滑出来。
        float xFade = pointing == Pointing.RIGHT ? -1 : pointing == Pointing.LEFT ? 1 : 0;
        float yFade = pointing == Pointing.DOWN ? -1 : pointing == Pointing.UP ? 1 : 0;
        xFade *= 10 * (1 - fade);
        yFade *= 10 * (1 - fade);

        // 真实指针换算到面板的 UI 坐标：悬停高亮与 tooltip 都靠它。
        // 内容区原点是 speech box 的落点（boxX/boxY）再加内边距，不是指向点。
        Vec2 mouse = guiMouse();
        float contentX = boxX + dx + xFade + PADDING;
        float contentY = boxY + dy + yFade + PADDING;
        float uiMouseX = (float) ((mouse.x - contentX) / scale) + panel.originX();
        float uiMouseY = (float) ((mouse.y - contentY) / scale) + panel.originY();
        // 把落点交给交互层：点「查看 UI 详情」打开后，点击就是按这套换算反算回 UI 坐标的。
        MachineUiInteraction.publish(owner, panel, contentX, contentY, scale, full);

        graphics.pose().pushPose();
        graphics.pose().translate(projected.x + dx + xFade, projected.y + dy + yFade, Z);
        // 指针尖落在锚点上；(0, 0) 之后即面板内容区左上角。
        PonderUI.renderSpeechBox(graphics, 0, 0, width, height, false, pointing, true);
        graphics.pose().translate(PADDING, PADDING, 100);
        graphics.pose().scale(scale, scale, 1);
        // 面板边界可能带负原点（标题栏在面板上方、页签在左侧），对齐到内容区左上角。
        graphics.pose().translate(-panel.originX(), -panel.originY(), 0);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, fade);
        panel.modularUi().mainGroup.drawInBackground(graphics, Math.round(uiMouseX), Math.round(uiMouseY),
                partialTicks);
        drawForeground(panel, graphics, Math.round(uiMouseX), Math.round(uiMouseY), partialTicks);
        // 红框叠在面板之上，仍在同一套缩放与淡入里，所以跟着面板一起缩、一起淡。
        drawBoxes(graphics, boxes, pulse);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.pose().popPose();

        renderTooltips(graphics, panel, uiMouseX, uiMouseY, mouse.x, mouse.y);
    }

    /**
     * 前景层：选中高亮、幽灵槽文字这些画在这里。
     *
     * <p>但它会去要 {@code ModularUIGuiContainer}，而 ponder 里没有这个容器：
     * GT 的 ConfiguratorPanel 与 LDLib 的 SlotWidget 在那里都会 NPE。所以整层包一层保护，
     * 崩了就只跳过前景层（背景层已经画完，面板照常显示），并且只报一次。
     */
    private static void drawForeground(MachineUiPanel panel, GuiGraphics graphics, int mouseX, int mouseY,
                                        float partialTicks) {
        try {
            panel.modularUi().mainGroup.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            if (!foregroundFailed) {
                foregroundFailed = true;
                BetterGregTechPonder.LOGGER.warn("BetterGregTechPonder: the machine UI foreground pass is not " +
                        "available in ponder (no ModularUIGuiContainer); highlights will be skipped", t);
            }
        }
    }

    /** 红框：面板坐标里的空心方框，透明度跟着 pulse 呼吸。 */
    private static void drawBoxes(GuiGraphics graphics, List<Box> boxes, float pulse) {
        if (boxes.isEmpty()) {
            return;
        }
        int alpha = Math.round((BOX_ALPHA_MIN + (BOX_ALPHA_MAX - BOX_ALPHA_MIN) * pulse) * 255f);
        int color = (alpha << 24) | 0xFF3020;
        for (Box box : boxes) {
            int x0 = box.x() - 1;
            int y0 = box.y() - 1;
            int x1 = box.x() + box.width() + 1;
            int y1 = box.y() + box.height() + 1;
            graphics.fill(x0, y0, x1, y0 + BOX_THICKNESS, color);
            graphics.fill(x0, y1 - BOX_THICKNESS, x1, y1, color);
            graphics.fill(x0, y0 + BOX_THICKNESS, x0 + BOX_THICKNESS, y1 - BOX_THICKNESS, color);
            graphics.fill(x1 - BOX_THICKNESS, y0 + BOX_THICKNESS, x1, y1 - BOX_THICKNESS, color);
        }
    }

    /** 面板坐标里的一个矩形，红框用。 */
    record Box(int x, int y, int width, int height) {}

    /** 真实指针位置，换算成 PonderUI 的 GUI 坐标（同 PonderUI 里 MouseHandler#xpos 的换算）。 */
    private static Vec2 guiMouse() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return new Vec2((float) x, (float) y);
    }

    /**
     * tooltip 由这里自己画。LDLib 的 {@code drawInForeground} 会访问不存在的 {@code ModularUIGuiContainer}，
     * 这里改成按悬停到的控件取文字，画在真实指针位置。
     */
    private static void renderTooltips(GuiGraphics graphics, MachineUiPanel panel, float uiMouseX, float uiMouseY,
                                       float screenX, float screenY) {
        try {
            Widget hovered = panel.modularUi().mainGroup.getHoverElement(uiMouseX, uiMouseY);
            List<Component> lines = new ArrayList<>(tooltipFor(hovered, uiMouseX, uiMouseY));
            // 空槽位的 getFullTooltipTexts() 是空列表，序号得在判空之前加。
            appendIndexLine(panel, hovered, lines);
            if (lines.isEmpty()) {
                return;
            }
            graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), (int) screenX,
                    (int) screenY);
        } catch (Throwable t) {
            // 物品 tooltip 可能来自任意模组，出错不该拖垮整个面板。
            BetterGregTechPonder.LOGGER.debug("MachineUI tooltip failed", t);
        }
    }

    /**
     * Ponder 的编辑模式（{@code PonderConfig.Client().editingMode}）打开时，把悬停控件在机器里的真实序号
     * 加在 tooltip 第一行——写场景时对着它填 {@code slot(index)} / {@code tank(index)} /
     * {@code outlineButton(index)}。玩家背包的槽位不算在内，也不出现这行。
     *
     * <p>
     * 槽位、储罐、按钮都靠「控件对象在这份收集清单里的位置」反查序号，和 {@code MachineUiPanel} 里
     * {@code slot}/{@code tank}/{@code button} 用的是同一份清单，所以这里读到的数字就是场景里该填的数字。
     */
    private static void appendIndexLine(MachineUiPanel panel, Widget hovered, List<Component> lines) {
        if (!PonderIndex.editingModeActive()) {
            return;
        }
        if (hovered instanceof SlotWidget slot) {
            int index = panel.machineSlots().indexOf(slot);
            if (index >= 0) {
                lines.add(0, Component.translatable(SLOT_INDEX_KEY, index).withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (MachineUiPanel.isTank(hovered)) {
            int index = panel.machineTanks().indexOf(hovered);
            if (index >= 0) {
                lines.add(0, Component.translatable(TANK_INDEX_KEY, index).withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (hovered instanceof SwitchWidget || hovered instanceof ButtonWidget) {
            int index = panel.machineButtons().indexOf(hovered);
            if (index >= 0) {
                lines.add(0, Component.translatable(BUTTON_INDEX_KEY, index).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static List<Component> tooltipFor(Widget hovered, float mouseX, float mouseY) {
        if (hovered instanceof com.gregtechceu.gtceu.api.gui.widget.TankWidget tank) {
            return tank.getFullTooltipTexts();
        }
        if (hovered instanceof com.lowdragmc.lowdraglib.gui.widget.TankWidget tank) {
            return tank.getFullTooltipTexts();
        }
        if (hovered instanceof SlotWidget slot) {
            // 含 LargeStackSlotWidget 的「64 / 256」数量行。
            return slot.getFullTooltipTexts();
        }
        if (hovered instanceof SwitchWidget || hovered instanceof ButtonWidget) {
            // 按钮的悬停提示只存在控件里，dev 下的 drawTooltipTexts 走的是不存在的容器，这里自己取出来。
            return hovered.getTooltipTexts();
        }
        if (hovered instanceof TabsWidget tabs) {
            IFancyUIProvider tab = tabs.getHoveredTab(mouseX, mouseY);
            return tab == null ? List.of() : tab.getTabTooltips();
        }
        return List.of();
    }

    private static float clampOffset(float start, float size, float screenSize) {
        float min = MARGIN;
        float max = screenSize - MARGIN - size;
        if (max < min) {
            return min - start;
        }
        if (start < min) {
            return min - start;
        }
        if (start > max) {
            return max - start;
        }
        return 0;
    }
}
