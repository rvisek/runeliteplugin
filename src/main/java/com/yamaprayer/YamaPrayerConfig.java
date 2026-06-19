/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.yamaprayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(YamaPrayerConfig.GROUP)
public interface YamaPrayerConfig extends Config
{
	String GROUP = "yamaprayer";

	@ConfigItem(
		keyName = "onlyOnSwitch",
		name = "Only on prayer switch",
		description = "Only call out when the incoming attack needs a different prayer than the one you already have on.<br>"
			+ "e.g. if you're already on Protect from Missiles and a ranged attack comes in, stay silent.",
		position = -1
	)
	default boolean onlyOnSwitch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "earliestWarning",
		name = "Earliest warning",
		description = "Call the prayer the instant Yama starts casting (watches Yama) instead of waiting for the<br>"
			+ "flames/shadow to land on you. Gives the most lead time.",
		position = 0
	)
	default boolean earliestWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announceMelee",
		name = "Call out melee",
		description = "Also call out when Yama does a melee axe swing. Yama only melees when he is next to you.",
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

	@ConfigSection(
		name = "Sounds",
		description = "Which sound effect plays for each attack style.",
		position = 10,
		closedByDefault = true
	)
	String soundSection = "soundSection";

	@ConfigItem(
		keyName = "soundsEnabled",
		name = "Play sounds",
		description = "Master toggle for the audio callouts.",
		position = 11,
		section = soundSection
	)
	default boolean soundsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useCustomSounds",
		name = "Use custom sounds",
		description = "Play the bundled custom voice clips (magic/ranged/melee). Turn off to use the in-game<br>"
			+ "sound effect IDs below instead.",
		position = 12,
		section = soundSection
	)
	default boolean useCustomSounds()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "customVolume",
		name = "Custom sound volume",
		description = "Volume (0-100) for the bundled custom sounds. Drag to taste.",
		position = 13,
		section = soundSection
	)
	default int customVolume()
	{
		return 50;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "magicSoundId",
		name = "Magic sound ID",
		description = "In-game sound effect ID for a magic attack (used when custom sounds are off). "
			+ "Default 2675 is the Protect from Magic sound.",
		position = 14,
		section = soundSection
	)
	default int magicSoundId()
	{
		return 2675;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "rangedSoundId",
		name = "Ranged sound ID",
		description = "In-game sound effect ID for a ranged attack (used when custom sounds are off). "
			+ "Default 2677 is the Protect from Missiles sound.",
		position = 15,
		section = soundSection
	)
	default int rangedSoundId()
	{
		return 2677;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "meleeSoundId",
		name = "Melee sound ID",
		description = "In-game sound effect ID for a melee attack (used when custom sounds are off). "
			+ "Default 2676 is the Protect from Melee sound.",
		position = 16,
		section = soundSection
	)
	default int meleeSoundId()
	{
		return 2676;
	}

	@ConfigSection(
		name = "Debug",
		description = "Tools for verifying / tweaking the detection IDs in-game.",
		position = 20,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = "debugLogging",
		name = "Log spotanims & animations",
		description = "Print every spotanim on you and every animation on Yama to the chat box. Use this to confirm<br>"
			+ "the IDs if Jagex ever changes them, then update the plugin.",
		position = 21,
		section = debugSection
	)
	default boolean debugLogging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "testHotkey",
		name = "Test callout hotkey",
		description = "Press in-game to cycle Magic -> Ranged -> Melee callouts so you can hear the sounds without<br>"
			+ "fighting Yama. (Dev convenience; you can leave it unset for normal play.)",
		position = 22,
		section = debugSection
	)
	default Keybind testHotkey()
	{
		return Keybind.NOT_SET;
	}
}
