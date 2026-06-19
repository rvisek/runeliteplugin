/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.yamaprayer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a full RuneLite client with this plugin side-loaded.
 * Run with: ./gradlew run   (or run this main() from your IDE).
 */
public class YamaPrayerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(YamaPrayerPlugin.class);
		RuneLite.main(args);
	}
}
