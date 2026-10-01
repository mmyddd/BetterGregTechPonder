// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

/**
 * 设置机器的物品/流体自动输出口朝向——只是改机器状态，和界面无关。
 *
 * <p>顺带把这一路自动输出打开：GT 给输出面画一个箭头，自动输出开着时再多一个标记，场景里才看得出来
 * 东西从哪一面出去。朝向与开关在回退时都还原。
 */
public final class AutoOutputChange implements MachineEdit {

    private final Direction side;
    private final boolean items;
    private final boolean fluids;
    @Nullable
    private Direction previousItems;
    @Nullable
    private Direction previousFluids;
    private boolean previousItemsAuto;
    private boolean previousFluidsAuto;
    private boolean itemsApplied;
    private boolean fluidsApplied;

    public AutoOutputChange(Direction side, boolean items, boolean fluids) {
        this.side = side;
        this.items = items;
        this.fluids = fluids;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        if (items) {
            if (machine instanceof IAutoOutputItem output) {
                try {
                    previousItems = output.getOutputFacingItems();
                    previousItemsAuto = output.isAutoOutputItems();
                    output.setOutputFacingItems(side);
                    output.setAutoOutputItems(true);
                    itemsApplied = true;
                } catch (Throwable t) {
                    BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: setting the item auto-output side of the machine at {} to {} " +
                            "threw", machinePos, side, t);
                }
            } else {
                report(machinePos, true, "this machine cannot auto-output items");
            }
        }
        if (fluids) {
            if (machine instanceof IAutoOutputFluid output) {
                try {
                    previousFluids = output.getOutputFacingFluids();
                    previousFluidsAuto = output.isAutoOutputFluids();
                    output.setOutputFacingFluids(side);
                    output.setAutoOutputFluids(true);
                    fluidsApplied = true;
                } catch (Throwable t) {
                    BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: setting the fluid auto-output side of the machine at {} to {} " +
                            "threw", machinePos, side, t);
                }
            } else {
                report(machinePos, false, "this machine cannot auto-output fluids");
            }
        }
    }

    @Override
    public void revert(MetaMachine machine, BlockPos machinePos) {
        if (itemsApplied) {
            itemsApplied = false;
            if (machine instanceof IAutoOutputItem output) {
                output.setOutputFacingItems(previousItems);
                output.setAutoOutputItems(previousItemsAuto);
            }
            previousItems = null;
        }
        if (fluidsApplied) {
            fluidsApplied = false;
            if (machine instanceof IAutoOutputFluid output) {
                output.setOutputFacingFluids(previousFluids);
                output.setAutoOutputFluids(previousFluidsAuto);
            }
            previousFluids = null;
        }
    }

    private void report(BlockPos machinePos, boolean itemSide, String reason) {
        BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot set the {} auto-output side of the machine at {} to {}: {}",
                itemSide ? "item" : "fluid", machinePos, side, reason);
    }
}
