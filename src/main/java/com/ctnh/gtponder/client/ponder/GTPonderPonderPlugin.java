// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder;

import com.ctnh.gtponder.GTPonder;
import com.ctnh.gtponder.client.ponder.scenes.InputBusUi;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** 注册本 mod 的思索场景。 */
public class GTPonderPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return GTPonder.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(GTMachines.ITEM_IMPORT_BUS[GTValues.ULV].getId())
                .addStoryBoard("input_bus_ui/common", InputBusUi::common);
    }
}
