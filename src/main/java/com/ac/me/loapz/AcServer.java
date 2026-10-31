package com.ac.me.loapz;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AcServer {
	private static final Map<String, Double> lastY = new HashMap<>();
	private static final Map<String, Double> lastX = new HashMap<>();
	private static final Map<String, Double> lastZ = new HashMap<>();
	private static final Map<String, Double> lastFall = new HashMap<>();
	private static final Map<String, long[]> violations = new HashMap<>();
	private static final long EXPIRE = 400_000L;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AcServer::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
			checkMovement(p);
		}
	}

	private static void checkMovement(ServerPlayer p) {
		String name = p.getGameProfile().name();
		Vec3 pos = p.position();
		double prevX = lastX.getOrDefault(name, pos.x);
		double prevY = lastY.getOrDefault(name, pos.y);
		double prevZ = lastZ.getOrDefault(name, pos.z);
		double dx = pos.x - prevX;
		double dy = pos.y - prevY;
		double dz = pos.z - prevZ;
		lastX.put(name, pos.x);
		lastY.put(name, pos.y);
		lastZ.put(name, pos.z);

		if (p.isPassenger() || p.isSpectator() || p.isSwimming()) return;

		if (dy > 0.55 && !p.onGround() && p.fallDistance == 0 && p.getDeltaMovement().y > 0.45) {
			if (!p.isInWater() && !p.isSwimming() && !p.isFallFlying()) {
				flag(p, "fly (y-delta " + String.format("%.3f", dy) + ")");
			}
		}
		double baseSpeed = p.isSprinting() ? 0.28 : (p.isShiftKeyDown() ? 0.065 : 0.215);
		int speedLevel = p.getEffect(MobEffects.SPEED) != null ? p.getEffect(MobEffects.SPEED).getAmplifier() + 1 : 0;
		double maxSpeed = baseSpeed * (1.0 + speedLevel * 0.2);
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		if (horizontalDist > maxSpeed * 1.3 && !p.isFallFlying()) {
			flag(p, "speed (dist " + String.format("%.3f", horizontalDist) + ")");
		}
		double fallDist = p.fallDistance;
		double prevFall = lastFall.getOrDefault(name, fallDist);
		lastFall.put(name, fallDist);
		if (!p.onGround() && dy < -0.1 && fallDist == 0 && prevFall == 0) {
			if (!p.isInWater() && !p.isSwimming()) {
				flag(p, "nofall (falling but fallDistance 0)");
			}
		}
	}

	private static void flag(ServerPlayer p, String reason) {
		String name = p.getGameProfile().name();
		long now = System.currentTimeMillis();
		long[] v = violations.getOrDefault(name, new long[] {0, 0});
		if (now - v[1] > EXPIRE) { v[0] = 0; }
		v[0]++;
		v[1] = now;
		violations.put(name, v);
		net.minecraft.server.MinecraftServer srv = p.level().getServer();
		net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("Warning " + v[0] + "/2");
		if (srv.getPlayerList().isOp(new net.minecraft.server.players.NameAndId(p.getGameProfile().id(), p.getGameProfile().name()))) {
			p.sendSystemMessage(msg);
		}
		String notify = name + " flagged: " + reason + " (strike " + v[0] + "/3)";
		for (ServerPlayer op : srv.getPlayerList().getPlayers()) {
			if (srv.getPlayerList().isOp(new net.minecraft.server.players.NameAndId(op.getGameProfile().id(), op.getGameProfile().name()))) {
				op.sendSystemMessage(net.minecraft.network.chat.Component.literal(notify));
			}
		}
		if (v[0] >= 3) {
			p.connection.disconnect(net.minecraft.network.chat.Component.literal("Anti-cheat kick"));
		}
	}
}