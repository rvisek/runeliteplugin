/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws freeze-timer and defence-reduction text above tracked NPCs.
 */
class NpcStatusOverlay extends Overlay
{
	private static final Color FREEZE_COLOR = new Color(0x6FD3FF);
	private static final Color DEFENCE_COLOR = new Color(0xFF9933);

	private final Client client;
	private final PrayerCallerConfig config;
	private final PrayerCallerPlugin plugin;

	@Inject
	NpcStatusOverlay(Client client, PrayerCallerConfig config, PrayerCallerPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final int tick = client.getTickCount();
		for (Map.Entry<NPC, NpcStatus> entry : plugin.getNpcStatuses().entrySet())
		{
			final NPC npc = entry.getKey();
			final NpcStatus status = entry.getValue();

			if (config.freezeTimers() && status.isFrozen(tick))
			{
				final double seconds = status.freezeTicksLeft(tick) * 0.6;
				draw(graphics, npc, String.format("%.1f", seconds), FREEZE_COLOR, 75);
			}

			if (config.defenceTracker() && status.hasDefenceReduction())
			{
				draw(graphics, npc, "-" + status.defenceReductionPercent() + "% def", DEFENCE_COLOR, 55);
			}
		}
		return null;
	}

	private void draw(Graphics2D graphics, NPC npc, String text, Color color, int zOffset)
	{
		final Point loc = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + zOffset);
		if (loc != null)
		{
			OverlayUtil.renderTextLocation(graphics, loc, text, color);
		}
	}
}
