// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.datagen;

import com.ctnh.gtponder.GTPonder;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** datagen 入口：{@code gradlew runData} 把结果写进 {@code src/generated/resources}。 */
@Mod.EventBusSubscriber(modid = GTPonder.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTPonderDatagen {

    private GTPonderDatagen() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(event.includeClient(), new GTPonderLang(output, "en_us"));
        generator.addProvider(event.includeClient(), new GTPonderLang(output, "zh_cn"));
    }
}
