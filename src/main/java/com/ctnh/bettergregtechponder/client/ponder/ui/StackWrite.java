// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.client.ponder.ui;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * 往机器槽位、储罐里写内容时的取空值规则。
 *
 * <p>空物品是 {@link ItemStack#EMPTY}、空流体是 {@link FluidStack#EMPTY}，而读取控件时拿到的是
 * {@code null} 或者一个空栈 —— 这个差别必须只在一个地方决定，否则「这次写入到底动没动过」的记录会跟着
 * 读取方式的细节漂移，还原时就可能把没动过的槽位也写回去。
 *
 * <p>这里的实现刻意只碰作为参数传进来的那份数据：只要参数是 null 或空，返回的就是常量
 * {@link FluidStack#EMPTY}，不主动去触类初始化，于是这段判定能被单元测试直接加载。
 */
final class StackWrite {

    private StackWrite() {}

    /** 控件读出来的空流体一律归一成 {@link FluidStack#EMPTY}，不写 null。 */
    static FluidStack normalized(FluidStack stack) {
        return stack == null || stack.isEmpty() ? FluidStack.EMPTY : stack;
    }
}
