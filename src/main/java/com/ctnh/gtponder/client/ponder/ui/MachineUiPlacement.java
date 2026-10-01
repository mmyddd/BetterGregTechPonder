// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import com.ctnh.gtponder.client.ponder.machine.CoverChange;
import com.ctnh.gtponder.client.ponder.machine.MachineEdit;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MachineUI} 的一次摆放：指向点、指针方向与槽位写入计划，由 {@code MachineUIs#showUI} 创建。
 *
 * <p>机器默认按指向点所在方块解析；{@link #at(BlockPos)} 直接用该方块的中心当指向点，
 * 需要「尾巴指向这里、面板画那台机器」时用 {@link #at(Vec3, BlockPos)}。
 */
public final class MachineUiPlacement {

    private final SceneBuilder builder;
    private final MachineUI ui;
    private final List<SlotWrite> writes = new ArrayList<>();
    /** 时间线上的机器改动：覆盖板等，UI 只负责按 tick 驱动。 */
    private final List<MachineEdit> edits = new ArrayList<>();
    private Vec3 anchor = Vec3.ZERO;
    private Pointing pointing = Pointing.DOWN;
    private BlockPos machinePos;

    MachineUiPlacement(SceneBuilder builder, MachineUI ui) {
        this.builder = builder;
        this.ui = ui;
    }

    /** 指向点：面板的指针尖（speech box 的小尾巴）对齐这个场景坐标。 */
    public MachineUiPlacement at(Vec3 anchor) {
        this.anchor = anchor;
        return this;
    }

    /** 面板落在指向点的哪一侧，默认 {@link Pointing#DOWN}（面板在指向点上方，指针朝下）。 */
    public MachineUiPlacement pointing(Pointing pointing) {
        this.pointing = pointing;
        return this;
    }

    /** 指向该方块的中心，面板也展示这个方块上的机器。 */
    public MachineUiPlacement at(BlockPos machinePos) {
        return at(Vec3.atCenterOf(machinePos), machinePos);
    }

    /** 指向点与机器分开指定：尾巴对准 anchor，面板画 machinePos 上的机器。 */
    public MachineUiPlacement at(Vec3 anchor, BlockPos machinePos) {
        this.anchor = anchor;
        this.machinePos = machinePos;
        return this;
    }

    /** 在指定面放一块覆盖板，覆盖板用 GT 的注册 id 指定，例如 {@code gtceu:conveyor}。 */
    public MachineUiPlacement cover(Direction side, ResourceLocation coverId) {
        return cover(side, coverId, 0);
    }

    /** 面板出现 delayTicks 个 tick 后再放这块覆盖板。 */
    public MachineUiPlacement cover(Direction side, ResourceLocation coverId, int delayTicks) {
        edits.add(new CoverChange(side, coverId, delayTicks));
        return this;
    }

    /** 同上，直接给 {@link CoverDefinition}。 */
    public MachineUiPlacement cover(Direction side, CoverDefinition cover) {
        return cover(side, cover, 0);
    }

    public MachineUiPlacement cover(Direction side, CoverDefinition cover, int delayTicks) {
        return cover(side, cover.getId(), delayTicks);
    }

    /** 第 index 个机器槽位，顺序与 UI 里槽位的排列一致，也就是实机 UI 里的真实槽位序号。 */
    public SlotTarget slot(int index) {
        return new SlotTarget(index);
    }

    /** 按给定 tick 数展示面板；此前登记的槽位写入按各自延迟执行。 */
    public void show(int ticks) {
        MachineUiElement element = new MachineUiElement(ui, anchor, pointing, machinePos, List.copyOf(writes),
                List.copyOf(edits));
        builder.addInstruction(new ShowMachineUiInstruction(element, ticks));
    }

    public final class SlotTarget {

        private final int index;

        private SlotTarget(int index) {
            this.index = index;
        }

        /** 面板出现的同一 tick 就把物品写进该槽位。 */
        public MachineUiPlacement withItem(ItemStack stack) {
            return withItem(stack, 0);
        }

        /** 面板出现 delayTicks 个 tick 后开始放入：数量从 0 在 1 秒内叠到该数量，直接写进槽位。 */
        public MachineUiPlacement withItem(ItemStack stack, int delayTicks) {
            writes.add(new SlotWrite(index, stack.copy(), Math.max(0, delayTicks)));
            return MachineUiPlacement.this;
        }
    }

    record SlotWrite(int index, ItemStack stack, int delayTicks) {}
}
