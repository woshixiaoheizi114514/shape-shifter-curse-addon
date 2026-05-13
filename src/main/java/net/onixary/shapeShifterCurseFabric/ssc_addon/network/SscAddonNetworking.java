package net.onixary.shapeShifterCurseFabric.ssc_addon.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaTeleport;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPrimary;
//import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.Ability_AllayHeal;


public class SscAddonNetworking {
	public static final Identifier PACKET_KEY_PRESS = new Identifier("my_addon", "key_press");
	/** 契灵 - 次要技能：瞬移。payload: byte mode (0=RAYCAST, 1=PLATFORM) */
	public static final Identifier PACKET_MANCIANIMA_TELEPORT = new Identifier("my_addon", "mancianima_teleport");
	/** 契灵 - 主要技能：三段标记。无 payload，服务端根据当前状态分支。 */
	public static final Identifier PACKET_MANCIANIMA_PRIMARY = new Identifier("my_addon", "mancianima_primary");

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(PACKET_KEY_PRESS, (server, player, handler, buf, responseSender) -> {
			int keyId = buf.readInt();
			server.execute(() -> handleKeyPress(player, keyId));
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_MANCIANIMA_TELEPORT, (server, player, handler, buf, responseSender) -> {
			byte mode = buf.readByte();
			server.execute(() -> MancianimaTeleport.execute(player, mode));
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_MANCIANIMA_PRIMARY, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> MancianimaPrimary.execute(player));
		});
	}

	private static void handleKeyPress(ServerPlayerEntity player, int keyId) {
		// Find current form
		PlayerFormBase form = FormAbilityManager.getForm(player);
		if (form == null) return;

		Identifier formId = form.FormID;


		// Allay Heal (using keyId 1 for now, mapped from client)
        /*if (keyId == 1 && (formId.getPath().equals("form_allay_sp") || formId.getPath().equals("allay_sp"))) {
             Ability_AllayHeal.onHold(player);
        }*/

		// Add other key handlers here if needed (e.g. Fox Fire)
	}
}
