// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一次解析出来的机器面板：机器真实的 {@link ModularUI}、它在屏幕上的边界，以及按控件顺序排好的
 * 机器槽位、储罐与进度条。BlockEntity 重建（场景重播、跳步）后这份快照就作废，重新解析。
 *
 * <p>控件都画自己的缓存值，所以槽位、储罐、进度条在 {@link MachineUiPanelBuilder} 里统一打开
 * client-side 模式，让它们每帧从真实机器上取值。
 */
record MachineUiPanel(BlockEntity blockEntity, ModularUI modularUi, int originX, int originY, int width, int height,
                      MetaMachine machine, List<SlotWidget> machineSlots, List<Widget> machineTanks,
                      List<ProgressWidget> progressWidgets, @Nullable Widget circuit,
                      @Nullable ConfiguratorPanel configurators, @Nullable Widget tabs) {

    /**
     * 红框要框的那些控件；这一类这台机器没有（比如没电路槽、没画配置器面板）就返回空表，
     * 交给调用方报错。自动输出那种一格一个开关的会返回多个。
     */
    List<Widget> parts(MachineUiPlacement.Outline outline) {
        return switch (outline.part()) {
            case SLOT -> one(slot(outline.index()));
            case TANK -> one(tank(outline.index()));
            case PROGRESS -> one(outline.index() >= 0 && outline.index() < progressWidgets.size() ?
                    progressWidgets.get(outline.index()) : null);
            case CIRCUIT -> one(circuit);
            case POWER, AUTO_OUTPUT, CIRCUIT_BUTTON, DISTINCT -> ConfiguratorTabs.buttons(configurators, outline.part());
        };
    }

    private static List<Widget> one(@Nullable Widget widget) {
        return widget == null ? List.of() : List.of(widget);
    }

    /** 机器 UI 里第 index 个槽位控件；越界返回 null。 */
    @Nullable
    SlotWidget slot(int index) {
        return index < 0 || index >= machineSlots.size() ? null : machineSlots.get(index);
    }

    /** 机器 UI 里第 index 个储罐控件；越界返回 null。 */
    @Nullable
    Widget tank(int index) {
        return index < 0 || index >= machineTanks.size() ? null : machineTanks.get(index);
    }

    /**
     * 机器 UI 里的流体槽：GT 的 TankWidget 和 LDLib 的 TankWidget 是两份实现，都要认。
     * LDLib 那份用的是自己的 {@code com.lowdragmc.lowdraglib.side.fluid.FluidStack}，走 FluidHelperImpl 转换。
     */
    static boolean isTank(Widget widget) {
        return widget instanceof com.gregtechceu.gtceu.api.gui.widget.TankWidget ||
                widget instanceof com.lowdragmc.lowdraglib.gui.widget.TankWidget;
    }

    static @Nullable FluidStack fluidOf(Widget tank) {
        if (tank instanceof com.gregtechceu.gtceu.api.gui.widget.TankWidget gtTank) {
            return gtTank.getFluid();
        }
        if (tank instanceof com.lowdragmc.lowdraglib.gui.widget.TankWidget ldlTank) {
            return FluidHelperImpl.toFluidStack(ldlTank.getFluid());
        }
        return null;
    }

    /**
     * 写储罐。{@code notify} 传 false：那是给服务端同步用的，ponder 里没有服务端，而且控件的
     * {@code lastFluidInTank} 没经过 ModularUIGuiContainer 初始化，进同步路径会 NPE。
     */
    static void setFluid(Widget tank, FluidStack stack) {
        if (tank instanceof com.gregtechceu.gtceu.api.gui.widget.TankWidget gtTank) {
            gtTank.setFluid(stack, false);
        } else if (tank instanceof com.lowdragmc.lowdraglib.gui.widget.TankWidget ldlTank) {
            ldlTank.setFluid(FluidHelperImpl.toFluidStack(stack), false);
        }
    }
}
