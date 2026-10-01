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
import net.minecraftforge.fml.loading.FMLEnvironment;

/** 注册本 mod 的思索场景。 */
public class GTPonderPonderPlugin implements PonderPlugin {

    /**
     * 示例场景的开关。默认开发环境开、正式环境关——正式 jar 里 {@link InputBusUi} 不会被注册，
     * 免得把使用者的 ULV 输入总线思索顶掉。想强制打开或关掉，加 JVM 参数
     * {@code -Dgtponder.exampleScenes=true|false}。
     */
    public static final String EXAMPLE_SCENES_PROPERTY = "gtponder.exampleScenes";

    @Override
    public String getModId() {
        return GTPonder.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        if (!exampleScenesEnabled()) {
            return;
        }
        helper.forComponents(GTMachines.ITEM_IMPORT_BUS[GTValues.ULV].getId())
                .addStoryBoard("input_bus_ui/common", InputBusUi::common);
    }

    private static boolean exampleScenesEnabled() {
        return Boolean.parseBoolean(System.getProperty(EXAMPLE_SCENES_PROPERTY,
                Boolean.toString(!FMLEnvironment.production)));
    }
}
