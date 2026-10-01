// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.ctnh.bettergregtechponder.client.ponder.ui.MachineUiInteraction;

import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 在思索界面底部的按钮栏里加一个「查看 UI 详情」：按下之后，那一段里画的机器面板才允许点击
 * （放行左侧与左下的页签、以及配置器面板里的电路页签，见 {@link MachineUiInteraction}）。
 *
 * <p>按钮只在当前场景画着机器 UI 时出现，图标与文字都自己画 —— Ponder 那排按钮是画在
 * {@code Screen#renderables} 里的，外面拿不到也不该动，所以这里自己建、自己在渲染事件里画、
 * 自己在鼠标事件里收点击。
 *
 * <p>排布只动「中间那一簇」：认得出快捷键的那些（显示方块名称 / 思索结束 / 重放…），它们连新按钮
 * 一起在两侧固定按钮之间等分；没有快捷键的边缘按钮（退出、舒适阅读）原地不动。
 */
@Mod.EventBusSubscriber(modid = BetterGregTechPonder.MODID, value = Dist.CLIENT)
public final class PonderUiButtons {

    /** 按钮的 tooltip 与按钮下方那行小字。 */
    private static final String LABEL = "bettergregtechponder.ponder.ui.ui_details";
    private static final int ICON = 16;
    private static final int ICON_PAD = 2;
    private static final int LABEL_GAP = 4;

    @Nullable
    private static Field shortcutField;
    private static boolean shortcutFieldMissing;

    @Nullable
    private static PonderButton button;
    private static boolean logged;

    private PonderUiButtons() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PonderUI)) {
            button = null;
            logged = false;
            return;
        }
        MachineUiInteraction.setEnabled(false);
        button = new PonderButton(0, 0).withCallback(PonderUiButtons::toggle);
        button.dim();
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
            logged = false;
            return;
        }
        layout(ponder, button);
        GuiGraphics graphics = event.getGuiGraphics();
        button.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        drawIcon(graphics, button.getX(), button.getY());
        drawLabel(graphics, ponder, button);

        if (!logged) {
            logged = true;
            BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: UI details button at {}x{} (screen {}x{}), " +
                    "bar buttons: {}", button.getX(), button.getY(), ponder.width, ponder.height, describe(ponder));
        }
        if (button.isMouseOver(event.getMouseX(), event.getMouseY())) {
            graphics.renderTooltip(Minecraft.getInstance().font,
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

    /** 图标自己画：一块面板 + 右下角放大镜，16×16。 */
    private static void drawIcon(GuiGraphics graphics, int x, int y) {
        int px = x + ICON_PAD;
        int py = y + ICON_PAD;
        int light = 0xFFE4E4E4;
        int dim = 0xFF969696;
        graphics.fill(px, py + 1, px + 12, py + 2, light);
        graphics.fill(px, py + 12, px + 12, py + 13, light);
        graphics.fill(px, py + 1, px + 1, py + 13, light);
        graphics.fill(px + 11, py + 1, px + 12, py + 13, light);
        graphics.fill(px, py + 4, px + 12, py + 5, light);
        graphics.fill(px + 2, py + 6, px + 9, py + 7, dim);
        graphics.fill(px + 2, py + 8, px + 7, py + 9, dim);
        graphics.fill(px + 8, py + 7, px + 14, py + 8, 0xFFFFFFFF);
        graphics.fill(px + 8, py + 13, px + 14, py + 14, 0xFFFFFFFF);
        graphics.fill(px + 8, py + 7, px + 9, py + 14, 0xFFFFFFFF);
        graphics.fill(px + 13, py + 7, px + 14, py + 14, 0xFFFFFFFF);
        graphics.fill(px + 12, py + 12, px + 16, py + 13, 0xFFFFFFFF);
        graphics.fill(px + 15, py + 12, px + 16, py + 16, 0xFFFFFFFF);
    }

    /** 按钮下方常显一行小字，免得只靠图标认不出来；开着的时候换成高亮色。 */
    private static void drawLabel(GuiGraphics graphics, PonderUI ponder, PonderButton button) {
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable(LABEL);
        int color = MachineUiInteraction.enabled() ? 0xFF_FFE08A : 0xFF_C8C8C8;
        // 画在按钮上方：下面就是进度条，压上去会看不清。
        int x = button.getX() + button.getWidth() / 2 - font.width(text) / 2;
        int y = Math.max(4, button.getY() - font.lineHeight - LABEL_GAP);
        graphics.drawString(font, text, Math.max(4, x), y, color, true);
    }

    /**
     * 只排「中间那一簇」：可见、并且挂得住快捷键的按钮（显示方块名称、思索结束、重放…）。
     * 没有快捷键的边缘按钮（退出、舒适阅读）原地不动，但把它们之间的空间让给这一簇。
     */
    private static void layout(PonderUI ponder, PonderButton ours) {
        List<PonderButton> row = new ArrayList<>();
        for (var widget : ponder.children()) {
            if (widget instanceof PonderButton other && other != ours) {
                row.add(other);
            }
        }
        if (row.isEmpty()) {
            ours.setX((ponder.width - ours.getWidth()) / 2);
            ours.setY(ponder.height - 51);
            return;
        }
        int rowY = row.stream()
                .mapToInt(PonderButton::getY)
                .max()
                .orElse(0);
        int left = 8;
        int right = ponder.width - 8;
        List<PonderButton> cluster = new ArrayList<>();
        for (PonderButton other : row) {
            if (other.getY() != rowY || !other.isVisible()) {
                continue;
            }
            if (shortcutOf(other) == null) {
                if (other.getX() < ponder.width / 2) {
                    left = Math.max(left, other.getX() + other.getWidth() + 8);
                } else {
                    right = Math.min(right, other.getX() - 8);
                }
                continue;
            }
            cluster.add(other);
        }
        cluster.sort(Comparator.comparingInt(PonderButton::getX));
        // 插在「显示方块名称」右边；认不出来就排在中间那一簇的最左边。
        int insert = 0;
        KeyMapping drop = Minecraft.getInstance().options.keyDrop;
        for (int i = 0; i < cluster.size(); i++) {
            if (shortcutOf(cluster.get(i)) == drop) {
                insert = i + 1;
            }
        }
        cluster.add(Math.min(insert, cluster.size()), ours);

        int count = cluster.size();
        for (int i = 0; i < count; i++) {
            PonderButton current = cluster.get(i);
            int center = left + Math.round((i + 0.5f) * (right - left) / count);
            current.setX(center - current.getWidth() / 2);
            current.setY(rowY);
        }
    }

    /** PonderButton 的 shortcut 字段是 protected，反射读一次并缓存；读不到就当认不出（一切原地不动）。 */
    @Nullable
    private static KeyMapping shortcutOf(PonderButton button) {
        Field field = shortcutField();
        if (field == null) {
            return null;
        }
        try {
            return (KeyMapping) field.get(button);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Field shortcutField() {
        if (shortcutField == null && !shortcutFieldMissing) {
            try {
                Field field = PonderButton.class.getDeclaredField("shortcut");
                field.setAccessible(true);
                shortcutField = field;
            } catch (Throwable t) {
                shortcutFieldMissing = true;
                BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot read the shortcut of a ponder " +
                        "button; the bottom bar will be left as it is", t);
            }
        }
        return shortcutField;
    }

    private static String describe(PonderUI ponder) {
        StringBuilder builder = new StringBuilder();
        for (var widget : ponder.children()) {
            if (widget instanceof PonderButton other) {
                builder.append('[')
                        .append(other.getX())
                        .append(", y=")
                        .append(other.getY())
                        .append(", vis=")
                        .append(other.isVisible())
                        .append(", key=")
                        .append(shortcutOf(other) == null ? "-" : shortcutOf(other).getName())
                        .append("] ");
            }
        }
        return builder.toString();
    }
}
