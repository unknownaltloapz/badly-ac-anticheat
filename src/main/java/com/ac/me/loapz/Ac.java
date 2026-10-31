package com.ac.me.loapz;

import net.fabricmc.api.ModInitializer;

public class Ac implements ModInitializer {
	public static final String MOD_ID = "ac";

	@Override
	public void onInitialize() {
		AcServer.init();
	}
}