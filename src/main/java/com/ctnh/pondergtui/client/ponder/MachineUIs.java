// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.pondergtui.client.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;

import com.ctnh.pondergtui.client.ponder.ui.MachineUI;
import com.ctnh.pondergtui.client.ponder.ui.MachineUiPlacement;

/**
 * 场景侧入口，等价于 CTNH 的 {@code CTNHPonderSceneBuilder#showUI}：
 *
 * <pre>{@code
 * MachineUIs.showUI(builder, LV_INPUT_BUS_UI).at(anchor).forMachine(pos)
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
}
