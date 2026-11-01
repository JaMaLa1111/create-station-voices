## 1.2.0

- Implemented forced silence tag parsing (`[s:X]`) in `TextSanitizer` and both audio generation pipelines (Web API and local Piper), injecting appropriately padded silent audio buffers.
- Increased max string lengths in network payloads: announcement text limits raised to 1000 characters, and train profile JSON payloads raised to 262144 characters.
- Added `NetworkConsentScreen` and client config option `allowNetworkUsage` to guard external API and model download requests.
- `PiperManager` now explicitly clears the `espeak-ng-data` directory prior to re-extraction to prevent initialization issues on repeat runs.
- Fixed `ground` display transform in all announcer block models (`ra_noconfig_*`, `ar_*`, `speaker_*`, `train_announcer_block`), replaced off-center `translation: [0, 6, 9]` with the vanilla standard `rotation: [0, 0, 0], translation: [0, 3, 0]` so dropped items spin on-center.

## 1.1.1 (Retracted)

*Note: Version 1.1.1 was retracted, but all of its changes remain and are included in version 1.2.0.*

- Fixed native Piper ONNX runtime failing to initialize on servers with restrictive temp-folder permissions. Native DLLs now extract to `<game>/create_station_voices_piper/native` (alongside voice models) instead of the system temp folder.
- Fixed `IllegalAccessError` crash caused by piper-jni 1.4.2.1's breaking API change. Pinned piper-jni to 1.4.2 (last instance-API version) via jarJar range `[1.4.1, 1.4.2.1)`.
- Fixed duplicate `setupPiper()` call on dedicated servers (onCommonSetup + onServerSetup both firing) that caused a secondary `AccessDeniedException` from re-extracting already-loaded DLLs.
- Moved `setupPiper()` from `onCommonSetup` to `ServerAboutToStartEvent` so the server config is loaded before reading `allowVcRedistAutoInstall`.
- Added the native directory to the Windows DLL search path via `SetDllDirectoryW` so `onnxruntime.dll`'s DllMain can locate companion DLLs.
- Added a `setupAttempted` guard to prevent retrying Piper initialization after a failure, avoiding Windows locked-DLL errors.
- Added `allowVcRedistAutoInstall` config option (default `false`) in the `tts` section. On `ERROR_DLL_INIT_FAILED` (Win32 1114, caused by the JVM shipping an outdated VC++ runtime incompatible with ONNX Runtime 1.22):
  - If `true`: downloads `vc_redist.x64.exe` from Microsoft, installs it silently, then renames the JVM's outdated `msvcp140.dll`/`vcruntime140.dll`/`vcruntime140_1.dll` to `*.bak` so the JVM falls back to the system-installed versions on next restart.
  - If `false` (default): generates `fix-vc-runtime.bat` (with `takeown`/`icacls` for permission handling) for the user to run as administrator.
  - A server restart is always required after the fix is applied, since the old DLLs are already loaded in the current process.
