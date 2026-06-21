/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Prayer Audio Cues",
	description = "Accessibility: speaks the protection prayer to use as a boss attacks, for players who can't rely on visual tells",
	tags = {"accessibility", "accessible", "audio", "sound", "cue", "blind", "low vision", "colourblind", "colorblind",
		"prayer", "boss", "pvm", "combat", "yama", "jad", "vorkath", "zulrah", "olm", "cerberus", "gauntlet", "toa"}
)
public class PrayerCallerPlugin extends Plugin
{
	// Collapse a single attack's repeated events into one callout.
	private static final int DEDUPE_TICKS = 3;

	// Priority order for "priority prayer" mode: most dangerous first.
	private static final AttackStyle[] PRIORITY_ORDER = {AttackStyle.MAGIC, AttackStyle.RANGED, AttackStyle.MELEE};

	// Inferno NPC id -> spoken/chat threat name (dangerous spawns worth announcing).
	private static final Map<Integer, String> INFERNO_THREATS = Map.ofEntries(
		Map.entry(7693, "Blob"),
		Map.entry(7697, "Meleer"),
		Map.entry(7698, "Ranger"), Map.entry(7702, "Ranger"),
		Map.entry(7699, "Mager"), Map.entry(7703, "Mager"),
		Map.entry(7700, "Jad"), Map.entry(7704, "Jad"),
		Map.entry(7701, "Healers"), Map.entry(7705, "Healers"),
		Map.entry(7706, "Zuk"),
		Map.entry(7708, "Zuk healers"));

	// Don't repeat the same threat name within this many ticks.
	private static final int THREAT_DEDUPE_TICKS = 5;

	// Every NPC id the plugin reacts to (boss presence + spawn triggers + Inferno threats). Lets us
	// skip recomputing the active-boss list when an irrelevant NPC spawns (e.g. the Yama room's burst).
	private static final Set<Integer> RELEVANT_NPC_IDS = buildRelevantNpcIds();

	private static Set<Integer> buildRelevantNpcIds()
	{
		final Set<Integer> ids = new HashSet<>(INFERNO_THREATS.keySet());
		for (BossDefinition boss : Bosses.ALL)
		{
			ids.addAll(boss.getPresenceNpcIds());
			final Map<Integer, AttackStyle> spawnTriggers = boss.getTriggers().get(TriggerType.NPC_SPAWN);
			if (spawnTriggers != null)
			{
				ids.addAll(spawnTriggers.keySet());
			}
		}
		return ids;
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private PrayerCallerConfig config;

	@Inject
	private SoundManager soundManager;

	// Bosses physically present right now (ignores per-boss enable; that's checked when handling events).
	private final List<BossDefinition> activeBosses = new ArrayList<>();
	// Projectiles already handled — ProjectileMoved fires every frame for the same instance.
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
	// NPC-spawn callouts are emitted one per tick so Cerberus's three souls announce in order.
	private final Queue<Pending> spawnQueue = new ArrayDeque<>();

	// Priority-prayer mode buffers each tick's styles, then announces only the most dangerous on GameTick.
	private final Map<AttackStyle, BossDefinition> tickStyleBuffer = new EnumMap<>(AttackStyle.class);
	// Last tick each Inferno threat name was announced (anti-spam).
	private final Map<String, Integer> lastThreatTick = new HashMap<>();

	private AttackStyle lastStyle;
	private int lastTriggerTick = Integer.MIN_VALUE;
	private int testCycleIndex;

	private final HotkeyListener testHotkeyListener = new HotkeyListener(() -> config.testHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(PrayerCallerPlugin.this::testCallout);
		}
	};

	@Value
	private static class Pending
	{
		BossDefinition boss;
		AttackStyle style;
	}

	@Provides
	PrayerCallerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayerCallerConfig.class);
	}

	@Override
	protected void startUp()
	{
		recomputeActiveBosses();
		resetState();
		keyManager.registerKeyListener(testHotkeyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(testHotkeyListener);
		soundManager.shutdown();
		activeBosses.clear();
		resetState();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			activeBosses.clear();
			resetState();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final int id = event.getNpc().getId();
		if (!RELEVANT_NPC_IDS.contains(id))
		{
			return; // irrelevant NPC — no effect on the active boss / triggers, skip the rescan
		}

		recomputeActiveBosses();

		for (BossDefinition boss : activeBosses)
		{
			if (!boss.getEnabled().test(config))
			{
				continue;
			}
			final AttackStyle style = boss.lookup(TriggerType.NPC_SPAWN, id);
			if (style != null)
			{
				// Queue rather than call immediately so simultaneous spawns (Cerberus souls) space out.
				spawnQueue.add(new Pending(boss, style));
			}
		}

		announceInfernoThreat(id);
	}

	private void announceInfernoThreat(int npcId)
	{
		if (!config.infernoThreats() || !config.inferno())
		{
			return;
		}
		final String threat = INFERNO_THREATS.get(npcId);
		if (threat == null)
		{
			return;
		}
		final int tick = client.getTickCount();
		final Integer last = lastThreatTick.get(threat);
		if (last != null && tick - last < THREAT_DEDUPE_TICKS)
		{
			return;
		}
		lastThreatTick.put(threat, tick);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=ff3333>Inferno: " + threat + "!</col>", null);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!RELEVANT_NPC_IDS.contains(event.getNpc().getId()))
		{
			return;
		}
		recomputeActiveBosses();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		final Pending pending = spawnQueue.poll();
		if (pending != null)
		{
			callOut(pending.getBoss(), pending.getStyle());
		}

		flushPriority();
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!config.soundsEnabled() || !config.killSounds() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		final int id = ((NPC) event.getActor()).getId();
		for (BossDefinition boss : Bosses.ALL)
		{
			if (boss.getKillSound() != null && boss.getPresenceNpcIds().contains(id))
			{
				soundManager.play(boss.getKillSound(), config.customVolume());
				return;
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (activeBosses.isEmpty() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		final NPC npc = (NPC) event.getActor();
		final int animation = npc.getAnimation();

		for (BossDefinition boss : activeBosses)
		{
			if (!boss.getPresenceNpcIds().contains(npc.getId()))
			{
				continue;
			}
			if (config.debugLogging())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[PrayerCaller] " + boss.getName() + " animation " + animation, null);
			}
			if (!boss.getEnabled().test(config))
			{
				continue;
			}
			final AttackStyle style = boss.lookup(TriggerType.NPC_ANIMATION, animation);
			if (style != null)
			{
				callOut(boss, style);
			}
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		final Actor actor = event.getActor();

		if (config.debugLogging() && actor == client.getLocalPlayer() && !activeBosses.isEmpty())
		{
			logSpotAnims(actor);
		}

		if (activeBosses.isEmpty())
		{
			return;
		}

		final boolean isPlayer = actor == client.getLocalPlayer();
		final boolean isNpc = actor instanceof NPC;
		if (!isPlayer && !isNpc)
		{
			return;
		}

		for (BossDefinition boss : activeBosses)
		{
			if (!boss.getEnabled().test(config))
			{
				continue;
			}

			// Yama produces a cast graphic on itself AND an impact graphic on you for the same attack.
			// Use exactly one source (controlled by "earliest warning") to avoid double-calling.
			if (isNpc && boss.getPresenceNpcIds().contains(((NPC) actor).getId()) && config.yamaEarliestWarning())
			{
				matchSpotAnim(boss, actor, TriggerType.NPC_SPOTANIM);
			}
			else if (isPlayer && !config.yamaEarliestWarning())
			{
				matchSpotAnim(boss, actor, TriggerType.PLAYER_SPOTANIM);
			}
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		final Projectile projectile = event.getProjectile();
		if (!seenProjectiles.add(projectile))
		{
			return; // already handled this projectile instance
		}

		if (activeBosses.isEmpty())
		{
			return;
		}

		final int id = projectile.getId();
		if (config.debugLogging())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[PrayerCaller] projectile " + id, null);
		}

		for (BossDefinition boss : activeBosses)
		{
			if (!boss.getEnabled().test(config))
			{
				continue;
			}
			final AttackStyle style = boss.lookup(TriggerType.PROJECTILE, id);
			if (style != null)
			{
				callOut(boss, style);
				return;
			}
		}
	}

	private void matchSpotAnim(BossDefinition boss, Actor actor, TriggerType type)
	{
		final Map<Integer, AttackStyle> byId = boss.getTriggers().get(type);
		if (byId == null)
		{
			return;
		}
		for (Map.Entry<Integer, AttackStyle> entry : byId.entrySet())
		{
			if (actor.hasSpotAnim(entry.getKey()))
			{
				callOut(boss, entry.getValue());
				return;
			}
		}
	}

	private void recomputeActiveBosses()
	{
		activeBosses.clear();
		for (BossDefinition boss : Bosses.ALL)
		{
			for (NPC npc : client.getNpcs())
			{
				if (boss.getPresenceNpcIds().contains(npc.getId()))
				{
					activeBosses.add(boss);
					break;
				}
			}
		}
	}

	private void callOut(BossDefinition boss, AttackStyle style)
	{
		// In priority mode, buffer this tick's styles and resolve to the most dangerous on the next
		// GameTick. Otherwise announce immediately (lowest latency).
		if (config.priorityPrayer())
		{
			tickStyleBuffer.put(style, boss);
			return;
		}
		announceCallout(boss, style);
	}

	private void flushPriority()
	{
		if (tickStyleBuffer.isEmpty())
		{
			return;
		}
		for (AttackStyle style : PRIORITY_ORDER)
		{
			final BossDefinition boss = tickStyleBuffer.get(style);
			if (boss != null)
			{
				announceCallout(boss, style);
				break;
			}
		}
		tickStyleBuffer.clear();
	}

	private void announceCallout(BossDefinition boss, AttackStyle style)
	{
		if (style == AttackStyle.MELEE && !config.announceMelee())
		{
			return;
		}

		final int tick = client.getTickCount();
		if (style == lastStyle && tick - lastTriggerTick < DEDUPE_TICKS)
		{
			return;
		}
		lastStyle = style;
		lastTriggerTick = tick;

		// Stay silent if you're already on the right protection prayer.
		if (config.onlyOnSwitch() && client.isPrayerActive(style.getPrayer()))
		{
			return;
		}

		playFor(style);

		if (config.chatMessage())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=ff0000>" + boss.getName() + ": " + style.getPrayerName() + "!</col>", null);
		}

		log.debug("{}: {} -> {}", boss.getName(), style, style.getPrayerName());
	}

	private void testCallout()
	{
		final AttackStyle style = AttackStyle.values()[testCycleIndex % AttackStyle.values().length];
		testCycleIndex++;

		playFor(style);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=00aaff>[TEST] " + style.getDisplayName() + " -> " + style.getPrayerName() + "</col>", null);
	}

	private void playFor(AttackStyle style)
	{
		if (!config.soundsEnabled())
		{
			return;
		}

		// Prefer the bundled custom clip from the selected voice pack; fall back to an in-game sound
		// effect if custom sounds are off or the clip is missing.
		final String resource = config.voicePack().getDir() + "/" + style.getSoundResource();
		if (config.useCustomSounds() && soundManager.play(resource, config.customVolume()))
		{
			return;
		}

		client.playSoundEffect(soundIdFor(style));
	}

	private int soundIdFor(AttackStyle style)
	{
		switch (style)
		{
			case MAGIC:
				return config.magicSoundId();
			case RANGED:
				return config.rangedSoundId();
			case MELEE:
			default:
				return config.meleeSoundId();
		}
	}

	private void logSpotAnims(Actor actor)
	{
		final StringBuilder ids = new StringBuilder();
		if (actor.getSpotAnims() != null)
		{
			actor.getSpotAnims().forEach(spotAnim ->
			{
				if (ids.length() > 0)
				{
					ids.append(", ");
				}
				ids.append(spotAnim.getId());
			});
		}
		if (ids.length() > 0)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[PrayerCaller] your spotanims: " + ids, null);
		}
	}

	private void resetState()
	{
		spawnQueue.clear();
		seenProjectiles.clear();
		tickStyleBuffer.clear();
		lastThreatTick.clear();
		lastStyle = null;
		lastTriggerTick = Integer.MIN_VALUE;
	}
}
