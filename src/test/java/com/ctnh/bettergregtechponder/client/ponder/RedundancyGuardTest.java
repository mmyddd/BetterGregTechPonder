// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住这次「去冗余」动到的判定逻辑。这些测试刻意不碰 Minecraft / Ponder 的任何类，
 * 所以在没有游戏运行时的环境里也能跑。
 */
class RedundancyGuardTest {

    /** 与 PonderUiButtons.EDGE_FRACTION 一致。 */
    private static final float EDGE_FRACTION = 0.12f;

    /**
     * {@code PonderUiButtons.beforeRender} 每帧会被 mixin 与 Forge 事件各调一次：第一次读到的才是上一帧
     * 的真实面板登记，第二次读到的已经被自己清空 —— 那会把状态全判成「没有 UI」，模式刚点开就被关掉。
     */
    @Nested
    @DisplayName("一帧只处理一次")
    class FrameGuardOnce {

        @AfterEach
        void releaseFrame() {
            // 静态标记不能在测试之间串味。
            FrameGuard.endFrame();
        }

        @Test
        void firstCallHandlesTheFrame() {
            assertTrue(FrameGuard.handled());
        }

        @Test
        void secondCallInTheSameFrameIsIgnored() {
            assertTrue(FrameGuard.handled());
            assertFalse(FrameGuard.handled());
            assertFalse(FrameGuard.handled());
        }

        @Test
        void nextFrameIsHandledAgain() {
            assertTrue(FrameGuard.handled());
            FrameGuard.endFrame();
            assertTrue(FrameGuard.handled());
        }
    }

    /**
     * 锚点判定只看坐标（不读 shortcut 字段），而且必须落在左半边 —— 落到右边就会在「思索结束」「重放」
     * 身上叠一个按钮；最左边贴边的「退出」也不能当锚点。
     */
    @Nested
    @DisplayName("底部按钮排布的锚点")
    class RowAnchor {

        @Test
        void skipsEdgeButtonsAndPicksTheFirstNonEdgeOne() {
            List<Integer> xs = Arrays.asList(10, 120, 170, 330);
            assertEquals(1, ButtonRow.anchor(xs, 400, EDGE_FRACTION));
        }

        @Test
        void fallsBackToTheLeftmostWhenEveryButtonHugsTheEdge() {
            List<Integer> xs = Arrays.asList(10, 20, 30);
            assertEquals(0, ButtonRow.anchor(xs, 400, EDGE_FRACTION));
        }

        @Test
        void refusesToAnchorOnTheRightHalf() {
            List<Integer> xs = Arrays.asList(260, 330);
            assertEquals(-1, ButtonRow.anchor(xs, 400, EDGE_FRACTION));
        }

        @Test
        void emptyRowHasNoAnchor() {
            assertEquals(-1, ButtonRow.anchor(List.of(), 400, EDGE_FRACTION));
            assertEquals(-1, ButtonRow.anchor(null, 400, EDGE_FRACTION));
        }

        @Test
        void edgeFloorIsOnePixelEvenOnTinyScreens() {
            List<Integer> xs = Arrays.asList(0, 2);
            assertEquals(1, ButtonRow.anchor(xs, 8, EDGE_FRACTION));
        }
    }
}
