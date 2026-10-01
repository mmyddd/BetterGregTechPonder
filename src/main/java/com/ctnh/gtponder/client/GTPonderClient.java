// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client;

import com.ctnh.gtponder.GTPonder;
import com.ctnh.gtponder.client.ponder.GTPonderPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 客户端入口：把场景插件交给 Ponder。 */
@Mod.EventBusSubscriber(modid = GTPonder.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTPonderClient {

    private GTPonderClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new GTPonderPonderPlugin()));
    }
}
