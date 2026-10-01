// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.datagen;

import com.ctnh.gtponder.GTPonder;
import com.ctnh.gtponder.client.ponder.GTPonderPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * datagen 入口：{@code gradlew runData} 把结果写进 {@code src/generated/resources}。
 *
 * <p>思索文案不在这里手写：注册插件后交给 {@link PonderIndex#getLangAccess()}，
 * 场景里 {@code title(...)} 与 {@code .text(...)} 的英文原文会被 Ponder 按
 * {@code <modid>.ponder.<场景 id>.header|text_N} 的 key 收出来，改场景文案不用同步改 lang（{@code zh_cn} 是手写的，不走这里）。
 */
@Mod.EventBusSubscriber(modid = GTPonder.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTPonderDatagen {

    private GTPonderDatagen() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(event.includeClient(), new LanguageProvider(output, GTPonder.MODID, "en_us") {

            @Override
            protected void addTranslations() {
                // FMLClientSetupEvent 在 datagen 里不触发，插件得自己注册一遍，Ponder 才收得到场景文案。
                PonderIndex.addPlugin(new GTPonderPonderPlugin());
                PonderIndex.getLangAccess().provideLang(GTPonder.MODID, this::add);

                add("gtponder.tooltip.slot_index", "slot %s");
                add("gtponder.tooltip.tank_index", "tank %s");
            }
        });
    }
}
