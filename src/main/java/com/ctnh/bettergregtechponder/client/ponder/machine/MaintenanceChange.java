// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;

import net.minecraft.core.BlockPos;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;

/**
 * 把维护仓的故障清掉（可选同时贴上维护胶带）：只写机器状态，界面里那份故障列表读的是同一个字段。
 *
 * <p>
 * 回退时把故障位与胶带状态都还原成改动前的样子。
 */
public final class MaintenanceChange implements MachineEdit {

    /** 无故障。 */
    private static final byte NO_PROBLEMS = 0;

    private final boolean taped;
    private byte previousProblems;
    private boolean previousTaped;
    private boolean applied;

    /** 清掉全部故障，并贴上维护胶带（默认）。 */
    public MaintenanceChange() {
        this(true);
    }

    public MaintenanceChange(boolean taped) {
        this.taped = taped;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        if (!(machine instanceof MaintenanceHatchPartMachine hatch)) {
            BetterGregTechPonder.LOGGER.error("MaintenanceChange expected a maintenance hatch, but {} sits at {}",
                    machine.getDefinition(), machinePos);
            return;
        }
        previousProblems = hatch.getMaintenanceProblems();
        previousTaped = hatch.isTaped();
        hatch.setMaintenanceProblems(NO_PROBLEMS);
        if (taped) {
            hatch.setTaped(true);
        }
        applied = true;
    }

    @Override
    public void revert(MetaMachine machine, BlockPos machinePos) {
        if (applied && machine instanceof MaintenanceHatchPartMachine hatch) {
            hatch.setMaintenanceProblems(previousProblems);
            hatch.setTaped(previousTaped);
            applied = false;
        }
    }
}
