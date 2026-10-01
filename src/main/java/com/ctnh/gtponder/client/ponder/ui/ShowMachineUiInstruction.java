// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.FadeInOutInstruction;

/** 与 {@code ShowInputInstruction} 同构：展示时把元素挂进场景，隐藏时只是不再可见。 */
public class ShowMachineUiInstruction extends FadeInOutInstruction {

    private final MachineUiElement element;

    public ShowMachineUiInstruction(MachineUiElement element, int ticks) {
        super(ticks);
        this.element = element;
    }

    @Override
    protected void show(PonderScene scene) {
        scene.addElement(element);
        element.setVisible(true);
    }

    @Override
    protected void hide(PonderScene scene) {
        // 面板演完就把机器恢复原状：每段 showUI 只负责自己那一段，写入不带进下一段。
        element.restoreMachine();
        element.setVisible(false);
    }

    @Override
    protected void applyFade(PonderScene scene, float fade) {
        element.setFade(fade);
    }
}
