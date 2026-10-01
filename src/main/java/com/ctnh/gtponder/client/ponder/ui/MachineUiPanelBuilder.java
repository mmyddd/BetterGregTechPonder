// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import com.ctnh.gtponder.GTPonder;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TitleBarWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    /** GT 配置器页签的边长，也是它的标题栏高度。 */
    private static final int TAB_SIZE = 24;
    /** GT 配置器面板的留白。 */
    private static final int BORDER = 4;
    /** 电路面板与上方机器内容之间的间距。 */
    private static final int CIRCUIT_GAP = 8;

    private MachineUiPanelBuilder() {}

    /** 造面板；这里不是机器、机器没有 UI、玩家不在（比如主菜单）时返回 null，元素这一段就不画。 */
    static @Nullable MachineUiPanel build(MachineUI ui, BlockPos machinePos, BlockEntity blockEntity,
                                         boolean recipeCircuit) {
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
        FancyMachineUIWidget fancy = root instanceof FancyMachineUIWidget widget ? widget : null;
        Widget inventory = fancy == null ? null : fancy.getPlayerInventory();
        // 场景写了 showCircuit()，或者这段要展示的配方本身要电路，就把电路 UI 一起画出来。
        boolean circuit = (ui.circuit() || recipeCircuit) && hasCircuitSlot(machine);
        if (fancy != null) {
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
        // 槽位收集完再挂电路 UI：它的幽灵槽不算进 slot(index) 里，序号跟实机 UI 保持一致。
        Widget circuitUi = circuit ? attachCircuit(machine, root, inventory) : null;
        Bounds bounds = measure(root);
        GTPonder.LOGGER.debug("MachineUI at {}: panel {}x{} at ({}, {}), {} machine slot(s), {} tank(s)", machinePos,
                bounds.width(), bounds.height(), bounds.x(), bounds.y(), slots.size(), tanks.size());
        return new MachineUiPanel(blockEntity, modularUi, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                machine, slots, tanks, progress, circuitUi);
    }

    /** 机器有没有可用的编程电路槽。 */
    private static boolean hasCircuitSlot(MetaMachine machine) {
        return machine instanceof IHasCircuitSlot holder && holder.isCircuitSlotEnabled();
    }

    /**
     * 挂上编程电路 UI：展开的面板挂在面板下方、与背包那一行水平对齐，按钮贴在它左边、垂直居中。
     *
     * <p>展开的面板自己带 GT 的背景与标题（跟 {@code ConfiguratorPanel} 里那个浮层一样），里面是 GT 自己的
     * {@link CircuitFancyConfigurator}：上面幽灵电路槽、下面 0~32 的格子。按钮图标每帧重取，所以电路换了
     * 按钮与格子里的东西也跟着换；这里只画，不改机器状态。
     *
     * @return 按钮与展开面板合成的那一组（{@code outlineCircuit()} 框的就是它）；没画就返回 null
     */
    private static @Nullable Widget attachCircuit(MetaMachine machine, Widget root, @Nullable Widget inventory) {
        if (!(root instanceof WidgetGroup parent) || !(machine instanceof IHasCircuitSlot holder)) {
            return null;
        }
        Widget content = root instanceof FancyMachineUIWidget fancy ? fancy.getPageContainer() : root;
        // 水平方向对背包那一行居中；竖直方向挂在整块面板下方，与它之间留出 speech box 的底色，
        // 不然两块灰底直接连在一起，看着还是贴着的。
        int rowX = inventory == null ? content.getPositionX() : inventory.getPositionX();
        int rowWidth = inventory == null ? content.getSizeWidth() : inventory.getSizeWidth();
        int rowY = root.getPositionY() + root.getSizeHeight() + CIRCUIT_GAP;

        CircuitFancyConfigurator configurator = new CircuitFancyConfigurator(holder.getCircuitInventory().storage);
        Widget body = configurator.createConfigurator();
        WidgetGroup view = new WidgetGroup(rowX + (rowWidth - body.getSizeWidth() - BORDER * 2) / 2, rowY,
                body.getSizeWidth() + BORDER * 2, body.getSizeHeight() + TAB_SIZE + BORDER);
        view.setBackground(GuiTextures.BACKGROUND);
        body.setSelfPosition(BORDER, TAB_SIZE);
        view.addWidget(body);
        view.addWidget(new ImageWidget(BORDER + 5, BORDER, body.getSizeWidth() - TAB_SIZE - 5, TAB_SIZE - BORDER,
                new TextTexture(configurator.getTitle().getString()).setType(TextTexture.TextType.LEFT_HIDE)
                        .setWidth(body.getSizeWidth() - TAB_SIZE)));
        // 按钮贴在展开面板的左边，跟它垂直居中（GT 实机里那一列也是贴着配置器面板的左边）。
        Widget button = new Widget(view.getPositionX() - TAB_SIZE - 2,
                view.getPositionY() + (view.getSizeHeight() - TAB_SIZE) / 2, TAB_SIZE, TAB_SIZE) {

            @Override
            public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
                GuiTextures.BACKGROUND.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(),
                        getSizeHeight());
                // 与 GT 配置器页签一样，16×16 的图标落在 (width - 20, 4)。
                configurator.getIcon().draw(graphics, mouseX, mouseY, getPositionX() + getSizeWidth() - 20,
                        getPositionY() + 4, 16, 16);
            }
        };

        // 两个控件合成一组：红框（outlineCircuit）框的是整组，位置也随组一起算。
        int minX = Math.min(view.getPositionX(), button.getPositionX());
        int minY = Math.min(view.getPositionY(), button.getPositionY());
        int maxX = Math.max(view.getPositionX() + view.getSizeWidth(),
                button.getPositionX() + button.getSizeWidth());
        int maxY = Math.max(view.getPositionY() + view.getSizeHeight(),
                button.getPositionY() + button.getSizeHeight());
        WidgetGroup group = new WidgetGroup(minX, minY, maxX - minX, maxY - minY);
        view.setSelfPosition(view.getPositionX() - minX, view.getPositionY() - minY);
        button.setSelfPosition(button.getPositionX() - minX, button.getPositionY() - minY);
        group.addWidget(view);
        group.addWidget(button);
        parent.addWidget(group);
        return group;
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
