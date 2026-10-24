// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd
package com.ctnh.bettergregtechponder.client.ponder.ui;

import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.IToolGridHighlight;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.util.RenderUtil;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.enums.PonderSpecialTextures;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.ctnh.bettergregtechponder.BetterGregTechPonder;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GT 的九宫格工具覆盖层：手持工具对准方块时，该面上浮出 3x3 格子，格子里是「点这里会做什么」的图标。
 *
 * <p>
 * 几何照抄 {@code BlockHighlightRenderer#drawGridOverlays}：面外 0.01 处铺一块 16x16 画布，
 * 分界线落在 texel 4 与 12。图标由方块自己给（{@link IToolGridHighlight#sideTips}），
 * 机器上的盖子与自动输出口都照实反映。
 *
 * <p>
 * 铺不铺格子也由方块说了算（{@link IToolGridHighlight#shouldRenderGrid}）：扳手铺九宫格，
 * 软锤这类整面只有一个动作的工具只在面中央放一个图标。
 */
public class ToolGridElement extends AnimatedSceneElementBase {

    private static final float MIN_FADE = 1 / 16f;
    private static final float FACE_OFFSET = 0.01f;
    /** 分界线位置（texel）：4 与 12 把 16 切成 4/8/4。 */
    private static final float[] DIVISIONS = { 0, 4, 12, 16 };
    /** 线宽（texel）：实机是 1px 的 GL 线，这里只能用有宽度的面片。 */
    private static final float LINE_WIDTH = 0.6f;
    /** 不铺格子时那个图标的边长与左上角（texel），对应实机 8x8 居中。 */
    private static final float SINGLE_ICON_SIZE = 8f;
    private static final float SINGLE_ICON_AT = 4f;
    /**
     * 网格线固定用这支蓝。实机的 r/g 随 sin 在 0.2~0.7 之间跳，回放里会整面闪；
     * 取区间中点，就是实机那条稳定的蓝线。
     */
    private static final float GRID_RED = 0.45f;
    private static final float GRID_GREEN = 0.45f;
    private static final float GRID_BLUE = 1f;
    private static final float ICON_ALPHA_FOCUS = 1f;
    private static final float ICON_ALPHA_DIM = 0x44 / 255f;

    /** 九格对应的方块方向，行优先、从面外侧看：四角是背面，四边是相邻面，正中是这一面。 */
    private static final RelativeDirection[] CELL_DIRECTION = {
            RelativeDirection.BACK, RelativeDirection.UP, RelativeDirection.BACK,
            RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.RIGHT,
            RelativeDirection.BACK, RelativeDirection.DOWN, RelativeDirection.BACK,
    };

    private final BlockPos pos;
    private final Direction face;
    private final GTToolType tool;
    private final boolean tips;
    private final List<ToolGridPlacement.Highlight> highlights;
    private final ResourceTexture[] sideTips = new ResourceTexture[6];

    /** 这一次画不画格子。 */
    private boolean grid = true;
    private int ticksShown;
    private boolean reportedTips;
    private boolean reportedNoCells;

    ToolGridElement(ToolGridPlacement.Plan plan) {
        this.pos = plan.pos();
        this.face = plan.face();
        this.tool = plan.tool();
        this.tips = plan.tips();
        this.highlights = plan.highlights();
    }

    /**
     * 面中心 + 转到该面朝向 + 缩到 texel，此后 +x 朝右、+y 朝上，与实机的图标坐标一致。
     *
     * <p>
     * {@code spin} 是画布绕面法线的转角：实机九宫格一律以 SOUTH 立画布，「整面只有一个图标」
     * 那条支路在水平面上用 NORTH，两者图标朝向差 180 度，照抄摆放才与实机一致。
     */
    private void applyCanvas(PoseStack ms, Direction spin) {
        ms.translate(face.getStepX() * FACE_OFFSET, face.getStepY() * FACE_OFFSET, face.getStepZ() * FACE_OFFSET);
        RenderUtil.moveToFace(ms, new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f), face);
        RenderUtil.rotateToFace(ms, face, spin);
        ms.scale(1 / 16f, 1 / 16f, 0);
        ms.translate(-8, -8, 0);
    }

    @Override
    public void reset(PonderScene scene) {
        ticksShown = 0;
    }

    @Override
    public void tick(PonderScene scene) {
        ticksShown++;
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade,
                              float partialTicks) {
        if (fade < MIN_FADE) {
            return;
        }
        boolean hasTips = refreshTips(world);
        PoseStack ms = graphics.pose();
        ms.pushPose();
        applyCanvas(ms, grid || face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.NORTH);
        PoseStack.Pose pose = ms.last();
        try {
            if (grid) {
                drawGrid(buffer, pose, fade);
                if (hasTips) {
                    drawCellTips(buffer, pose, fade);
                }
            } else if (hasTips) {
                drawFaceTip(buffer, pose, fade);
            }
        } catch (Throwable t) {
            BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: drawing the tool grid on {} at {} threw", face, pos, t);
        }
        ms.popPose();
    }

    private void drawGrid(MultiBufferSource buffer, PoseStack.Pose pose, float fade) {
        VertexConsumer consumer = buffer.getBuffer(lineType());
        float half = LINE_WIDTH / 2;
        for (int i = 1; i < DIVISIONS.length - 1; i++) {
            float at = DIVISIONS[i];
            quad(consumer, pose, at - half, 0, at + half, 16, null, GRID_RED, GRID_GREEN, GRID_BLUE, fade);
            quad(consumer, pose, 0, at - half, 16, at + half, null, GRID_RED, GRID_GREEN, GRID_BLUE, fade);
        }
    }

    /** 九宫格的九个图标：被点名的格子画满，其余压暗。 */
    private void drawCellTips(MultiBufferSource buffer, PoseStack.Pose pose, float fade) {
        // 按贴图分组，换渲染类型会刷出上一批顶点，否则九个图标要画九次。
        Map<ResourceLocation, List<Integer>> byTexture = new LinkedHashMap<>();
        for (int cell = 0; cell < CELL_DIRECTION.length; cell++) {
            ResourceTexture texture = tip(cell);
            if (texture != null) {
                byTexture.computeIfAbsent(texture.imageLocation, key -> new ArrayList<>())
                        .add(cell);
            }
        }
        for (Map.Entry<ResourceLocation, List<Integer>> entry : byTexture.entrySet()) {
            VertexConsumer consumer = buffer.getBuffer(PonderRenderTypes.outlineTranslucent(entry.getKey(), false));
            for (int cell : entry.getValue()) {
                float[] origin = new float[2];
                iconOrigin(cell, origin);
                quad(consumer, pose, origin[0], origin[1], origin[0] + 4, origin[1] + 4, tip(cell), 1f, 1f, 1f,
                        fade * cellAlpha(cell));
            }
        }
    }

    /** 不铺格子的工具（软锤启停之类）：面中央一个大图标。 */
    private void drawFaceTip(MultiBufferSource buffer, PoseStack.Pose pose, float fade) {
        ResourceTexture texture = sideTips[face.ordinal()];
        if (texture == null) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(PonderRenderTypes.outlineTranslucent(texture.imageLocation, false));
        quad(consumer, pose, SINGLE_ICON_AT, SINGLE_ICON_AT, SINGLE_ICON_AT + SINGLE_ICON_SIZE,
                SINGLE_ICON_AT + SINGLE_ICON_SIZE, texture, 1f, 1f, 1f, fade);
    }

    /**
     * 这一格的图标画多亮。点名了就只亮点名的那些（按各自登记的延迟亮起），没点名就亮
     * 正对这一面的中心格，其余压到 0x44。
     */
    private float cellAlpha(int cell) {
        if (highlights.isEmpty()) {
            return CELL_DIRECTION[cell].getActualDirection(face) == face ? ICON_ALPHA_FOCUS : ICON_ALPHA_DIM;
        }
        for (ToolGridPlacement.Highlight highlight : highlights) {
            if (highlight.cell() == cell && ticksShown >= highlight.delayTicks()) {
                return ICON_ALPHA_FOCUS;
            }
        }
        return ICON_ALPHA_DIM;
    }

    private @Nullable ResourceTexture tip(int cell) {
        return sideTips[CELL_DIRECTION[cell].getActualDirection(face)
                .ordinal()];
    }

    /** 图标 4x4 texel，中间那行/列各偏 2，与 GT 的摆放对齐。 */
    private static void iconOrigin(int cell, float[] out) {
        int row = cell / 3;
        int col = cell % 3;
        out[0] = DIVISIONS[col] + (col == 1 ? 2 : 0);
        out[1] = DIVISIONS[2 - row] + (row == 1 ? 2 : 0);
    }

    /** 问方块要图标，顺便问它铺不铺格子；方块没有高亮实现时照常画空网格。 */
    private boolean refreshTips(PonderLevel world) {
        Arrays.fill(sideTips, null);
        grid = true;
        if (!tips) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        BlockState state = world.getBlockState(pos);
        IToolGridHighlight highlight = null;
        if (blockEntity instanceof IToolGridHighlight fromEntity) {
            highlight = fromEntity;
        } else if (state.getBlock() instanceof IToolGridHighlight fromBlock) {
            highlight = fromBlock;
        }
        if (highlight == null) {
            return false;
        }
        Set<GTToolType> tools = Set.of(tool);
        ItemStack held = player.getMainHandItem();
        try {
            grid = highlight.shouldRenderGrid(player, pos, state, held, tools);
            if (!grid && !highlights.isEmpty()) {
                reportNoCells();
            }
            for (Direction side : Direction.values()) {
                sideTips[side.ordinal()] = highlight.sideTips(player, pos, state, tools, side);
            }
        } catch (Throwable t) {
            if (!reportedTips) {
                reportedTips = true;
                BetterGregTechPonder.LOGGER.error("BetterGregTechPonder: asking the block at {} for its tool tips threw - the cells stay " +
                        "empty for this segment", pos, t);
            }
            Arrays.fill(sideTips, null);
        }
        return true;
    }

    /** 这个工具只画一个图标，没有格子，说清原因免得作者以为坐标写错了。 */
    private void reportNoCells() {
        if (reportedNoCells) {
            return;
        }
        reportedNoCells = true;
        BetterGregTechPonder.LOGGER.warn("BetterGregTechPonder: the tool {} does not open the 3x3 grid on the block at {} - only one icon is " +
                "drawn on {} and highlight(...) is ignored", tool, pos, face);
    }

    private static RenderType lineType() {
        return PonderRenderTypes.outlineTranslucent(PonderSpecialTextures.BLANK.getLocation(), false);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1,
                             @Nullable ResourceTexture texture, float r, float g, float b, float a) {
        float u0 = texture == null ? 0 : texture.offsetX;
        float v0 = texture == null ? 0 : texture.offsetY;
        float u1 = texture == null ? 0 : texture.imageWidth;
        float v1 = texture == null ? 0 : texture.imageHeight;
        vertex(consumer, pose, x0, y1, u0, v0 + v1, r, g, b, a);
        vertex(consumer, pose, x1, y1, u0 + u1, v0 + v1, r, g, b, a);
        vertex(consumer, pose, x1, y0, u0 + u1, v0, r, g, b, a);
        vertex(consumer, pose, x0, y0, u0, v0, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v,
                               float r, float g, float b, float a) {
        consumer.vertex(pose.pose(), x, y, 0)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
    }
}
