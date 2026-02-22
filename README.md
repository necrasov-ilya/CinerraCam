# CinerraCam

CinerraCam — Android-проект на `Camera2 API` с фокусом на `DNG-first` RAW-пайплайн.

## Текущая реализация

- Камера: только `Camera2 API` + `RAW_SENSOR` (без CameraX).
- Режимы: `Фото`, `Видео RAW`, `Тест`.
- Новый UX камеры:
  - полноширинный верхний бар (`CinerraCam` + статус + настройки),
  - обычный прямоугольный видоискатель,
  - нижняя панель с mode strip + кнопкой затвора + `PARAM`.
- Гибридные настройки:
  - `QuickSettingsDrawer` для быстрых live-параметров,
  - `ProSettingsSheet` для расширенной конфигурации.
- Preview:
  - матрица `fill+crop` через `PreviewViewportCalculator`,
  - выбор preview-размера по аспекту/viewport через `PreviewSizeSelector`.
- Пайплайн записи:
  - сопоставление `Image` + `CaptureResult` по timestamp,
  - ограниченная очередь записи и учёт dropped frames.
- Путь сохранения: `DCIM/CinerraCam` (отдельный альбом, не Downloads).

## Модули

- `app/` — UI и orchestration.
- `core/` — публичные контракты и модели.
- `camera2/` — утилиты Camera2 (capabilities, preview transform, session helpers).
- `pipeline/` — абстракции очередей и backpressure.
- `storage-dng/` — компоненты сохранения DNG/manifest.
- `native-writer/` — JNI + C++ каркас hot-path.
- `benchmark/` — локальные benchmark-сценарии и парсинг метрик.

## Сборка

1. Установите Android SDK (JDK 17).
2. Настройте `local.properties` (`sdk.dir=...`).
3. Соберите debug:
   - Windows: `gradlew.bat :app:assembleDebug`
   - Unix: `./gradlew :app:assembleDebug`

### Native Writer (опционально)

- По умолчанию native-ветка отключена.
- Чтобы включить сборку native writer:
  - установите Android NDK + CMake через SDK Manager в Android Studio,
  - запускайте сборку с флагом `-PenableNativeWriter=true`.

## Команды проверки

- Guardrail по размеру файлов `app`:
  - `powershell -ExecutionPolicy Bypass -File scripts/check_app_file_lengths.ps1`
- Debug-сборка:
  - `gradlew.bat :app:assembleDebug`

## Документация

- Архитектура: `docs/architecture.md`
- План тестирования: `docs/testing-plan.md`
- Базовое ADR: `docs/adr/ADR-0001-dng-first.md`
