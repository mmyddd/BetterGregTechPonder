// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.utils.ToolItemHelper;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次九宫格覆盖层的摆放，由 {@code MachineUIs#showToolGrid} 创建：
 * {@code MachineUIs.showToolGrid(scene, pos, Direction.NORTH).highlight(1).show(120)}。
 *
 * <p>
 * 默认在这块面外挂一排「右键 + 手里的工具」的按键提示，图标抬到网格上方（朝上的面抬一格）。
 * 挡住格子时用 {@link #toolIconLift(double)} 再抬，不要提示就调 {@link #hideTool()}。
 */
public final class ToolGridPlacement {

    private final SceneBuilder builder;
    private final BlockPos pos;
    private final Direction face;
    private final List<Highlight> highlights = new ArrayList<>();
    private GTToolType tool = GTToolType.WRENCH;
    private boolean tips = true;
    private boolean toolIcon = true;
    /** 按键提示前面要不要再挂一句「蹲下 +」。 */
    private boolean sneakClick;
    private Pointing iconPointing = Pointing.DOWN;
    /** 图标额外抬高多少格，没写就按面方向取默认值。 */
    private @Nullable Double iconLift;
    @Nullable
    private ItemStack iconOverride;

    public ToolGridPlacement(SceneBuilder builder, BlockPos pos, Direction face) {
        this.builder = builder;
        this.pos = pos;
        this.face = face;
    }

    /** 问方块要哪个工具的图标：扳手转向、软锤启停、硬锤静音，默认扳手；按键提示里的物品也跟着换。 */
    public ToolGridPlacement tool(GTToolType tool) {
        this.tool = tool;
        return this;
    }

    /** 按键提示改用指定物品，不写就按 {@link #tool} 取默认材质的工具。 */
    public ToolGridPlacement toolItem(ItemStack stack) {
        this.iconOverride = stack.copy();
        return this;
    }

    /** 图标浮在指向点的哪一侧，默认朝下（图标在面上方）。 */
    public ToolGridPlacement toolIcon(Pointing pointing) {
        this.iconPointing = pointing;
        return this;
    }

    /** 图标再往上抬多少格，默认朝上的面抬一格、其余面抬半格，传 0 就贴在面中心。 */
    public ToolGridPlacement toolIconLift(double blocks) {
        this.iconLift = Math.max(0, blocks);
        return this;
    }

    /** 按键提示前面再加一句「蹲下 +」，变成「蹲下 + 右键 工具」。 */
    public ToolGridPlacement sneakClick() {
        this.sneakClick = true;
        return this;
    }

    /** 不挂按键提示。 */
    public ToolGridPlacement hideTool() {
        this.toolIcon = false;
        return this;
    }

    /** 不画格子里的图标，只留网格。 */
    public ToolGridPlacement hideTips() {
        this.tips = false;
        return this;
    }

    /**
     * 高亮第 {@code cell} 格，行优先、从面外侧看：0 左上、1 上、2 右上、3 左、4 中心、
     * 5 右、6 左下、7 下、8 右下，可以点名多格。
     *
     * <p>
     * 被点名的格子画满，其余压到 0x44，与实机的高亮一致；一格都不点名时取正对这一面的中心格。
     */
    public ToolGridPlacement highlight(int cell) {
        return highlight(cell, 0);
    }

    public ToolGridPlacement highlight(int cell, int delayTicks) {
        highlights.add(new Highlight(clamp(cell), Math.max(0, delayTicks)));
        return this;
    }

    /** 展示 ticks 个 tick，高亮按各自登记的延迟亮起。 */
    public void show(int ticks) {
        if (toolIcon) {
            showToolIcon(ticks);
        }
        builder.addInstruction(new ShowToolGridInstruction(
                new ToolGridElement(new Plan(pos, face, tool, tips, List.copyOf(highlights))), ticks));
    }

    /** 把这排按键提示挂在这块面外侧，箭头指着要操作的那一面。 */
    private void showToolIcon(int ticks) {
        ItemStack stack = iconOverride != null ? iconOverride : defaultToolItem(tool);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // 指向点压在面中心，再抬一段，免得图标挡住格子。
        double lift = iconLift == null ? (face == Direction.UP ? 1.0 : 0.5) : iconLift;
        Vec3 anchor = Vec3.atCenterOf(pos)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + lift, face.getStepZ() * 0.5);
        InputElementBuilder controls = builder.overlay().showControls(anchor, iconPointing, ticks);
        // 从左到右依次是「蹲下 +」「右键」「工具」，与实机一致。
        if (sneakClick) {
            controls.whileSneaking();
        }
        controls.rightClick();
        controls.withItem(stack);
    }

    /** 按 {@link GTToolType} 取一把默认材质的工具物品，取不到就不挂提示（九宫格照常画）。 */
    private static @Nullable ItemStack defaultToolItem(GTToolType tool) {
        try {
            ItemStack stack = ToolItemHelper.getToolItem(tool);
            return stack == null || stack.isEmpty() ? null : stack;
        } catch (Throwable t) {
            BetterGregTechPonder.LOGGER.warn("BetterGregTechPonder: cannot build a default item icon for the tool {} - pass toolItem(stack) to " +
                    "choose one yourself; this segment keeps the grid and drops the icon", tool, t);
            return null;
        }
    }

    private static int clamp(int cell) {
        return Math.max(0, Math.min(8, cell));
    }

    record Plan(BlockPos pos, Direction face, GTToolType tool, boolean tips, List<Highlight> highlights) {}

    record Highlight(int cell, int delayTicks) {}
}
