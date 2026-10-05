// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

/**
 * 「一帧只处理一次」的去重标记。
 *
 * <p>{@code PonderUiButtons.beforeRender} 有两条进入路径：{@code PonderUIMixin} 的渲染注入与 Forge 的
 * {@code ScreenEvent.Render.Pre}。两条都会先调它，而第一次调用读到的才是上一帧的真实登记 —— 第二次读到的
 * 已经被自己清空，会把状态全判成「没有 UI」。这个类就是那个「只认第一次」的判断本身。
 *
 * <p>单独拿出来是为了能被单元测试直接加载：{@code PonderUiButtons} 一加载就会牵扯 Minecraft 与 Ponder 的
 * 类，纯单元测试跑不起来。这个类的语义（第一次真、同一帧内后续都假、收尾后重新放行）由
 * {@code RedundancyGuardTest} 钉住。
 */
final class FrameGuard {

    private static boolean handled;

    private FrameGuard() {}

    /** 这一帧是否已经处理过；返回 true 表示本次就是第一次。 */
    static boolean handled() {
        if (handled) {
            return false;
        }
        handled = true;
        return true;
    }

    /** 一帧收尾：允许下一帧重新处理。 */
    static void endFrame() {
        handled = false;
    }
}
