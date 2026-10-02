// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;

import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * 场景侧入口：把机器改动挂到思索时间线上，和界面无关。
 *
 * <pre>{@code
 * MachineEdits.placeCover(scene, busPos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack());
 * MachineEdits.setWorkingModel(scene, machinePos, true, 20);
 * MachineEdits.setItemOutput(scene, machinePos, Direction.UP, 20);
 * }</pre>
 */
public final class MachineEdits {

    private MachineEdits() {}

    /**
     * 让场景里的方块重画一次。Ponder 把场景方块的渲染缓存住了（{@code PonderScene#seekToTime} 里也是这么刷的），
     * 改完机器状态不重画，要等到跳帧或者点关键帧才看得到。
     */
    public static void redraw(PonderScene scene) {
        scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
    }

    /** 立刻（下一 tick）执行这段机器改动。 */
    public static void add(SceneBuilder scene, BlockPos machinePos, MachineEdit edit) {
        add(scene, machinePos, edit, 0);
    }

    /** delayTicks 个 tick 后执行这段机器改动。 */
    public static void add(SceneBuilder scene, BlockPos machinePos, MachineEdit edit, int delayTicks) {
        scene.addInstruction(new MachineEditInstruction(edit, machinePos, delayTicks));
    }

    /** 给机器的指定面放一块覆盖板，用覆盖板物品指定。 */
    public static void placeCover(SceneBuilder scene, BlockPos machinePos, Direction side, ItemStack coverItem) {
        placeCover(scene, machinePos, side, coverItem, 0);
    }

    /** delayTicks 个 tick 后放这块覆盖板。 */
    public static void placeCover(SceneBuilder scene, BlockPos machinePos, Direction side, ItemStack coverItem,
                                  int delayTicks) {
        add(scene, machinePos, new CoverChange(side, coverItem), delayTicks);
    }

    /** 同上，直接给覆盖板定义。 */
    public static void placeCover(SceneBuilder scene, BlockPos machinePos, Direction side, CoverDefinition cover) {
        placeCover(scene, machinePos, side, cover, 0);
    }

    public static void placeCover(SceneBuilder scene, BlockPos machinePos, Direction side, CoverDefinition cover,
                                  int delayTicks) {
        add(scene, machinePos, new CoverChange(side, cover), delayTicks);
    }

    /** 把机器的模型切成工作中的样子（{@code working} 为 false 就是待机）。只换外观，配方逻辑不动。 */
    public static void setWorkingModel(SceneBuilder scene, BlockPos machinePos, boolean working) {
        setWorkingModel(scene, machinePos, working, 0);
    }

    /** delayTicks 个 tick 后换外观。 */
    public static void setWorkingModel(SceneBuilder scene, BlockPos machinePos, boolean working, int delayTicks) {
        add(scene, machinePos, new WorkingModelChange(working), delayTicks);
    }

    /** 把物品自动输出口设到某个面，顺带打开物品自动输出。 */
    public static void setItemOutput(SceneBuilder scene, BlockPos machinePos, Direction side) {
        setItemOutput(scene, machinePos, side, 0);
    }

    /** delayTicks 个 tick 后设置。 */
    public static void setItemOutput(SceneBuilder scene, BlockPos machinePos, Direction side, int delayTicks) {
        add(scene, machinePos, new AutoOutputChange(side, true, false), delayTicks);
    }

    /** 把流体自动输出口设到某个面，顺带打开流体自动输出。 */
    public static void setFluidOutput(SceneBuilder scene, BlockPos machinePos, Direction side) {
        setFluidOutput(scene, machinePos, side, 0);
    }

    public static void setFluidOutput(SceneBuilder scene, BlockPos machinePos, Direction side, int delayTicks) {
        add(scene, machinePos, new AutoOutputChange(side, false, true), delayTicks);
    }

    /** 物品与流体一起设到同一面。 */
    public static void setAutoOutput(SceneBuilder scene, BlockPos machinePos, Direction side) {
        setAutoOutput(scene, machinePos, side, 0);
    }

    public static void setAutoOutput(SceneBuilder scene, BlockPos machinePos, Direction side, int delayTicks) {
        add(scene, machinePos, new AutoOutputChange(side, true, true), delayTicks);
    }

    /** 设置并行仓的并行数。 */
    public static void setParallel(SceneBuilder scene, BlockPos hatchPos, int amount) {
        add(scene, hatchPos, new ParallelChange(amount));
    }

    public static void setParallel(SceneBuilder scene, BlockPos hatchPos, int amount, int delayTicks) {
        add(scene, hatchPos, new ParallelChange(amount), delayTicks);
    }

    /** 清掉维护仓的全部故障（默认同时贴上维护胶带）。 */
    public static void fixMaintenance(SceneBuilder scene, BlockPos hatchPos) {
        add(scene, hatchPos, new MaintenanceChange());
    }

    public static void fixMaintenance(SceneBuilder scene, BlockPos hatchPos, int delayTicks) {
        add(scene, hatchPos, new MaintenanceChange(), delayTicks);
    }

    /** 只是把故障位清零、不贴胶带。 */
    public static void fixMaintenanceWithoutTape(SceneBuilder scene, BlockPos hatchPos, int delayTicks) {
        add(scene, hatchPos, new MaintenanceChange(false), delayTicks);
    }

    /**
     * 机器状态被场景指令改过：画着这些机器的面板下次 tick 重建，控件才能读到新值。
     *
     * <p>
     * 由 {@link MachineEditInstruction} 在改动与回退之后自动请求，场景侧不用管。
     */
    public static void requestRebuild() {
        rebuildGeneration++;
    }

    /**
     * 当前的重建代际：每来一次改动请求就 +1。
     *
     * <p>
     * 用代际而不是布尔标志：布尔标志会被第一个 tick 到的面板吃掉，场景里还有别的 UI 元素时，
     * 真正改动过的那台机器反而等不到重建。各面板记住自己见过的代际，就能各自重建一次。
     */
    public static long rebuildGeneration() {
        return rebuildGeneration;
    }

    private static long rebuildGeneration;
}
