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
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * 示例场景：把 LV 化学反应釜真实的 fancy UI 画在机器上方，往 0 号槽位写 64 个草方块、0 号储罐灌 1000 mB 水，
 * 最后在顶面贴一条传送带覆盖板。
 *
 * <p>storyboard 是 3x3 地板 + (1,1,1) 的 {@code gtceu:lv_chemical_reactor}（facing=north）。
 */
public class ChemicalReactorUi {

    /** 界面定义一次，其余场景可以直接复用这个常量。 */
    private static final MachineUI LV_CHEMICAL_REACTOR_UI = MachineUI.of(GTMachines.CHEMICAL_REACTOR[GTValues.LV])
            .scale(0.6f);

    private ChemicalReactorUi() {}

    public static void common(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("chemical_reactor_ui", "Chemical Reactor UI");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos machinePos = util.grid().at(1, 1, 1);
        scene.world().showSection(util.select().position(machinePos), Direction.DOWN);
        scene.overlay().showText(60)
                .text("The panel is the machine's own UI, without the player inventory.")
                .pointAt(util.vector().centerOf(machinePos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);

        // 第一次调用：只画界面。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos).show(120);
        scene.idle(130);

        // 第二次调用：往 0 号槽位写 64 个草方块。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos)
                .slot(1)
                .withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
                .show(200);
        scene.overlay().showText(80)
                .text("64 grass blocks go into slot 0; the panel shows the machine's real inventory.")
                .attachKeyFrame();
        scene.idle(120);

        // 第三次调用：往 0 号储罐灌 1000 mB 水，数量同样从 0 在 1 秒内涨到目标值。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos)
                .tank(0)
                .withFluid(new FluidStack(Fluids.WATER, 1000), 20)
                .show(200);
        scene.overlay().showText(80)
                .text("Fluids work the same way: tank 0 fills from 0 to 1000 mB in one second.")
                .attachKeyFrame();
        scene.idle(120);

        // 覆盖板是改机器状态，跟界面无关：直接挂一条场景指令，这条不画面板。
        MachineEdits.placeCover(scene, machinePos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack(), 10);
        scene.idle(50);
        scene.overlay().showText(70)
                .text("Covers can be put on a chosen side as well: a conveyor on top.")
                .pointAt(util.vector().topOf(machinePos))
                .attachKeyFrame();
        scene.idle(120);
        scene.markAsFinished();
    }
}
