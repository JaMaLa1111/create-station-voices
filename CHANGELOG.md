## 1.1.1
- Fixed a crash on servers with restrictive folder permissions where the native Piper TTS engine could not extract its files.
- Fixed a startup crash caused by an incompatible library update.
- Fixed a secondary crash that could occur on dedicated servers during Piper initialization.
- The mod now bundles the required Visual C++ Runtime, so Windows users and server owners no longer need to install the VC++ Redistributable manually.
- Improved reliability of the Piper TTS engine startup on Windows.
- Added clearer error messages in the logs when the Piper TTS engine fails to start.

## 1.1.0
- Added path conversion for models to support non-ASCII characters in file paths. This allows for better compatibility with different languages and character sets.
- Added a new Notification that displays when joining a world/server without any models downloaded prompting the user to download one.
- Added a config option for the required permission to download models and manage jingles.
- Fixed missing server-side permission checks for downloading models and managing jingles.
- Fixed all Announcer blocks defaulting to missing models when none were downloaded. Now the block will disable itself and display a message prompting the user to download a model.