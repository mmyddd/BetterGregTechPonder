// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「查看 UI 详情」打开之后，面板上只有两类控件能点：左下与左侧的页签（机器主界面、更改方向设置这些页面切换），
 * 以及配置器面板里的电路页签。别的控件一律不转发事件。
 *
 * <p>面板每帧渲染时把落点登记进来（{@link #publish}），点击时按屏幕坐标反算回 UI 坐标再命中判定，
 * 所以不需要给 Ponder 或 LDLib 挂任何输入钩子。
 */
public final class MachineUiInteraction {

    /** 这一帧画出来的面板：屏幕落点、缩放，以及面板原点（用来把屏幕坐标换算成 UI 坐标）。 */
    public record View(MachineUiElement owner, MachineUiPanel panel, float contentX, float contentY, float scale,
                       int originX, int originY, boolean full) {

        float uiX(double screenX) {
            return originX + (float) ((screenX - contentX) / scale);
        }

        float uiY(double screenY) {
            return originY + (float) ((screenY - contentY) / scale);
        }
    }

    private static final List<View> FRAME = new ArrayList<>();
    /** 开启详情时记下的开关状态，关闭时还原。 */
    private static final List<SwitchState> SWITCHES = new ArrayList<>();

    /** 换了页面就得重建面板快照的那个元素。 */
    @Nullable
    private static MachineUiElement dirtyOwner;
    private static boolean enabled;
    @Nullable
    private static Field pressCallback;
    private static boolean pressCallbackMissing;
    @Nullable
    private static Constructor<ClickData> clickConstructor;

    private MachineUiInteraction() {}

    /** 「查看 UI 详情」是否打开。 */
    public static boolean enabled() {
        return enabled;
    }

    /**
     * 开关模式：开启时记下这几个开关当前的状态，关闭时原样还回去。
     *
     * <p>观众在冻结期间拨动电源、自动输出或电路，改的是机器本身；不还回去的话，
     * 恢复播放之后场景自己的状态就被这层改动顶掉了，看起来就是「回不到正常状态」。
     */
    public static void setEnabled(boolean value) {
        if (value == enabled) {
            return;
        }
        enabled = value;
        if (value) {
            snapshotSwitches();
        } else {
            restoreSwitches();
        }
    }

    /** 这一帧有没有画过机器 UI 面板，按钮按它决定要不要出现。 */
    public static boolean hasPanel() {
        return !FRAME.isEmpty();
    }

    /** 这一帧画的是不是原版整套 UI（{@code showFullUI()}）；只有这种才允许点。 */
    public static boolean hasFullPanel() {
        for (View view : FRAME) {
            if (view.full()) {
                return true;
            }
        }
        return false;
    }

    /** 开关值的简写，用于日志对照点击前后。 */
    private static String describeSwitches(MetaMachine machine) {
        return "[work=" + (machine instanceof IControllable controllable ? controllable.isWorkingEnabled() : "-") +
                " item=" + (machine instanceof IAutoOutputItem items ? items.isAutoOutputItems() : "-") +
                " fluid=" + (machine instanceof IAutoOutputFluid fluids ? fluids.isAutoOutputFluids() : "-") +
                " circuit=" + (machine instanceof IHasCircuitSlot slot ?
                        slot.getCircuitInventory().storage.getStackInSlot(0).getCount() : "-") +
                "]";
    }

    /** 记下电源、自动输出、电路这几个开关当前的值。 */
    private static void snapshotSwitches() {
        SWITCHES.clear();
        for (View view : FRAME) {
            MetaMachine machine = view.panel().machine();
            SWITCHES.add(new SwitchState(machine,
                    machine instanceof IControllable controllable ? controllable.isWorkingEnabled() : null,
                    machine instanceof IAutoOutputItem items ? items.isAutoOutputItems() : null,
                    machine instanceof IAutoOutputFluid fluids ? fluids.isAutoOutputFluids() : null,
                    machine instanceof IHasCircuitSlot slot ?
                            slot.getCircuitInventory().storage.getStackInSlot(0).copy() : null));
        }
    }

    /** 把开关还回开启前的样子，然后清掉备份。 */
    private static void restoreSwitches() {
        for (SwitchState state : SWITCHES) {
            if (state.working() != null && state.machine() instanceof IControllable controllable) {
                controllable.setWorkingEnabled(state.working());
            }
            if (state.autoItems() != null && state.machine() instanceof IAutoOutputItem items) {
                items.setAutoOutputItems(state.autoItems());
            }
            if (state.autoFluids() != null && state.machine() instanceof IAutoOutputFluid fluids) {
                fluids.setAutoOutputFluids(state.autoFluids());
            }
            if (state.circuit() != null && state.machine() instanceof IHasCircuitSlot slot) {
                slot.getCircuitInventory().storage.setStackInSlot(0, state.circuit());
            }
        }
        SWITCHES.clear();
    }

    /** 一台机器上会被观众拨动的开关：电源、自动输出（物品/流体）、电路。 */
    private record SwitchState(MetaMachine machine, @Nullable Boolean working, @Nullable Boolean autoItems,
                               @Nullable Boolean autoFluids, @Nullable ItemStack circuit) {}

    /** 每帧渲染前清一次，面板自己在渲染时把落点登记进来。 */
    public static void beginFrame() {
        FRAME.clear();
    }

    /** 面板渲染时登记本帧落点。 */
    public static void publish(MachineUiElement owner, MachineUiPanel panel, float contentX, float contentY,
                               float scale, boolean full) {
        FRAME.add(new View(owner, panel, contentX, contentY, scale, panel.originX(), panel.originY(), full));
    }

    /**
     * 冻结场景时由 mixin 每个 tick 调：场景不推进，但面板自己的 UI 还得继续更新。
     *
     * <p>LDLib 的控件靠 updateScreen() 跑动画与布局（页签切换就是要它把新页面淡进来），
     * 而它平时是被场景元素的 tick 驱动的 —— 场景一冻就全停了，所以这里补上；
     * 顺手处理换页后的面板重建（元素 tick 同样被冻住，不能指望它）。
     */
    public static void tickPanels() {
        for (View view : FRAME) {
            view.panel()
                    .modularUi().mainGroup.updateScreen();
            // 没有容器帮忙同步，开关的缓存得我们自己刷：不刷的话点击算出的新状态永远是旧的。
            ConfiguratorTabs.syncConfigurators(view.panel().configurators());
        }
        MachineUiElement owner = dirtyOwner;
        if (owner != null) {
            dirtyOwner = null;
            owner.invalidate();
        }
    }

    /**
     * 屏幕坐标的一次点击：落在允许交互的控件上就处理掉并返回 true。
     *
     * <p>只有当前画着原版整套 UI 时才有交互可言：裁剪版或者没有面板时一律不处理。
     */
    public static boolean click(double mouseX, double mouseY, int button) {
        if (!enabled || !hasFullPanel()) {
            return false;
        }
        for (View view : FRAME) {
            if (!view.full()) {
                continue;
            }
            MachineUiPanel panel = view.panel();
            float uiX = view.uiX(mouseX);
            float uiY = view.uiY(mouseY);
            Widget tabs = panel.tabs();
            boolean handled = tabs != null && tabs.mouseClicked(uiX, uiY, button);
            BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: panel click at ui=({}, {}), tabs={} visible={} " +
                    "handled={}", Math.round(uiX), Math.round(uiY), tabs != null,
                    tabs != null && tabs.isVisible(), handled);
            if (handled) {
                // 页签换了页面：内容、边界、槽位表都变了，让面板重建一次。
                dirtyOwner = view.owner();
                return true;
            }
            // 左下那一列配置器整个交给 GT：
            // ConfiguratorPanel#mouseClicked 会先把点击转给展开中的页签（电路格子那些就能点），
            // 否则分发给各页签；页签自己区分「开关按钮」与「展开/收起」。全是原版行为。
            ConfiguratorPanel configurators = panel.configurators();
            if (configurators != null) {
                String before = describeSwitches(panel.machine());
                // 1) 直接点页签里那个按钮：开关、展开/收起都是 GT 挂在这个按钮上的 onClick。
                for (ConfiguratorPanel.Tab tab : configurators.getTabs()) {
                    if (!tab.isVisible() || !inside(tab, uiX, uiY)) {
                        continue;
                    }
                    Widget tabButton = ConfiguratorTabs.buttonOf(tab);
                    boolean fired = tabButton != null && tabButton.mouseClicked(uiX, uiY, button);
                    ConfiguratorTabs.syncConfigurators(configurators);
                    BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: tab button ui=({}, {}) fired={} " +
                            "before={} after={}", Math.round(uiX), Math.round(uiY), fired, before,
                            describeSwitches(panel.machine()));
                    if (fired) {
                        return true;
                    }
                }
                // 2) 展开出来的配置器内容（电路格子那些）：找到点中的按钮，用「非远程」的 ClickData 调它的回调。
                //    GT 的电路格子写的是 if (!clickData.isRemote) 才改本地槽位，而 LDLib 客户端构造的
                //    ClickData 恒为 isRemote = true（正常靠同步包回传），ponder 里没有服务端，于是点了没反应。
                ButtonWidget hit = findButton(configurators, uiX, uiY);
                if (hit != null && press(hit, button) || configurators.mouseClicked(uiX, uiY, button)) {
                    BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: configurator body ui=({}, {}) button={}",
                            Math.round(uiX), Math.round(uiY), hit != null);
                    return true;
                }
            }
        }
        return false;
    }

    /** 在这棵控件树里找点中的那个按钮（取最深的，展开出来的配置器内容就在里面）。 */
    @Nullable
    private static ButtonWidget findButton(WidgetGroup root, float uiX, float uiY) {
        ButtonWidget found = null;
        for (Widget widget : root.getContainedWidgets(true)) {
            if (widget instanceof ButtonWidget buttonWidget && widget.isVisible() && widget.isActive() &&
                    widget.isMouseOverElement(uiX, uiY)) {
                found = buttonWidget;
            }
        }
        return found;
    }

    /**
     * 按一次按钮：直接调它的 onPressCallback，传入 isRemote = false 的 ClickData。
     *
     * <p>走 mouseClicked 的话 LDLib 会自己造一个 isRemote = true 的 ClickData，
     * GT 那些「只在非远程时改本地状态」的回调就全被跳过了。
     */
    private static boolean press(ButtonWidget widget, int mouseButton) {
        Field field = pressCallbackField();
        if (field == null) {
            return false;
        }
        try {
            Object callback = field.get(widget);
            if (!(callback instanceof Consumer<?> consumer)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Consumer<ClickData> press = (Consumer<ClickData>) consumer;
            press.accept(localClick(mouseButton));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 造一个 isRemote = false 的 ClickData（客户端用的那个构造器硬编码 true）。 */
    private static ClickData localClick(int mouseButton) throws Exception {
        if (clickConstructor == null) {
            clickConstructor = ClickData.class.getDeclaredConstructor(int.class, boolean.class, boolean.class,
                    boolean.class);
            clickConstructor.setAccessible(true);
        }
        return clickConstructor.newInstance(mouseButton, false, false, false);
    }

    @Nullable
    private static Field pressCallbackField() {
        if (pressCallback == null && !pressCallbackMissing) {
            try {
                Field field = ButtonWidget.class.getDeclaredField("onPressCallback");
                field.setAccessible(true);
                pressCallback = field;
            } catch (Throwable t) {
                pressCallbackMissing = true;
                BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot read the press callback of a " +
                        "button; configurator contents will not be clickable", t);
            }
        }
        return pressCallback;

    }

    /** 点是否落在某个控件的矩形里（面板 UI 坐标）。 */
    private static boolean inside(Widget widget, float uiX, float uiY) {
        return uiX >= widget.getPositionX() && uiX < widget.getPositionX() + widget.getSizeWidth() &&
                uiY >= widget.getPositionY() && uiY < widget.getPositionY() + widget.getSizeHeight();
    }
}
