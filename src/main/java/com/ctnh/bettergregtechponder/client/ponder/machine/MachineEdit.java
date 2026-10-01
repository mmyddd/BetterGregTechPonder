// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;

/**
 * 一段「改机器状态」的操作：作用在目标机器上，场景回退时还原。
 *
 * <p>什么时候执行由 {@link MachineEditInstruction} 决定，它跟 UI 没关系——面板画不画、画在哪，
 * 都不影响机器改动。
 */
public interface MachineEdit {

    /** 执行；机器不支持时自行报错，不要抛出去。 */
    void apply(MetaMachine machine, BlockPos machinePos);

    /** 场景回退时还原，没执行过的什么都不用做。 */
    default void revert(MetaMachine machine, BlockPos machinePos) {}
}
