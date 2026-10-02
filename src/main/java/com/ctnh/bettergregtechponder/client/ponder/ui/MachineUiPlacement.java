// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

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
 * {@link MachineUI} 的一次摆放：指向点、指针方向、缩放、槽位写入与红框计划，由 {@code MachineUIs#showUI} 创建。
 *
 * <p>机器默认按指向点所在方块解析；{@link #at(BlockPos)} 直接用该方块的中心当指向点，
 * 需要「尾巴指向这里、面板画那台机器」时用 {@link #at(Vec3, BlockPos)}。
 *
 * <pre>{@code
 * MachineUIs.showUI(scene, LV_CHEMICAL_REACTOR_UI).at(machinePos)
 *         .slot(1).withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
 *         .outlineSlot(1)
 *         .show(160);
 * }</pre>
 */
public final class MachineUiPlacement {

    private final SceneBuilder builder;
    private final MachineUI ui;
    private final List<SlotWrite> writes = new ArrayList<>();
    private final List<FluidWrite> fluids = new ArrayList<>();
    private final List<Outline> outlines = new ArrayList<>();
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

    /**
     * 框住某一类控件。这是唯一需要对外扩展的入口：新增一类控件只要在 {@link Part} 里加一个值，
     * 再在面板里把它收集起来，这里的公共 API 一行都不用动。
     *
     * <p>
     * {@code index} 只对「有顺序」的类别有意义（槽位、储罐、机器页按钮按收集顺序编号）；
     * 语义类（编程电路 UI、配置器面板里的那几个开关）忽略它，传 0 即可。
     */
    public MachineUiPlacement outline(Part part, int index, int delayTicks) {
        outlines.add(new Outline(part, index, Math.max(0, delayTicks)));
        return this;
    }

    public MachineUiPlacement outline(Part part, int index) {
        return outline(part, index, 0);
    }

    /** 给第 index 个机器槽位套一个红框，面板一出现就亮，一直亮到面板收起。 */
    public MachineUiPlacement outlineSlot(int index) {
        return outlineSlot(index, 0);
    }

    /** delayTicks 个 tick 后亮起。 */
    public MachineUiPlacement outlineSlot(int index, int delayTicks) {
        return outline(Part.SLOT, index, delayTicks);
    }

    /** 给第 index 个机器储罐套一个红框。 */
    public MachineUiPlacement outlineTank(int index) {
        return outlineTank(index, 0);
    }

    public MachineUiPlacement outlineTank(int index, int delayTicks) {
        return outline(Part.TANK, index, delayTicks);
    }

    /** 框住面板里的进度条。 */
    public MachineUiPlacement outlineProgress() {
        return outlineProgress(0);
    }

    public MachineUiPlacement outlineProgress(int delayTicks) {
        return outline(Part.PROGRESS, 0, delayTicks);
    }

    /** 框住编程电路 UI（按钮与展开的设置面板一起框）。 */
    public MachineUiPlacement outlineCircuit() {
        return outlineCircuit(0);
    }

    public MachineUiPlacement outlineCircuit(int delayTicks) {
        return outline(Part.CIRCUIT, 0, delayTicks);
    }

    /**
     * 框住 GT 配置器面板里的工作开关；机器没有这个开关就报错。这几个开关都在那一列配置器面板上，
     * 所以通常配合 {@link MachineUI#showFullUI()} 用。
     */
    public MachineUiPlacement outlinePowerToggle() {
        return outlinePowerToggle(0);
    }

    public MachineUiPlacement outlinePowerToggle(int delayTicks) {
        return outline(Part.POWER, 0, delayTicks);
    }

    /** 框住物品 / 流体自动输出开关（有几个框几个）。 */
    public MachineUiPlacement outlineAutoOutput() {
        return outlineAutoOutput(0);
    }

    public MachineUiPlacement outlineAutoOutput(int delayTicks) {
        return outline(Part.AUTO_OUTPUT, 0, delayTicks);
    }

    /** 框住 GT 配置器面板里的电路设置按钮；要框本库画的那组电路 UI，用 {@link #outlineCircuit()}。 */
    public MachineUiPlacement outlineCircuitButton() {
        return outlineCircuitButton(0);
    }

    public MachineUiPlacement outlineCircuitButton(int delayTicks) {
        return outline(Part.CIRCUIT_BUTTON, 0, delayTicks);
    }

    /** 框住总线隔离（Distinct）开关。 */
    public MachineUiPlacement outlineDistinct() {
        return outlineDistinct(0);
    }

    public MachineUiPlacement outlineDistinct(int delayTicks) {
        return outline(Part.DISTINCT, 0, delayTicks);
    }

    /**
     * 框住机器页里的第 {@code index} 个按钮或开关，序号按 {@code createUIWidget()} 的添加顺序。
     * 标题栏、页签、配置器那一列都是 chrome，不算在内。
     */
    public MachineUiPlacement outlineButton(int index) {
        return outlineButton(index, 0);
    }

    public MachineUiPlacement outlineButton(int index, int delayTicks) {
        return outline(Part.BUTTON, index, delayTicks);
    }

    /** 按给定 tick 数展示面板；此前登记的写入与红框按各自延迟执行。 */
    public void show(int ticks) {
        MachineUiElement element = new MachineUiElement(new Plan(ui, anchor, pointing, machinePos, scale,
                List.copyOf(writes), List.copyOf(fluids), recipeId == null ? null : new RecipeFill(recipeId,
                        recipeDelayTicks),
                List.copyOf(outlines)));
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

    /** 一次摆放的完整快照，交给 {@link MachineUiElement} 跑。 */
    record Plan(MachineUI ui, Vec3 anchor, Pointing pointing, BlockPos machinePos, float scale,
                List<SlotWrite> slots, List<FluidWrite> fluids, @Nullable RecipeFill recipe,
                List<Outline> outlines) {}

    /** 一次红框请求：框哪一类控件、第几个、延迟多少 tick 亮起。 */
    record Outline(Part part, int index, int delayTicks) {}

    /**
     * 面板里可以被红框框住的控件。后四种是 GT 配置器面板那一列里的按钮，没画那列面板就框不到。
     *
     * <p>
     * 公开是刻意的：它是红框的唯一扩展点，加一类控件就加一个值，配合 {@link #outline(Part, int, int)}
     * 使用。带索引的值（SLOT / TANK / BUTTON）身份由收集顺序决定，改机器 UI 的添加顺序会让编号平移。
     */
    public enum Part {
        SLOT,
        TANK,
        PROGRESS,
        /** 本库自己画的编程电路 UI（按钮 + 展开面板）。 */
        CIRCUIT,
        /** 机器页（{@code createUIWidget()} 那块）里的按钮与开关，按添加顺序编号。 */
        BUTTON,
        /** GT 配置器面板里的工作开关。 */
        POWER,
        /** GT 配置器面板里的物品 / 流体自动输出开关，有几个框几个。 */
        AUTO_OUTPUT,
        /** GT 配置器面板里的电路设置按钮。 */
        CIRCUIT_BUTTON,
        /** GT 配置器面板里的总线隔离（Distinct）开关。 */
        DISTINCT
    }

    /** 一次配方填充：配方 id 与开始入料的延迟。 */
    record RecipeFill(String recipeId, int delayTicks) {}

    record SlotWrite(int index, ItemStack stack, int delayTicks) {}

    record FluidWrite(int index, FluidStack stack, int delayTicks) {}
}
