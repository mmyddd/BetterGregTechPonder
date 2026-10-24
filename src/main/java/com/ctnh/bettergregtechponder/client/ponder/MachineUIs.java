// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.ctnh.bettergregtechponder.client.ponder.ui.MachineUI;
import com.ctnh.bettergregtechponder.client.ponder.ui.MachineUiPlacement;
import com.ctnh.bettergregtechponder.client.ponder.ui.MachineUiStart;
import com.ctnh.bettergregtechponder.client.ponder.ui.ToolGridPlacement;

/**
 * 场景侧入口，等价于 CTNH-Lib 的 {@code CTNHPonderSceneBuilder#showUI(MachineUI)}：
 *
 * <pre>{@code
 * MachineUIs.showUI(builder, LV_INPUT_BUS_UI).at(busPos)
 *         .slot(0).withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
 *         .show(200);
 * }</pre>
 */
public final class MachineUIs {

    private MachineUIs() {}

    /** 在当前场景里摆放一个可复用的机器界面；返回起点态，接着必须用 at(...) 给出坐标。 */
    public static MachineUiStart showUI(SceneBuilder builder, MachineUI ui) {
        return ui.in(builder);
    }

    /** 同上，顺便指定这次摆放的缩放（1.0 即 GUI 原始像素），盖过界面定义上的 scale / fitToPanel。 */
    public static MachineUiStart showUI(SceneBuilder builder, MachineUI ui, float scale) {
        return ui.in(builder).scale(scale);
    }

    /**
     * 在方块的某一面上画出 GT 的九宫格工具覆盖层：手持工具对准方块时会浮出来的那块 3x3 格子，
     * 格子里是「点这里会做什么」的图标（扳手转向、软锤启停、硬锤静音……）。
     *
     * <pre>{@code
     * MachineUIs.showToolGrid(builder, controllerPos, Direction.NORTH).highlight(4).show(120);
     * }</pre>
     *
     * <p>
     * 格子按行优先编号（从面外侧看）：0 左上、1 上、2 右上、3 左、4 中心、5 右、6 左下、7 下、8 右下。
     * 图标由方块自己给，所以机器上的盖子、自动输出口都会照实反映出来；给不出图标的格子留空。
     */
    public static ToolGridPlacement showToolGrid(SceneBuilder builder, BlockPos pos, Direction face) {
        return new ToolGridPlacement(builder, pos, face);
    }
}
