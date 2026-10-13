# MLM2PRO Local-Mode WiFi Reverse Engineering — Implementation Plan

> **For agentic workers:** Execute task-by-task; `- [ ]` checkboxes track progress. REQUIRED SUB-SKILL: superpowers:executing-plans (inline) or superpowers:subagent-driven-development (dispatch per task). **Switch the Neuralwatt model at task boundaries per the Model Strategy table** (TUI: `/models` — pick model and reasoning variant before each task).

**Goal:** Determine how the Rapsodo Android app joins the MLM2PRO "Local" mode WiFi link and streams the Impact Vision alignment camera / swing clips — and whether our app can do the same directly, without the Rapsodo app (working hypothesis: credentials/endpoint are BLE-provisioned, and we already speak that GATT protocol).

**Architecture:** Pull the Rapsodo APK from the user's tablet, decompile with REA/headless JADX, and statically map the Local-mode connection path anchored on obfuscation-surviving strings (our known GATT service UUID, Android WiFi API calls, protocol schemes, IP/port literals). Cross-reference findings against our BLE decoder and the community protocol spec. Confirm empirically during a live Local-mode session. Write everything up as a dated research doc in the repo; keep all proprietary artifacts outside the repo.

**Tech Stack:** REA (`npx rea-agents@latest`) in CLI mode, headless JADX, Apktool, adb v37.0.1 at `C:\Users\smiles\AppData\Local\Android\Sdk\platform-tools\adb.exe`, Node.js v24.14.1, JDK (Android Studio JBR). No Gradle involved — JAVA_HOME setup not required.

---

## Model Strategy (Neuralwatt, verified from live catalog 2026-10-13)

| Task(s) | Model | Reasoning variant | Why this model |
|---|---|---|---|
| 0, 1, 2, 3A, 4-capture, 6 | `neuralwatt/glm-5.3-flash` | low-medium | Mechanical shell/grep work. $0.15 in / $0.50 out per M tokens — ~6x cheaper than Flex. Reliable tool use for scripted steps. |
| 3B, 4-interpret, 5 | `neuralwatt/glm-5.3-flex` | high | The core intellectual work: reading obfuscated decompiled code, protocol inference, deliverable writing. |
| 3B escalation 1 | `neuralwatt/kimi-k2.7-code-fast` | (fixed) | Code specialist for gnarly obfuscated sections when Flex stalls twice. |
| 3B escalation 2 | `neuralwatt/kimi-k3-flex` | high | Flagship — hardest inferences only (crypto/handshake), surgically scoped. |

**Full Neuralwatt price list (USD per 1M tokens: input / output / cache-read):**

| Model | In | Out | Cache read |
|---|---|---|---|
| Gemma 4 31B | 0.144 | 0.42 | 0.0144 |
| GLM 5.3 Flash | 0.15 | 0.50 | 0.03 |
| DeepSeek V4.1 Flash | 0.15 | 0.60 | 0.015 |
| Qwen3.6 35B Fast | 0.29 | 1.15 | 0.029 |
| Qwen 3.8 27B | 0.45 | 3.20 | 0.25 |
| MiMo V2.6 Pro | 0.87 | 1.74 | 0.036 |
| GLM 5.3 Flex | 0.9425 | 2.925 | 0.09425 |
| Kimi K2.7 Code Fast | 0.95 | 4.00 | 0.095 |
| Kimi K3 Flex | 1.95 | 9.75 | 0.195 |

**Cost-control rules:**
- Escalate one tier only after 2 failed attempts at the current tier; when escalating, paste scoped excerpts to the stronger model, not the whole decompiled tree.
- Prefer keeping a session alive across same-tier tasks — cache reads run 5-24x cheaper than fresh input on the recommended models (Qwen 3.8 27B is the outlier at <2x — one reason to skip it).
- `nw-*` house aliases (nw-flash, nw-large, ...) returned no published pricing — check the model picker before substituting one.
- Not recommended: Gemma 4 31B (31B — flaky tool use risks retry loops costing more than the $0.006/M saved), Qwen 3.8 27B (poor fit: $3.20/M output, $0.25/M cache read). DeepSeek V4.1 Flash is an equal-cost alternative to GLM 5.3 Flash. MiMo V2.6 Pro is a fine Flex substitute if Flex is unavailable.
- Ballpark total: roughly $5-30 with this split (Task 3B dominates); running everything on the flagship throughout would cost ~3-5x more.

---

## Ground Rules

1. **The repo is public.** The APK, decompiled code, and proprietary code excerpts NEVER get committed or written inside the repo. Stage everything at `C:\Users\smiles\mlm2pro-re\`.
2. Only findings docs (our own analysis, facts, evidence references — no copied proprietary code) go in `docs/research/`.
3. Rapsodo ToS §6 prohibits reverse engineering — personal use on own hardware; conscious decision already recorded in `docs/research/2026-09-26-mlm2pro-club-data-and-transports.md` §4.
4. Read `AGENTS.md` for repo conventions (research docs: dated filename, TL;DR verdicts, evidence tables, drift-watch).
5. If a command here does not match reality (REA subcommand names may differ from expectations), adapt, and record what you actually ran — evidence over ceremony.
6. REA note: if `npx rea-agents setup` offers to register an MCP server into OpenCode, CLI mode works regardless and avoids any host restart — prefer CLI.

### Task 0: Create staging workspace
Model: GLM 5.3 Flash
- [ ] `New-Item -ItemType Directory -Force -Path C:\Users\smiles\mlm2pro-re\apk, C:\Users\smiles\mlm2pro-re\decompiled, C:\Users\smiles\mlm2pro-re\findings`
- Verify: all three directories exist.

### Task 1: Install and verify REA
Model: GLM 5.3 Flash
- [ ] `npx rea-agents@latest setup` — approve the setup plan when prompted.
- [ ] `npx -y rea-agents@latest --help` — record the exact subcommand names for APK analysis and Android-device work in `C:\Users\smiles\mlm2pro-re\findings\notes.md`.
- [ ] Verify headless JADX availability per the help output (install if REA does not bundle it).
- Verify: help output captured; you know the exact APK-analysis command name.

### Task 2: Pull the Rapsodo APK (REQUIRES: tablet on USB, Developer Options > USB debugging enabled)
Model: GLM 5.3 Flash
- [ ] `& "C:\Users\smiles\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices` — expect one device, state `device`. If `unauthorized`: accept the prompt on the tablet. If empty: check cable/debugging.
- [ ] Find the package: `adb shell pm list packages | findstr /i rapsodo` — record the exact package name (expected `com.rapsodo.…`).
- [ ] `adb shell pm path <package>` — often returns several split APKs (`base.apk`, `split_config.*.apk`).
- [ ] `adb pull` every listed path into `C:\Users\smiles\mlm2pro-re\apk\`.
- [ ] `adb shell dumpsys package <package> | findstr /i version` — record `versionName`/`versionCode` in notes (protocol-drift pin).
- Verify: at least one APK on disk with plausible size (tens-hundreds of MB); package + version recorded.

### Task 3A: Decompile + anchor sweep
Model: GLM 5.3 Flash (medium reasoning)
- [ ] Decompile `base.apk` with REA's APK-analysis command (name discovered in Task 1) into `C:\Users\smiles\mlm2pro-re\decompiled\`. Config splits are resources-only; base is what matters.
- [ ] Run these sweeps over the decompiled tree (case-insensitive; rg or grep) and record every hit (file:line + ~3-line snippet) in `C:\Users\smiles\mlm2pro-re\findings\anchor-hits.md`:

| Sweep | Pattern | What it tells us |
|---|---|---|
| BLE anchor | `DAF9B2A4` | Our known GATT service UUID — locates the app's BLE layer |
| WiFi join | `WifiNetworkSpecifier\|WifiNetworkSuggestion\|WifiP2pManager\|requestNetwork` | Topology (AP join vs P2P vs LAN) |
| Discovery | `NsdManager\|MulticastLock\|_tcp` | mDNS/NSD service names |
| Video | `rtsp\|ExoPlayer\|media3\|MediaPlayer\|rtp` | Player stack + stream scheme |
| Strings | `ImpactVision\|Rapsodo\|MLM2\|ssid\|passphrase` | Feature/branding + credential strings |
| Endpoints | IPv4 regex `(192\.168\|10\.\|172\.(1[6-9]\|2\d\|3[01]))\.` and `:554\|:8554\|:8080\|:9000` | Hardcoded IPs/ports |

- Verify: anchor-hits.md exists; the BLE sweep found the GATT UUID at least once (if not: check the decompile succeeded, then widen the pattern — UUID may be lowercase or byte-array encoded).

### Task 3B: Deep reading + protocol reconstruction (THE core task)
Model: GLM 5.3 Flex (high). Escalations per Model Strategy.
- [ ] From the BLE anchor hits, trace the app-to-monitor GATT writes (characteristics, message formats). Cross-reference against our decoder in `core/ble` (`Mlm2proDecoder`) and the community spec `mlm2pro.md` (referenced from docs/research/2026-09-26…, §2). We decode notifications; the app's WRITES are the unmapped half — provisioning payloads are the prize.
- [ ] From the WiFi-join hits, determine the Local-mode topology and how credentials reach the tablet (BLE-provisioned? hardcoded? QR? cloud-fetched?).
- [ ] From discovery/video hits, determine the stream/clip endpoints (scheme, IP or hostname, port, auth headers/tokens).
- [ ] Bonus: locate the "3rd Party Apps" token-exchange code and confirm it matches our implementation.
- [ ] Record everything with file:line evidence in `findings\protocol-reconstruction.md`.
- STOP CONDITION: if evidence shows the video endpoint requires an app-initiated session/token that cannot be reproduced from the monitor alone — record as DEAD END with evidence, skip Task 4, go to Task 5.
- Verify: protocol-reconstruction.md answers topology / credentials / endpoint / auth-gating, each with file:line evidence; conclusions marked CONFIRMED vs INFERRED.

### Task 4: Empirical confirmation (REQUIRES: user + shed; schedule with user)
Models: Flash for capture, Flex to interpret.
- [ ] During a live Rapsodo Local-mode session: `adb shell dumpsys wifi | findstr /i "SSID BSSID gateway"`; `adb shell dumpsys netstats`; mDNS browse from a laptop joined to the same network (`dns-sd -B _services._udp.local.` or avahi-browse); Wireshark on that laptop during a shot + replay.
- [ ] Compare each observation against Task 3B predictions; record matches/deltas (firmware-drift check per AGENTS.md).
- Verify: `findings\empirical.md` written; every 3B conclusion confirmed or corrected.

### Task 5: Findings doc in the repo
Model: GLM 5.3 Flex
- [ ] Create `docs/research/<today's date>-mlm2pro-local-mode-wifi-protocol.md`: TL;DR verdict (viable / dead end), topology, credentials mechanism, endpoints/protocol, auth gating, evidence table (file:line + live-capture refs), implications for our app, drift-watch entries.
- [ ] Add a pointer in the existing 2026-09-26 research doc §4 + drift-watch; update the transport bullet in `AGENTS.md` if the Local-mode picture is now concrete.
- [ ] Commit: `git add docs/research AGENTS.md && git commit -m "docs: MLM2PRO Local-mode WiFi link reverse-engineering findings"`
- Verify: doc exists, follows repo research-doc conventions (see the 2026-09-26 doc), committed.

### Task 6: Report + next step
Model: GLM 5.3 Flash (or finish the Task 5 Flex session)
- [ ] Summarize the verdict to the user. If VIABLE: propose a brainstorming session for the in-app feature (M9 candidate "Local-mode camera link") — a separate plan, NOT this effort. If DEAD END: done.

---

## Self-review notes
- Coverage: user goal (connect without Rapsodo app + camera access) is answered by Task 3B feasibility; tasks map 1:1 to the approved conversation plan.
- No placeholders: every step has exact commands/patterns; REA subcommand names are discovered in Task 1 by design (cannot be verified before install) — that is an executable instruction, not a gap.
- Consistency: staging paths and findings filenames are referenced identically across tasks.
