// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import org.jetbrains.annotations.Nullable;

/**
 * 把机器的正面转到某个方向，也就是蹲下右键那一下的效果。
 *
 * <p>
 * 正面朝向存在方块状态的 {@code facing} 属性里，所以走 {@link MetaMachine#setFrontFacing}：它会先问
 * 这一面能不能当正面（自动输出面、覆盖板、旋转状态都会拦），能才写回去。被拦下来时什么都不改，
 * 只报一行 error，场景照常往下播。
 *
 * <p>
 * Ponder 的世界不会把新状态写回方块实体，改完要从世界里读回来手动同步，否则机器读到的正面还是旧的。
 *
 * <p>
 * 回退时转回原来那一面。
 */
public final class FrontFacingChange implements MachineEdit {

    private final Direction facing;
    /** 改之前的正面，回退时转回去。 */
    @Nullable
    private Direction previous;
    private boolean applied;

    public FrontFacingChange(Direction facing) {
        this.facing = facing;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        if (machine == null) {
            report(machinePos, "there is no machine here");
            return;
        }
        if (!machine.hasFrontFacing()) {
            report(machinePos, "this machine has no front face");
            return;
        }
        Direction before = machine.getFrontFacing();
        if (before == facing) {
            return;
        }
        if (!turn(machine, machinePos, facing)) {
            return;
        }
        previous = before;
        applied = true;
    }

    @Override
    public void revert(MetaMachine machine, BlockPos machinePos) {
        Direction before = previous;
        boolean wasApplied = applied;
        previous = null;
        applied = false;
        if (!wasApplied || before == null || machine == null) {
            return;
        }
        turn(machine, machinePos, before);
    }

    /** 转过去并把新状态同步回方块实体；被拦下来时返回 false。 */
    private boolean turn(MetaMachine machine, BlockPos machinePos, Direction target) {
        try {
            machine.setFrontFacing(target);
        } catch (Throwable t) {
            BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: turning the front of the machine at {} to {} threw", machinePos, target, t);
            return false;
        }
        BlockState turned;
        try {
            // setFrontFacing 被拦下来时不抛异常，只是什么都不做，所以到世界里回读确认。
            turned = machine.getLevel() == null ? null : machine.getLevel().getBlockState(machinePos);
        } catch (Throwable t) {
            BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: reading the block state of the machine at {} threw", machinePos, t);
            return false;
        }
        if (turned == null || !turned.hasProperty(machine.getRotationState().property) ||
                turned.getValue(machine.getRotationState().property) != target) {
            report(machinePos, "that side cannot become the front face");
            return false;
        }
        try {
            machine.holder.getSelf().setBlockState(turned);
        } catch (Throwable t) {
            BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: syncing the block state of the machine at {} threw", machinePos, t);
            return false;
        }
        return machine.getFrontFacing() == target;
    }

    private void report(BlockPos machinePos, String reason) {
        BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot turn the front of the machine at {} to {}: {}", machinePos, facing,
                reason);
    }
}
