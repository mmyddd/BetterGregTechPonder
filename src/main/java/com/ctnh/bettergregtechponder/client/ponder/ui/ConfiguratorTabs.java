// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 在 GT 的配置器面板里认页签：电源开关、自动输出、电路、总线隔离都是这一列里的按钮。
 *
 * <p>页签的 {@code configurator} 字段是 protected，只能反射读一次并缓存；认哪一类看它的 tooltip
 * （GT 自己的 lang key：{@code behaviour.soft_hammer.*}、{@code gtceu.gui.*_auto_output.*}、
 * {@code gtceu.multiblock.universal.distinct}），电路那一类直接看类型。
 */
final class ConfiguratorTabs {

    /** 电源开关的 tooltip key 片段。 */
    private static final String POWER = "soft_hammer";
    /** 物品/流体自动输出的 tooltip key 片段。 */
    private static final String AUTO_OUTPUT = "auto_output";
    /** 总线隔离（Distinct）的 tooltip key 片段。 */
    private static final String DISTINCT = "distinct";

    private static Field configuratorField;
    private static boolean fieldMissing;

    private ConfiguratorTabs() {}

    /**
     * 面板里对得上这一类的页签控件（就是那一列按钮）。面板没画、机器没这类控件、或者拿不到
     * 反射字段时返回空表，交给调用方报错。
     */
    static List<Widget> buttons(@Nullable ConfiguratorPanel panel, MachineUiPlacement.Part part) {
        if (panel == null || !panel.isVisible()) {
            return List.of();
        }
        List<Widget> buttons = new ArrayList<>();
        for (Object tab : panel.getTabs()) {
            if (!(tab instanceof Widget widget)) {
                continue;
            }
            IFancyConfigurator configurator = configuratorOf(tab);
            if (configurator != null && matches(configurator, part)) {
                buttons.add(widget);
            }
        }
        return buttons;
    }

    /** 配置器面板里的电路页签；没有（机器没电路槽、面板没画、或反射拿不到）就是 null。 */
    static @Nullable ConfiguratorPanel.Tab circuitTab(@Nullable ConfiguratorPanel panel) {
        if (panel == null) {
            return null;
        }
        for (ConfiguratorPanel.Tab tab : panel.getTabs()) {
            if (configuratorOf(tab) instanceof CircuitFancyConfigurator) {
                return tab;
            }
        }
        return null;
    }

    private static boolean matches(IFancyConfigurator configurator, MachineUiPlacement.Part part) {
        return switch (part) {
            case CIRCUIT_BUTTON -> configurator instanceof CircuitFancyConfigurator;
            case POWER -> tooltipContains(configurator, POWER);
            case AUTO_OUTPUT -> tooltipContains(configurator, AUTO_OUTPUT);
            case DISTINCT -> tooltipContains(configurator, DISTINCT);
            default -> false;
        };
    }

    private static boolean tooltipContains(IFancyConfigurator configurator, String needle) {
        for (Component tooltip : configurator.getTooltips()) {
            if (tooltip.getContents() instanceof TranslatableContents contents &&
                    contents.getKey().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable IFancyConfigurator configuratorOf(Object tab) {
        Field field = configuratorField();
        if (field == null) {
            return null;
        }
        try {
            return (IFancyConfigurator) field.get(tab);
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable Field configuratorField() {
        if (configuratorField == null && !fieldMissing) {
            try {
                Field field = Class.forName("com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel$Tab")
                        .getDeclaredField("configurator");
                field.setAccessible(true);
                configuratorField = field;
            } catch (Throwable t) {
                fieldMissing = true;
                BetterGregTechPonder.LOGGER.error("GTPonder: cannot read the configurator of a GT configurator tab; " +
                        "outlining the power / auto-output / circuit / distinct buttons will be skipped", t);
            }
        }
        return configuratorField;
    }
}
