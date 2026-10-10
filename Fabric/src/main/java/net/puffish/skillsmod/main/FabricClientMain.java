package net.puffish.skillsmod.main;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.client.SkillsClientMod;
import net.puffish.skillsmod.client.event.ClientEventListener;
import net.puffish.skillsmod.client.event.ClientEventReceiver;
import net.puffish.skillsmod.client.keybinding.KeyBindingHandler;
import net.puffish.skillsmod.client.keybinding.KeyBindingReceiver;
import net.puffish.skillsmod.client.network.ClientPacketHandler;
import net.puffish.skillsmod.client.network.ClientPacketSender;
import net.puffish.skillsmod.client.setup.ClientRegistrar;
import net.puffish.skillsmod.network.InPacket;
import net.puffish.skillsmod.network.OutPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class FabricClientMain implements ClientModInitializer {
	private final Map<Identifier, CustomPayload.Id<FabricMain.InOutPayload<?>>> outPackets = new HashMap<>();

	@Override
	public void onInitializeClient() {
		SkillsClientMod.setup(
				new ClientRegistrarImpl(),
				new ClientEventReceiverImpl(),
				new KeyBindingReceiverImpl(),
				new ClientPacketSenderImpl()
		);
	}

	private class ClientRegistrarImpl implements ClientRegistrar {
		@Override
		public <T extends InPacket> void registerInPacket(Identifier id, Function<RegistryByteBuf, T> reader, ClientPacketHandler<T> handler) {
			var pId = new CustomPayload.Id<FabricMain.InOutPayload<T>>(id);
			PayloadTypeRegistry.playS2C().register(pId, CustomPayload.codecOf(
					(value, buf) -> value.outPacket().write(buf),
					buf -> new FabricMain.InOutPayload<>(pId, reader.apply(buf), null)
			));
			ClientPlayNetworking.registerGlobalReceiver(
					pId,
					(payload, context) -> handler.handle(payload.inValue())
			);
		}

		@Override
		public void registerOutPacket(Identifier id) {
			outPackets.put(id, new CustomPayload.Id<>(id));
		}
	}

	private static class ClientEventReceiverImpl implements ClientEventReceiver {
		@Override
		public void registerListener(ClientEventListener eventListener) {
			ClientTickEvents.END_CLIENT_TICK.register(client -> eventListener.onClientTick());
			ClientPlayConnectionEvents.JOIN.register(
					(handler, sender, client) -> eventListener.onPlayerJoin()
			);
		}
	}

	private static class KeyBindingReceiverImpl implements KeyBindingReceiver {
		@Override
		public void registerKeyBinding(KeyBinding keyBinding, KeyBindingHandler handler) {
			ClientTickEvents.END_CLIENT_TICK.register(
					client -> {
						if (keyBinding.wasPressed()) {
							handler.handle();
						}
					}
			);
			KeyBindingHelper.registerKeyBinding(keyBinding);
		}
	}

	private class ClientPacketSenderImpl implements ClientPacketSender {
		@Override
		public void send(OutPacket packet) {
			ClientPlayNetworking.send(new FabricMain.InOutPayload<>(outPackets.get(packet.getId()), null, packet));
		}
	}
}
