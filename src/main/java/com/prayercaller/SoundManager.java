/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Plays custom WAV clips bundled with the plugin. Java's audio stack only decodes PCM WAV/AIFF
 * (not MP3), so the source files are converted to WAV at build time and loaded from resources here.
 * Playback runs on a single daemon thread so callouts don't block the game thread and don't exhaust
 * audio lines.
 */
@Slf4j
class SoundManager
{
	private final Map<String, byte[]> cache = new HashMap<>();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "prayer-caller-sound");
		t.setDaemon(true);
		return t;
	});

	/**
	 * @return true if the resource exists and was queued to play; false if missing (caller can fall back).
	 */
	boolean play(String resourceName, int volumePercent)
	{
		if (volumePercent <= 0)
		{
			return true; // muted, but a valid "handled" outcome
		}

		final byte[] data = load(resourceName);
		if (data == null)
		{
			return false;
		}

		executor.submit(() -> doPlay(data, volumePercent));
		return true;
	}

	private byte[] load(String resourceName)
	{
		if (cache.containsKey(resourceName))
		{
			return cache.get(resourceName);
		}

		byte[] data = null;
		try (InputStream is = SoundManager.class.getResourceAsStream(resourceName))
		{
			if (is != null)
			{
				data = readAll(is);
			}
		}
		catch (IOException e)
		{
			log.warn("Could not read sound resource {}", resourceName, e);
		}

		cache.put(resourceName, data); // cache misses too, so we don't retry a missing file every attack
		return data;
	}

	private void doPlay(byte[] data, int volumePercent)
	{
		try (AudioInputStream ais = AudioSystem.getAudioInputStream(
			new BufferedInputStream(new ByteArrayInputStream(data))))
		{
			final Clip clip = AudioSystem.getClip();
			final CountDownLatch finished = new CountDownLatch(1);
			clip.addLineListener(event ->
			{
				if (event.getType() == LineEvent.Type.STOP)
				{
					finished.countDown();
				}
			});
			clip.open(ais);
			applyVolume(clip, volumePercent);
			clip.start();
			finished.await();
			clip.close();
		}
		catch (Exception e)
		{
			log.warn("Failed to play prayer-caller sound", e);
		}
	}

	private void applyVolume(Clip clip, int volumePercent)
	{
		if (volumePercent >= 100 || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			return;
		}
		final FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
		float db = (float) (20.0 * Math.log10(volumePercent / 100.0));
		db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
		gain.setValue(db);
	}

	private static byte[] readAll(InputStream is) throws IOException
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buf = new byte[8192];
		int read;
		while ((read = is.read(buf)) != -1)
		{
			out.write(buf, 0, read);
		}
		return out.toByteArray();
	}

	void shutdown()
	{
		executor.shutdownNow();
	}
}
