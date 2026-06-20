/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(PrayerCallerConfig.GROUP)
public interface PrayerCallerConfig extends Config
{
	String GROUP = "prayercaller";

	@ConfigItem(
		keyName = "onlyOnSwitch",
		name = "Only on prayer switch",
		description = "Only call out when the incoming attack needs a different prayer than the one you already have on.<br>"
			+ "e.g. if you're already on Protect from Missiles and a ranged attack comes in, stay silent.",
		position = 0
	)
	default boolean onlyOnSwitch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announceMelee",
		name = "Call out melee",
		description = "Also call out melee attacks (some players only flick mage/range).",
		position = 1
	)
	default boolean announceMelee()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatMessage",
		name = "Chat message",
		description = "Print the prayer to use in the game chat box in addition to the sound.",
		position = 2
	)
	default boolean chatMessage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "yamaEarliestWarning",
		name = "Yama: earliest warning",
		description = "Yama only. Call the prayer the instant Yama starts casting (watches Yama) instead of waiting for<br>"
			+ "the flames/shadow to land on you. Turn off for duo accuracy. Also prevents Yama double-calls.",
		position = 3
	)
	default boolean yamaEarliestWarning()
	{
		return true;
	}

	@ConfigSection(
		name = "Bosses",
		description = "Which bosses to call prayers for.",
		position = 10
	)
	String bossSection = "bossSection";

	@ConfigItem(keyName = "yama", name = "Yama", description = "Call prayers for Yama.",
		position = 11, section = bossSection)
	default boolean yama()
	{
		return true;
	}

	@ConfigItem(keyName = "jad", name = "TzTok-Jad (Fight Cave)", description = "Call prayers for TzTok-Jad.",
		position = 12, section = bossSection)
	default boolean jad()
	{
		return true;
	}

	@ConfigItem(keyName = "hunllef", name = "Hunllef (Gauntlet)",
		description = "Call prayers for the Crystalline/Corrupted Hunllef.", position = 13, section = bossSection)
	default boolean hunllef()
	{
		return true;
	}

	@ConfigItem(keyName = "cerberus", name = "Cerberus", description = "Call prayers for Cerberus (ghost phase).",
		position = 14, section = bossSection)
	default boolean cerberus()
	{
		return true;
	}

	@ConfigItem(keyName = "vorkath", name = "Vorkath", description = "Call prayers for Vorkath's basic attacks.",
		position = 15, section = bossSection)
	default boolean vorkath()
	{
		return true;
	}

	@ConfigItem(keyName = "zulrah", name = "Zulrah", description = "Call prayers for Zulrah's form changes.",
		position = 16, section = bossSection)
	default boolean zulrah()
	{
		return true;
	}

	@ConfigItem(keyName = "olm", name = "Great Olm (CoX)",
		description = "Call prayers for the Great Olm's auto-attacks.", position = 17, section = bossSection)
	default boolean olm()
	{
		return true;
	}

	@ConfigItem(keyName = "akkha", name = "Akkha (ToA)",
		description = "Call prayers for Akkha's mage/range/melee attacks.", position = 18, section = bossSection)
	default boolean akkha()
	{
		return true;
	}

	@ConfigItem(keyName = "zebak", name = "Zebak (ToA)",
		description = "Call prayers for Zebak's mage/range/melee attacks.", position = 19, section = bossSection)
	default boolean zebak()
	{
		return true;
	}

	@ConfigItem(keyName = "kephri", name = "Kephri (ToA)",
		description = "Call Protect from Missiles for Kephri's agile scarabs. (Her fireball is a dodge, not a prayer.)",
		position = 20, section = bossSection)
	default boolean kephri()
	{
		return true;
	}

	@ConfigItem(keyName = "baba", name = "Ba-Ba (ToA)",
		description = "Call Protect from Melee for Ba-Ba. (Her rocks/boulders bypass prayer.)",
		position = 21, section = bossSection)
	default boolean baba()
	{
		return true;
	}

	@ConfigItem(keyName = "wardens", name = "Wardens (ToA)",
		description = "Call prayers for the Wardens' phase-2 mage/range auto-attacks.", position = 22, section = bossSection)
	default boolean wardens()
	{
		return true;
	}

	@ConfigItem(keyName = "muspah", name = "Phantom Muspah",
		description = "Call prayers for Phantom Muspah's mage/range/melee attacks.", position = 23, section = bossSection)
	default boolean muspah()
	{
		return true;
	}

	@ConfigItem(keyName = "inferno", name = "The Inferno",
		description = "Call prayers for Inferno monsters (bat/ranger/mager/meleer, blob splits, Jad).<br>"
			+ "Busy waves can be noisy - 'Only on prayer switch' and turning off melee help.",
		position = 24, section = bossSection)
	default boolean inferno()
	{
		return true;
	}

	@ConfigSection(
		name = "NPC overlays",
		description = "On-screen status text drawn above NPCs.",
		position = 18
	)
	String overlaySection = "overlaySection";

	@ConfigItem(keyName = "freezeTimers", name = "Freeze timer",
		description = "Show a countdown above an NPC while it is frozen/bound (Ice spells, Bind/Snare/Entangle).",
		position = 19, section = overlaySection)
	default boolean freezeTimers()
	{
		return true;
	}

	@ConfigItem(keyName = "defenceTracker", name = "Defence reduction",
		description = "Show an estimated defence reduction above an NPC from special attacks (Dragon Warhammer,<br>"
			+ "Elder Maul, Statius). Estimate only - the client cannot read an NPC's real defence.",
		position = 20, section = overlaySection)
	default boolean defenceTracker()
	{
		return true;
	}

	@ConfigSection(
		name = "Sounds",
		description = "Which sound plays for each attack style.",
		position = 25,
		closedByDefault = true
	)
	String soundSection = "soundSection";

	@ConfigItem(keyName = "soundsEnabled", name = "Play sounds", description = "Master toggle for the audio callouts.",
		position = 21, section = soundSection)
	default boolean soundsEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "useCustomSounds", name = "Use custom sounds",
		description = "Play the bundled custom voice clips (magic/ranged/melee). Turn off to use the in-game<br>"
			+ "sound effect IDs below instead.", position = 22, section = soundSection)
	default boolean useCustomSounds()
	{
		return true;
	}

	@ConfigItem(keyName = "voicePack", name = "Voice",
		description = "Which set of spoken callout clips to use (only applies when custom sounds are on).",
		position = 23, section = soundSection)
	default VoicePack voicePack()
	{
		return VoicePack.VOICE_1;
	}

	@ConfigItem(keyName = "killSounds", name = "Kill sound",
		description = "Play a celebratory sound when you kill a boss (currently Yama).",
		position = 22, section = soundSection)
	default boolean killSounds()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "customVolume", name = "Custom sound volume",
		description = "Volume (0-100) for the bundled custom sounds. Drag to taste.", position = 23, section = soundSection)
	default int customVolume()
	{
		return 50;
	}

	@Range(min = 0)
	@ConfigItem(keyName = "magicSoundId", name = "Magic sound ID",
		description = "In-game sound effect ID for magic (used when custom sounds are off). Default 2675 = Protect from Magic.",
		position = 24, section = soundSection)
	default int magicSoundId()
	{
		return 2675;
	}

	@Range(min = 0)
	@ConfigItem(keyName = "rangedSoundId", name = "Ranged sound ID",
		description = "In-game sound effect ID for ranged (used when custom sounds are off). Default 2677 = Protect from Missiles.",
		position = 25, section = soundSection)
	default int rangedSoundId()
	{
		return 2677;
	}

	@Range(min = 0)
	@ConfigItem(keyName = "meleeSoundId", name = "Melee sound ID",
		description = "In-game sound effect ID for melee (used when custom sounds are off). Default 2676 = Protect from Melee.",
		position = 26, section = soundSection)
	default int meleeSoundId()
	{
		return 2676;
	}

	@ConfigSection(
		name = "Debug",
		description = "Tools for verifying / tweaking detection IDs in-game.",
		position = 30,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(keyName = "debugLogging", name = "Log spotanims & animations",
		description = "Print spotanims on you and animations/projectiles near the boss to chat. Use this to verify or<br>"
			+ "fix IDs if Jagex changes them.", position = 31, section = debugSection)
	default boolean debugLogging()
	{
		return false;
	}

	@ConfigItem(keyName = "testHotkey", name = "Test callout hotkey",
		description = "Press in-game to cycle Magic -> Ranged -> Melee callouts so you can hear the sounds without a boss.",
		position = 32, section = debugSection)
	default Keybind testHotkey()
	{
		return Keybind.NOT_SET;
	}
}
