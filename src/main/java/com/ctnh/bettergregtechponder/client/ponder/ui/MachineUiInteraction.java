// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
                       int originX, int originY) {

        float uiX(double screenX) {
            return originX + (float) ((screenX - contentX) / scale);
        }

        float uiY(double screenY) {
            return originY + (float) ((screenY - contentY) / scale);
        }
    }

    private static final List<View> FRAME = new ArrayList<>();
    /** 展开着的电路页签：它自己的展开状态不在公开 API 上，这里记一份。 */
    @Nullable
    private static ConfiguratorPanel.Tab expandedCircuit;
    /** 换了页面就得重建面板快照的那个元素。 */
    @Nullable
    private static MachineUiElement dirtyOwner;
    private static boolean enabled;

    private MachineUiInteraction() {}

    /** 「查看 UI 详情」是否打开。 */
    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            expandedCircuit = null;
        }
    }

    /** 这一帧有没有画过机器 UI 面板，按钮按它决定要不要出现。 */
    public static boolean hasPanel() {
        return !FRAME.isEmpty();
    }

    /** 每帧渲染前清一次，面板自己在渲染时把落点登记进来。 */
    public static void beginFrame() {
        FRAME.clear();
    }

    /** 面板渲染时登记本帧落点。 */
    public static void publish(MachineUiElement owner, MachineUiPanel panel, float contentX, float contentY,
                               float scale) {
        FRAME.add(new View(owner, panel, contentX, contentY, scale, panel.originX(), panel.originY()));
    }

    /** 页面被换掉之后，面板的边界与槽位表都要重算，让对应元素下一 tick 重建。 */
    @Nullable
    public static MachineUiElement takeDirtyOwner() {
        MachineUiElement owner = dirtyOwner;
        dirtyOwner = null;
        return owner;
    }

    /** 屏幕坐标的一次点击：落在允许交互的控件上就处理掉并返回 true。 */
    public static boolean click(double mouseX, double mouseY, int button) {
        if (!enabled) {
            return false;
        }
        for (View view : FRAME) {
            MachineUiPanel panel = view.panel();
            float uiX = view.uiX(mouseX);
            float uiY = view.uiY(mouseY);
            Widget tabs = panel.tabs();
            if (tabs != null && tabs.mouseClicked(uiX, uiY, button)) {
                // 页签换了页面：内容、边界、槽位表都变了，让面板重建一次。
                dirtyOwner = view.owner();
                expandedCircuit = null;
                return true;
            }
            ConfiguratorPanel configurators = panel.configurators();
            ConfiguratorPanel.Tab circuit = ConfiguratorTabs.circuitTab(configurators);
            if (configurators != null && circuit != null && inside(circuit, uiX, uiY)) {
                // 走 GT 自己的展开/收起：FloatingTab 的动画、标题栏与内容全是原版那套。
                if (expandedCircuit == circuit) {
                    configurators.collapseTab();
                    expandedCircuit = null;
                } else {
                    configurators.expandTab(circuit);
                    expandedCircuit = circuit;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean inside(Widget widget, float uiX, float uiY) {
        return uiX >= widget.getPositionX() && uiX < widget.getPositionX() + widget.getSizeWidth() &&
                uiY >= widget.getPositionY() && uiY < widget.getPositionY() + widget.getSizeHeight();
    }
}
