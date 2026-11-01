## 1.2.0
- Added a "Forced Silence" option: You can now insert `[s:X]` in the announcement text to add `X` seconds of silence at that point (e.g., `[s:2]` for 2 seconds).
- Added a help button (`?`) to the announcer screens to explain the forced silence feature.
- Increased the maximum announcement text length from 256 to 1000 characters.
- Added a new client config setting `allowNetworkUsage` to control whether the mod can connect to the internet to download voice models or fetch Web API voices. A consent screen will prompt you when attempting to use network features for the first time.

## 1.1.1 (Retracted)
*Note: Version 1.1.1 was retracted, but all of its changes remain and are included in version 1.2.0.*
- Fixed a crash on servers with restrictive folder permissions where the native Piper TTS engine could not extract its files.
- Fixed a startup crash caused by an incompatible library update.
- Fixed a secondary crash that could occur on dedicated servers during Piper initialization.
- Fixed Piper TTS failing to start on Windows servers where the Java installation bundles an outdated Visual C++ runtime. The mod can now automatically download and install the Microsoft Visual C++ Redistributable (if enabled in the config) and generate a helper script (`fix-vc-runtime.bat`) for manual installation. A server restart is required after applying the fix.
- Added a new config option `allowVcRedistAutoInstall` (default: false) to control whether the mod may automatically download and install the VC++ Redistributable.
- Improved reliability of the Piper TTS engine startup on Windows.
- Added clearer error messages in the logs when the Piper TTS engine fails to start.

## 1.1.0
- Added path conversion for models to support non-ASCII characters in file paths. This allows for better compatibility with different languages and character sets.
- Added a new Notification that displays when joining a world/server without any models downloaded prompting the user to download one.
- Added a config option for the required permission to download models and manage jingles.
- Fixed missing server-side permission checks for downloading models and managing jingles.
- Fixed all Announcer blocks defaulting to missing models when none were downloaded. Now the block will disable itself and display a message prompting the user to download a model.