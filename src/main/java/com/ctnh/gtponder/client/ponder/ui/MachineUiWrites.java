// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import com.ctnh.gtponder.GTPonder;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一次摆放的写入时间线：往第几个槽位、第几个储罐写多少东西、从第几个 tick 开始。数量一律在
 * {@link #FILL_TICKS} 个 tick 内从 0 叠到目标值，面板收起或场景回退时按写入前的内容还原。
 *
 * <p>摆放自带的写入（{@code slot(...).withItem(...)}）排在前面，配方追加的排在后面，
 * 回退时把追加的那批截掉，免得重播时越叠越多。
 */
final class MachineUiWrites {

    /** 数量从 0 叠到目标值的固定时长：1 秒。 */
    static final int FILL_TICKS = 20;

    private final BlockPos machinePos;
    private final List<MachineUiPlacement.SlotWrite> slots;
    /** 摆放自带的那批写入的条数。 */
    private final int ownSlots;
    private final List<MachineUiPlacement.FluidWrite> tanks;
    private final int ownTanks;
    private boolean[] written;
    /** 各写入槽位在首次写入前的内容，回退时按它还原。 */
    private ItemStack[] originals;
    private boolean[] fluidWritten;
    /** 各写入储罐在首次写入前的流体，回退时按它还原。 */
    private FluidStack[] fluidOriginals;

    MachineUiWrites(BlockPos machinePos, List<MachineUiPlacement.SlotWrite> slots,
                    List<MachineUiPlacement.FluidWrite> tanks) {
        this.machinePos = machinePos;
        this.slots = new ArrayList<>(slots);
        this.ownSlots = slots.size();
        this.written = new boolean[ownSlots];
        this.originals = new ItemStack[ownSlots];
        this.tanks = new ArrayList<>(tanks);
        this.ownTanks = tanks.size();
        this.fluidWritten = new boolean[ownTanks];
        this.fluidOriginals = new FluidStack[ownTanks];
    }

    /** 追加一条槽位写入（配方用）。 */
    void addSlot(int index, ItemStack stack, int delayTicks) {
        slots.add(new MachineUiPlacement.SlotWrite(index, stack.copy(), delayTicks));
        written = Arrays.copyOf(written, slots.size());
        originals = Arrays.copyOf(originals, slots.size());
    }

    /** 追加一条储罐写入（配方用）。 */
    void addTank(int index, FluidStack stack, int delayTicks) {
        tanks.add(new MachineUiPlacement.FluidWrite(index, stack.copy(), delayTicks));
        fluidWritten = Arrays.copyOf(fluidWritten, tanks.size());
        fluidOriginals = Arrays.copyOf(fluidOriginals, tanks.size());
    }

    /** 回退时把追加的写入摘掉，下次重播再按配方摊一遍。 */
    void dropAdded() {
        while (slots.size() > ownSlots) {
            slots.remove(slots.size() - 1);
        }
        while (tanks.size() > ownTanks) {
            tanks.remove(tanks.size() - 1);
        }
        written = Arrays.copyOf(written, slots.size());
        originals = Arrays.copyOf(originals, slots.size());
        fluidWritten = Arrays.copyOf(fluidWritten, tanks.size());
        fluidOriginals = Arrays.copyOf(fluidOriginals, tanks.size());
    }

    /** BlockEntity 被重建（场景重播 / 跳步）后，写入要重放一遍。 */
    void resetMarks() {
        Arrays.fill(written, false);
        Arrays.fill(fluidWritten, false);
    }

    /** 跑一次时间线：到点的槽位与储罐按各自的延迟写入。 */
    void tick(MachineUiPanel panel, int ticksShown) {
        tickSlots(panel, ticksShown);
        tickTanks(panel, ticksShown);
    }

    /** 槽位写入时间线：到点后让数量在 {@link #FILL_TICKS} 个 tick 内从 0 叠加到目标值。 */
    private void tickSlots(MachineUiPanel panel, int ticksShown) {
        for (int i = 0; i < slots.size(); i++) {
            if (written[i]) {
                continue;
            }
            MachineUiPlacement.SlotWrite write = slots.get(i);
            int elapsed = ticksShown - write.delayTicks();
            if (elapsed < 0) {
                continue;
            }
            SlotWidget slot = panel.slot(write.index());
            if (slot == null) {
                continue;
            }
            if (originals[i] == null) {
                // 第一次动这个槽位之前记下原值，回退时按它还原。
                originals[i] = slot.getItem().copy();
            }
            int target = write.stack().getCount();
            if (elapsed >= FILL_TICKS) {
                writeSlot(slot, write.stack().copy(), i);
                written[i] = true;
                continue;
            }
            int count = (int) Math.round(target * (elapsed / (double) FILL_TICKS));
            if (count <= 0) {
                continue;
            }
            ItemStack partial = write.stack().copy();
            partial.setCount(count);
            writeSlot(slot, partial, i);
        }
    }

    /** 储罐写入时间线：与槽位同理，让数量在 {@link #FILL_TICKS} 个 tick 内从 0 涨到目标值。 */
    private void tickTanks(MachineUiPanel panel, int ticksShown) {
        for (int i = 0; i < tanks.size(); i++) {
            if (fluidWritten[i]) {
                continue;
            }
            MachineUiPlacement.FluidWrite write = tanks.get(i);
            int elapsed = ticksShown - write.delayTicks();
            if (elapsed < 0) {
                continue;
            }
            Widget tank = panel.tank(write.index());
            if (tank == null) {
                continue;
            }
            if (fluidOriginals[i] == null) {
                FluidStack current = MachineUiPanel.fluidOf(tank);
                fluidOriginals[i] = current == null ? FluidStack.EMPTY : current.copy();
            }
            int target = write.stack().getAmount();
            if (elapsed >= FILL_TICKS) {
                writeTank(tank, write.stack().copy(), i);
                fluidWritten[i] = true;
                continue;
            }
            int amount = (int) Math.round(target * (elapsed / (double) FILL_TICKS));
            if (amount <= 0) {
                continue;
            }
            FluidStack partial = write.stack().copy();
            partial.setAmount(amount);
            writeTank(tank, partial, i);
        }
    }

    /**
     * 把写进机器的内容还原：槽位回到写入前的内容、储罐回到写入前的流体，并清掉写入标记。
     * 面板演完与场景回退都走这里，所以每一段 {@code showUI} 结束后机器都是干净的。
     */
    void restore(MachineUiPanel panel) {
        int restoredSlots = 0;
        int restoredTanks = 0;
        if (panel != null) {
            for (int i = 0; i < slots.size(); i++) {
                if (written[i] && originals[i] != null) {
                    SlotWidget slot = panel.slot(slots.get(i).index());
                    if (slot != null) {
                        writeSlot(slot, originals[i].copy(), i);
                        restoredSlots++;
                    }
                }
            }
            for (int i = 0; i < tanks.size(); i++) {
                if (fluidWritten[i] && fluidOriginals[i] != null) {
                    Widget tank = panel.tank(tanks.get(i).index());
                    if (tank != null) {
                        writeTank(tank, fluidOriginals[i].copy(), i);
                        restoredTanks++;
                    }
                }
            }
        }
        resetMarks();
        if (restoredSlots + restoredTanks > 0) {
            GTPonder.LOGGER.info("GTPonder: restored {} slot(s) and {} tank(s) of the machine at {}", restoredSlots,
                    restoredTanks, machinePos);
        }
    }

    /** 写入控件出错不该让游戏崩掉：记一行日志，这条写入就算做完。 */
    private void writeSlot(SlotWidget slot, ItemStack stack, int index) {
        try {
            slot.setItem(stack);
        } catch (Throwable t) {
            written[index] = true;
            GTPonder.LOGGER.error("GTPonder: writing slot {} failed (machine at {})", index, machinePos, t);
        }
    }

    private void writeTank(Widget tank, FluidStack stack, int index) {
        try {
            MachineUiPanel.setFluid(tank, stack);
        } catch (Throwable t) {
            fluidWritten[index] = true;
            GTPonder.LOGGER.error("GTPonder: writing tank {} failed (machine at {})", index, machinePos, t);
        }
    }
}
