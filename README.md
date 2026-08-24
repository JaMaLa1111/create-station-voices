# Create: Station Voices
*A Create addon that brings automated, customizable Text-to-Speech announcements to your railway network.*

> [!WARNING]  
> **Work in Progress:** This mod is currently in beta. You may encounter bugs, if you find any, please [open an issue](https://github.com/JaMaLa1111/create-station-voices/issues) on GitHub!

## Features

1. **Dynamic TTS (Piper TTS)**  
   Voices can be downloaded on-demand to minimize the mod's file size.  
   Open the voice manager GUI in-game using:  
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

5. **Audio Falloff**  
   Announcements feature natural sound falloff (volume begins to decrease after one-third of the max range).

6. **Auto-Populate Profiles**  
   Automatically detects and populates profiles for all trains scheduled to pass the station.

**Currently all Block textures are missing**

---

### Voice Models & TTS Engine
- Voice models are downloaded from [Hugging Face](https://huggingface.co/Ja-Ma-La1111/piper-voice-mirror).
- By default, TTS is generated locally using the bundled Piper TTS binary and downloaded models.
- The configuration file includes an option to use an external API (currently limited to a private API).

---

## Dependencies
- **Minecraft:** `1.21.1`
- **NeoForge:** `21.1.219+`
- **Create:** `6.0+`
