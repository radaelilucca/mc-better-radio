# Better Radio — Music Ownership Pivot Architecture

## Status

This document describes the approved pivot. The common file-identity/catalog contract, both loader source inventories, sound-definition flattening, fixed-file `MUSIC` playback, gameplay-only vanilla control, concrete-file Previous/Next queue, F7/F8/F9 command integration, reload recovery, contextual selection, and final diagnostics are implemented. Common tests, both loader builds, and both dev-client startup smoke checks passed. Manual in-game audio and HUD checks remain for final acceptance.

## Goal and scope

Better Radio will own gameplay background music playback on the local client. It will build a library of concrete audio files reachable from biome background definitions and other built-in/modded gameplay situation `Music` definitions, plus every registered music-disc song, then select and play those files directly through Minecraft's sound engine.

Targets are Minecraft 1.20.1 (Forge) and 1.21.1 (NeoForge). The feature is gameplay-only. Menu, credits, and other non-gameplay music remain under vanilla control.

Physical jukeboxes remain vanilla. Disc tracks are available as local playlist entries without changing jukebox blocks, their redstone behavior, or their world audio. A physical jukebox continues to play in `RECORDS` while Better Radio's background plays in `MUSIC`, matching vanilla's existing overlay behavior.

## Approved decisions

- **Ownership:** replace vanilla's gameplay background-music selection and playback with a Better Radio-owned player. Do not control menu/non-gameplay music.
- **Pool:** include concrete files reachable from biome and other natural gameplay background `Music` definitions contributed by vanilla, mods, or datapacks, plus concrete files reachable from every registered music-disc song. Do not sweep arbitrary `.ogg` files into the pool.
- **Identity:** a playlist entry is a concrete resolved audio resource, not a `SoundEvent` ID. Keep source affiliations (biome/situation and disc song) as metadata on that file.
- **Discs:** allow local playback in the user's playlist. Never replace, intercept, or stop a physical jukebox. Keep disc audio in the normal `MUSIC` category for personal playback.
- **Jukebox coexistence:** keep the vanilla `RECORDS` sound independent and spatial. The owned `MUSIC` background continues underneath as vanilla background music does.
- **Navigation:** Previous and Next operate on exact files from the session queue. Pause/resume retains the current file and playback position.
- **Random choice:** all eligible files remain in the pool. A soft preference for files associated with the current biome/situation may influence a new random choice; it must not filter out the rest of the pool.
- **Feedback:** keep the short, localized action/status message above the XP bar. It disappears after two seconds without an exit animation and reappears only for a new action or playback-state change. Use `Previous`, `Next`, `Paused`, `Playing`, or `Muted`; identify background audio generically and use a music disc's localized song description when available.
- **Diagnostics:** logs must distinguish the requested file, the actual resolved/playing file, its source affiliations, and queue action so observed audio can be compared with internal state.

## Runtime model

```text
gameplay context
     │
     ├── current biome/situation ── natural Music definitions ─┐
     └── registered jukebox songs ────────────────────────────┤
                                                              ▼
                                                source catalog / reload
                                                              │
                                             flatten event definitions
                                             into concrete audio files
                                                              │
                                   owned session queue ◄── random selector
                                      │       │       │
                                   Previous   Next   Pause/Resume
                                      │       │       │
                                      └───────┴───────┘
                                               │
                                  fixed-file SoundInstance (MUSIC)
                                               │
                             vanilla sound engine + Music volume

Physical jukebox ── vanilla positional SoundInstance (RECORDS), untouched
```

### Catalog and entry identity

The catalog starts from biome and other registered gameplay-situation background `Music` values, together with registered disc-song definitions, rather than scanning all assets. Exclude menu, credits, and other non-gameplay situations. For each source, resolve its `SoundEvent` definition and enumerate the concrete file members it can select, including weighted alternatives and valid nested event references. Deduplicate playback entries by canonical resource location while preserving every source affiliation.

Use the platform's resource/sound registry and resource reload lifecycle so datapack and mod-provided definitions are visible. Rebuild or refresh the catalog after a relevant reload; avoid retaining stale `WeighedSoundEvents` or resource objects. The same file can be referenced by several biomes, sound events, and disc songs, but remains one playback identity with multiple affiliations.

Eligibility must follow the selected gameplay sources. Do not add menu, credits, UI, arbitrary mob/block effects, or unrelated `.ogg` assets just because they exist in a resource pack. If a source definition references a missing or invalid file, skip it with a diagnostic and keep the rest of the catalog usable.

### Playback

The platform adapter starts a sound instance bound to the chosen concrete file path. It uses the vanilla sound engine and the `MUSIC` category so the player's Music slider, pause/mute behavior, and engine lifecycle continue to apply. The implementation must not ask `MusicManager` to resolve a `SoundEvent` again, because that would re-randomize a weighted group and break exact-file history.

The owned gameplay player must prevent vanilla's autonomous gameplay music from competing with it while active, without changing vanilla menu/non-gameplay music. Define and test transitions when entering gameplay, leaving gameplay, disconnecting, or disabling/resetting the owned player. Do not alter other sound categories.

### Queue and selection

Queue entries reference the exact file resource and catalog metadata. Previous selects the prior queue entry; Next moves forward through existing future entries before generating a new random entry. Generating a new choice after moving backward truncates the abandoned future branch. Define behavior for a one-entry pool, catalog changes while an item is playing, natural completion, and world/session reset.

For a newly generated random entry, use a soft biome/situation affinity bias. The candidate pool remains global to the approved sources. The bias should be deterministic under a seeded test and must fall back to any eligible file when no candidate has the current affinity.

Pause/resume acts only on Better Radio's current sound channel and preserves its playback cursor; it must not pause the whole engine or vanilla jukebox sounds.

## Module responsibilities

### `common`

- Loader-neutral file identity and catalog/queue metadata.
- Source affiliation model and deterministic random selection policy.
- Queue state machine, including Previous/Next branching and session reset.
- Playback command/state contracts and toast timing/action state.
- Unit tests for catalog flattening where practical, selection, queue, and state transitions.

### Forge 1.20.1 adapter

- Enumerate natural background `Music` sources and registered `RecordItem` songs.
- Resolve sound definitions to file candidates using 1.20.1 APIs/resource reload hooks.
- Start/pause/stop a fixed concrete resource under `MUSIC`.
- Gate vanilla gameplay background selection while preserving non-gameplay music and normal jukebox behavior.

### NeoForge 1.21.1 adapter

- Enumerate natural background `Music` sources and registered jukebox-song entries/items.
- Resolve sound definitions to file candidates using 1.21.1 APIs/resource reload hooks.
- Start/pause/stop a fixed concrete resource under `MUSIC`.
- Gate vanilla gameplay background selection while preserving non-gameplay music and normal jukebox behavior.

The adapters may differ where the registries, sound definitions, hooks, or sound-instance APIs differ. Keep the common contract as small as both targets allow.

## Diagnostics

Debug logging should make each transition auditable without logging every audio tick. For a catalog refresh, report counts of source definitions, discovered file entries, deduplicated entries, skipped/missing definitions, and source namespaces. For a playback command, report action, requested concrete file, selected source/affinity, actual sound path once resolved, queue cursor/size, and whether the sound channel started, paused, resumed, or stopped. This is specifically intended to compare logs with what the player hears.

## Implementation phases

See the phase-by-phase plan, deliverables, and acceptance checks in `PRD.md`. Phases 0–1 are complete; file expansion, fixed-file playback, owned gameplay control, and final validation are underway.
