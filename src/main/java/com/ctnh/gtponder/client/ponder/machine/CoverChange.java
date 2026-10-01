// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.machine;

import com.ctnh.gtponder.GTPonder;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * 给机器的指定面放一块覆盖板——只是改机器状态，和界面无关。
 *
 * <p>覆盖板用它的物品指定，例如 {@code GTItems.CONVEYOR_MODULE_LV.asStack()}：物品上挂着
 * {@link CoverPlaceBehavior}，定义就在里面；也可以直接给 {@link CoverDefinition}。
 * 用物品指定时会把这件物品一并交给覆盖板，{@code gtceu:facade} 这类要读物品数据的覆盖板得用它。
 *
 * <p>机器没有覆盖板容器、这一面放不下、覆盖板拒绝附着（例如机器这一面没有它要的接口）时，
 * 在日志里报一行 error 并跳过，场景继续播。
 */
public final class CoverChange implements MachineEdit {

    private final Direction side;
    private final ItemStack coverItem;
    @Nullable
    private final CoverDefinition definition;
    private boolean applied;
    /** 放之前这一面上的覆盖板，回退时放回去。 */
    private CoverBehavior previous;

    public CoverChange(Direction side, ItemStack coverItem) {
        this.side = side;
        this.coverItem = coverItem.copy();
        this.definition = null;
    }

    public CoverChange(Direction side, CoverDefinition definition) {
        this.side = side;
        this.coverItem = ItemStack.EMPTY;
        this.definition = definition;
    }

    /** 覆盖板物品上挂的 {@link CoverPlaceBehavior} 就是它的定义。 */
    public static @Nullable CoverDefinition definitionOf(ItemStack stack) {
        if (stack.getItem() instanceof IComponentItem componentItem) {
            for (IItemComponent component : componentItem.getComponents()) {
                if (component instanceof CoverPlaceBehavior placeBehavior) {
                    return placeBehavior.coverDefinition();
                }
            }
        }
        return null;
    }

    public Direction side() {
        return side;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        applied = true;
        ICoverable coverable = machine == null ? null : machine.getCoverContainer();
        if (coverable == null) {
            report(machinePos, "the machine has no cover container");
            return;
        }
        CoverDefinition cover = definition != null ? definition : definitionOf(coverItem);
        if (cover == null) {
            report(machinePos, "this item is not a cover item, it carries no CoverPlaceBehavior");
            return;
        }
        try {
            if (!coverable.canPlaceCoverOnSide(cover, side)) {
                report(machinePos, "this side of the machine cannot take a cover");
                return;
            }
            previous = coverable.getCoverAtSide(side);
            if (!coverable.placeCoverOnSide(side, coverItem, cover, null)) {
                report(machinePos, "the cover refused to attach, the machine may lack the capability it needs");
            }
        } catch (Throwable t) {
            GTPonder.LOGGER.error("GTPonder: {} on the {} side of the machine at {} threw", describe(), side, machinePos,
                    t);
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

    private String describe() {
        return coverItem.isEmpty() ? String.valueOf(definition) : coverItem.getHoverName().getString();
    }

    private void report(BlockPos machinePos, String reason) {
        GTPonder.LOGGER.error("GTPonder: cannot put cover {} on the {} side of the machine at {}: {}", describe(), side,
                machinePos, reason);
    }
}
