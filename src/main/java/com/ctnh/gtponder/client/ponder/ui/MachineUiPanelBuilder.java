// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import com.ctnh.gtponder.GTPonder;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TitleBarWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 按坐标把机器自己的 {@link ModularUI} 建出来，并整理成 {@link MachineUiPanel}：白名单留下该画的
 * fancy 组件，量出面板边界，按控件顺序收好机器槽位、储罐与进度条。
 */
final class MachineUiPanelBuilder {

    private MachineUiPanelBuilder() {}

    /** 造面板；这里不是机器、机器没有 UI、玩家不在（比如主菜单）时返回 null，元素这一段就不画。 */
    static @Nullable MachineUiPanel build(MachineUI ui, BlockPos machinePos, BlockEntity blockEntity) {
        if (!(blockEntity instanceof IMachineBlockEntity holder)) {
            return null;
        }
        MetaMachine machine = holder.getMetaMachine();
        if (!(machine instanceof IUIMachine uiMachine)) {
            return null;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        if (ui.definition() != null && machine.getDefinition() != ui.definition()) {
            GTPonder.LOGGER.warn("MachineUI was defined for {} but {} sits at {}", ui.definition(),
                    machine.getDefinition(), machinePos);
        }

        ModularUI modularUi = uiMachine.createUI(player);
        if (modularUi == null) {
            return null;
        }
        modularUi.initWidgets();

        Widget root = pickRoot(modularUi);
        if (root instanceof FancyMachineUIWidget fancy) {
            applyFancyChrome(fancy, ui);
        }
        List<SlotWidget> slots = collectMachineSlots(modularUi);
        List<Widget> tanks = collectMachineTanks(modularUi);
        List<ProgressWidget> progress = collectProgressWidgets(modularUi);
        // 储罐控件画的是自己的 lastFluidInTank 缓存，缓存只在 client-side 模式下每帧从真实储罐刷新
        // （TankWidget#drawInBackground 里那个 if）；ponder 里没有 ModularUIGuiContainer，
        // 不打开这个开关，储罐永远画成空的、tooltip 也一直是「空 / 0/0 mB」。
        tanks.forEach(Widget::setClientSideWidget);
        // 进度条同理：ProgressWidget#drawInBackground 只在 client-side 模式下每帧问一次 supplier，
        // 否则画的是初始化那一刻的 lastProgressValue，永远是 0。
        progress.forEach(Widget::setClientSideWidget);
        Bounds bounds = measure(root);
        GTPonder.LOGGER.debug("MachineUI at {}: panel {}x{} at ({}, {}), {} machine slot(s), {} tank(s)", machinePos,
                bounds.width(), bounds.height(), bounds.x(), bounds.y(), slots.size(), tanks.size());
        return new MachineUiPanel(blockEntity, modularUi, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                machine, slots, tanks, progress);
    }

    /**
     * 按 MachineUI 的开关决定哪些 fancy 组件可见：玩家背包、配置器面板、提示面板等不在白名单里的一律隐藏，
     * 标题栏上的返回与翻页按钮也一并关掉。
     */
    private static void applyFancyChrome(FancyMachineUIWidget fancy, MachineUI ui) {
        PlayerInventoryWidget inventory = fancy.getPlayerInventory();
        if (inventory != null) {
            if (ui.playerInventory()) {
                inventory.setVisible(true);
            } else if (inventory.isVisible()) {
                inventory.setVisible(false);
                fancy.setSize(fancy.getSizeWidth(), Math.max(0, fancy.getSizeHeight() - inventory.getSizeHeight()));
            }
        }

        TitleBarWidget titleBar = fancy.getTitleBar();
        if (fancy.getCurrentPage() != null) {
            titleBar.updateState(fancy.getCurrentPage(), ui.navigationButtons(), ui.navigationButtons());
        }

        for (Widget child : fancy.widgets) {
            if (child == titleBar) {
                child.setVisible(ui.titleBar());
            } else if (child == fancy.getSideTabsWidget()) {
                child.setVisible(ui.sideTabs());
            } else if (child == fancy.getPageContainer()) {
                child.setVisible(true);
            } else if (ui.playerInventory() && child == inventory) {
                child.setVisible(true);
            } else if (ui.configurators() && child instanceof ConfiguratorPanel) {
                child.setVisible(true);
            } else {
                child.setVisible(false);
            }
        }
    }

    /**
     * 面板边界取根容器与当前可见子控件的并集。fancy UI 的标题栏在根容器上方、页签在它左侧，
     * 只算根容器矩形，这两块就会落在 speech box 外面。
     */
    private static Bounds measure(Widget root) {
        int minX = root.getPositionX();
        int minY = root.getPositionY();
        int maxX = minX + root.getSizeWidth();
        int maxY = minY + root.getSizeHeight();
        if (root instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                if (!child.isVisible()) {
                    continue;
                }
                minX = Math.min(minX, child.getPositionX());
                minY = Math.min(minY, child.getPositionY());
                maxX = Math.max(maxX, child.getPositionX() + child.getSizeWidth());
                maxY = Math.max(maxY, child.getPositionY() + child.getSizeHeight());
            }
        }
        return new Bounds(minX, minY, maxX - minX, maxY - minY);
    }

    private static Widget pickRoot(ModularUI modularUi) {
        if (modularUi.mainGroup.widgets.size() == 1 &&
                modularUi.mainGroup.widgets.get(0) instanceof FancyMachineUIWidget fancy) {
            return fancy;
        }
        return modularUi.mainGroup;
    }

    private static List<Widget> collectMachineTanks(ModularUI modularUi) {
        List<Widget> tanks = new ArrayList<>();
        for (Widget widget : modularUi.mainGroup.getContainedWidgets(true)) {
            if (MachineUiPanel.isTank(widget) && widget.isVisible()) {
                tanks.add(widget);
            }
        }
        return tanks;
    }

    private static List<ProgressWidget> collectProgressWidgets(ModularUI modularUi) {
        List<ProgressWidget> progress = new ArrayList<>();
        for (Widget widget : modularUi.mainGroup.getContainedWidgets(true)) {
            if (widget instanceof ProgressWidget bar && widget.isVisible()) {
                progress.add(bar);
            }
        }
        return progress;
    }

    private static List<SlotWidget> collectMachineSlots(ModularUI modularUi) {
        List<SlotWidget> slots = new ArrayList<>();
        for (Widget widget : modularUi.mainGroup.getContainedWidgets(true)) {
            if (widget instanceof SlotWidget slot && !slot.isPlayerContainer && slot.isVisible()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private record Bounds(int x, int y, int width, int height) {}
}
