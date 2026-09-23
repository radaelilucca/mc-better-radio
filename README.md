# Better Radio

Better Radio adds client-side hotkeys for Minecraft's natural background music. It supports Minecraft 1.20.1 with Forge and Minecraft 1.21.1 with NeoForge. The server does not need the mod.

## Use

- **Play/Next Music** starts a random background track or advances to another one. Default: **F8**.
- **Play Previous Music** returns to the previous track in the current session's playback history. Default: **F7**.
- **Pause/Resume Music** pauses the current track and resumes it from the same position. Default: **F9**.
- Pressing Play/Next after going back moves forward through tracks already in the history; at the end, it chooses a new track.
- Playback history lasts for the current world session and resets when leaving the world.
- Both keys can be changed in **Options → Controls → Key Binds → Better Radio**.
- Playback uses Minecraft's **Music** volume slider. Jukeboxes and other game sounds are unaffected.
- Better Radio can show a short status toast above the experience bar when playback changes. Set `showNowPlaying` to `false` in `config/better_radio-client.toml` to hide it.
- The toast identifies the action or state: **Previous**, **Next**, **Paused**, **Playing**, or **Muted**.

Music configured as a biome's natural background music is eligible, including tracks supplied by mods and datapacks. The toast uses generic status text because Minecraft does not provide a consistent localized title for background music tracks.

## Install

Choose the JAR matching both your loader and Minecraft version, then place it in the instance's `mods` folder.
