// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.ui;

import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * {@code showUI(...)} 之后、还没给出坐标的中间态。只有两个出口：
 * {@link #at(BlockPos)}（一步到位，指向方块中心并画该方块上的机器）或 {@link #at(Vec3)}（只定箭头，
 * 之后必须用 {@link MachineUiAnchor#machinePos(BlockPos)} 补上机器位置）。
 *
 * <p>
 * 这样写漏机器位置在编译期就会报错，不必等运行期的日志。
 */
public final class MachineUiStart {

    private final MachineUiPlacement placement;

    MachineUiStart(MachineUiPlacement placement) {
        this.placement = placement;
    }

    /** 一步到位：指向该方块的中心，也画这个方块上的机器。 */
    public MachineUiPlacement at(BlockPos machinePos) {
        return placement.at(machinePos);
    }

    /** 只定箭头指向；返回的中间态必须再给机器位置才能继续。 */
    public MachineUiAnchor at(Vec3 anchor) {
        return new MachineUiAnchor(placement.anchor(anchor));
    }

    /** 面板落在指向点的哪一侧，默认 {@link Pointing#DOWN}。 */
    public MachineUiStart pointing(Pointing pointing) {
        placement.pointing(pointing);
        return this;
    }

    /** 这次摆放单独指定缩放。 */
    public MachineUiStart scale(float scale) {
        placement.scale(scale);
        return this;
    }
}
