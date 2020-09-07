package com.lothrazar.cyclic.block.collectfluid;

import com.lothrazar.cyclic.util.UtilRender;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderPlacerFluid extends TileEntityRenderer<TileFluidCollect> {

  public RenderPlacerFluid(TileEntityRendererDispatcher d) {
    super(d);
  }

  @Override
  public void render(TileFluidCollect te, float v, MatrixStack matrix,
      IRenderTypeBuffer ibuffer, int partialTicks, int destroyStage) {
    if (1 == te.getField(TileFluidCollect.Fields.RENDER.ordinal())) {
      UtilRender.renderOutline(te.getPos(), te.getShape(), matrix);
    }
  }
}
