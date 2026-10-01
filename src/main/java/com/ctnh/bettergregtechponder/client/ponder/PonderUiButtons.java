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
 * 思索界面底部的「查看 UI 详情」按钮：按下之后，那一段里画的机器面板才允许点击（放行左侧与左下的页签，
 * 以及配置器面板里的电路页签，见 {@link MachineUiInteraction}）。
 *
 * <p>按钮由 {@code PonderUIMixin} 在 {@code PonderUI.init()} 末尾挂进 PonderUI 自己的控件表，
 * 于是渲染、淡入淡出、点击派发都跟底部那一排一模一样；这里只负责：排布、图标与文字、
 * 以及把面板上的点击转给 {@link MachineUiInteraction}。
 *
 * <p>排布只动「中间那一簇」：挂得住快捷键的那些按钮（显示方块名称 / 思索结束 / 重放…），
 * 它们连新按钮一起在两侧固定的按钮之间等分；没有快捷键的边缘按钮（退出、舒适阅读）原地不动。
 */
@Mod.EventBusSubscriber(modid = BetterGregTechPonder.MODID, value = Dist.CLIENT)
public final class PonderUiButtons {

    /** 按钮的 tooltip 与按钮上方那行小字。 */
    private static final String LABEL = "bettergregtechponder.ponder.ui.ui_details";
    private static final int ICON_PAD = 2;
    private static final int LABEL_GAP = 3;

    @Nullable
    private static PonderUI screen;
    @Nullable
    private static PonderButton button;
    @Nullable
    private static Field shortcutField;
    private static boolean shortcutFieldMissing;
    @Nullable
    private static Method addRenderableWidget;
    private static boolean addRenderableWidgetMissing;
    private static boolean logged;

    private PonderUiButtons() {}

    /** 事件这条路：屏幕初始化完成后挂按钮。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PonderUI ponder) {
            attach(ponder);
        } else {
            screen = null;
            button = null;
        }
    }

    /** 事件这条路：场景渲染前清落点、排布。 */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof PonderUI ponder) {
            beforeRender(ponder);
        }
    }

    /** 事件这条路：渲染完补图标、文字与 tooltip。 */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof PonderUI ponder) {
            afterRender(ponder, event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    /** 事件这条路：点击。面板上的点击与按钮自身都在这里处理掉并取消事件。 */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof PonderUI ponder)) {
            return;
        }
        attach(ponder);
        if (clickPanel(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (button != null && button.visible && button.isMouseOver(event.getMouseX(), event.getMouseY()) &&
                button.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    /** 事件这条路：让按钮的亮/暗动画跟着走。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick();
        }
    }

    /**
     * 建按钮并挂进控件表。mixin 与事件两条路都会调，所以按屏幕去重：同一屏只挂一次。
     *
     * <p>两条路都留着是因为 dev 环境下 mixin 配置是否被 ModLauncher 收下取决于构建插件，
     * 事件这条路不依赖它；反过来 mixin 又不受事件总线注册影响。谁先到谁挂，另一个空转。
     */
    public static void attach(PonderUI ponder) {
        if (screen == ponder && button != null) {
            return;
        }
        screen = ponder;
        button = new PonderButton(0, 0).withCallback(PonderUiButtons::toggle);
        button.dim();
        MachineUiInteraction.setEnabled(false);
        logged = false;
        if (!register(ponder, button)) {
            button = null;
            return;
        }
        BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: UI details button attached to a ponder screen " +
                "({}x{})", ponder.width, ponder.height);
        layout(ponder, button);
    }

    /** 事件那条路进来时用：先确保这一屏挂上了按钮。 */
    public static void attachIfNeeded(PonderUI ponder) {
        attach(ponder);
    }

    /** 每帧渲染前：清一次面板落点登记，并把按钮摆到当前这一排里。 */
    public static void beforeRender(PonderUI ponder) {
        MachineUiInteraction.beginFrame();
        if (button == null) {
            return;
        }
        button.visible = MachineUiInteraction.hasPanel();
        if (button.visible) {
            layout(ponder, button);
        }
    }

    /** 每帧渲染后：图标、那行小字与 tooltip 都自己画（Ponder 只认得它自己那几个按钮）。 */
    public static void afterRender(PonderUI ponder, GuiGraphics graphics, int mouseX, int mouseY) {
        if (button == null || !button.visible) {
            return;
        }
        drawIcon(graphics, button.getX(), button.getY());
        drawLabel(graphics, ponder, button);
        if (!logged) {
            logged = true;
            BetterGregTechPonder.LOGGER.info("BetterGregTechPonder: UI details button at {}x{} (screen {}x{}), " +
                    "bar: {}", button.getX(), button.getY(), ponder.width, ponder.height, describe(ponder));
        }
        if (button.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(Minecraft.getInstance().font,
                    Component.translatable(LABEL).withStyle(ChatFormatting.GRAY), mouseX, mouseY);
        }
    }

    /** 面板上的点击：只放行页签与配置器里的电路页签，命中了就吃掉这次点击。 */
    public static boolean clickPanel(double mouseX, double mouseY, int mouseButton) {
        return MachineUiInteraction.click(mouseX, mouseY, mouseButton);
    }

    public static void tick() {
        if (button != null) {
            button.tick();
        }
    }

    /** 开关这个模式，顺带把按钮的亮/暗切过去。 */
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

    /**
     * 把按钮挂进屏幕的控件表。{@code Screen#addRenderableWidget} 是 protected 的泛型方法，
     * 只能反射调；实在拿不到就退回直接往 renderables / children 里塞，至少不会静默失效。
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

    /** 图标自己画：一块面板 + 右下角放大镜（Ponder 的按钮是 20×20，图标 16×16 居中）。 */
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

    /** 按钮上方常显一行小字，免得只靠图标认不出来；开着的时候换成高亮色。 */
    private static void drawLabel(GuiGraphics graphics, PonderUI ponder, PonderButton widget) {
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable(LABEL);
        int color = MachineUiInteraction.enabled() ? 0xFFFFE08A : 0xFFC8C8C8;
        int x = widget.getX() + widget.getWidth() / 2 - font.width(text) / 2;
        int y = Math.max(4, widget.getY() - font.lineHeight - LABEL_GAP);
        graphics.drawString(font, text, Math.max(4, x), y, color, true);
    }

    /**
     * 只排「中间那一簇」：可见、并且挂得住快捷键的按钮。没有快捷键的边缘按钮原地不动，
     * 但把它们之间的空间让给这一簇；新按钮插在「显示方块名称」（绑 drop 键那个）右边。
     */
    private static void layout(PonderUI ponder, PonderButton ours) {
        List<PonderButton> row = new ArrayList<>();
        for (GuiEventListener listener : ponder.children()) {
            if (listener instanceof PonderButton other && other != ours) {
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
        for (GuiEventListener listener : ponder.children()) {
            if (listener instanceof PonderButton other) {
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
