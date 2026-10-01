// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.pondergtui.client.ponder.ui;

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
        element.setVisible(false);
    }

    @Override
    protected void applyFade(PonderScene scene, float fade) {
        element.setFade(fade);
    }
}
