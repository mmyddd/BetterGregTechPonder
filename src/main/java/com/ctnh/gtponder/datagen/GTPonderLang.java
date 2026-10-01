// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.datagen;

import com.ctnh.gtponder.GTPonder;
import com.ctnh.gtponder.client.ponder.scenes.ChemicalReactorUi;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/**
 * 生成 {@code assets/gtponder/lang/*.json}。思索场景的 key 是
 * {@code <modid>.ponder.<场景 id>.<header|title|text_N>}，英文正文放在 {@link ChemicalReactorUi} 里，
 * 这里补中文。
 */
public class GTPonderLang extends LanguageProvider {

    private final boolean chinese;

    public GTPonderLang(PackOutput output, String locale) {
        super(output, GTPonder.MODID, locale);
        this.chinese = "zh_cn".equals(locale);
    }

    @Override
    protected void addTranslations() {
        add("gtponder.tooltip.slot_index", chinese ? "槽位 %s" : "slot %s");
        add("gtponder.tooltip.tank_index", chinese ? "储罐 %s" : "tank %s");

        String scene = GTPonder.MODID + ".ponder." + ChemicalReactorUi.SCENE_ID;
        add(scene + ".header", chinese ? "LV 化学反应釜" : "LV Chemical Reactor");
        add(scene + ".title", chinese ? "化学反应釜界面" : ChemicalReactorUi.TITLE);
        add(scene + ".text_1", chinese ? "面板就是这台机器自己的界面，不含玩家背包。" : ChemicalReactorUi.TEXT_1);
        add(scene + ".text_2",
                chinese ? "64 个草方块进 1 号槽位、64 个玻璃进 2 号槽位，面板显示的就是机器真实库存。" :
                        ChemicalReactorUi.TEXT_2);
        add(scene + ".text_3", chinese ? "流体同理：0 号储罐在 1 秒内从 0 灌到 1000 mB。" : ChemicalReactorUi.TEXT_3);
        add(scene + ".text_4", chinese ? "覆盖板也能指定面：顶面放一条传送带。" : ChemicalReactorUi.TEXT_4);
    }
}
