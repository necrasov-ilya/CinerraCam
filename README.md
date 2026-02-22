# CinerraCam

CinerraCam is an Android Camera2 project with a DNG-first recording pipeline.

Current implementation focus:

- Multi-module architecture (`app`, `core`, `camera2`, `pipeline`, `storage-dng`, `native-writer`, `benchmark`)
- UI-first Compose prototype with fake-data mode for emulator workflows
- Core recorder contracts and state machine
- Camera2 RAW capability probing and session lifecycle scaffold
- Pipeline with bounded queue and explicit backpressure handling
- DNG sequence sink with `manifest.json`
- JNI/NDK compression bridge scaffold
- Benchmark module for manifest aggregation

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

Note: project path contains non-ASCII characters, so `android.overridePathCheck=true` is enabled in `gradle.properties`.
Native C++ build in `native-writer` is disabled by default; enable it with `-PenableNativeWriter=true`.


