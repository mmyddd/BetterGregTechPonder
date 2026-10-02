// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.ui;

import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;

/**
 * {@code at(Vec3)} 之后的中间态：箭头已经定好，必须给出机器位置才能继续。
 * 忘了写 {@link #machinePos(BlockPos)} 就调 {@code show(...)} 之类的，在这里是编译错误。
 */
public final class MachineUiAnchor {

    private final MachineUiPlacement placement;

    MachineUiAnchor(MachineUiPlacement placement) {
        this.placement = placement;
    }

    /** 指定这次画哪台机器的界面。 */
    public MachineUiPlacement machinePos(BlockPos machinePos) {
        return placement.machinePos(machinePos);
    }

    /** 面板落在指向点的哪一侧，默认 {@link Pointing#DOWN}。 */
    public MachineUiAnchor pointing(Pointing pointing) {
        placement.pointing(pointing);
        return this;
    }

    /** 这次摆放单独指定缩放。 */
    public MachineUiAnchor scale(float scale) {
        placement.scale(scale);
        return this;
    }
}
