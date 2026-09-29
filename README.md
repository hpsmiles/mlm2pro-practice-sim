# MLM2PRO Practice Sim

Tour-style golf practice & sim app for the Rapsodo MLM2PRO launch monitor.
Single-device: runs entirely on an Android tablet — no companion PC, no cloud.

- Project setup & tech decisions: [AGENTS.md](AGENTS.md)
- Development roadmap: [ROADMAP.md](ROADMAP.md)

## Status

Shipped through **M5.5** (2026-09-29): live BLE shot pipeline (decode → ball-flight physics → POV/top-down range with tracers, follow-cam and standing distance signs), Room-backed sessions with club tagging, history with per-club AVG/σ and shot exclusion, club types with session-scoped TEST clubs — plus two practice games, **Target Practice** (rest-position band scoring on an archery target face) and **Break the Pane** (fly-through + land-on-green break rule), with game history. Next up: M6 bag mapping & true gapping.

## Modules

`:app` · `:core:ble` (MLM2PRO byte-decoder) · `:core:physics` (ball-flight ODE) · `:core:connect` (GATT client) · `:core:designsystem` · `:core:data` (Room)

## Build

Requires JDK 17 and an Android SDK (Android Studio bundles both).

    ./gradlew build
