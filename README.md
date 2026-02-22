# CinerraCam

CinerraCam is an Android Camera2 project with a DNG-first recording pipeline.

Current implementation focus:

- Multi-module architecture (`app`, `core`, `camera2`, `pipeline`, `storage-dng`, `native-writer`, `benchmark`)
- Real Camera2 + `RAW_SENSOR` capture flow in app UI
- Camera modes: `Photo`, `RAW Video`, `Stress Test`
- In-app settings: RAW size, FPS, stress-test duration
- Live load metrics: captured/written/dropped, avg write time, queue high-watermark, elapsed time
- DNG output saved to dedicated album path `DCIM/CinerraCam` (not Downloads)
- App launcher icon integrated from `app/src/main/res/drawable/ic_launcher.png`

## Project Structure

- `app/` Compose UI, view model, and integration entrypoints
- `core/` Public contracts and shared models
- `camera2/` Camera2 capability/session components
- `pipeline/` Queueing and frame write orchestration
- `storage-dng/` DNG writing and manifest persistence
- `native-writer/` JNI + C++ hot-path scaffold
- `benchmark/` Perf summary utilities
- `docs/` Architecture ADRs and test plans

## Local Setup

1. Install Android SDK and set `sdk.dir` in `local.properties`.
2. Run `gradlew.bat help` (Windows) or `./gradlew help` (Unix).
3. Open project in Android Studio (AGP 8.5.2, JDK 17).
4. Build/install app:
   - `gradlew.bat :app:assembleDebug`
   - `gradlew.bat :app:installDebug`

## Vivo Test Flow

1. Grant camera permission on first launch.
2. Select RAW size and FPS in settings.
3. Use `Photo` mode for single DNG capture.
4. Use `RAW Video` mode for continuous DNG sequence recording.
5. Use `Stress Test` mode to run fixed-duration high-load recording and observe live metrics.
6. Verify output in gallery/file manager under `DCIM/CinerraCam`.

Note: project path contains non-ASCII characters, so `android.overridePathCheck=true` is enabled in `gradle.properties`.
Native C++ build in `native-writer` is disabled by default; enable it with `-PenableNativeWriter=true`.

