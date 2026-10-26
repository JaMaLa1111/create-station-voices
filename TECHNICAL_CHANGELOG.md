## 1.1.1
- Fixed native Piper ONNX runtime failing to initialize on servers with restrictive temp-folder permissions. Native DLLs now extract to `<game>/create_station_voices_piper/native` (alongside voice models) instead of the system temp folder.
- Fixed `IllegalAccessError` crash caused by piper-jni 1.4.2.1's breaking API change. Pinned piper-jni to 1.4.2 (last instance-API version) via jarJar range `[1.4.1, 1.4.2.1)`.
- Fixed duplicate `setupPiper()` call on dedicated servers (onCommonSetup + onServerSetup both firing) that caused a secondary `AccessDeniedException` from re-extracting already-loaded DLLs.
- Added the native directory to the Windows DLL search path via `SetDllDirectoryW` so `onnxruntime.dll`'s DllMain can locate companion DLLs.
- Added a `setupAttempted` guard to prevent retrying Piper initialization after a failure, avoiding Windows locked-DLL errors.
- On `ERROR_DLL_INIT_FAILED` (Win32 1114), the mod now automatically downloads and installs the Microsoft Visual C++ Redistributable (`vc_redist.x64.exe`), then renames the JVM's outdated bundled VC++ runtime DLLs so the JVM falls back to the system-installed versions. If auto-install fails (permissions), a `fix-vc-runtime.bat` script is generated for the user to run as administrator.
