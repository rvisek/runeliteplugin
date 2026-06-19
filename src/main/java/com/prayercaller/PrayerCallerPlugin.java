/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
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

	// Freeze/bind target spotanim id -> duration in game ticks.
	private static final Map<Integer, Integer> FREEZE_TICKS = Map.of(
		181, 8,    // Bind
		180, 16,   // Snare
		179, 24,   // Entangle
		361, 8,    // Ice Rush
		363, 16,   // Ice Burst
		367, 24,   // Ice Blitz
		369, 32);  // Ice Barrage

	// Equipped weapon item id -> defence multiplier applied on a landed special (multiplicative reducers only).
	private static final Map<Integer, Double> DEFENCE_MULTIPLIER = Map.of(
		13576, 0.70,  // Dragon warhammer (-30% of current)
		21003, 0.65,  // Elder maul (-35% of current)
		22622, 0.25); // Statius's warhammer (-75% of current)

	// How many ticks after a spec fires we still accept its landing hitsplat.
	private static final int SPEC_HIT_WINDOW = 3;

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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcStatusOverlay npcStatusOverlay;

	// Bosses physically present right now (ignores per-boss enable; that's checked when handling events).
	private final List<BossDefinition> activeBosses = new ArrayList<>();
	// Projectiles already handled — ProjectileMoved fires every frame for the same instance.
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
	// NPC-spawn callouts are emitted one per tick so Cerberus's three souls announce in order.
	private final Queue<Pending> spawnQueue = new ArrayDeque<>();
	// Per-NPC freeze / defence-reduction state, read by the overlay.
	@Getter
	private final Map<NPC, NpcStatus> npcStatuses = new HashMap<>();

	private AttackStyle lastStyle;
	private int lastTriggerTick = Integer.MIN_VALUE;
	private int testCycleIndex;

	// Defence-reduction spec tracking.
	private int lastSpecEnergy = -1;
	private NPC pendingSpecTarget;
	private double pendingSpecMultiplier = 1.0;
	private int pendingSpecExpiryTick = Integer.MIN_VALUE;

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
		overlayManager.add(npcStatusOverlay);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(testHotkeyListener);
		overlayManager.remove(npcStatusOverlay);
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
		npcStatuses.remove(event.getNpc());
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

		trackSpecialAttack();

		// Drop NPC status once it's no longer frozen and has no tracked defence reduction.
		final int tick = client.getTickCount();
		npcStatuses.values().removeIf(s -> s.isExpired(tick));
	}

	/** Watch the spec-energy bar; when it drops while a defence-reducer is wielded, arm a pending hit. */
	private void trackSpecialAttack()
	{
		final int energy = client.getVarpValue(VarPlayerID.SA_ENERGY);
		final int previous = lastSpecEnergy;
		lastSpecEnergy = energy;

		if (previous < 0 || energy >= previous)
		{
			return; // first read, or energy regenerating (not a spec)
		}

		final Double multiplier = DEFENCE_MULTIPLIER.get(equippedWeaponId());
		final Actor target = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getInteracting();
		if (multiplier == null || !(target instanceof NPC))
		{
			return;
		}

		pendingSpecTarget = (NPC) target;
		pendingSpecMultiplier = multiplier;
		pendingSpecExpiryTick = client.getTickCount() + SPEC_HIT_WINDOW;
	}

	private int equippedWeaponId()
	{
		final ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return -1;
		}
		final Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? -1 : weapon.getId();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (pendingSpecTarget == null || event.getActor() != pendingSpecTarget)
		{
			return;
		}
		if (client.getTickCount() > pendingSpecExpiryTick)
		{
			pendingSpecTarget = null;
			return;
		}
		// A landed defence-reducing spec deals damage; ignore a 0 (missed) hit.
		if (event.getHitsplat().getAmount() > 0)
		{
			final NpcStatus status = npcStatuses.computeIfAbsent(pendingSpecTarget, k -> new NpcStatus());
			status.setDefenceMultiplier(status.getDefenceMultiplier() * pendingSpecMultiplier);
			pendingSpecTarget = null;
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

		// Freeze detection applies to any NPC, independent of the boss callouts below.
		if (config.freezeTimers() && actor instanceof NPC)
		{
			detectFreeze((NPC) actor);
		}

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

	private void detectFreeze(NPC npc)
	{
		for (Map.Entry<Integer, Integer> entry : FREEZE_TICKS.entrySet())
		{
			if (npc.hasSpotAnim(entry.getKey()))
			{
				npcStatuses.computeIfAbsent(npc, k -> new NpcStatus())
					.setFreezeExpiryTick(client.getTickCount() + entry.getValue());
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
		npcStatuses.clear();
		lastStyle = null;
		lastTriggerTick = Integer.MIN_VALUE;
		lastSpecEnergy = -1;
		pendingSpecTarget = null;
		pendingSpecExpiryTick = Integer.MIN_VALUE;
	}
}
