/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.partydps;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(PartyDpsConfig.GROUP)
public interface PartyDpsConfig extends Config
{
	String GROUP = "partydps";

	@ConfigItem(
		keyName = "showPercent",
		name = "Show percent",
		description = "Show each member's share of total damage.",
		position = 0
	)
	default boolean showPercent()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTotal",
		name = "Show total",
		description = "Show a combined total line at the bottom.",
		position = 1
	)
	default boolean showTotal()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "idlePauseSeconds",
		name = "Pause after (s)",
		description = "Pause the timer after this many seconds with no damage (so out-of-combat time isn't counted).",
		position = 2
	)
	default int idlePauseSeconds()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "autoReset",
		name = "Auto reset new fight",
		description = "Automatically clear the meter when a new fight starts after a long idle gap.",
		position = 3
	)
	default boolean autoReset()
	{
		return true;
	}

	@Range(min = 5, max = 300)
	@ConfigItem(
		keyName = "idleResetSeconds",
		name = "New fight after (s)",
		description = "How long with no damage before the next hit is treated as a fresh fight (auto reset).",
		position = 4
	)
	default int idleResetSeconds()
	{
		return 20;
	}
}
