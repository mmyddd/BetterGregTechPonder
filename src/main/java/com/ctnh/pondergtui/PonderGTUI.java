// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.pondergtui;

import net.minecraftforge.fml.common.Mod;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 入口只声明 mod 身份。界面绘制相关的类都在 {@code client.ponder} 下，只由客户端代码触碰，
 * 服务端加载本 mod 时不会去加载它们。
 */
@Mod(PonderGTUI.MODID)
public class PonderGTUI {

    public static final String MODID = "pondergtui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PonderGTUI() {}
}
