# ADR-0001: DNG-first Baseline

## Status
Accepted

## Context
The project starts from a research document and needs a practical implementation path with visible progress on UI and recording pipeline.

## Decision
Use DNG-first architecture for Stage A:

- Capture RAW frames with Camera2
- Persist as DNG sequence + manifest (+ optional audio timeline)
- Implement bounded queue and explicit backpressure/drop accounting

## Consequences

- Faster MVP delivery and easier debugging
- Higher filesystem pressure at 4K30
- Stage B profiling determines whether to move to a custom container branch
