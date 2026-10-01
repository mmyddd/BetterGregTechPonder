// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.scenes;

import com.ctnh.gtponder.client.ponder.MachineUIs;
import com.ctnh.gtponder.client.ponder.machine.MachineEdits;
import com.ctnh.gtponder.client.ponder.ui.MachineUI;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 示例场景：把 ULV 输入总线真实的 fancy UI 画在机器上方，往 0 号槽位写 64 个草方块，
 * 再在机器顶面贴一条传送带覆盖板。
 *
 * <p>storyboard 是 3x3 地板 + (1,1,1) 的 {@code gtceu:ulv_input_bus}（facing=north）。
 */
public class InputBusUi {

    /** 界面定义一次，其余场景可以直接复用这个常量。 */
    private static final MachineUI ULV_INPUT_BUS_UI = MachineUI.of(GTMachines.ITEM_IMPORT_BUS[GTValues.ULV])
            .scale(0.6f);

    private InputBusUi() {}

    public static void common(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("input_bus_ui", "Input Bus UI");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos busPos = util.grid().at(1, 1, 1);
        scene.world().showSection(util.select().position(busPos), Direction.DOWN);
        scene.overlay().showText(60)
                .text("The panel is the machine's own UI, without the player inventory.")
                .pointAt(util.vector().centerOf(busPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);

        // 同一个界面常量的第一次调用：只画界面。
        MachineUIs.showUI(scene, ULV_INPUT_BUS_UI).at(busPos).show(120);
        scene.idle(130);

        // 第二次调用：往 0 号槽位写 64 个草方块，面板显示的就是机器真实库存。
        MachineUIs.showUI(scene, ULV_INPUT_BUS_UI).at(busPos)
                .slot(0)
                .withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
                .show(200);
        scene.overlay().showText(80)
                .text("64 grass blocks go into slot 0; the panel shows the machine's real inventory.")
                .attachKeyFrame();
        scene.idle(120);

        // 覆盖板是改机器状态，跟界面无关：直接挂一条场景指令，这条不画面板。
        MachineEdits.placeCover(scene, busPos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack(), 10);
        scene.idle(50);
        scene.overlay().showText(70)
                .text("Covers can be put on a chosen side as well: a conveyor on top.")
                .pointAt(util.vector().topOf(busPos))
                .attachKeyFrame();
        scene.idle(120);
        scene.markAsFinished();
    }
}
