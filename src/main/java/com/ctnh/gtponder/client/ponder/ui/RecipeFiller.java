// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.gtponder.client.ponder.ui;

import com.ctnh.gtponder.GTPonder;
import com.ctnh.gtponder.client.ponder.machine.WorkingModelChange;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

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
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 一次配方摆放的全部动作：入料进输入槽、流体进输入储罐；进度条走起来就把机器模型切成工作中的样子，
 * 走完立刻切回待机，成品同时落进输出槽与输出储罐。
 *
 * <p>哪些槽位、储罐是输入，哪些是输出，看 GT 自己打的 {@link IngredientIO} 标签（GT 给 EMI 传配方
 * 用的也是这一套），所以不用猜槽位顺序。
 *
 * <p>机器与配方对不上——不是配方机器、配方 id 不存在、配方类型不属于这台机器、面板里没有对应的槽位——
 * 就在日志里报一行 error，这一段跳过，面板照常画，机器也不动。
 */
final class RecipeFiller {

    /** 进度条走满的时长：1 秒。 */
    private static final int PROGRESS_TICKS = 20;

    private final MachineUiPlacement.RecipeFill fill;
    private final BlockPos machinePos;
    /** 进度条开始时的开机、走完后的关机；两个都记着改之前的样子，还原时按相反顺序退回去。 */
    private final WorkingModelChange running = new WorkingModelChange(true);
    private final WorkingModelChange idle = new WorkingModelChange(false);
    private boolean planned;
    private boolean runningApplied;
    private boolean idleApplied;
    private double progressValue;
    /** 配方要的编程电路：进机器的电路槽，不占输入槽。 */
    private ItemStack circuit = ItemStack.EMPTY;
    private boolean circuitApplied;
    private ItemStack previousCircuit = ItemStack.EMPTY;

    /**
     * 这张配方要不要编程电路。面板出现前就得知道——要的话连电路 UI 一起画出来——所以单独问一次，
     * 这里只看配方本身，机器对不对得上交给 {@link #plan} 那一步报错。
     */
    static boolean needsCircuit(@Nullable MachineUiPlacement.RecipeFill fill) {
        if (fill == null) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(fill.recipeId());
        ClientLevel level = Minecraft.getInstance().level;
        RecipeManager manager = key == null || level == null ? null : level.getRecipeManager();
        Recipe<?> found = manager == null ? null : manager.byKey(key).orElse(null);
        if (!(found instanceof GTRecipe recipe)) {
            return false;
        }
        for (ItemStack stack : RecipeHelper.getInputItems(recipe)) {
            if (IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                return true;
            }
        }
        return false;
    }

    RecipeFiller(MachineUiPlacement.RecipeFill fill, BlockPos machinePos) {
        this.fill = fill;
        this.machinePos = machinePos;
    }

    /** 校验配方并生成入料/成品写入，同时把面板里的进度条接到自己身上。 */
    void plan(MachineUiPanel panel, MachineUiWrites writes) {
        planned = false;
        runningApplied = false;
        idleApplied = false;
        circuitApplied = false;
        progressValue = 0;
        circuit = ItemStack.EMPTY;
        previousCircuit = ItemStack.EMPTY;
        if (!(panel.machine() instanceof IRecipeLogicMachine recipeMachine)) {
            error("this machine is not a recipe machine");
            return;
        }
        GTRecipe recipe = find(recipeMachine);
        if (recipe == null) {
            return;
        }
        List<Integer> inputSlots = slotIndexes(panel.machineSlots(), IngredientIO.INPUT);
        List<Integer> outputSlots = slotIndexes(panel.machineSlots(), IngredientIO.OUTPUT);
        List<Integer> inputTanks = tankIndexes(panel.machineTanks(), IngredientIO.INPUT);
        List<Integer> outputTanks = tankIndexes(panel.machineTanks(), IngredientIO.OUTPUT);
        List<ItemStack> itemsIn = new ArrayList<>(RecipeHelper.getInputItems(recipe));
        // 配方里的编程电路摘出来：它进机器的电路槽，不占输入槽。
        circuit = takeCircuit(itemsIn);
        if (!circuit.isEmpty() && !(panel.machine() instanceof IHasCircuitSlot)) {
            error("the recipe needs circuit " + IntCircuitBehaviour.getCircuitConfiguration(circuit) +
                    " but this machine has no circuit slot");
        }
        List<ItemStack> itemsOut = RecipeHelper.getOutputItems(recipe);
        List<FluidStack> fluidsIn = RecipeHelper.getInputFluids(recipe);
        List<FluidStack> fluidsOut = RecipeHelper.getOutputFluids(recipe);
        if (itemsIn.size() > inputSlots.size() || itemsOut.size() > outputSlots.size() ||
                fluidsIn.size() > inputTanks.size() || fluidsOut.size() > outputTanks.size()) {
            error("the panel has " + inputSlots.size() + " in / " + outputSlots.size() + " out slot(s) and " +
                    inputTanks.size() + " in / " + outputTanks.size() + " out tank(s), the recipe needs " +
                    itemsIn.size() + " in / " + itemsOut.size() + " out item(s) and " +
                    fluidsIn.size() + " in / " + fluidsOut.size() + " out fluid(s)");
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
        panel.progressWidgets().forEach(widget -> widget.setProgressSupplier(this::progress));
        planned = true;
        GTPonder.LOGGER.info("GTPonder: filled the machine at {} with recipe {} ({} item in, {} item out, " +
                "{} fluid in, {} fluid out)", machinePos, fill.recipeId(), itemsIn.size(), itemsOut.size(),
                fluidsIn.size(), fluidsOut.size());
    }

    /**
     * 每 tick 一次：进度条从入料结束那一刻开始走；它一开始走就把机器模型切成工作中的样子，
     * 走到头立刻切回待机。
     *
     * @return 这一 tick 是否切了模型——切了就得让场景重画一次，不然画面还是缓存里那张
     */
    boolean tick(MachineUiPanel panel, int ticksShown) {
        if (!planned) {
            return false;
        }
        int start = fill.delayTicks() + MachineUiWrites.FILL_TICKS;
        int finish = start + PROGRESS_TICKS;
        progressValue = ticksShown <= start ? 0 : Math.min(1, (ticksShown - start) / (double) PROGRESS_TICKS);
        boolean switched = false;
        if (!circuit.isEmpty() && ticksShown >= fill.delayTicks() && !circuitApplied) {
            circuitApplied = true;
            applyCircuit(panel);
        }
        if (ticksShown >= start && !runningApplied) {
            runningApplied = true;
            running.apply(panel.machine(), machinePos);
            switched = true;
        }
        if (ticksShown >= finish && !idleApplied) {
            idleApplied = true;
            idle.apply(panel.machine(), machinePos);
            switched = true;
        }
        return switched;
    }

    /** 面板收起或场景回退：进度条停下，电路槽与机器模型退回原来的样子。 */
    void revert(MachineUiPanel panel) {
        if (panel == null) {
            return;
        }
        if (circuitApplied) {
            circuitApplied = false;
            if (panel.machine() instanceof IHasCircuitSlot holder) {
                try {
                    holder.getCircuitInventory().storage.setStackInSlot(0, previousCircuit);
                } catch (Throwable t) {
                    GTPonder.LOGGER.error("GTPonder: restoring the circuit of the machine at {} threw", machinePos,
                            t);
                }
            }
            previousCircuit = ItemStack.EMPTY;
        }
        if (idleApplied) {
            idleApplied = false;
            idle.revert(panel.machine(), machinePos);
        }
        if (runningApplied) {
            runningApplied = false;
            running.revert(panel.machine(), machinePos);
        }
    }

    /** 把配方要的电路写进机器的电路槽；面板下方那组编码设置 UI 读的就是这个槽。 */
    private void applyCircuit(MachineUiPanel panel) {
        if (!(panel.machine() instanceof IHasCircuitSlot holder)) {
            return;
        }
        try {
            ItemStackHandler slot = holder.getCircuitInventory().storage;
            previousCircuit = slot.getStackInSlot(0).copy();
            slot.setStackInSlot(0, circuit.copy());
        } catch (Throwable t) {
            GTPonder.LOGGER.error("GTPonder: setting the circuit of the machine at {} threw", machinePos, t);
        }
    }

    /** 把配方的编程电路从物品输入里摘出来；一张配方最多一个电路。 */
    private static ItemStack takeCircuit(List<ItemStack> items) {
        for (Iterator<ItemStack> iterator = items.iterator(); iterator.hasNext(); ) {
            ItemStack stack = iterator.next();
            if (IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                iterator.remove();
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    /** 进度条位置，给面板里的控件每帧问一次。 */
    private double progress() {
        return progressValue;
    }

    /** 按 id 找配方，并确认它就是这台机器的配方。 */
    private @Nullable GTRecipe find(IRecipeLogicMachine machine) {
        ResourceLocation key = ResourceLocation.tryParse(fill.recipeId());
        if (key == null) {
            error("it is not a valid resource location");
            return null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        RecipeManager manager = level == null ? null : level.getRecipeManager();
        if (manager == null) {
            error("no recipe manager, is a world loaded?");
            return null;
        }
        Recipe<?> recipe = manager.byKey(key).orElse(null);
        if (!(recipe instanceof GTRecipe gtRecipe)) {
            error("there is no GT recipe with this id");
            return null;
        }
        for (GTRecipeType type : machine.getRecipeTypes()) {
            if (type == gtRecipe.getType()) {
                return gtRecipe;
            }
        }
        error("this recipe is for " + gtRecipe.getType().registryName + ", not for this machine");
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

    private void error(String reason) {
        GTPonder.LOGGER.error("GTPonder: cannot fill the machine at {} with recipe {}: {}", machinePos,
                fill.recipeId(), reason);
    }
}
