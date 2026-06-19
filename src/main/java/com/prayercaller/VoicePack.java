/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A bundled set of spoken callout clips. Each pack is a resource sub-folder holding
 * {@code magic.wav}, {@code ranged.wav}, and {@code melee.wav}.
 */
@Getter
@RequiredArgsConstructor
enum VoicePack
{
	VOICE_1("Voice 1", "voice1"),
	VOICE_2("Voice 2", "voice2");

	private final String displayName;
	private final String dir;

	@Override
	public String toString()
	{
		return displayName;
	}
}
