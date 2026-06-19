/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a full RuneLite client with this plugin side-loaded.
 * Run with: ./gradlew run   (or run this main() from your IDE).
 */
public class PrayerCallerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PrayerCallerPlugin.class);
		RuneLite.main(args);
	}
}
