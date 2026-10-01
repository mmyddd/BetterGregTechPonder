// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.createmod.ponder.api.element.WorldSectionElement;
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
            redrawWorld(scene);
        }
    }

    @Override
    public void reset(PonderScene scene) {
        if (applied) {
            applied = false;
            edit.revert(machine(scene), machinePos);
            redrawWorld(scene);
        }
        super.reset(scene);
    }

    /**
     * Ponder 把场景方块的渲染缓存住了（{@code PonderScene#seekToTime} 里也是这么刷的），
     * 改完机器状态得让它重画一次，否则要等到跳帧或者点关键帧才看得到。
     */
    private static void redrawWorld(PonderScene scene) {
        scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
    }

    private MetaMachine machine(PonderScene scene) {
        return scene.getWorld().getBlockEntity(machinePos) instanceof IMachineBlockEntity holder ?
                holder.getMetaMachine() : null;
    }
}
