// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import com.ctnh.gtponder.GTPonder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * 按配方 id 把面板填起来：入料进输入槽、流体进输入储罐；进度条走完之后，成品落进输出槽与输出储罐。
 *
 * <p>哪些槽位、储罐是输入，哪些是输出，看 GT 自己打的 {@link IngredientIO} 标签（GT 给 EMI 传配方
 * 用的也是这一套），所以不用猜槽位顺序。
 *
 * <p>机器与配方对不上——不是配方机器、配方 id 不存在、配方类型不属于这台机器、面板里没有对应的槽位——
 * 就在日志里报一行 error，这一段跳过，面板照常画。
 */
final class RecipeFiller {

    /** 进度条走满的时长：1 秒。 */
    private static final int PROGRESS_TICKS = 20;

    private RecipeFiller() {}

    /**
     * 把配方摊进写入时间线，并把面板里的进度条接到 {@code progress} 上；摊不动（校验不过）就什么都不写。
     */
    static void fill(MachineUiPanel panel, MachineUiPlacement.RecipeFill fill, MachineUiWrites writes,
                     BlockPos machinePos, DoubleSupplier progress) {
        if (!(panel.machine() instanceof IRecipeLogicMachine recipeMachine)) {
            error(machinePos, fill, "this machine is not a recipe machine");
            return;
        }
        GTRecipe recipe = find(recipeMachine, fill, machinePos);
        if (recipe == null) {
            return;
        }
        List<Integer> inputSlots = slotIndexes(panel.machineSlots(), IngredientIO.INPUT);
        List<Integer> outputSlots = slotIndexes(panel.machineSlots(), IngredientIO.OUTPUT);
        List<Integer> inputTanks = tankIndexes(panel.machineTanks(), IngredientIO.INPUT);
        List<Integer> outputTanks = tankIndexes(panel.machineTanks(), IngredientIO.OUTPUT);
        List<ItemStack> itemsIn = RecipeHelper.getInputItems(recipe);
        List<ItemStack> itemsOut = RecipeHelper.getOutputItems(recipe);
        List<FluidStack> fluidsIn = RecipeHelper.getInputFluids(recipe);
        List<FluidStack> fluidsOut = RecipeHelper.getOutputFluids(recipe);
        if (itemsIn.size() > inputSlots.size() || itemsOut.size() > outputSlots.size() ||
                fluidsIn.size() > inputTanks.size() || fluidsOut.size() > outputTanks.size()) {
            error(machinePos, fill, "the panel has " + inputSlots.size() + " in / " + outputSlots.size() +
                    " out slot(s) and " + inputTanks.size() + " in / " + outputTanks.size() +
                    " out tank(s), the recipe needs " + itemsIn.size() + " in / " + itemsOut.size() +
                    " out item(s) and " + fluidsIn.size() + " in / " + fluidsOut.size() + " out fluid(s)");
            return;
        }

        int base = fill.delayTicks();
        int products = base + MachineUiWrites.FILL_TICKS + PROGRESS_TICKS;
        for (int i = 0; i < itemsIn.size(); i++) {
            writes.addSlot(inputSlots.get(i), itemsIn.get(i), base);
        }
        for (int i = 0; i < fluidsIn.size(); i++) {
            writes.addTank(inputTanks.get(i), fluidsIn.get(i), base);
        }
        for (int i = 0; i < itemsOut.size(); i++) {
            writes.addSlot(outputSlots.get(i), itemsOut.get(i), products);
        }
        for (int i = 0; i < fluidsOut.size(); i++) {
            writes.addTank(outputTanks.get(i), fluidsOut.get(i), products);
        }
        // 面板里的进度条改成这条时间线：入料结束后从 0 走到 1。
        panel.progressWidgets().forEach(widget -> widget.setProgressSupplier(progress));
        GTPonder.LOGGER.info("GTPonder: filled the machine at {} with recipe {} ({} item in, {} item out, " +
                "{} fluid in, {} fluid out)", machinePos, fill.recipeId(), itemsIn.size(), itemsOut.size(),
                fluidsIn.size(), fluidsOut.size());
    }

    /**
     * 进度条位置：入料结束那一刻从 0 开始，走满 {@link #PROGRESS_TICKS} 个 tick 到 1 就停住。
     * 槽位与进度条同时开始、一起走完，所以直接按摆放的 tick 数算。
     */
    static double progress(MachineUiPlacement.RecipeFill fill, int ticksShown) {
        int elapsed = ticksShown - (fill.delayTicks() + MachineUiWrites.FILL_TICKS);
        return elapsed <= 0 ? 0 : Math.min(1, elapsed / (double) PROGRESS_TICKS);
    }

    /** 按 id 找配方，并确认它就是这台机器的配方。 */
    private static @Nullable GTRecipe find(IRecipeLogicMachine machine, MachineUiPlacement.RecipeFill fill,
                                           BlockPos machinePos) {
        ResourceLocation key = ResourceLocation.tryParse(fill.recipeId());
        if (key == null) {
            error(machinePos, fill, "it is not a valid resource location");
            return null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        RecipeManager manager = level == null ? null : level.getRecipeManager();
        if (manager == null) {
            error(machinePos, fill, "no recipe manager, is a world loaded?");
            return null;
        }
        Recipe<?> recipe = manager.byKey(key).orElse(null);
        if (!(recipe instanceof GTRecipe gtRecipe)) {
            error(machinePos, fill, "there is no GT recipe with this id");
            return null;
        }
        for (GTRecipeType type : machine.getRecipeTypes()) {
            if (type == gtRecipe.getType()) {
                return gtRecipe;
            }
        }
        error(machinePos, fill, "this recipe is for " + gtRecipe.getType().registryName + ", not for this machine");
        return null;
    }

    /** 面板里打了 Input/Output 标签的槽位下标，顺序就是控件的排列顺序。 */
    private static List<Integer> slotIndexes(List<SlotWidget> slots, IngredientIO io) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (ingredientIO(slots.get(i)) == io) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static List<Integer> tankIndexes(List<Widget> tanks, IngredientIO io) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < tanks.size(); i++) {
            if (ingredientIO(tanks.get(i)) == io) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static IngredientIO ingredientIO(Widget widget) {
        return widget instanceof IRecipeIngredientSlot slot ? slot.getIngredientIO() : IngredientIO.RENDER_ONLY;
    }

    private static void error(BlockPos machinePos, MachineUiPlacement.RecipeFill fill, String reason) {
        GTPonder.LOGGER.error("GTPonder: cannot fill the machine at {} with recipe {}: {}", machinePos,
                fill.recipeId(), reason);
    }
}
