// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.ctnh.bettergregtechponder.client.ponder.ui.MachineUiInteraction;

import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 思索界面底部的「查看 UI 详情」按钮。
 *
 * <p>规则只有三条：
 * <ol>
 *   <li>只有当前这一段画着机器 UI 时它才出现，也只在这时候动那一排按钮；别的场景一个像素都不碰。</li>
 *   <li>进入这类场景时按 <strong>Ponder 原始位置</strong>算一次目标位置（认得出快捷键的那些按钮连它一起等分），
 *       离开时全部还原。不做逐帧重排，所以没有抖动，也不会越排越偏。</li>
 *   <li>画图标与文字只有一个地方：按钮自己的 render。别处一律不画，避免出现两个。</li>
 * </ol>
 *
 * <p>按钮由 {@code PonderUIMixin} 在 {@code PonderUI.init()} 末尾挂进 PonderUI 自己的控件表，
 * 于是渲染、淡入淡出、点击派发都跟底部那一排一样；点击面板时由 mixin 转给 {@link MachineUiInteraction}。
 */
@Mod.EventBusSubscriber(modid = BetterGregTechPonder.MODID, value = Dist.CLIENT)
public final class PonderUiButtons {

    /** 按钮的 tooltip 与按钮上方那行小字。 */
    private static final String LABEL = "bettergregtechponder.ponder.ui.ui_details";
    private static final int ICON_PAD = 2;
    private static final int LABEL_GAP = 3;
    /** 与"显示方块名称"之间的间隔；和 Ponder 自己按钮之间的间距保持一致。 */
    private static final int GAP = 8;
    /** 连续这么多帧没有面板，才认定这一段确实没有 UI。 */
    private static final int MISSING_FRAMES = 10;
    /** 贴在这个比例的屏幕宽度以内的按钮算"边缘按钮"（退出那类），不当锚点。 */
    private static final float EDGE_FRACTION = 0.12f;


    @Nullable
    private static PonderUI screen;
    @Nullable
    private static DetailsButton button;
    /** 是否已经贴到"显示方块名称"右边。 */
    private static boolean placed;
    /** 这一帧是否已经处理过（mixin 与事件两条路都会调，只认第一次）。 */
    private static boolean frameHandled;
    /** 连续多少帧没看到面板；连着几帧都没有才关模式。 */
    private static int absentFrames;
    private static boolean logged;

    @Nullable
    private static Field shortcutField;
    private static boolean shortcutFieldMissing;
    @Nullable
    private static Method addRenderableWidget;
    private static boolean addRenderableWidgetMissing;

    private PonderUiButtons() {}

    /**
     * 挂按钮：同一屏里已经有一个就复用，绝不建第二个。
     *
     * <p>去重必须看这一屏自己的控件表：从子界面返回或窗口尺寸变化都会让 {@code Screen#init()} 再跑一次，
     * 那时候别的屏幕可能已经把静态标记清掉了，靠标记去重就会多挂一个（屏幕上出现两个按钮）。
     *
     * <p>每次都重新记一遍原始坐标（用 putIfAbsent，不覆盖更早的值），并让排布重来一次 ——
     * Ponder 重跑 init 时可能已经把那一排按钮换成了新对象。
     */
    public static void attach(PonderUI ponder) {
        DetailsButton existing = existing(ponder);
        if (existing == null) {
            existing = new DetailsButton((ponder.width - 20) / 2, ponder.height - 51)
                    .withCallback(PonderUiButtons::toggle);
            existing.dim();
            if (!register(ponder, existing)) {
                return;
            }
            MachineUiInteraction.setEnabled(false);
        }
        screen = ponder;
        button = existing;
        placed = false;
        logged = false;
        // 刚挂上/重新挂上时先藏起来：PonderButton 默认可见，在下一帧 afterRender 校正之前
        // 它会以构造位置（屏幕正中）露一下脸，正好压住「思索结束」。
        button.visible = false;
        // 同一屏里只允许有一个我们的按钮：init 重跑、screen 被别的界面清空之后，
        // 旧对象可能还留在控件表里，那种孤儿没人校正，会一直停在正中且可见。
        for (DetailsButton other : detailsButtons(ponder)) {
            if (other != button) {
                other.visible = false;
            }
        }
        BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: UI details button attached (screen {}x{}), bar: {}",
                ponder.width, ponder.height, describe(ponder));
    }

    @Nullable
    private static DetailsButton existing(PonderUI ponder) {
        List<DetailsButton> all = detailsButtons(ponder);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 这一屏里我们的全部按钮（正常只有一个，重挂之后可能留下孤儿）。 */
    private static List<DetailsButton> detailsButtons(PonderUI ponder) {
        List<DetailsButton> found = new ArrayList<>();
        for (GuiEventListener listener : ponder.children()) {
            if (listener instanceof DetailsButton details) {
                found.add(details);
            }
        }
        return found;
    }

    /**
     * 每帧渲染前：先读上一帧登记的面板状态（读早了这帧还没人登记，读晚了就被清空），
     * 再清空登记，最后决定按钮显不显示、能不能点。
     *
     * <p>这个方法是「每帧只处理一次」的：mixin 与 Forge 事件两条路都会调，第一次读到的才是
     * 上一帧的真实登记，第二次读到的已经被自己清空了 —— 那会把状态全判成「没有 UI」，
     * 于是模式刚点开就被关掉（场景照旧推进）、按钮也一直灰着点不动。
     */
    public static void beforeRender(PonderUI ponder) {
        if (frameHandled) {
            return;
        }
        frameHandled = true;
        boolean anyPanel = MachineUiInteraction.hasPanel();
        boolean fullPanel = MachineUiInteraction.hasFullPanel();
        MachineUiInteraction.beginFrame();
        if (button == null) {
            return;
        }
        if (anyPanel) {
            absentFrames = 0;
        } else {
            absentFrames++;
        }
        // 关模式要连续几帧都确实没有面板才算数：任何一帧的时序抖动都不该让观众刚点开的模式失效。
        if (absentFrames >= MISSING_FRAMES) {
            if (MachineUiInteraction.enabled()) {
                BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: no machine UI for {} frames, mode OFF",
                        absentFrames);
            }
            // 这一段没有机器 UI：直接收起来。Ponder 自己的按钮我们一根手指都不碰。
            placed = false;
            button.visible = false;
            MachineUiInteraction.setEnabled(false);
            return;
        }
        // 显示与否交给 afterRender：只有那里才知道这一帧能不能贴到锚点旁边。
        // 这里只处理「不能点就关模式」这一条。
        button.active = fullPanel;
        if (!fullPanel) {
            MachineUiInteraction.setEnabled(false);
        }
    }

    /**
     * 每帧渲染后：这时 Ponder 才把整排按钮的 fade 更新完（按钮的 isVisible() 读的就是它），
     * 所以排布必须在这里做。还没排成（比如刚进场景那一帧 fade 还没起来）就留着下一帧重试。
     */
    public static void afterRender(PonderUI ponder, GuiGraphics graphics, int mouseX, int mouseY) {
        frameHandled = false;
        // 每帧都重新贴一次：锚点（显示方块名称）在场景切换那几帧可能还没稳定，
        // 只贴一次会把按钮留在初始的居中位置（正好压住「思索结束」）。
        if (button != null && MachineUiInteraction.hasFullPanel()) {
            placed = place(ponder);
            button.visible = placed;
        } else if (button != null) {
            placed = false;
            button.visible = false;
        }
        if (button != null && button.visible && !logged) {
            logged = true;
            BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: UI details button at {}x{}; bar now {}",
                    button.getX(), button.getY(), describe(ponder));
        }
    }

    /** 面板上的点击：只放行页签与配置器里的电路页签。 */
    public static boolean clickPanel(double mouseX, double mouseY, int mouseButton) {
        return MachineUiInteraction.click(mouseX, mouseY, mouseButton);
    }

    public static void tick() {
        if (button != null) {
            button.tick();
        }
        if (MachineUiInteraction.enabled()) {
            // 场景冻着，但面板自己的 UI 得继续跑（页签切换的动画与布局都靠它）。
            MachineUiInteraction.tickPanels();
        }
    }

    /** 事件那条路的兜底：只负责把按钮挂上，排布与绘制都走同一条路。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PonderUI ponder) {
            attach(ponder);
        } else if (event.getScreen() != screen) {
            screen = null;
            button = null;
            placed = false;
        }
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof PonderUI ponder) {
            beforeRender(ponder);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof PonderUI ponder) {
            afterRender(ponder, event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof PonderUI ponder)) {
            return;
        }
        if (screen != ponder) {
            attach(ponder);
        }
        if (clickPanel(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (button != null && button.visible && button.active &&
                button.isMouseOver(event.getMouseX(), event.getMouseY()) &&
                button.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick();
        }
    }

    /** 开关这个模式：开启时记下暂停那一帧（冻结场景要用），顺带把按钮的亮/暗切过去。 */
    private static void toggle() {
        boolean on = !MachineUiInteraction.enabled();
        MachineUiInteraction.setEnabled(on);
        BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: UI details mode {}", on ? "ON" : "OFF");
        if (on) {
            // 冻结用：记下当前这一帧，渲染与 tick 都按它来（与"查看方块名称"同一套）。
            PonderUI.ponderPartialTicksPaused = Minecraft.getInstance()
                    .getFrameTime();
        }
        if (button == null) {
            return;
        }
        if (MachineUiInteraction.enabled()) {
            button.flash();
        } else {
            button.dim();
        }
    }

    // ===== 定位 =====



    /**
     * 把按钮贴在"显示方块名称"右边，紧挨着放。
     *
     * <p>"显示方块名称"= 这一排里第一个不贴着屏幕左边缘的按钮：最左边那个"退出"是贴边的，
     * 而它在有些段落里还会变成不可见，所以不能简单地把"最左"去掉。判定只看坐标、不读任何字段。
     * Ponder 自己的按钮一个都不移动。
     */
    private static boolean place(PonderUI ponder) {
        List<PonderButton> row = row(ponder, button);
        row.removeIf(other -> !other.isVisible());
        if (row.isEmpty()) {
            return false;
        }
        row.sort(Comparator.comparingInt(PonderButton::getX));
        // 锚点必须在左半边：最左边的那些贴边按钮（退出之类）与中间的显示方块名称二选一，
        // 绝不会落到右边的思索结束、重放上 —— 落到那里就会在它们身上叠一个按钮。
        int edge = Math.max(1, Math.round(ponder.width * EDGE_FRACTION));
        int half = ponder.width / 2;
        PonderButton anchor = null;
        for (PonderButton other : row) {
            if (other.getX() >= edge && other.getX() < half) {
                anchor = other;
                break;
            }
        }
        if (anchor == null) {
            for (PonderButton other : row) {
                if (other.getX() < half) {
                    anchor = other;
                    break;
                }
            }
        }
        if (anchor == null) {
            return false;
        }
        button.setX(anchor.getX() + anchor.getWidth() + GAP);
        button.setY(anchor.getY());
        return true;
    }

    /** 底部那一排按钮（排除自己）。 */
    private static List<PonderButton> row(PonderUI ponder, @Nullable PonderButton excluded) {
        List<PonderButton> row = new ArrayList<>();
        if (screen == null) {
            return row;
        }
        for (GuiEventListener listener : ponder.children()) {
            if (listener instanceof PonderButton other && other != excluded) {
                row.add(other);
            }
        }
        return row;
    }

    // ===== 按钮本体 =====

    /**
     * 按钮：图标与那行小字都在自己的 render 里补画（控件是屏幕统一绘制的，别处画会被按钮的底盖住），
     * 并且跟着按钮自己的 fade 走 —— 切场景时整排会淡出，补画的部分必须同进同退，
     * 否则会留下一个没有底框的裸图标。
     */
    private static final class DetailsButton extends PonderButton {

        private DetailsButton(int x, int y) {
            super(x, y);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.render(graphics, mouseX, mouseY, partialTicks);
            if (!visible) {
                return;
            }
            float alpha = Mth.clamp(fade().getValue(partialTicks), 0F, 1F);
            if (alpha < 0.05F) {
                return;
            }
            drawIcon(graphics, getX(), getY(), alpha);
            drawLabel(graphics, getX(), getY(), alpha);
            if (isMouseOver(mouseX, mouseY)) {
                graphics.renderTooltip(Minecraft.getInstance().font,
                        Component.translatable(LABEL).withStyle(ChatFormatting.GRAY), mouseX, mouseY);
            }
        }
    }

    /** 图标：一块面板 + 右下角放大镜，16×16。 */
    private static void drawIcon(GuiGraphics graphics, int x, int y, float alpha) {
        int px = x + ICON_PAD;
        int py = y + ICON_PAD;
        int light = withAlpha(0xE4E4E4, alpha);
        int dim = withAlpha(0x969696, alpha);
        int white = withAlpha(0xFFFFFF, alpha);
        graphics.fill(px, py + 1, px + 12, py + 2, light);
        graphics.fill(px, py + 12, px + 12, py + 13, light);
        graphics.fill(px, py + 1, px + 1, py + 13, light);
        graphics.fill(px + 11, py + 1, px + 12, py + 13, light);
        graphics.fill(px, py + 4, px + 12, py + 5, light);
        graphics.fill(px + 2, py + 6, px + 9, py + 7, dim);
        graphics.fill(px + 2, py + 8, px + 7, py + 9, dim);
        graphics.fill(px + 8, py + 7, px + 14, py + 8, white);
        graphics.fill(px + 8, py + 13, px + 14, py + 14, white);
        graphics.fill(px + 8, py + 7, px + 9, py + 14, white);
        graphics.fill(px + 13, py + 7, px + 14, py + 14, white);
        graphics.fill(px + 12, py + 12, px + 16, py + 13, white);
        graphics.fill(px + 15, py + 12, px + 16, py + 16, white);
    }

    /** 按钮上方常显一行小字，免得只靠图标认不出来；开着的时候换成高亮色。 */
    private static void drawLabel(GuiGraphics graphics, int x, int y, float alpha) {
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable(LABEL);
        int color = withAlpha(MachineUiInteraction.enabled() ? 0xFFE08A : 0xC8C8C8, alpha);
        int textX = Math.max(4, x + 10 - font.width(text) / 2);
        int textY = Math.max(4, y - font.lineHeight - LABEL_GAP);
        graphics.drawString(font, text, textX, textY, color, true);
    }

    private static int withAlpha(int rgb, float alpha) {
        return (Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24) | rgb;
    }

    // ===== 挂按钮与认快捷键 =====

    /**
     * 把按钮挂进屏幕的控件表。{@code Screen#addRenderableWidget} 是 protected 的泛型方法，
     * 只能反射调；实在拿不到就退回直接往 renderables / children 里塞。
     */
    private static boolean register(Screen screen, PonderButton widget) {
        Method method = addRenderableWidget();
        if (method != null) {
            try {
                method.invoke(screen, widget);
                return true;
            } catch (Throwable t) {
                BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot add the UI details button", t);
                return false;
            }
        }
        try {
            Field renderables = Screen.class.getDeclaredField("renderables");
            renderables.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Renderable> list = (List<Renderable>) renderables.get(screen);
            list.add(widget);
            @SuppressWarnings("unchecked")
            List<GuiEventListener> children = (List<GuiEventListener>) screen.children();
            children.add(widget);
            return true;
        } catch (Throwable t) {
            BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: cannot add the UI details button", t);
            return false;
        }
    }

    @Nullable
    private static Method addRenderableWidget() {
        if (addRenderableWidget == null && !addRenderableWidgetMissing) {
            try {
                Method method = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
                method.setAccessible(true);
                addRenderableWidget = method;
            } catch (Throwable t) {
                addRenderableWidgetMissing = true;
            }
        }
        return addRenderableWidget;
    }

    /** PonderButton 的 shortcut 字段是 protected，反射读一次并缓存。 */
    @Nullable
    private static KeyMapping shortcutOf(PonderButton widget) {
        Field field = shortcutField();
        if (field == null) {
            return null;
        }
        try {
            return (KeyMapping) field.get(widget);
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
        for (PonderButton other : row(ponder, button)) {
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
        return builder.toString();
    }
}
