// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TitleBarWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ctnh.gtponder.GTPonder;
import com.ctnh.gtponder.client.ponder.machine.MachineEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把机器真实的 {@link ModularUI} 画进思索场景的叠加层元素：面板画成 Ponder 的 speech box，
 * 指针尖指向场景里的锚点，并按时间线把物品写进指定槽位。
 *
 * <p>
 * 机器实例在渲染/运行期按坐标解析，不跨重播持有；场景重播（{@code PonderScene#begin()} 会重建
 * BlockEntity）时自动重建界面并重放写入。
 */
public class MachineUiElement extends AnimatedOverlayElementBase {

    /** 叠在场景之上、文本框之下。 */
    private static final float Z = 250f;
    private static final float MIN_FADE = 1 / 16f;
    /** 数量从 0 叠到目标值的固定时长：1 秒。 */
    private static final int FILL_TICKS = 20;
    /** 面板内容与 speech box 边框之间的留白。 */
    private static final int PADDING = 3;
    /** speech box 的指针占位：PonderUI#renderSpeechBox 里的 divotSize(8) + 1 + distance(1)。 */
    private static final int DIVOT_SPAN = 10;
    /** 面板离屏幕边缘至少留出的像素。 */
    private static final int MARGIN = 6;
    /** 编辑模式下贴在槽位 tooltip 首行的序号，参数是机器里的真实槽位序号。 */
    private static final String SLOT_INDEX_KEY = "gtponder.tooltip.slot_index";

    private final MachineUI ui;
    private final Vec3 anchor;
    private final Pointing pointing;
    private final BlockPos machinePos;
    private final List<MachineUiPlacement.SlotWrite> writes;
    private final boolean[] written;
    /** 各写入槽位在首次写入前的内容，回退时按它还原。 */
    private final ItemStack[] originals;
    /** 时间线上的机器改动（覆盖板等），由各自实现决定怎么执行与还原。 */
    private final List<MachineEdit> edits;

    private Resolved resolved;
    private boolean failed;
    private int ticksShown;

    MachineUiElement(MachineUI ui, Vec3 anchor, Pointing pointing, BlockPos machinePos,
                     List<MachineUiPlacement.SlotWrite> writes, List<MachineEdit> edits) {
        this.ui = ui;
        this.anchor = anchor;
        this.pointing = pointing;
        this.machinePos = machinePos == null ? BlockPos.containing(anchor) : machinePos;
        this.writes = writes;
        this.written = new boolean[writes.size()];
        this.originals = new ItemStack[writes.size()];
        this.edits = edits;
    }

    /**
     * 场景回退（点关键帧、拖进度条）会走 {@code PonderUI#replay -> PonderScene#begin()}：Ponder 先给元素
     * {@code reset()}，再重建 BlockEntity、重跑整条指令，而 {@code ShowMachineUiInstruction} 里的元素还是
     * 同一个实例。把时间线和写入记录一起清掉，回退后数量才会重新从 0 叠起。
     */
    @Override
    public void reset(PonderScene scene) {
        ticksShown = 0;
        for (int i = 0; i < written.length; i++) {
            if (written[i] && originals[i] != null && resolved != null) {
                SlotWidget slot = slotAt(resolved, writes.get(i).index());
                if (slot != null) {
                    slot.setItem(originals[i].copy());
                }
            }
            written[i] = false;
        }
        revertMachineEdits(resolved);
        resolved = null;
    }

    @Override
    public void tick(PonderScene scene) {
        if (failed) {
            return;
        }
        ticksShown++;
        Resolved current = resolve(scene);
        if (current == null) {
            return;
        }
        current.modularUi().mainGroup.updateScreen();
        applyScheduledWrites(current);
        applyMachineEdits(current);
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        if (failed || fade < MIN_FADE) {
            return;
        }
        Resolved current = resolve(scene);
        if (current == null) {
            return;
        }
        try {
            Vec2 projected = scene.getTransform().sceneToScreen(anchor, partialTicks);
            float scale = ui.fitFraction() > 0 ? ui.fitFraction() * screen.width / Math.max(1, current.width()) :
                    ui.scale();
            int width = Math.round(current.width() * scale) + PADDING * 2;
            int height = Math.round(current.height() * scale) + PADDING * 2;

            // speech box 实际占用的矩形（与 PonderUI#renderSpeechBox 的布局一致）：越界就整体推回屏内。
            float boxX = switch (pointing) {
                case LEFT -> projected.x + DIVOT_SPAN;
                case RIGHT -> projected.x - width - DIVOT_SPAN;
                default -> projected.x - width / 2f;
            };
            float boxY = switch (pointing) {
                case UP -> projected.y + DIVOT_SPAN;
                case LEFT, RIGHT -> projected.y - height / 2f;
                default -> projected.y - height - DIVOT_SPAN;
            };
            float dx = clampOffset(boxX, width, screen.width);
            float dy = clampOffset(boxY, height, screen.height);

            // 与 InputWindowElement 一致的淡入位移：面板从指向点滑出来。
            float xFade = pointing == Pointing.RIGHT ? -1 : pointing == Pointing.LEFT ? 1 : 0;
            float yFade = pointing == Pointing.DOWN ? -1 : pointing == Pointing.UP ? 1 : 0;
            xFade *= 10 * (1 - fade);
            yFade *= 10 * (1 - fade);

            // 真实指针换算到面板的 UI 坐标：悬停高亮与 tooltip 都靠它。
            // 内容区原点是 speech box 的落点（boxX/boxY）再加内边距，不是指向点。
            Vec2 mouse = guiMouse();
            float contentX = boxX + dx + xFade + PADDING;
            float contentY = boxY + dy + yFade + PADDING;
            float uiMouseX = (float) ((mouse.x - contentX) / scale) + current.originX();
            float uiMouseY = (float) ((mouse.y - contentY) / scale) + current.originY();

            graphics.pose().pushPose();
            graphics.pose().translate(projected.x + dx + xFade, projected.y + dy + yFade, Z);
            // 指针尖落在锚点上；(0, 0) 之后即面板内容区左上角。
            PonderUI.renderSpeechBox(graphics, 0, 0, width, height, false, pointing, true);
            graphics.pose().translate(PADDING, PADDING, 100);
            graphics.pose().scale(scale, scale, 1);
            // 面板边界可能带负原点（标题栏在面板上方、页签在左侧），对齐到内容区左上角。
            graphics.pose().translate(-current.originX(), -current.originY(), 0);

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, fade);
            current.modularUi().mainGroup.drawInBackground(graphics, Math.round(uiMouseX), Math.round(uiMouseY),
                    partialTicks);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.pose().popPose();

            renderTooltips(graphics, current, uiMouseX, uiMouseY, mouse.x, mouse.y);
        } catch (Throwable t) {
            fail("rendering the machine UI", t);
        }
    }

    /** 真实指针位置，换算成 PonderUI 的 GUI 坐标（同 PonderUI 里 MouseHandler#xpos 的换算）。 */
    private static Vec2 guiMouse() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return new Vec2((float) x, (float) y);
    }

    /**
     * tooltip 由这里自己画。LDLib 的 {@code drawInForeground} 会访问不存在的 {@code ModularUIGuiContainer}，
     * 这里改成按悬停到的控件取文字，画在真实指针位置。
     */
    private void renderTooltips(GuiGraphics graphics, Resolved current, float uiMouseX, float uiMouseY, float screenX,
                                float screenY) {
        try {
            Widget hovered = current.modularUi().mainGroup.getHoverElement(uiMouseX, uiMouseY);
            List<Component> lines = new ArrayList<>(tooltipFor(hovered, uiMouseX, uiMouseY));
            if (lines.isEmpty()) {
                return;
            }
            appendSlotIndex(current, hovered, lines);
            graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), (int) screenX,
                    (int) screenY);
        } catch (Throwable t) {
            // 物品 tooltip 可能来自任意模组，出错不该拖垮整个面板。
            GTPonder.LOGGER.debug("MachineUI tooltip failed", t);
        }
    }

    /**
     * Ponder 的编辑模式（{@code PonderConfig.Client().editingMode}）打开时，把悬停槽位在机器里的真实序号
     * 加在 tooltip 第一行——写场景时对着它填 {@code slot(index)}。玩家背包的槽位不算在内。
     */
    private static void appendSlotIndex(Resolved current, Widget hovered, List<Component> lines) {
        if (!(hovered instanceof SlotWidget slot) || !PonderIndex.editingModeActive()) {
            return;
        }
        int index = current.machineSlots().indexOf(slot);
        if (index >= 0) {
            lines.add(0, Component.translatable(SLOT_INDEX_KEY, index).withStyle(ChatFormatting.GRAY));
        }
    }

    private static List<Component> tooltipFor(Widget hovered, float mouseX, float mouseY) {
        if (hovered instanceof SlotWidget slot) {
            // 含 LargeStackSlotWidget 的「64 / 256」数量行。
            return slot.getFullTooltipTexts();
        }
        if (hovered instanceof TabsWidget tabs) {
            IFancyUIProvider tab = tabs.getHoveredTab(mouseX, mouseY);
            return tab == null ? List.of() : tab.getTabTooltips();
        }
        return List.of();
    }

    /** 槽位写入时间线：到点后让数量在 {@link #FILL_TICKS} 个 tick 内从 0 叠加到目标值。 */
    private void applyScheduledWrites(Resolved current) {
        for (int i = 0; i < writes.size(); i++) {
            if (written[i]) {
                continue;
            }
            MachineUiPlacement.SlotWrite write = writes.get(i);
            int elapsed = ticksShown - write.delayTicks();
            if (elapsed < 0) {
                continue;
            }
            SlotWidget slot = slotAt(current, write.index());
            if (slot == null) {
                continue;
            }
            if (originals[i] == null) {
                // 第一次动这个槽位之前记下原值，回退时按它还原。
                originals[i] = slot.getItem().copy();
            }
            int target = write.stack().getCount();
            if (elapsed >= FILL_TICKS) {
                slot.setItem(write.stack().copy());
                written[i] = true;
                continue;
            }
            int count = (int) Math.round(target * (elapsed / (double) FILL_TICKS));
            if (count <= 0) {
                continue;
            }
            ItemStack partial = write.stack().copy();
            partial.setCount(count);
            slot.setItem(partial);
        }
    }

    /** 机器改动时间线：到点后交给各自的 {@link MachineEdit}，怎么执行、怎么报错由它自己决定。 */
    private void applyMachineEdits(Resolved current) {
        for (MachineEdit edit : edits) {
            if (edit.isApplied() || ticksShown < edit.delayTicks()) {
                continue;
            }
            edit.apply(current.machine(), machinePos);
        }
    }

    /** 回退时让每条机器改动还原自己动过的东西。 */
    private void revertMachineEdits(Resolved current) {
        if (current == null) {
            return;
        }
        for (MachineEdit edit : edits) {
            edit.revert(current.machine(), machinePos);
        }
    }

    private static SlotWidget slotAt(Resolved current, int index) {
        if (index < 0 || index >= current.machineSlots().size()) {
            return null;
        }
        return current.machineSlots().get(index);
    }

    private Resolved resolve(PonderScene scene) {
        BlockEntity blockEntity = scene.getWorld().getBlockEntity(machinePos);
        if (blockEntity == null) {
            return null;
        }
        if (resolved != null && resolved.blockEntity() == blockEntity) {
            return resolved;
        }
        try {
            Resolved built = build(blockEntity);
            if (built == null) {
                return null;
            }
            // BlockEntity 被重建（场景重播 / 跳步）后，槽位写入需要重放。
            for (int i = 0; i < written.length; i++) {
                written[i] = false;
            }
            resolved = built;
            return built;
        } catch (Throwable t) {
            fail("building the machine UI", t);
            return null;
        }
    }

    private Resolved build(BlockEntity blockEntity) {
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
            applyFancyChrome(fancy);
        }
        List<SlotWidget> slots = collectMachineSlots(modularUi);
        Bounds bounds = measure(root);
        GTPonder.LOGGER.debug("MachineUI at {}: panel {}x{} at ({}, {}), {} machine slot(s)", machinePos,
                bounds.width(), bounds.height(), bounds.x(), bounds.y(), slots.size());
        return new Resolved(blockEntity, machine, modularUi, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                slots);
    }

    /**
     * 按 MachineUI 的开关决定哪些 fancy 组件可见：玩家背包、配置器面板、提示面板等不在白名单里的一律隐藏，
     * 标题栏上的返回与翻页按钮也一并关掉。
     */
    private void applyFancyChrome(FancyMachineUIWidget fancy) {
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
     * 只算根容器矩形会把这两块落在 speech box 外面。
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

    private static float clampOffset(float start, float size, float screenSize) {
        float min = MARGIN;
        float max = screenSize - MARGIN - size;
        if (max < min) {
            return min - start;
        }
        if (start < min) {
            return min - start;
        }
        if (start > max) {
            return max - start;
        }
        return 0;
    }

    private static Widget pickRoot(ModularUI modularUi) {
        if (modularUi.mainGroup.widgets.size() == 1 &&
                modularUi.mainGroup.widgets.get(0) instanceof FancyMachineUIWidget fancy) {
            return fancy;
        }
        return modularUi.mainGroup;
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

    private void fail(String phase, Throwable throwable) {
        if (failed) {
            return;
        }
        failed = true;
        GTPonder.LOGGER.error("MachineUI element failed while {} (machine at {})", phase, machinePos, throwable);
    }

    private record Bounds(int x, int y, int width, int height) {}

    private record Resolved(BlockEntity blockEntity, MetaMachine machine, ModularUI modularUi, int originX,
                            int originY, int width, int height, List<SlotWidget> machineSlots) {}
}
