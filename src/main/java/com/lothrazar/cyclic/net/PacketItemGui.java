package com.lothrazar.cyclic.net;

import java.util.function.Supplier;
import com.lothrazar.cyclic.item.storagebag.ContainerStorageBag;
import com.lothrazar.cyclic.item.storagebag.StorageBagContainerProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

public class PacketItemGui extends PacketBaseCyclic {

  private int slot;

  public PacketItemGui(int slot) {
    this.slot = slot;
  }

  public static void handle(PacketItemGui message, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
      ServerPlayer player = ctx.get().getSender();
      if ((player.containerMenu instanceof ContainerStorageBag) == false) {
        NetworkHooks.openGui(player, new StorageBagContainerProvider(), player.blockPosition());
      }
    });
    message.done(ctx);
  }

  public static PacketItemGui decode(FriendlyByteBuf buf) {
    PacketItemGui p = new PacketItemGui(buf.readInt());
    return p;
  }

  public static void encode(PacketItemGui msg, FriendlyByteBuf buf) {
    buf.writeInt(msg.slot);
  }
}
