// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import java.util.List;

/**
 * 底部那一排按钮的锚点判定：不贴左右边缘、又落在左半边的第一个按钮，就是「显示方块名称」。
 *
 * <p>判定只看 X 坐标，绝不读 {@code PonderButton.shortcut} 之类的字段 —— 最左边那个「退出」贴着屏幕边缘，
 * 而且它在有些段落里会变成不可见，所以不能简单地把「最左」去掉。
 *
 * <p>这里接收的是「已经按 X 升序排好、且已经滤掉不可见按钮」的坐标列表，所以不牵扯任何 Ponder 类型，
 * 可以被纯单元测试直接加载（见 {@code RedundancyGuardTest}）。
 */
final class ButtonRow {

    private ButtonRow() {}

    /**
     * 在已排序的候选按钮里选锚点。
     *
     * @param xs          候选按钮的 X 坐标，按升序；null 或空表示这一排没有可用按钮
     * @param screenWidth 屏幕宽度
     * @param edgeFraction 屏幕宽度在这个比例以内的按钮算「边缘按钮」，不当锚点
     * @return 选中的下标；没有可用的锚点返回 -1
     */
    static int anchor(List<Integer> xs, int screenWidth, float edgeFraction) {
        if (xs == null || xs.isEmpty()) {
            return -1;
        }
        int edge = Math.max(1, Math.round(screenWidth * edgeFraction));
        int half = screenWidth / 2;
        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i) >= edge && xs.get(i) < half) {
                return i;
            }
        }
        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i) < half) {
                return i;
            }
        }
        return -1;
    }
}
