/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the bundled WAV callout clips via RuneLite's {@link AudioPlayer}, which handles decoding,
 * the audio line lifecycle, and gain off the game thread.
 */
@Slf4j
@Singleton
class SoundManager
{
	@Inject
	private AudioPlayer audioPlayer;

	/**
	 * Play a clip bundled in this package.
	 *
	 * @param resource      resource path relative to this package, e.g. {@code "voice1/magic.wav"}
	 * @param volumePercent 0-100
	 * @return true if the clip played (or was muted); false if it was missing/failed so the caller can fall back
	 */
	boolean play(String resource, int volumePercent)
	{
		if (volumePercent <= 0)
		{
			return true; // muted, but a valid "handled" outcome
		}

		final float gain = (float) (20.0 * Math.log10(Math.min(volumePercent, 100) / 100.0));
		try
		{
			// play(Class, path, gainDb) resolves the resource relative to this package and handles
			// decoding/line lifecycle off the game thread.
			audioPlayer.play(SoundManager.class, resource, gain);
			return true;
		}
		catch (Exception e)
		{
			log.warn("Could not play sound {}", resource, e);
			return false;
		}
	}
}
