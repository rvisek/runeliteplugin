/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the bundled WAV callout clips via RuneLite's {@link AudioPlayer}. Playback (which opens an
 * audio line and reads the clip — a synchronous device operation) runs on a single daemon thread so it
 * never stalls the game thread, even on the first sound or under audio-line contention.
 */
@Slf4j
@Singleton
class SoundManager
{
	@Inject
	private AudioPlayer audioPlayer;

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
	{
		final Thread t = new Thread(r, "prayer-caller-sound");
		t.setDaemon(true);
		return t;
	});

	/**
	 * Queue a bundled clip for playback off the game thread.
	 *
	 * @param resource      resource path relative to this package, e.g. {@code "voice1/magic.wav"}
	 * @param volumePercent 0-100
	 * @return true if the clip exists and was queued (or muted); false if missing, so the caller can fall back
	 */
	boolean play(String resource, int volumePercent)
	{
		if (volumePercent <= 0)
		{
			return true; // muted, but a valid "handled" outcome
		}
		if (SoundManager.class.getResource(resource) == null)
		{
			return false; // missing — let the caller fall back to an in-game sound effect
		}

		final float gain = (float) (20.0 * Math.log10(Math.min(volumePercent, 100) / 100.0));
		executor.submit(() ->
		{
			try
			{
				audioPlayer.play(SoundManager.class, resource, gain);
			}
			catch (Exception e)
			{
				log.warn("Could not play sound {}", resource, e);
			}
		});
		return true;
	}

	void shutdown()
	{
		executor.shutdownNow();
	}
}
