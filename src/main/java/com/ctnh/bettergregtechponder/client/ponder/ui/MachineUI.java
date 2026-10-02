// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.world.level.block.Block;

/**
 * 一个可复用的机器界面描述对象：定义一次，在任意思索场景里重复摆放。
 *
 * <p>
 * 默认只画标题栏、左侧页签和机器页；玩家背包、配置器面板、编程电路 UI，以及标题栏上的返回与翻页按钮都不画。
 * 想原样画出 GT 那一整套，用 {@link #showFullUI()}。
 *
 * <p>
 * 定义侧：
 *
 * <pre>{@code
 * private static final MachineUI LV_INPUT_BUS_UI = MachineUI.of(GTMachines.ITEM_IMPORT_BUS[GTValues.LV])
 *         .scale(0.6f);
 * }</pre>
 *
 * <p>
 * 使用侧：
 *
 * <pre>{@code
 * MachineUIs.showUI(scene, LV_INPUT_BUS_UI).at(busPos)
 *         .slot(0).withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
 *         .show(200);
 * }</pre>
 *
 * <p>
 * 本对象只描述画什么，不持有机器实例：机器在场景运行时按坐标解析，同一个常量可以跨场景、跨重播复用。
 * 面板是 Ponder 的 speech box，指针尖对准 {@link MachineUiPlacement#at(net.minecraft.world.phys.Vec3)} 传入的坐标，
 * 朝向由 {@link MachineUiPlacement#pointing(net.createmod.catnip.math.Pointing)} 决定。
 */
public final class MachineUI {

    private final MachineDefinition definition;
    private final boolean titleBar;
    private final boolean sideTabs;
    private final boolean playerInventory;
    private final boolean configurators;
    private final boolean circuit;
    private final boolean navigationButtons;
    /** 原版整套：GT 自己的布局一个组件都不裁剪。 */
    private final boolean full;
    /** 控制器在思索里默认按真实结果显示（校验通过才成型）；打开这个开关后，校验没通过也强制成型。 */
    private boolean forceMultiblockActivated;
    private final float scale;
    private final float fitFraction;

    private MachineUI(MachineDefinition definition, boolean titleBar, boolean sideTabs, boolean playerInventory,
                      boolean configurators, boolean circuit, boolean navigationButtons, boolean full, float scale,
                      float fitFraction) {
        this.definition = definition;
        this.titleBar = titleBar;
        this.sideTabs = sideTabs;
        this.playerInventory = playerInventory;
        this.configurators = configurators;
        this.circuit = circuit;
        this.navigationButtons = navigationButtons;
        this.full = full;
        this.scale = scale;
        this.fitFraction = fitFraction;
    }

    /** 以 GT 机器定义创建界面描述，默认画「标题栏 + 页签 + 机器页」。 */
    public static MachineUI of(MachineDefinition definition) {
        return new MachineUI(definition, true, true, false, false, false, false, false, 1.0f, 0.0f);
    }

    /** 兜底开关：控制器自身的结构校验没通过时，也让它在思索里直接成型。不写这一句就照实显示「结构无效」。 */
    public MachineUI forceMultiblockActivated() {
        this.forceMultiblockActivated = true;
        return this;
    }

    /** 见 {@link #forceMultiblockActivated()}：校验没过时是否也强制成型。 */
    public boolean isForceMultiblockActivated() {
        return forceMultiblockActivated;
    }

    /** 以机器方块创建界面描述。 */
    public static MachineUI of(Block block) {
        if (block instanceof IMachineBlock machineBlock) {
            return of(machineBlock.getDefinition());
        }
        throw new IllegalArgumentException("Not a GT machine block: " + block);
    }

    private MachineUI copy(boolean titleBar, boolean sideTabs, boolean playerInventory, boolean configurators,
                           boolean circuit, boolean navigationButtons, boolean full, float scale, float fitFraction) {
        return new MachineUI(definition, titleBar, sideTabs, playerInventory, configurators, circuit, navigationButtons,
                full, scale, fitFraction);
    }

    /**
     * 原版 GT 的那一整套都画上：标题栏、左侧页签、机器页、配置器面板（工作开关、电路、覆盖板那些图标）、
     * 提示面板与玩家背包，一个组件都不裁剪，位置也照 GT 自己的布局。
     *
     * <p>这套里已经有 GT 自己的电路按钮，所以不用再写 {@link #showCircuit()}；想在这个基础上加也行。
     */
    public MachineUI showFullUI() {
        return copy(true, true, true, true, circuit, true, true, scale, fitFraction);
    }

    /** 不画标题栏。 */
    public MachineUI hideTitleBar() {
        return copy(false, sideTabs, playerInventory, configurators, circuit, navigationButtons, full, scale, fitFraction);
    }

    /** 不画左侧页签。 */
    public MachineUI hideSideTabs() {
        return copy(titleBar, false, playerInventory, configurators, circuit, navigationButtons, full, scale, fitFraction);
    }

    /** 额外画上玩家背包（默认不画）。 */
    public MachineUI showPlayerInventory() {
        return copy(titleBar, sideTabs, true, configurators, circuit, navigationButtons, full, scale, fitFraction);
    }

    /** 额外画上左右配置器面板（覆盖板、工作开关一类的图标）。 */
    public MachineUI showConfigurators() {
        return copy(titleBar, sideTabs, playerInventory, true, circuit, navigationButtons, full, scale, fitFraction);
    }

    /** 额外画上编程电路 UI（默认不画）：展开的设置面板占背包那一行，按钮贴在它左边、垂直居中。 */
    public MachineUI showCircuit() {
        return copy(titleBar, sideTabs, playerInventory, configurators, true, navigationButtons, full, scale, fitFraction);
    }

    /** 额外画上标题栏的返回与翻页按钮（默认不画）。 */
    public MachineUI showNavigationButtons() {
        return copy(titleBar, sideTabs, playerInventory, configurators, circuit, true, full, scale, fitFraction);
    }

    /** 固定缩放，1.0 即 GUI 原始像素。 */
    public MachineUI scale(float scale) {
        return copy(titleBar, sideTabs, playerInventory, configurators, circuit, navigationButtons, full, scale, 0.0f);
    }

    /**
     * 自适应缩放：面板宽度取 Ponder 界面的 {@code fraction}（0~1），不受 GUI Scale 影响。
     * 与 {@link #scale(float)} 二选一，后设的生效。
     */
    public MachineUI fitToPanel(float fraction) {
        return copy(titleBar, sideTabs, playerInventory, configurators, circuit, navigationButtons, full, scale, fraction);
    }

    /** 开始一次摆放。 */
    public MachineUiPlacement in(SceneBuilder builder) {
        return new MachineUiPlacement(builder, this);
    }

    MachineDefinition definition() {
        return definition;
    }

    boolean titleBar() {
        return titleBar;
    }

    boolean sideTabs() {
        return sideTabs;
    }

    boolean playerInventory() {
        return playerInventory;
    }

    boolean configurators() {
        return configurators;
    }

    boolean circuit() {
        return circuit;
    }

    /** 是否原版整套（{@link #showFullUI()}）：是的话什么都不裁剪。 */
    boolean full() {
        return full;
    }

    boolean navigationButtons() {
        return navigationButtons;
    }

    float scale() {
        return scale;
    }

    float fitFraction() {
        return fitFraction;
    }
}
