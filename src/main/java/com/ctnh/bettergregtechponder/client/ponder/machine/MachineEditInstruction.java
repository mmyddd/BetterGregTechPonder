// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;

/**
 * 把一段机器改动挂到思索时间线上：{@code delayTicks} 个 tick 后执行一次，场景回退时还原。
 *
 * <p>机器按坐标现场解析，所以不持有实例；它和 UI 完全无关，面板画不画都照常生效。
 */
public class MachineEditInstruction extends TickingInstruction {

    private final MachineEdit edit;
    private final BlockPos machinePos;
    private boolean applied;

    public MachineEditInstruction(MachineEdit edit, BlockPos machinePos, int delayTicks) {
        // 0 tick 的指令会在场景 tick 之前就被判定完成，所以至少留一 tick。
        super(false, Math.max(1, delayTicks));
        this.edit = edit;
        this.machinePos = machinePos;
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (!applied && remainingTicks == 0) {
            applied = true;
            edit.apply(machine(scene), machinePos);
            MachineEdits.redraw(scene);
            // 机器状态变了：让画着它的面板下次 tick 重建，控件才能读到新值。
            MachineEdits.requestRebuild();
        }
    }

    @Override
    public void reset(PonderScene scene) {
        if (applied) {
            applied = false;
            edit.revert(machine(scene), machinePos);
            MachineEdits.redraw(scene);
            MachineEdits.requestRebuild();
        }
        super.reset(scene);
    }

    private MetaMachine machine(PonderScene scene) {
        return scene.getWorld().getBlockEntity(machinePos) instanceof IMachineBlockEntity holder ?
                holder.getMetaMachine() : null;
    }
}
