/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private PrayerCallerConfig config;

	private final SoundManager soundManager = new SoundManager();

	// Bosses physically present right now (ignores per-boss enable; that's checked when handling events).
	private final List<BossDefinition> activeBosses = new ArrayList<>();
	// Projectiles already handled — ProjectileMoved fires every frame for the same instance.
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
	// NPC-spawn callouts are emitted one per tick so Cerberus's three souls announce in order.
	private final Queue<Pending> spawnQueue = new ArrayDeque<>();

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
		recomputeActiveBosses();

		final int id = event.getNpc().getId();
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
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
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

		// Prefer the bundled custom clip; fall back to an in-game sound effect if it's off or missing.
		if (config.useCustomSounds() && soundManager.play(style.getSoundResource(), config.customVolume()))
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
		lastStyle = null;
		lastTriggerTick = Integer.MIN_VALUE;
	}
}
