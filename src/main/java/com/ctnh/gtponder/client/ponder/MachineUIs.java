// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;

import com.ctnh.gtponder.client.ponder.ui.MachineUI;
import com.ctnh.gtponder.client.ponder.ui.MachineUiPlacement;

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

    /** 在当前场景里摆放一个可复用的机器界面。 */
    public static MachineUiPlacement showUI(SceneBuilder builder, MachineUI ui) {
        return ui.in(builder);
    }

    /** 同上，顺便指定这次摆放的缩放（1.0 即 GUI 原始像素），盖过界面定义上的 scale / fitToPanel。 */
    public static MachineUiPlacement showUI(SceneBuilder builder, MachineUI ui, float scale) {
        return ui.in(builder).scale(scale);
    }
}
