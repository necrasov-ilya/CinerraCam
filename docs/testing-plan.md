# План тестирования

## Матрица устройств

- Основное устройство: Vivo X200 Ultra (реальные RAW-тесты).
- Эмулятор: Android API 34+ (smoke UI и интерактивные проверки).

## Функциональные проверки

1. Discovery capability:
   - корректное определение поддержки RAW;
   - корректный опрос доступных resolution/aspect ratio/stabilization режимов.
2. Жизненный цикл session:
   - цикл start/stop x20;
   - цикл foreground/background recovery x20;
   - отсутствие устойчивого `CameraDevice` error-state после recovery.
3. Корректность preview:
   - портрет/ландшафт;
   - изменение размеров view и смена режима;
   - отсутствие растянутого preview при fill+crop поведении.
4. Политика блокировок контролов:
   - в REC применяются WB/EV/stabilization/FPS;
   - в REC блокируются resolution/aspect/manual controls.
5. Стабильность записи:
   - длительный прогон RAW video/stress;
   - контроль dropped frames и queue high-watermark.

## DNG и метрики

1. Проверка открываемости DNG-сэмплов во внешних RAW-инструментах.
2. Проверка консистентности таймлайна:
   - согласованность `captured/written/dropped`;
   - изменение write latency (`avgWriteMs`) под нагрузкой.

## UX-валидация

1. Гибридная панель настроек:
   - quick drawer открывается/закрывается без утечек ввода;
   - pro sheet открывается из top bar и quick drawer.
2. Motion-поведение:
   - плавные переходы mode strip и shutter;
   - отсутствие тяжёлых эффектов в критичном recording path.
3. Haptics-поведение:
   - корректные паттерны на события;
   - debounce на быстрых изменениях слайдеров/частых тачах.

## Автоматизация

- Guardrail по размеру файлов:
  - `powershell -ExecutionPolicy Bypass -File scripts/check_app_file_lengths.ps1`
