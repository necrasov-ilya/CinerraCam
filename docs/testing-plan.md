# Testing Plan

## Device Matrix

- Primary: Vivo X200 Ultra (real RAW tests)
- Emulator: Android API 34+ (UI smoke and fake data mode)

## Core Checks

1. Capability discovery correctness
2. Start/stop stability (20 cycles)
3. Frame queue backpressure handling
4. Manifest consistency
5. DNG file readability spot checks

## Performance

- Baseline scenarios: 1080p24, 4K24, 4K30
- Metrics: throughput, avg write time, dropped frame rate, queue high watermark
