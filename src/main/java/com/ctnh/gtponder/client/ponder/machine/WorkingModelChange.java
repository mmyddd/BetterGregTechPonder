// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.machine;

import com.ctnh.gtponder.GTPonder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

/**
 * 只换机器的模型：工作状态画的是运行中的正面贴图（{@code overlay_front_active}），待机画普通贴图。
 *
 * <p>配方逻辑一点不动：状态、进度、耗电、工作开关都原样，只换外观。
 *
 * <p>外观存在机器的 {@link MachineRenderState} 里：能工作的机器看 {@code RECIPE_LOGIC_STATUS}，
 * 少数机器（世界加速器那种）挂在 {@code IS_ACTIVE} 上，两种都认；都没有就在日志里报一行 error。
 */
public final class WorkingModelChange implements MachineEdit {

    /** 状态常量的名字：官方枚举是 {@code RecipeLogic.Status}，CTNH 分支里叫 {@code WorkLogic.Status}，所以按名字找。 */
    private static final String WORKING = "WORKING";
    private static final String IDLE = "IDLE";

    private final boolean working;
    /** 改之前那整个模型状态，回退时写回去。 */
    @Nullable
    private MachineRenderState previous;

    public WorkingModelChange(boolean working) {
        this.working = working;
    }

    @Override
    public void apply(MetaMachine machine, BlockPos machinePos) {
        if (machine == null) {
            report(machinePos, "there is no machine here");
            return;
        }
        try {
            MachineRenderState state = machine.getRenderState();
            if (state.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)) {
                MachineRenderState next = withStatus(state, GTMachineModelProperties.RECIPE_LOGIC_STATUS,
                        working ? WORKING : IDLE);
                if (next == null) {
                    report(machinePos, "the model has no " + (working ? WORKING : IDLE) + " state");
                    return;
                }
                previous = state;
                machine.setRenderState(next);
            } else if (state.hasProperty(GTMachineModelProperties.IS_ACTIVE)) {
                previous = state;
                machine.setRenderState(state.setValue(GTMachineModelProperties.IS_ACTIVE, working));
            } else {
                report(machinePos, "the model has no working or idle state");
            }
        } catch (Throwable t) {
            GTPonder.LOGGER.error("GTPonder: showing the machine at {} as {} threw", machinePos, describe(), t);
        }
    }

    @Override
    public void revert(MetaMachine machine, BlockPos machinePos) {
        MachineRenderState before = previous;
        previous = null;
        if (before == null || machine == null) {
            return;
        }
        try {
            machine.setRenderState(before);
        } catch (Throwable t) {
            GTPonder.LOGGER.error("GTPonder: restoring the model of the machine at {} threw", machinePos, t);
        }
    }

    /** 按名字取状态常量，找不到返回 {@code null}。 */
    @Nullable
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static MachineRenderState withStatus(MachineRenderState state, EnumProperty<?> property, String name) {
        for (Object value : property.getPossibleValues()) {
            if (value instanceof Enum<?> constant && constant.name().equals(name)) {
                return state.setValue((Property) property, (Comparable) value);
            }
        }
        return null;
    }

    private void report(BlockPos machinePos, String reason) {
        GTPonder.LOGGER.error("GTPonder: cannot show the machine at {} as {}: {}", machinePos, describe(), reason);
    }

    private String describe() {
        return working ? "working" : "idle";
    }
}
