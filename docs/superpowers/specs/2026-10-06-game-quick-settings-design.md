# Design: In-Game Quick Settings

Date: 2026-10-06
Status: Approved

## Problem

When the user is mid-game (Target Practice / Break the Pane) and taps the SETTINGS
rail button, the game session ends: `requestTab(SETTINGS)` resets
`activeGame = GameMode.NONE` (`AppRoot.kt:507-527`), the in-memory game progress
(shots/broken cells) is unrecoverable, and subsequent shots route to the range
session instead of the game. The user cannot even turn sounds on/off without
abandoning a game.

## Decision

Add an **in-game quick-settings overlay** instead of making the SETTINGS tab
game-safe. The full SETTINGS tab keeps its current behavior (leaving a game ends
it). The overlay gives in-game access to game-relevant quick toggles — today,
sound on/off is the only game-facing setting.

## Behavior

- **Entry point**: a small gear button on both game play screens
  (`TargetPracticePlay`, `BreakPanePlay`), placed consistently in the play HUD.
  Not shown on the game picker or result overlays.
- **Overlay**: modal `AlertDialog` (same pattern as the existing "LEAVE GAME?"
  dialog in `AppRoot.kt:713-730`), shared by both games via a single composable
  in `app/.../games/` (e.g. `GameQuickSettings.kt`).
- **Contents**: a Sound on/off toggle. The overlay is structured so future quick
  toggles add a row; no speculative settings are added now.
- **State & persistence**: the toggle drives the same hoisted `soundsEnabled`
  state in `AppRoot` (`AppRoot.kt:142-149`) that feeds `GameAudio.enabled` via
  `LaunchedEffect`, and persists through the existing `SoundPrefStore`
  (`sounds_enabled` key, SharedPreferences). One source of truth; the full
  SETTINGS screen reflects the change too.
- **Game safety**: the overlay never touches `activeGame`, `tab`, `GameLeavePolicy`,
  or `routeShot`. Game state holders (`TargetPracticeGame`, `BreakThePaneGame`)
  are unmodified.
- **Shots while open**: the dialog is modal (blocks game interaction), but a radar
  shot arriving while it is open still routes to the game via `routeShot` and
  counts normally.
- **Dismissal**: tapping outside / dismiss button closes the overlay and returns
  to the exact game state (shot count, sequence, reveal state) with no side effects.

## Out of scope

- Preserving game sessions across SETTINGS/RANGE/HISTORY tab navigation.
- Persisting mid-game state to Room or surviving app restart.
- Adding non-sound quick toggles (no other game-facing settings exist; green/turf
  conditions are range-rendering concerns and remain on the SETTINGS tab).

## Testing

- `GameLeavePolicy`, navigation, and shot routing are untouched — existing tests
  (`GameLeavePolicyTest`, `GamesScreenTest`) must still pass.
- No new pure logic is introduced (UI-only feature), so no new JVM unit tests are
  required. Verification is a build + manual check on device: open gear overlay
  mid-game, toggle sound, dismiss, confirm game continues and the shot count is
  unchanged.
