# Yama Prayer Caller

A RuneLite plugin that plays an **audio cue telling you which protection prayer to use, right as Yama
attacks** — so you can pray-flick the boss by ear instead of staring at his animations.

- **Flames envelop you** → Yama is attacking with **Magic** → plays the *Protect from Magic* sound.
- **Shadow swirls engulf you** → Yama is attacking with **Ranged** → plays the *Protect from Missiles* sound.
- **Axe swing (melee)** → plays the *Protect from Melee* sound (optional).

By default it fires the instant Yama *starts the cast* (watching the boss), which gives you the most
lead time. You can switch to "on impact" if you prefer (more accurate for duos — see below).

## How it works

Yama (NPC `14176`) telegraphs every basic attack with a graphic (spotanim):

| Attack  | On Yama (cast) | On you (impact) | Prayer               | Default sound ID |
|---------|----------------|-----------------|----------------------|------------------|
| Magic   | `3246` (fire)  | `3247` (fire)   | Protect from Magic   | `2675`           |
| Ranged  | `3243` (shadow)| `3244` (shadow) | Protect from Missiles| `2677`           |
| Melee   | anim `12146`   | —               | Protect from Melee   | `2676`           |

By default the plugin plays **bundled custom voice clips** (`magic.wav` / `ranged.wav` / `melee.wav` in
`src/main/resources/com/yamaprayer/`) with an adjustable volume. Turn off **Use custom sounds** to fall
back to in-game sound effect IDs instead (defaults are the real prayer-activation sounds: magic `2675`,
ranged `2677`, melee `2676`), all configurable.

To swap in your own clips, replace those three WAV files and rebuild. Java only decodes PCM WAV/AIFF,
so convert other formats first — e.g. on macOS: `afconvert -f WAVE -d LEI16@44100 in.mp3 out.wav`.

> **Note on the IDs.** These come from RuneLite's decompiled game cache (`gameval/SpotanimID.java`,
> `NpcID.java`, `AnimationID.java`). If Jagex ever changes them and the callouts stop working, turn on
> **Debug → Log spotanims & animations** in the config, do the fight, read the numbers it prints to chat,
> and update the constants at the top of `YamaPrayerPlugin.java`.

### Earliest warning vs. duo accuracy

- **Earliest warning ON (default):** triggers off the cast graphic on Yama. Maximum reaction time.
  In a *duo*, the cast graphic isn't target-aware, so it'll warn even when the attack is aimed at your
  partner.
- **Earliest warning OFF:** triggers only off the graphic that lands on *you* — slightly later, but
  correct in duos.

Melee hits everyone within 3 tiles, so it's always called regardless of this setting.

## Settings

- **Only on prayer switch** – stay silent when you're already on the correct protection prayer; only
  call out when the incoming attack needs a *different* prayer than the one you have on.
- **Earliest warning** – call the prayer the moment Yama casts (vs. when it lands on you). Also
  determines the single detection source, so each attack is only ever announced once.
- **Call out melee** – also announce Yama's melee axe swing.
- **Chat message** – also print the prayer to the chat box.
- **Sounds** – master toggle + a configurable sound effect ID per attack style.
- **Debug** – log every spotanim on you and every animation on Yama to chat (for verifying IDs), plus a
  **Test callout hotkey** that cycles Magic → Ranged → Melee on each press so you can hear the three
  sounds without fighting Yama.

## Building / installing

This is a standard RuneLite external (Plugin Hub style) plugin.

### Prerequisites
- **JDK 11** (RuneLite targets Java 11). On macOS: `brew install openjdk@11`, then point
  `JAVA_HOME` at it. Verify with `java -version` showing `11.x`.
- No need to install Gradle — the included Gradle wrapper (`./gradlew`) downloads it automatically.

### Run a dev client with the plugin loaded
```bash
./gradlew run
```
This launches a full RuneLite client (developer mode) with the plugin side-loaded via
`YamaPrayerPluginTest`. Enable it in the plugin list, go fight Yama.

### Build the jar
```bash
./gradlew build
```

### Logging in with a Jagex Account (dev client)
A `./gradlew run` dev client can't do the Jagex OAuth handoff by itself. Generate a credentials file
from the official launcher and the dev client will read it automatically:

1. In the **Jagex Launcher**, add `--insecure-write-credentials` to RuneLite's launch arguments.
2. Launch RuneLite from the Jagex Launcher and log in once — this writes
   `~/.runelite/credentials.properties`.
3. Run `./gradlew run`; it picks up the credentials and logs in. (Tokens refresh for a while; repeat
   step 2 if login ever stops working. You can remove the flag afterward.)

To hear the callouts without a Yama fight, bind the **Test callout hotkey** (config → Debug) and press
it in-game.

### IntelliJ IDEA
1. Open the project folder (it'll import the Gradle build).
2. Set the project SDK to JDK 11.
3. Install the **Lombok** plugin and enable annotation processing.
4. Run `YamaPrayerPluginTest.main()` to launch the client.

### Publishing to the Plugin Hub
To share it publicly, submit this repo to the
[RuneLite plugin-hub](https://github.com/runelite/plugin-hub) — fork it, add a file under `plugins/`
pointing at your repo + commit hash, and open a PR. `runelite-plugin.properties` is already set up.

## Project layout
```
build.gradle, settings.gradle      Gradle build (RuneLite latest.release)
runelite-plugin.properties         Plugin Hub manifest
src/main/java/com/yamaprayer/
  YamaPrayerPlugin.java            Detection + audio callout logic
  YamaPrayerConfig.java            Settings panel
  YamaAttackStyle.java             Magic / Ranged / Melee -> prayer mapping
src/test/java/com/yamaprayer/
  YamaPrayerPluginTest.java        Dev launcher (./gradlew run)
```

## Status
Code complete. **Not yet compiled in this workspace** — the machine it was authored on only had
JDK 8 installed, and RuneLite requires JDK 11. Install JDK 11 and run `./gradlew run` to build & test.
