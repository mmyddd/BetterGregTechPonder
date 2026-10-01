// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;

/**
 * 思索时间线上的一段「改机器」操作：到点后作用于目标机器，场景回退时还原。
 *
 * <p>UI 那边只按 tick 驱动它，不关心改的是什么；覆盖板是其中一种实现（{@link CoverChange}）。
 */
public interface MachineEdit {

    /** 面板出现多少 tick 后执行。 */
    int delayTicks();

    /** 是否已经执行过。失败也算执行过，免得每 tick 重复报一次错。 */
    boolean isApplied();

    /** 执行；机器不支持时自行报错，不要抛出去。 */
    void apply(MetaMachine machine, BlockPos machinePos);

    /** 场景回退时还原；没执行过的什么都不用做。 */
    default void revert(MetaMachine machine, BlockPos machinePos) {}
}
