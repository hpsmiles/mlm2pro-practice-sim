# In-Game Quick Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user change game-facing settings (sound on/off) mid-game via a gear button + dialog on the game play screens, without ending the game session.

**Architecture:** A shared UI composable (`GameQuickSettings.kt`) providing a gear chip + modal `AlertDialog`, placed in each game play screen's HUD corner. The toggle drives the existing hoisted `soundsEnabled` state in `AppRoot` (single source of truth, persists via `SoundPrefStore`, feeds `GameAudio.enabled`). Navigation, `GameLeavePolicy`, `activeGame`, and shot routing are untouched.

**Tech Stack:** Kotlin + Jetpack Compose (Material3 `AlertDialog`), SharedPreferences via existing `SoundPrefStore`. No Navigation-Compose, no ViewModels (repo convention). Spec: `docs/superpowers/specs/2026-10-06-game-quick-settings-design.md`.

**Environment note (Windows):** JDK is not on `PATH`. Prefix every Gradle command with:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```
Gradle flags precede task names; quiet output during configure ≠ hung.

**Existing anchors (verified):**
- `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt:142-149` — `soundsEnabled` state, `selectSoundsEnabled()`, `LaunchedEffect { gameAudio.enabled = soundsEnabled }`.
- `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt:628-637` — `GamesScreen(...)` call site.
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt:50-57` — `GamesScreen` signature; `:102-122` — `TargetPracticePlay` / `BreakPanePlay` call sites.
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt:41-45` — signature; HUD `Row` at `:118-147`; result overlay `:151-190`; confetti `:193-195`; composable ends `:204`.
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt:40-46` — signature; HUD `Column` at `:130-153`; result overlay `:170-196`; confetti `~:199`; composable ends `~:205`.
- Dialog pattern: `AppRoot.kt:713-730` (Material3 `AlertDialog` + `TextButton`s).
- Toggle styling source (copy): `SettingsScreen.kt:189-207` ("SOUNDS: ON/OFF" chip). `chipStyle()` there is private — inline equivalent colors below.
- The repo uses **no Material icons**; the gear is a text glyph chip.

---

### Task 1: Shared quick-settings composable

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameQuickSettings.kt`

- [ ] **Step 1: Create `GameQuickSettings.kt`**

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography

/**
 * In-game quick settings (spec 2026-10-06): gear chip + modal dialog so the
 * user can flip game-facing settings (today: sound) without leaving the game.
 * Pure UI over hoisted state — never touches activeGame / tab / shot routing,
 * so mid-game progress survives. Future quick toggles add a row to the dialog.
 */

/** Small gear glyph chip for a play-screen HUD corner. */
@Composable
fun GameQuickSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "⚙",
        style = GolfTypography.MetricValue,
        color = GolfColors.TextPrimary,
        modifier = modifier
            .semantics { contentDescription = "Game quick settings" }
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick)
            .padding(horizontal = GolfSpacing.Sm, vertical = 4.dp),
    )
}

/**
 * Modal quick-settings dialog. Sounds row mirrors Settings > SOUND
 * (SettingsScreen.kt "SOUNDS: ON/OFF" chip styling).
 */
@Composable
fun GameQuickSettingsDialog(
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GAME SETTINGS") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (soundsEnabled) "SOUNDS: ON" else "SOUNDS: OFF",
                        style = GolfTypography.MetricLabel,
                        color = if (soundsEnabled) GolfColors.Teal else GolfColors.TextSecondary,
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (soundsEnabled) GolfColors.Teal else GolfColors.Line,
                                RoundedCornerShape(50),
                            )
                            .clickable { onSoundsChange(!soundsEnabled) }
                            .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
                    )
                }
                Text(
                    text = "Game cue sounds (glass break, success, miss).",
                    style = GolfTypography.Body,
                    color = GolfColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = GolfSpacing.Sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("DONE") }
        },
    )
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add "app/src/main/kotlin/com/hpsmiles/golfsim/games/GameQuickSettings.kt"
git commit -m "feat(games): add shared in-game quick settings dialog"
```

---

### Task 2: Surface quick settings in both play screens + wire state

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt:50-57,102-122`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt:41-45` (+ HUD area `~:196`)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt:40-46` (+ HUD area `~:199`)
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt:629-636`

These four edits are atomic: the new parameters have no defaults, so all must land before the module compiles again.

- [ ] **Step 1: `GamesScreen` — add params, thread to both play screens**

In `GamesScreen.kt`, replace the signature (lines 50-57):
```kotlin
@Composable
fun GamesScreen(
    targetPractice: TargetPracticeGame,
    breakPane: BreakThePaneGame,
    activeGame: GameMode,
    onActiveGameChange: (GameMode) -> Unit,
    gameAudio: GameAudio,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
```
Replace the `TargetPracticePlay` call (lines 102-111):
```kotlin
        activeGame == GameMode.TARGET_PRACTICE -> {
            TargetPracticePlay(
                game = targetPractice,
                onBack = {
                    onActiveGameChange(GameMode.NONE)
                    pickerState = "PICKER"
                },
                soundsEnabled = soundsEnabled,
                onSoundsChange = onSoundsChange,
                modifier = modifier,
            )
        }
```
Replace the `BreakPanePlay` call (lines 112-122):
```kotlin
        activeGame == GameMode.BREAK_PANE -> {
            BreakPanePlay(
                game = breakPane,
                gameAudio = gameAudio,
                onBack = {
                    onActiveGameChange(GameMode.NONE)
                    pickerState = "PICKER"
                },
                soundsEnabled = soundsEnabled,
                onSoundsChange = onSoundsChange,
                modifier = modifier,
            )
        }
```

- [ ] **Step 2: `TargetPracticePlay` — add params, gear button, dialog**

Replace the signature (lines 40-45):
```kotlin
@Composable
fun TargetPracticePlay(
    game: TargetPracticeGame,
    onBack: () -> Unit,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
```
Inside the function, next to the other local state (after `revealedCount` at line 51):
```kotlin
    var showQuickSettings by remember { mutableStateOf(false) }
```
At the end of the canvas `Box` (the `Box(modifier = Modifier.weight(1f).fillMaxHeight())` block, after the `ConfettiBurst` block at line 193-195, before its closing `}` at line 196) add:
```kotlin
                // In-game quick settings: gear at TopEnd; dialog never touches
                // game state, so mid-game progress survives (spec 2026-10-06).
                GameQuickSettingsButton(
                    onClick = { showQuickSettings = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm),
                )
                if (showQuickSettings) {
                    GameQuickSettingsDialog(
                        soundsEnabled = soundsEnabled,
                        onSoundsChange = onSoundsChange,
                        onDismiss = { showQuickSettings = false },
                    )
                }
```
(No new imports needed: `mutableStateOf/remember/getValue/setValue`, `Alignment`, `Modifier`, `padding` are already imported.)

- [ ] **Step 3: `BreakPanePlay` — add params, gear button, dialog**

Replace the signature (lines 39-46):
```kotlin
@Composable
fun BreakPanePlay(
    game: BreakThePaneGame,
    onBack: () -> Unit,
    /** Cue sounds for the playback: glass break at pane crossing, ding on green, fail on miss. */
    gameAudio: GameAudio,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
```
Inside the function, after `speedMult` (line 48):
```kotlin
    var showQuickSettings by remember { mutableStateOf(false) }
```
At the end of the canvas `Box` (the `Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds())` block, after the `ConfettiBurst` block that follows the result overlay, before the block's closing `}`) add:
```kotlin
                // In-game quick settings: gear at TopEnd; dialog never touches
                // game state, so mid-game progress survives (spec 2026-10-06).
                GameQuickSettingsButton(
                    onClick = { showQuickSettings = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm),
                )
                if (showQuickSettings) {
                    GameQuickSettingsDialog(
                        soundsEnabled = soundsEnabled,
                        onSoundsChange = onSoundsChange,
                        onDismiss = { showQuickSettings = false },
                    )
                }
```
(`Alignment`, `Modifier`, `padding` already imported; `mutableStateOf/remember/getValue/setValue` already imported.)

- [ ] **Step 4: `AppRoot` — pass hoisted state to `GamesScreen`**

Replace the `GamesScreen(...)` call (lines 629-636):
```kotlin
                        GamesScreen(
                            modifier = Modifier.weight(1f),
                            targetPractice = targetPractice,
                            breakPane = breakPane,
                            activeGame = activeGame,
                            onActiveGameChange = { activeGame = it },
                            gameAudio = gameAudio,
                            soundsEnabled = soundsEnabled,
                            onSoundsChange = ::selectSoundsEnabled,
                        )
```

- [ ] **Step 5: Compile check**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add "app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt" "app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt" "app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt" "app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt"
git commit -m "feat(games): surface quick settings gear in play screens"
```

---

### Task 3: Full verification

- [ ] **Step 1: Full build (assemble + all unit tests)**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain build
```
Expected: `BUILD SUCCESSFUL` — existing `GameLeavePolicyTest` / `GamesScreenTest` still pass (no logic changed).

- [ ] **Step 2: Install on tablet (if connected)**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain :app:installDebug
```

- [ ] **Step 3: Manual verification checklist (device)**

1. Start Target Practice (or Break the Pane) → take ≥1 shot → tap ⚙ in HUD top-right → dialog opens, game visuals intact behind it.
2. Toggle SOUNDS ON→OFF→ON → label flips; feedback cues (glass break / ding / fail) follow the toggle immediately.
3. Dismiss (DONE or tap outside) → game state identical (shot count, reveal state); next shot still counts in the game.
4. Open Settings tab mid-game afterwards → the old LEAVE GAME? prompt still appears (intended, unchanged).
5. Settings > SOUND reflects the in-game toggle (single source of truth).

---

## Self-Review

- **Spec coverage:** gear on both play screens (Task 2), shared modal dialog with sound toggle (Task 1), hoisted-state persistence via `SoundPrefStore` (Task 2 Step 4), no `activeGame`/routing changes, no speculative toggles, existing tests untouched (Task 3). Out-of-scope items (tab preservation, mid-game persistence) excluded. ✓
- **Placeholder scan:** all steps contain complete code or exact commands. ✓
- **Type consistency:** `soundsEnabled: Boolean`, `onSoundsChange: (Boolean) -> Unit`, `GameQuickSettingsButton(onClick, modifier)`, `GameQuickSettingsDialog(soundsEnabled, onSoundsChange, onDismiss)` — used identically across Tasks 1-2. ✓
