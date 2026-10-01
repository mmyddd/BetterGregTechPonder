// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * 场景侧入口：把机器改动挂到思索时间线上。和界面无关，
 *
 * <pre>{@code
 * MachineEdits.placeCover(scene, busPos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack());
 * }</pre>
 */
public final class MachineEdits {

    private MachineEdits() {}

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
}
