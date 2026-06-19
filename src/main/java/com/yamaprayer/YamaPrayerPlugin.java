/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.yamaprayer;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Yama Prayer Caller",
	description = "Audio callout for which protection prayer to use right as Yama attacks",
	tags = {"yama", "prayer", "boss", "pvm", "combat", "sound", "audio"}
)
public class YamaPrayerPlugin extends Plugin
{
	// --- Yama identifiers, from RuneLite's gameval cache dumps ---
	private static final int YAMA_NPC_ID = 14176;

	// Telegraph graphics that play ON THE TARGETED PLAYER (accurate even in a duo).
	private static final int SPOTANIM_PLAYER_FIRE_IMPACT = 3247;   // flames -> Magic
	private static final int SPOTANIM_PLAYER_SHADOW_IMPACT = 3244; // shadow -> Ranged

	// Cast graphics that play ON YAMA when he starts the attack (earliest possible tell).
	private static final int SPOTANIM_YAMA_FIRE_CAST = 3246;   // flames -> Magic
	private static final int SPOTANIM_YAMA_SHADOW_CAST = 3243; // shadow -> Ranged

	// Yama's melee axe swing animation.
	private static final int ANIM_YAMA_MELEE = 12146;

	// Collapse the cast + impact graphics of a single attack into one callout.
	private static final int DEDUPE_TICKS = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private YamaPrayerConfig config;

	private final YamaSoundManager soundManager = new YamaSoundManager();

	private NPC yama;
	private YamaAttackStyle lastStyle;
	private int lastTriggerTick = Integer.MIN_VALUE;
	private int testCycleIndex;

	// Dev convenience: cycle the three callouts on a hotkey so the sounds can be heard without Yama.
	private final HotkeyListener testHotkeyListener = new HotkeyListener(() -> config.testHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(YamaPrayerPlugin.this::testCallout);
		}
	};

	@Provides
	YamaPrayerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(YamaPrayerConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Pick up Yama if the plugin is toggled on mid-fight.
		for (NPC npc : client.getNpcs())
		{
			if (npc.getId() == YAMA_NPC_ID)
			{
				yama = npc;
				break;
			}
		}
		resetCooldown();
		keyManager.registerKeyListener(testHotkeyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(testHotkeyListener);
		soundManager.shutdown();
		yama = null;
		resetCooldown();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			yama = null;
			resetCooldown();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (event.getNpc().getId() == YAMA_NPC_ID)
		{
			yama = event.getNpc();
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == yama)
		{
			yama = null;
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		final Actor actor = event.getActor();

		if (config.debugLogging() && actor == client.getLocalPlayer())
		{
			logDebug("Your spotanim", actor);
		}

		if (yama == null)
		{
			return;
		}

		// Each attack produces TWO graphics: a cast on Yama, then an impact on the target. To avoid
		// calling out twice, listen to exactly one source depending on the warning-timing preference.
		if (config.earliestWarning())
		{
			// Cast graphic on Yama himself — earliest possible tell (not target-aware in a duo).
			if (actor == yama)
			{
				if (actor.hasSpotAnim(SPOTANIM_YAMA_FIRE_CAST))
				{
					callOut(YamaAttackStyle.MAGIC);
				}
				else if (actor.hasSpotAnim(SPOTANIM_YAMA_SHADOW_CAST))
				{
					callOut(YamaAttackStyle.RANGED);
				}
			}
		}
		else if (actor == client.getLocalPlayer())
		{
			// Telegraph landing on you — slightly later, but target-accurate in a duo.
			if (actor.hasSpotAnim(SPOTANIM_PLAYER_FIRE_IMPACT))
			{
				callOut(YamaAttackStyle.MAGIC);
			}
			else if (actor.hasSpotAnim(SPOTANIM_PLAYER_SHADOW_IMPACT))
			{
				callOut(YamaAttackStyle.RANGED);
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (yama == null || event.getActor() != yama)
		{
			return;
		}

		if (config.debugLogging())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Yama] animation " + yama.getAnimation(), null);
		}

		if (config.announceMelee() && yama.getAnimation() == ANIM_YAMA_MELEE)
		{
			callOut(YamaAttackStyle.MELEE);
		}
	}

	private void callOut(YamaAttackStyle style)
	{
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
				"<col=ff0000>Yama: " + style.getPrayerName() + "!</col>", null);
		}

		log.debug("Yama attack: {} -> {}", style, style.getPrayerName());
	}

	private void testCallout()
	{
		final YamaAttackStyle style = YamaAttackStyle.values()[testCycleIndex % YamaAttackStyle.values().length];
		testCycleIndex++;

		playFor(style);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=00aaff>[Yama TEST] " + style.getDisplayName() + " -> " + style.getPrayerName() + "</col>", null);
	}

	private void playFor(YamaAttackStyle style)
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

	private int soundIdFor(YamaAttackStyle style)
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

	private void logDebug(String label, Actor actor)
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
				"[" + label + "] spotanims: " + ids, null);
		}
	}

	private void resetCooldown()
	{
		lastStyle = null;
		lastTriggerTick = Integer.MIN_VALUE;
	}
}
