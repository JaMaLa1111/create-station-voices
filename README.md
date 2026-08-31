# Create: Station Voices
*A Create addon that brings automated, customizable Text-to-Speech announcements to your railway network.*

> [!WARNING]  
> **Work in Progress:** This mod is currently in beta. You may encounter bugs, if you find any, please [open an issue](https://github.com/JaMaLa1111/create-station-voices/issues) on GitHub!
> If you want to be notified when the mod gets updated or take part in polls for new features join the [dedicated Discord server](https://discord.gg/22zCpHDxnw)

## Features

1. **Dynamic TTS ([Piper TTS](https://github.com/rhasspy/piper))**  
   Voices can be downloaded on-demand to minimize the mod's file size. 
TTS lines are generated once when set (via the Preview button) and then cached. 
   Open the voice manager GUI in-game (requires OP 2 or higher) using:  
   `/create_station_voices piper_models`

2. **Redstone Announcer Block**  
   Plays a set text-to-speech announcement whenever it receives a redstone signal.

3. **Train Announcer Block**  
   Links to either a **Train Station** or a **Train Observer**. It supports multiple profiles for different trains, triggering the announcement when a train arrives or passes by.

4. **Custom Audio Effects**  
   Configure audio directly per announcer block/profile:
    - Pitch & Speed
    - Volume
    - Audible Range (1-64 blocks)
    - A realism Slider (noticeably reduces the quality of the announcement to be more in-line with real-world station announcements)
    - Jingles can be turned on in the effects menu, the selected jingle will play either before, after or before and after the announcement depending on the selected option. Currently this only includes the DB jingle ("DB Gong")

5. **Audio Falloff**  
   Announcements feature natural sound falloff (volume begins to decrease after one-third of the max range).

6. **Auto-Populate Profiles**  
   Automatically detects and populates profiles for all trains scheduled to pass the station.

~~Currently all Block textures are missing~~<br>
Textures and mod logo added in v0.5.0-beta, all textures can become subject to change.

---

### Voice Models & TTS Engine
- Voice models are downloaded from [Hugging Face](https://huggingface.co/Ja-Ma-La1111/piper-voice-mirror).
- By default, TTS is generated locally using native in-process ONNX runtime Piper TTS and downloaded models.
- The configuration file includes an option to use an external API (currently limited to a private API).
- Supports Windows, Linux, and macOS out of the box via native libraries.
- **Notice:** Due to mod-hosting site moderation policies regarding bundled executable binaries, all mod versions prior to `v0.7.0-beta` (`v0.6.0-beta` and earlier) are exclusively available on [GitHub](https://github.com/JaMaLa1111/create-station-voices/releases). Starting with `v0.7.0-beta`, the mod uses in-process native ONNX Runtime bindings.

---

### Planned features
- More jingles
- Ponder scenes
- A cheap speaker block that can be linked to already placed announcers and will act as a secondary speaker for the announcer
- A Train-Mounted announcer that will function like a Configurable Announcer but play Audio inside the train when it arrives at a station
- Display link compatibility (display the  current announcement as text)

---

## Dependencies
- **Minecraft:** `1.21.1`
- **NeoForge:** `21.1.219+`
- **Create:** `6.0+`
