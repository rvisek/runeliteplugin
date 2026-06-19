# Prayer Caller

A RuneLite plugin that plays an **audio cue telling you which protection prayer to use as a boss
attacks** — so you can pray-flick by ear instead of watching animations.

By default it plays bundled custom voice clips (`magic` / `ranged` / `melee`); you can switch to in-game
prayer sounds and tune the volume. It only speaks when you actually need to **switch** prayer (configurable).

## Supported bosses

| Boss | Detected via | Notes |
|------|--------------|-------|
| **Yama** | spotanim (flames = magic, shadow = ranged) + melee animation | "Earliest warning" calls it the instant Yama casts |
| **TzTok-Jad** (Fight Cave) | distinct mage / ranged / melee attack animations | the classic prayer-switch boss |
| **Hunllef** (The Gauntlet) | attack projectile | crystalline **and** corrupted |
| **Cerberus** | the summoned-soul ghost that spawns | announces the souls in spawn order (one per tick) |
| **Vorkath** | attack projectile | the two basics share an animation, so projectile-based |
| **Zulrah** | form NPC id on spawn | green = range, blue = magic, red = melee (dodge) |
| **Great Olm** (CoX) | head auto-attack projectile | magic = green orb, ranged = crystal |
| **Akkha** (ToA) | mage / ranged / melee attack animations | incl. sword (enrage) variants |
| **Zebak** (ToA) | attack projectile (+ melee animation) | mage/range share an animation |
| **Kephri** (ToA) | agile-scarab projectile → ranged | her fireball is **dodged, not prayed** |
| **Ba-Ba** (ToA) | melee animation | melee-only; rocks bypass prayer |
| **Wardens** (ToA) | phase-2 auto-attack projectile | alternating magic / ranged |

Each boss has its own on/off toggle in the config (**Bosses** section).

Intentionally limited or excluded, because there's no clean prayer to call:
- **TzKal-Zuk** — single typeless/hybrid blast, no mage-vs-range tell.
- **Kephri** — her main fireball is a positional dodge; only the agile-scarab ranged hit is a real
  prayer call, so that's all she triggers.
- **Ba-Ba** — purely melee; her boulder/rock mechanics bypass protection prayers entirely.
- **Wardens** — only the phase-2 alternating autos are covered; the phase-3 "prayer skull" sequence
  isn't (it needs per-projectile flight timing).

### Where the IDs come from

All detection IDs are sourced from RuneLite's decompiled game cache (`gameval/` files) and the
corresponding (Open)OSRS boss plugins, then cross-checked against the OSRS Wiki. They live in
`Bosses.java`. If Jagex ever changes an id and a callout stops working, turn on **Debug → Log spotanims &
animations** — it prints the boss's animations, your spotanims, and nearby projectile ids to chat. Read
the live value and update the matching entry in `Bosses.java`.

## Settings

- **Only on prayer switch** – stay silent when you're already on the correct protection prayer.
- **Call out melee** – also announce melee attacks.
- **Chat message** – also print the prayer to chat.
- **Yama: earliest warning** – Yama only; call on his cast (max lead time) vs. on impact (duo-accurate).
  Also picks a single detection source so Yama is never double-called.
- **Bosses** – per-boss on/off toggles.
- **Sounds** – master toggle, **Use custom sounds**, **Kill sound** (a celebratory clip when you kill a
  boss — currently Yama plays `goodboy.wav`), **Custom sound volume** (0–100, default 50), and a
  configurable in-game sound effect id per style for when custom sounds are off.
- **Debug** – log ids to chat, and a **Test callout hotkey** that cycles Magic → Ranged → Melee so you
  can hear the sounds without a boss.

### Custom sounds

The bundled clips are `magic.wav` / `ranged.wav` / `melee.wav` in `src/main/resources/com/prayercaller/`.
To use your own, replace those three files and rebuild. Java only decodes PCM WAV/AIFF, so convert other
formats first — e.g. on macOS: `afconvert -f WAVE -d LEI16@44100 in.mp3 out.wav`.

## How it works

The plugin is data-driven. Each boss is a `BossDefinition`: a set of NPC ids that mean "you're fighting
it", plus a list of `AttackTrigger`s mapping a game-event id to an attack style. Trigger types:

- `NPC_ANIMATION` – an animation played by the boss (Jad, Yama melee)
- `NPC_SPOTANIM` / `PLAYER_SPOTANIM` – a graphic on the boss / on you (Yama)
- `PROJECTILE` – a projectile launched during the fight (Hunllef, Vorkath, Olm)
- `NPC_SPAWN` – an NPC appearing, e.g. a Zulrah form or a Cerberus soul

Detection is scoped to whichever supported boss is currently present, so ids never collide between fights.

## Building / installing

A standard RuneLite external (Plugin Hub style) plugin.

### Prerequisites
- **JDK 11** (RuneLite targets Java 11). On macOS: `brew install openjdk@11`, then point `JAVA_HOME` at it.
- No need to install Gradle — the included wrapper (`./gradlew`) downloads it.

### Run a dev client with the plugin loaded
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home   # if keg-only
./gradlew run
```
Launches a RuneLite client (developer mode) with the plugin side-loaded. Enable **Prayer Caller** in the
plugin list.

### Build the jar
```bash
./gradlew build
```

### Logging in with a Jagex Account (dev client)
A `./gradlew run` dev client can't do the Jagex OAuth handoff itself. Generate a credentials file from the
official launcher and the dev client will read it automatically:

1. In the **Jagex Launcher**, add `--insecure-write-credentials` to RuneLite's launch arguments.
2. Launch RuneLite from the Jagex Launcher and log in once — writes `~/.runelite/credentials.properties`.
3. Run `./gradlew run`; it picks up the credentials and logs in.

To hear the callouts without a fight, bind the **Test callout hotkey** (config → Debug) and press it.

### Publishing to the Plugin Hub
Submit this repo to the [RuneLite plugin-hub](https://github.com/runelite/plugin-hub):
fork it, add a file under `plugins/` pointing at your repo + commit hash, open a PR.

## Project layout
```
build.gradle, settings.gradle      Gradle build (RuneLite latest.release)
runelite-plugin.properties         Plugin Hub manifest
src/main/java/com/prayercaller/
  PrayerCallerPlugin.java          Event handling + callout logic
  PrayerCallerConfig.java          Settings panel
  Bosses.java                      The boss registry (all detection ids live here)
  BossDefinition.java              One boss: presence ids + triggers
  AttackTrigger.java, TriggerType.java
  AttackStyle.java                 Magic / Ranged / Melee -> prayer + sound
  SoundManager.java                Bundled-WAV playback with volume
src/main/resources/com/prayercaller/
  magic.wav, ranged.wav, melee.wav Custom callout clips
src/test/java/com/prayercaller/
  PrayerCallerPluginTest.java      Dev launcher (./gradlew run)
```

## Status
Builds and launches cleanly (verified with JDK 11). **In-game per-boss behavior is not yet verified** —
detection ids are best-effort from authoritative sources. Use the Debug logging to confirm/fix ids during
real fights and please report any that are off.
