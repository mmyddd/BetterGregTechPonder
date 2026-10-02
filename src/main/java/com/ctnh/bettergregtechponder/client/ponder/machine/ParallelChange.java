// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;

import net.minecraft.core.BlockPos;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;

/**
 * 改并行仓的并行数：只写机器状态，界面上那个并行数输入框读到的是同一个值。
 *
 * <p>
 * 回退时恢复成改动前的数值。
 */
public final class ParallelChange implements MachineEdit {

    private final int amount;
    private int previous;
    private boolean applied;

    public ParallelChange(int amount) {
        this.amount = amount;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        if (!(machine instanceof ParallelHatchPartMachine hatch)) {
            BetterGregTechPonder.LOGGER.error("ParallelChange expected a parallel hatch, but {} sits at {}",
                    machine.getDefinition(), machinePos);
            return;
        }
        previous = hatch.getCurrentParallel();
        hatch.setCurrentParallel(amount);
        applied = true;
    }

    @Override
    public void revert(MetaMachine machine, BlockPos machinePos) {
        if (applied && machine instanceof ParallelHatchPartMachine hatch) {
            hatch.setCurrentParallel(previous);
            applied = false;
        }
    }
}
