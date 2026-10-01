// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.machine;

import com.ctnh.gtponder.GTPonder;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 在机器的指定面放一块覆盖板。
 *
 * <p>机器没有覆盖板容器、这一面放不下、覆盖板拒绝附着（例如机器这一面没有它要的接口）时，
 * 在日志里报一行 error 并跳过，场景继续播。
 */
public final class CoverChange implements MachineEdit {

    private final Direction side;
    private final ResourceLocation coverId;
    private final int delayTicks;
    private boolean applied;
    /** 放之前这一面上的覆盖板，回退时放回去。 */
    private CoverBehavior previous;

    public CoverChange(Direction side, ResourceLocation coverId, int delayTicks) {
        this.side = side;
        this.coverId = coverId;
        this.delayTicks = Math.max(0, delayTicks);
    }

    public Direction side() {
        return side;
    }

    public ResourceLocation coverId() {
        return coverId;
    }

    @Override
    public int delayTicks() {
        return delayTicks;
    }

    @Override
    public boolean isApplied() {
        return applied;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        applied = true;
        ICoverable coverable = machine == null ? null : machine.getCoverContainer();
        if (coverable == null) {
            report(machinePos, "the machine has no cover container");
            return;
        }
        CoverDefinition definition = GTRegistries.COVERS.get(coverId);
        if (definition == null) {
            report(machinePos, "no cover is registered under this id");
            return;
        }
        try {
            if (!coverable.canPlaceCoverOnSide(definition, side)) {
                report(machinePos, "this side of the machine cannot take a cover");
                return;
            }
            previous = coverable.getCoverAtSide(side);
            if (!coverable.placeCoverOnSide(side, ItemStack.EMPTY, definition, null)) {
                report(machinePos, "the cover refused to attach, the machine may lack the capability it needs");
            }
        } catch (Throwable t) {
            GTPonder.LOGGER.error("GTPonder: cover {} on the {} side of the machine at {} threw", coverId, side,
                    machinePos, t);
        }
    }

    @Override
    public void revert(MetaMachine machine, BlockPos machinePos) {
        if (!applied) {
            return;
        }
        applied = false;
        ICoverable coverable = machine == null ? null : machine.getCoverContainer();
        if (coverable == null) {
            return;
        }
        if (previous == null) {
            coverable.removeCover(false, side, null);
        } else {
            coverable.setCoverAtSide(previous, side);
            coverable.notifyBlockUpdate();
            coverable.markDirty();
        }
        previous = null;
    }

    private void report(BlockPos machinePos, String reason) {
        GTPonder.LOGGER.error("GTPonder: cannot put cover {} on the {} side of the machine at {}: {}", coverId, side,
                machinePos, reason);
    }
}
