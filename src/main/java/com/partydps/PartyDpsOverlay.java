/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.partydps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class PartyDpsOverlay extends OverlayPanel
{
	static final String RESET = "Reset";
	private static final Color LOCAL_COLOR = new Color(0xFFD24D);
	private static final Color TOTAL_COLOR = new Color(0x9FE0FF);

	private final PartyService partyService;
	private final PartyDpsConfig config;
	private final PartyDpsPlugin plugin;

	@Inject
	PartyDpsOverlay(PartyService partyService, PartyDpsConfig config, PartyDpsPlugin plugin)
	{
		this.partyService = partyService;
		this.config = config;
		this.plugin = plugin;
		setPosition(net.runelite.client.ui.overlay.OverlayPosition.TOP_LEFT);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, RESET, "DPS meter"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final List<DpsMember> members = new ArrayList<>(plugin.getMembers().values());
		if (members.isEmpty())
		{
			return null;
		}
		members.sort(Comparator.comparingInt(DpsMember::getDamage).reversed());

		final int totalDamage = plugin.getTotal().getDamage();
		final String localName = plugin.getLocalName();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(partyService.isInParty() ? "Party DPS" : "DPS")
			.color(Color.WHITE)
			.build());

		for (DpsMember member : members)
		{
			final boolean isLocal = member.getName() != null && member.getName().equals(localName);
			panelComponent.getChildren().add(LineComponent.builder()
				.left(member.getName())
				.leftColor(isLocal ? LOCAL_COLOR : Color.WHITE)
				.right(formatRight(member.getDamage(), member.getDps(), totalDamage))
				.rightColor(isLocal ? LOCAL_COLOR : Color.WHITE)
				.build());
		}

		if (config.showTotal())
		{
			final DpsMember total = plugin.getTotal();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Total")
				.leftColor(TOTAL_COLOR)
				.right(String.format("%,d  %.1f", total.getDamage(), total.getDps()))
				.rightColor(TOTAL_COLOR)
				.build());
		}

		panelComponent.setPreferredSize(new Dimension(165, 0));
		return super.render(graphics);
	}

	private String formatRight(int damage, float dps, int totalDamage)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(String.format("%,d  %.1f", damage, dps));
		if (config.showPercent() && totalDamage > 0)
		{
			sb.append(String.format("  %d%%", Math.round(100f * damage / totalDamage)));
		}
		return sb.toString();
	}
}
