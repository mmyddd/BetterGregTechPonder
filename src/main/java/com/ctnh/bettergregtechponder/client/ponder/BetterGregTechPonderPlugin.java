// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import com.ctnh.bettergregtechponder.client.ponder.scenes.ChemicalReactorUi;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** 注册本 mod 的思索场景。 */
public class BetterGregTechPonderPlugin implements PonderPlugin {

    /**
     * 示例场景的开关。默认开发环境开、正式环境关——正式 jar 里 {@link ChemicalReactorUi} 不会被注册，
     * 免得占掉使用者自己给 LV 化学反应釜写的思索。想强制打开或关掉，加 JVM 参数
     * {@code -Dbettergregtechponder.exampleScenes=true|false}。
     */
    public static final String EXAMPLE_SCENES_PROPERTY = "bettergregtechponder.exampleScenes";

    @Override
    public String getModId() {
        return BetterGregTechPonder.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        if (!exampleScenesEnabled()) {
            return;
        }
        helper.forComponents(GTMachines.CHEMICAL_REACTOR[GTValues.LV].getId())
                .addStoryBoard("chemical_reactor_ui/common", ChemicalReactorUi::common);
    }

    private static boolean exampleScenesEnabled() {
        return Boolean.parseBoolean(System.getProperty(EXAMPLE_SCENES_PROPERTY,
                Boolean.toString(!FMLEnvironment.production)));
    }
}
