// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

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
    private final List<FluidWrite> fluids = new ArrayList<>();
    private Vec3 anchor = Vec3.ZERO;
    private Pointing pointing = Pointing.DOWN;
    private BlockPos machinePos;
    @Nullable
    private String recipeId;
    private int recipeDelayTicks;
    private float scale;

    MachineUiPlacement(SceneBuilder builder, MachineUI ui) {
        this.builder = builder;
        this.ui = ui;
    }

    /** 指向点：面板的指针尖（speech box 的小尾巴）对齐这个场景坐标。 */
    public MachineUiPlacement at(Vec3 anchor) {
        this.anchor = anchor;
        return this;
    }

    /**
     * 这次摆放单独指定缩放（1.0 即 GUI 原始像素）；不写就用 {@link MachineUI#scale(float)} /
     * {@link MachineUI#fitToPanel(float)} 定下的那套。
     */
    public MachineUiPlacement scale(float scale) {
        this.scale = scale;
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

    /** 第 index 个机器槽位，顺序与 UI 里槽位的排列一致，也就是游戏里这台机器的真实槽位序号。 */
    public SlotTarget slot(int index) {
        return new SlotTarget(index);
    }

    /** 第 index 个机器储罐，顺序与 UI 里 tank 控件的排列一致。 */
    public TankTarget tank(int index) {
        return new TankTarget(index);
    }

    /**
     * 按配方 id 自动填这台机器：输入进输入槽、流体进输入储罐、成品落进输出槽与输出储罐，
     * 面板里的进度条跟着走一遍。
     *
     * <p>槽位和储罐哪个是输入、哪个是输出，看 GT 自己打的 {@code IngredientIO} 标签，不用猜顺序。
     * 机器与配方对不上（不是配方机器、配方 id 不存在、配方类型不属于这台机器、面板里没有对应槽位）时
     * 在日志里报一行 error，这一段跳过，面板照常画。
     *
     * <p>时间线：入料 1 秒 → 进度条 1 秒 → 成品 1 秒，{@code show(...)} 的时长要留够。
     */
    public MachineUiPlacement recipe(String recipeId) {
        return recipe(recipeId, 0);
    }

    /** delayTicks 个 tick 后开始入料。 */
    public MachineUiPlacement recipe(String recipeId, int delayTicks) {
        this.recipeId = recipeId;
        this.recipeDelayTicks = Math.max(0, delayTicks);
        return this;
    }

    /** 按给定 tick 数展示面板；此前登记的槽位写入按各自延迟执行。 */
    public void show(int ticks) {
        MachineUiElement element = new MachineUiElement(ui, anchor, pointing, machinePos, List.copyOf(writes),
                List.copyOf(fluids), recipeId == null ? null : new RecipeFill(recipeId, recipeDelayTicks), scale);
        builder.addInstruction(new ShowMachineUiInstruction(element, ticks));
    }

    public final class TankTarget {

        private final int index;

        private TankTarget(int index) {
            this.index = index;
        }

        /** 面板出现的同一 tick 就把流体灌进该储罐。 */
        public MachineUiPlacement withFluid(FluidStack stack) {
            return withFluid(stack, 0);
        }

        /** 面板出现 delayTicks 个 tick 后开始灌注：数量从 0 在 1 秒内涨到目标值。 */
        public MachineUiPlacement withFluid(FluidStack stack, int delayTicks) {
            fluids.add(new FluidWrite(index, stack.copy(), Math.max(0, delayTicks)));
            return MachineUiPlacement.this;
        }
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

    /** 一次配方填充：配方 id 与开始入料的延迟。 */
    record RecipeFill(String recipeId, int delayTicks) {}

    record SlotWrite(int index, ItemStack stack, int delayTicks) {}

    record FluidWrite(int index, FluidStack stack, int delayTicks) {}
}
