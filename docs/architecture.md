# Архитектура CinerraCam

- Stage A: DNG-first + UI-first MVP.
- Stage B: профилирование на Vivo и оптимизация NDK hot-path.
- Stage C: точка принятия решения по custom container ветке.

## Runtime-слои

1. `app/ui`
   - `CameraRoute` + `CameraScaffold` + декомпозированные UI-компоненты камеры.
   - Гибридная система настроек:
     - `QuickSettingsDrawer` (правая панель быстрых live-контролов).
     - `ProSettingsSheet` (полноэкранные расширенные настройки).
   - Токены анимаций: `CameraMotionSpec`.
   - Политика тактильной обратной связи: `CameraHaptics`.
2. `app/camera`
   - `RawCameraController` как orchestration-слой камеры.
   - Матрица preview через `camera2.preview.PreviewViewportCalculator` в режиме fill+crop.
   - Выбор preview-размера через `camera2.preview.PreviewSizeSelector`.
3. `app/camera/internal`
   - `RawFramePipeline` для timestamp-matching `Image/CaptureResult`.
   - `FrameWriteTask` для записи DNG и проброса callback-метрик.

## Политика блокировок во время записи

- Можно менять в REC: `WB`, `EV`, стабилизацию, `FPS`.
- Только до старта записи: RAW/photo/video resolution, aspect ratio, manual ISO/exposure.
- Lock-state явно отражается в UI через `buildRecordingLockState`.

## Политика preview

- Режим по умолчанию: fill+crop (без стандартного letterbox).
- Пересчёт матрицы выполняется при:
  - изменении доступности/размера `TextureView`,
  - пересоздании session,
  - восстановлении после возврата приложения в foreground.

## Guardrails

- Скрипт: `scripts/check_app_file_lengths.ps1`.
- Базовый лимит: 300 строк на Kotlin-файл в `app/src/main/java/com/cinerracam/app`.

См. `docs/adr/ADR-0001-dng-first.md` для базового DNG-first решения.
