// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.ctnh.bettergregtechponder.client.ponder.machine.MachineEdits;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 把机器真实的 UI 画进思索场景的叠加层元素：面板画成 Ponder 的 speech box，指针尖指向场景里的锚点，
 * 按时间线把物品与流体写进指定槽位、储罐，需要时还会给面板里的控件套红框。
 *
 * <p>
 * 机器实例在渲染/运行期按坐标解析，不跨重播持有；场景重播（{@code PonderScene#begin()} 会重建
 * BlockEntity）时自动重建界面并重放写入。
 *
 * <p>
 * 这一层只管生命周期与解析：画法在 {@link MachineUiOverlay}，面板怎么建在 {@link MachineUiPanelBuilder}，
 * 写入时间线在 {@link MachineUiWrites}，配方在 {@link RecipeFiller}。
 */
public class MachineUiElement extends AnimatedOverlayElementBase {

    private static final float MIN_FADE = 1 / 16f;

    private final MachineUI ui;
    private final Vec3 anchor;
    private final Pointing pointing;
    private final BlockPos machinePos;
    private final MachineUiWrites writes;
    /** 这次摆放挂的配方：入料、成品、进度条与机器的开停机都听它的。 */
    @Nullable
    private final RecipeFiller recipe;
    /** 配方要编程电路时，面板里连电路 UI 一起画（场景没写 showCircuit() 也画）。 */
    private final boolean recipeCircuit;
    /** 这次摆放的缩放；0 表示用界面定义自己的 scale / fitToPanel。 */
    private final float scale;
    /** 红框计划：框哪一类控件、第几个、延迟多少 tick 亮起。 */
    private final List<MachineUiPlacement.Outline> outlines;

    private MachineUiPanel panel;
    /** 面板建不出来时只报一次，别每帧刷屏。 */
    private boolean reportedMissingPanel;
    /** 见过的最后一代机器改动；与 {@code MachineEdits.rebuildGeneration()} 不同就重建。 */
    private long seenRebuildGeneration;
    /** 每个红框只报一次「框不到」，免得每帧刷日志。 */
    private boolean[] reportedOutlines;
    private boolean failed;
    /** 面板已经演完：时间线不再跑，免得隐藏后还被 tick 到、把自己的写入重放一遍。 */
    private boolean finished;
    private int ticksShown;

    MachineUiElement(MachineUiPlacement.Plan plan) {
        this.ui = plan.ui();
        this.anchor = plan.anchor();
        this.pointing = plan.pointing();
        this.machinePos = plan.machinePos() == null ? BlockPos.containing(plan.anchor()) : plan.machinePos();
        this.writes = new MachineUiWrites(this.machinePos, plan.slots(), plan.fluids());
        this.recipe = plan.recipe() == null ? null : new RecipeFiller(plan.recipe(), this.machinePos);
        this.recipeCircuit = RecipeFiller.needsCircuit(plan.recipe());
        this.scale = plan.scale();
        this.outlines = plan.outlines();
        this.reportedOutlines = new boolean[this.outlines.size()];
    }

    /**
     * 场景回退（点关键帧、拖进度条）会走 {@code PonderUI#replay -> PonderScene#begin()}：Ponder 先给元素
     * {@code reset()}，再重建 BlockEntity、重跑整条指令，而 {@code ShowMachineUiInstruction} 里的元素还是
     * 同一个实例。把时间线和写入记录一起清掉，回退后数量才会重新从 0 叠起。
     */
    @Override
    public void reset(PonderScene scene) {
        restoreMachine();
        writes.dropAdded();
        finished = false;
        panel = null;
    }

    /**
     * 面板演完（{@link ShowMachineUiInstruction#hide}）时调用：还原写入，并停掉时间线。
     *
     * <p>必须停：{@code PonderScene.tick()} 会 tick <strong>所有</strong>元素（包括已经隐藏的），
     * 而 {@link #restoreMachine()} 把 {@code ticksShown} 和写入标记清零了，元素会以为自己是刚出场，
     * 于是把这一段的时间线又跑一遍——表现就是「下一段面板里莫名其妙又出现了上一段的物品」。
     */
    void finish() {
        restoreMachine();
        finished = true;
    }

    /** 把写进机器的内容、切过的模型状态与时间线一起还原。 */
    private void restoreMachine() {
        writes.restore(panel);
        if (recipe != null) {
            recipe.revert(panel);
        }
        ticksShown = 0;
        reportedMissingPanel = false;
    }

    @Override
    public void tick(PonderScene scene) {
        if (failed || finished) {
            return;
        }
        ticksShown++;
        // 场景指令改过机器状态（并行数、维护故障……）时要重建面板：
        // 那些控件会缓存自己的显示值，只改机器状态是看不出来的。用代际比对，
        // 保证同一场景里每一块面板都各自重建一次，而不是被最先 tick 到的那个吃掉。
        long generation = MachineEdits.rebuildGeneration();
        if (generation != seenRebuildGeneration) {
            seenRebuildGeneration = generation;
            invalidate();
        }
        MachineUiPanel current = resolve(scene);
        if (current == null) {
            return;
        }
        current.modularUi().mainGroup.updateScreen();
        // 不是详情模式时也要刷配置器缓存：关掉详情后机器状态已经写回，图标得跟着回到打开前的样子，
        // 不能等到下次再打开模式才更新（LDLib 容器不在，这个缓存的刷新只能我们自己来）。
        ConfiguratorTabs.syncConfigurators(current.configurators());
        // 配方按进度条开关机，改的是机器模型；Ponder 把世界渲染缓存住了，切完得让它重画一次。
        if (recipe != null && recipe.tick(current, ticksShown)) {
            MachineEdits.redraw(scene);
        }
        writes.tick(current, ticksShown);
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        if (failed || finished || fade < MIN_FADE) {
            return;
        }
        MachineUiPanel current = resolve(scene);
        if (current == null) {
            return;
        }
        try {
            MachineUiOverlay.render(scene, graphics, screen, this, current, anchor, pointing, partialTicks, fade,
                    actualScale(screen, current), outlineBoxes(current), pulse(), ui.full());
        } catch (Throwable t) {
            fail("rendering the machine UI", t);
        }
    }

    /** 丢掉面板快照，下一帧重新解析：观众点页签换了页面之后边界与槽位表都要重算。 */
    void invalidate() {
        panel = null;
    }

    /** 这次摆放的实际缩放：场景写死的优先，其次是界面定义上的 fitToPanel，最后是 scale。 */
    private float actualScale(PonderUI screen, MachineUiPanel panel) {
        if (scale > 0) {
            return scale;
        }
        return ui.fitFraction() > 0 ? ui.fitFraction() * screen.width / Math.max(1, panel.width()) : ui.scale();
    }

    /** 到点的红框：取出控件的矩形（面板坐标）；这台机器没有那一类控件时报一行 error，只报一次。 */
    private List<MachineUiOverlay.Box> outlineBoxes(MachineUiPanel panel) {
        if (outlines.isEmpty()) {
            return List.of();
        }
        List<MachineUiOverlay.Box> boxes = new ArrayList<>();
        for (int i = 0; i < outlines.size(); i++) {
            MachineUiPlacement.Outline outline = outlines.get(i);
            if (ticksShown < outline.delayTicks()) {
                continue;
            }
            List<Widget> widgets = panel.parts(outline);
            if (widgets.isEmpty()) {
                reportMissingOutline(i, outline);
                continue;
            }
            for (Widget widget : widgets) {
                boxes.add(new MachineUiOverlay.Box(widget.getPositionX(), widget.getPositionY(),
                        widget.getSizeWidth(), widget.getSizeHeight()));
            }
        }
        return boxes;
    }

    /** 框不到就说清楚为什么：这台机器没有这一路控件，或者配置器那一列面板根本没画。 */
    private void reportMissingOutline(int index, MachineUiPlacement.Outline outline) {
        if (reportedOutlines[index]) {
            return;
        }
        reportedOutlines[index] = true;
        BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot outline the {} control of the machine at " +
                "{}: this machine has no " +
                "such control, or the configurator panel is not drawn (try showFullUI())", outline.part(),
                machinePos);
    }

    /** 红框的呼吸：0~1 来回走，亮得有点节奏。 */
    private float pulse() {
        return (float) ((Math.sin(ticksShown * 0.25) + 1) / 2);
    }

    /** 按坐标取面板；BlockEntity 换了（重播、跳步）就重建一次，写入与配方也跟着重来。 */
    private MachineUiPanel resolve(PonderScene scene) {
        BlockEntity blockEntity = scene.getWorld().getBlockEntity(machinePos);
        if (blockEntity == null) {
            // 静默返回的话，场景里只会「什么都没有」，看不出原因：多半是这一格没有被 storyboard 载入，
            // 只是世界里有方块而没生成方块实体（例如代码 setBlock 出来的），或者指向点写到了机器上方那一格。
            if (!reportedMissingPanel) {
                reportedMissingPanel = true;
                BetterGregTechPonder.LOGGER.warn(
                        "BetterGregTechPonder: no block entity at {} - the UI panel cannot be built for this segment",
                        machinePos);
            }
            return null;
        }
        if (panel != null && panel.blockEntity() == blockEntity) {
            return panel;
        }
        try {
            MachineUiPanel built = MachineUiPanelBuilder.build(ui, machinePos, blockEntity, recipeCircuit);
            if (built == null) {
                // 同样别静默：面板建不出来有四种原因（不是机器方块 / 机器没有 UI / 玩家不在 / createUI 返回 null），
                // 报出坐标和方块 id 才能在日志里区分；每段只报一次。
                if (!reportedMissingPanel) {
                    reportedMissingPanel = true;
                    BetterGregTechPonder.LOGGER.warn(
                            "BetterGregTechPonder: the block at {} ({}) produced no panel - not a machine, no UI, no player, or createUI returned null",
                            machinePos, blockEntity.getBlockState().getBlock());
                }
                return null;
            }
            writes.resetMarks();
            if (recipe != null) {
                writes.dropAdded();
                recipe.plan(built, writes);
            }
            panel = built;
            return built;
        } catch (Throwable t) {
            fail("building the machine UI", t);
            return null;
        }
    }

    private void fail(String phase, Throwable throwable) {
        if (failed) {
            return;
        }
        failed = true;
        BetterGregTechPonder.LOGGER.error("MachineUI element failed while {} (machine at {})", phase, machinePos, throwable);
    }
}
