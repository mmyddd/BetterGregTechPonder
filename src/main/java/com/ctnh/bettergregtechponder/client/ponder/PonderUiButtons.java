// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.ctnh.bettergregtechponder.client.ponder.ui.MachineUiInteraction;

import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 在思索界面底部的按钮栏里加一个「查看 UI 详情」：按下之后，那一段里画的机器面板才允许点击
 * （只放行左侧与左下的页签、以及配置器面板里的电路页签，见 {@link MachineUiInteraction}）。
 *
 * <p>按钮只在当前场景画着机器 UI 时出现。Ponder 自己那排按钮是画在 {@code Screen#renderables} 里的，
 * 外面拿不到也不该动，所以这里换个路子：按钮自己建、自己在渲染事件里画、自己在鼠标事件里收点击，
 * 顺手把那一排（看得见的那些）连自己一起排成等分 —— 单场景时上一段/下一段那两个箭头是透明的，
 * 于是屏幕上正好是「三个 + 这个」四等分。
 */
@Mod.EventBusSubscriber(modid = BetterGregTechPonder.MODID, value = Dist.CLIENT)
public final class PonderUiButtons {

    /** 按钮的 tooltip。 */
    private static final String LABEL = "bettergregtechponder.ponder.ui.ui_details";
    /** 右下角那几个按钮的地盘：排布时给它们留着，不动。 */
    private static final int CORNER_KEEP_OUT = 120;

    @Nullable
    private static PonderButton button;

    private PonderUiButtons() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PonderUI)) {
            button = null;
            return;
        }
        MachineUiInteraction.setEnabled(false);
        button = new PonderButton(0, 0)
                .showing(PonderGuiTextures.ICON_PONDER_USER_MODE)
                .withCallback(PonderUiButtons::toggle);
        button.dim();
    }

    /** 开关这个模式，顺带把按钮的亮/暗切过去（Ponder 自己那排按钮就是这个视觉语言）。 */
    private static void toggle() {
        MachineUiInteraction.setEnabled(!MachineUiInteraction.enabled());
        if (button == null) {
            return;
        }
        if (MachineUiInteraction.enabled()) {
            button.flash();
        } else {
            button.dim();
        }
    }

    /** 场景渲染之前清一次落点登记，面板会在自己渲染时重新登记。 */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof PonderUI) {
            MachineUiInteraction.beginFrame();
        }
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PonderUI ponder) || button == null) {
            return;
        }
        boolean visible = MachineUiInteraction.hasPanel();
        button.visible = visible;
        if (!visible) {
            return;
        }
        layout(ponder, button);
        button.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        if (button.isMouseOver(event.getMouseX(), event.getMouseY())) {
            event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font,
                    Component.translatable(LABEL).withStyle(ChatFormatting.GRAY), event.getMouseX(),
                    event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof PonderUI)) {
            return;
        }
        if (MachineUiInteraction.click(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (button != null && button.visible && button.isMouseOver(event.getMouseX(), event.getMouseY()) &&
                button.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && button != null) {
            button.tick();
        }
    }

    /**
     * 把底部那一排里看得见的按钮连自己一起排成等分，右下角那几个（慢速阅读、用户模式）不动。
     *
     * <p>每帧重排：Ponder 按场景进度淡入淡出上一段/下一段按钮，露出来时它们会跟着一起排队。
     */
    private static void layout(PonderUI ponder, PonderButton ours) {
        List<PonderButton> row = new ArrayList<>();
        List<PonderButton> corners = new ArrayList<>();
        for (var widget : ponder.children()) {
            if (widget instanceof PonderButton other && other != ours) {
                row.add(other);
            }
        }
        if (row.isEmpty()) {
            ours.setX((ponder.width - ours.getWidth()) / 2);
            return;
        }
        int rowY = row.stream()
                .mapToInt(PonderButton::getY)
                .max()
                .orElse(0);
        List<PonderButton> line = new ArrayList<>();
        for (PonderButton other : row) {
            if (other.getY() != rowY) {
                continue;
            }
            if (other.getX() > ponder.width - CORNER_KEEP_OUT) {
                corners.add(other);
                continue;
            }
            if (other.isVisible()) {
                line.add(other);
            }
        }
        line.sort(Comparator.comparingInt(PonderButton::getX));
        // 插在「显示方块名称」（最左边那个）右边。
        line.add(line.isEmpty() ? 0 : 1, ours);

        int left = 8;
        int right = ponder.width - 8;
        for (PonderButton corner : corners) {
            right = Math.min(right, corner.getX() - 8);
        }
        int count = line.size();
        for (int i = 0; i < count; i++) {
            PonderButton current = line.get(i);
            int center = left + Math.round((i + 0.5f) * (right - left) / count);
            current.setX(center - current.getWidth() / 2);
            current.setY(rowY);
        }
    }
}
