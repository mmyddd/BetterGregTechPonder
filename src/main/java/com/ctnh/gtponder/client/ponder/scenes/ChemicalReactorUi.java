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
 * 示例场景：把 LV 化学反应釜真实的 fancy UI 画在机器上方，往 1 号槽位写 64 个草方块、2 号槽位写 64 个玻璃、
 * 0 号储罐灌 1000 mB 水，在顶面贴一条传送带覆盖板，把机器模型切成工作中的样子再切回待机，
 * 最后只给一个配方 id，让面板自己把料填好、进度条走完、成品出来。
 *
 * <p>storyboard 是 3x3 地板 + (1,1,1) 的 {@code gtceu:lv_chemical_reactor}（facing=north）。
 *
 * <p>文案写在调用点上，不用常量：{@code title(...)} 的第一个参数是场景 id，Ponder 按
 * {@code gtponder.ponder.<场景 id>.header|text_N} 把这里的正文收进 lang，改文案不用同步改别处。
 */
public class ChemicalReactorUi {

    /** 界面定义一次，其余场景可以直接复用这个常量。 */
    private static final MachineUI LV_CHEMICAL_REACTOR_UI = MachineUI.of(GTMachines.CHEMICAL_REACTOR[GTValues.LV])
            .scale(0.6f);

    private ChemicalReactorUi() {}

    public static void common(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("chemical_reactor_ui", "LV Chemical Reactor");
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

        // 第一次调用：只画界面。注意每段的 idle 要不短于上一块面板的寿命（show + 2×5 tick 淡入淡出），
        // 否则下一段的面板会在上一段还没淡出时就出现，看起来像「上一段的写入没被还原」。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos).show(120);
        scene.idle(140);

        // 第二次调用：往 1 号槽位写 64 个草方块、2 号槽位写 64 个玻璃。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos)
                .slot(1)
                .withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
                .slot(2)
                .withItem(new ItemStack(Items.GLASS, 64), 20)
                .show(160);
        scene.overlay().showText(80)
                .text("64 grass blocks go into slot 1 and 64 glass into slot 2; the panel shows the machine's real inventory.")
                .attachKeyFrame();
        scene.idle(180);

        // 第三次调用：往 0 号储罐灌 1000 mB 水，数量同样从 0 在 1 秒内涨到目标值。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos)
                .tank(0)
                .withFluid(new FluidStack(Fluids.WATER, 1000), 20)
                .show(160);
        scene.overlay().showText(80)
                .text("Fluids work the same way: tank 0 fills from 0 to 1000 mB in one second.")
                .attachKeyFrame();
        scene.idle(180);

        // 覆盖板是改机器状态，跟界面无关：直接挂一条场景指令，这条不画面板。
        MachineEdits.placeCover(scene, machinePos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack(), 10);
        scene.idle(40);
        scene.overlay().showText(70)
                .text("Covers can be put on a chosen side as well: a conveyor on top.")
                .pointAt(util.vector().topOf(machinePos))
                .attachKeyFrame();
        scene.idle(150);

        // 第五次：只换模型，正面亮起运行中的贴图，配方逻辑一点没动；最后再切回待机。
        MachineEdits.setWorkingModel(scene, machinePos, true, 10);
        scene.overlay().showText(70)
                .text("The model can be switched between working and idle: only the front overlay changes, the recipe logic is left alone.")
                .pointAt(util.vector().centerOf(machinePos))
                .attachKeyFrame();
        scene.idle(90);
        MachineEdits.setWorkingModel(scene, machinePos, false, 10);
        scene.idle(30);

        // 第六次：只给一个配方 id，入料、进度条、成品都自动走完。
        MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos)
                .recipe("gtceu:chemical_reactor/sodium_sulfide", 10)
                .show(160);
        scene.overlay().showText(80)
                .text("One recipe id does the rest: the panel fills the inputs, runs the progress bar, then drops the product in.")
                .attachKeyFrame();
        scene.idle(180);
        scene.markAsFinished();
    }
}
