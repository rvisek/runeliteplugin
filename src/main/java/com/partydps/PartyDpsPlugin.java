/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.partydps;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Party DPS Meter",
	description = "Per-fight damage and DPS breakdown for you and your party",
	tags = {"dps", "damage", "meter", "party", "recount", "boss", "pvm"}
)
public class PartyDpsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PartyDpsOverlay overlay;

	@Inject
	private PartyDpsConfig config;

	// Per-combatant damage, keyed by display name. LinkedHashMap keeps a stable order for equal damage.
	@Getter
	private final Map<String, DpsMember> members = new LinkedHashMap<>();
	@Getter
	private final DpsMember total = new DpsMember("Total");

	private Instant lastActivity = Instant.now();

	@Provides
	PartyDpsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PartyDpsConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(PartyDpsHit.class);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		wsClient.unregisterMessage(PartyDpsHit.class);
		resetAll();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		final Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		final Hitsplat hitsplat = event.getHitsplat();
		// Only the local player's own damage; party members report theirs over the network.
		if (!hitsplat.isMine())
		{
			return;
		}

		final int hit = hitsplat.getAmount();
		final PartyMember local = partyService.getLocalMember();
		if (local != null)
		{
			partyService.send(new PartyDpsHit(hit));
		}

		applyDamage(localName(), hit);
	}

	@Subscribe
	public void onPartyDpsHit(PartyDpsHit event)
	{
		final PartyMember local = partyService.getLocalMember();
		if (local != null && event.getMemberId() == local.getMemberId())
		{
			return; // our own broadcast echoed back
		}

		final PartyMember sender = partyService.getMemberById(event.getMemberId());
		if (sender == null || sender.getDisplayName() == null)
		{
			return;
		}
		applyDamage(sender.getDisplayName(), event.getHit());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickIdleCheck();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		resetAll();
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() == overlay && PartyDpsOverlay.RESET.equals(event.getEntry().getOption()))
		{
			resetAll();
		}
	}

	private void applyDamage(String name, int hit)
	{
		if (name == null)
		{
			return;
		}

		// A fresh fight after a long idle gap: clear the previous fight's numbers first.
		if (config.autoReset() && total.getEnd() != null
			&& Duration.between(total.getEnd(), Instant.now()).getSeconds() >= config.idleResetSeconds())
		{
			resetAll();
		}

		unpauseAll();
		members.computeIfAbsent(name, DpsMember::new).addDamage(hit);
		total.addDamage(hit);
		lastActivity = Instant.now();
	}

	void tickIdleCheck()
	{
		if (total.getStart() != null && total.getEnd() == null
			&& Duration.between(lastActivity, Instant.now()).getSeconds() >= config.idlePauseSeconds())
		{
			members.values().forEach(DpsMember::pause);
			total.pause();
		}
	}

	private void unpauseAll()
	{
		members.values().forEach(DpsMember::unpause);
		total.unpause();
	}

	private void resetAll()
	{
		members.clear();
		total.reset();
	}

	String getLocalName()
	{
		return localName();
	}

	private String localName()
	{
		final PartyMember local = partyService.getLocalMember();
		if (local != null && local.getDisplayName() != null)
		{
			return local.getDisplayName();
		}
		final Player player = client.getLocalPlayer();
		return player == null ? null : player.getName();
	}
}
