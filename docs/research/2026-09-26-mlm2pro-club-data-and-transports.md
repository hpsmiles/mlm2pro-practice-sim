# Research — MLM2PRO club data (AOA / club path) & transport architecture

**Date:** 2026-09-26 · **Method:** multi-source web research (3 librarian lanes), cross-verified against our decoder
**Questions:** (1) How does the Rapsodo app get angle of attack and club path — calculated or measured, and can we obtain it? (2) Connection options — BLE vs the Rapsodo app's WiFi usage. (3) How do the commercial simulators (GSPro, E6 Connect, Awesome Golf) connect to the monitor?

## TL;DR verdicts

1. **AOA & club path are MEASURED by the device (not calculated), but Rapsodo does not transmit them over BLE** — they exist only inside the Rapsodo app (premium membership) and the R-Cloud cloud API. Three independent open-source projects (springbok, Duwaynef, GolfForge) all confirm: no third-party access. Our BLE decoder is feature-complete for what the device exposes.
2. **No WiFi channel carries shot metrics — BLE is the only data channel anywhere in the ecosystem.** The monitor's WiFi is station mode on the user's router for internet (R-Cloud sync + firmware), plus the app's Local-mode video link (verdict 3). Even Rapsodo's own official sim partners (GSPro, E6, Awesome Golf) take metrics over BLE GATT (§5). BLE-only remains the correct architecture for this app.
3. **Video replays ride a LOCAL WiFi link in-session; cloud only after** — the app has "Direct" (BLE) and "Local" (WiFi) connection modes; in Local mode clips land on the tablet within seconds of the ball coming to rest, the alignment screen is a live "Impact Vision camera stream" (Rapsodo's own words), and R-Cloud upload happens post-session. Topology/ports/protocol undocumented — discovery plan in §4.
4. **All official sim integrations use BLE GATT + a 24-hour third-party auth token** — the sim software (or our app) *is* the BLE client; the Rapsodo app's only role is authorization (Play → 3rd Party Apps → Authenticate Now; Rapsodo Premium required; token expires every 24 h; single BLE link at a time). Our stack already implements this exact officially-sanctioned pattern. §5.

## 1. Where AOA / club path come from

- Rapsodo product page (mlm2pro): 15 metrics, 8 "Measured" — including **Club Path** and **Angle of Attack**. Both are **premium-membership gated**. Remaining 7 (carry, total, side carry, smash, descent, apex, shot type) are calculated — we already compute those ourselves with our physics ODE.
- Hardware is **one Doppler radar + two cameras** (240 fps impact camera for "Impact Vision" spin/RPT ball reading + forward camera). Marketing "dual camera", *not* dual radar. Rapsodo does not publicly attribute AOA/path to a specific sensor.
- Club path/AOA shipped **~May 2025 via app+firmware update** (springbok Discussion #225). The BLE MEASUREMENT frame format did **not** change — the club data never rides BLE.

### Evidence that BLE carries no club data

- `Duwaynef/MLM2PRO-BT-APP` `mlm2pro.md` (protocol spec): full 20-byte MEASUREMENT frame documented (table below) — no club fields beyond club speed.
- `springbok/MLM2PRO-GSPro-Connector` `src/ball_data.py` `from_mlm2pro_bt()`: parses the same first 12 bytes, offsets 12–15 commented out as "carry distance? / total distance?" (uncertain), no club fields. Club-path/AOA GSPro fields are left unpopulated *for MLM2PRO specifically* (they are populated for R10/Mevo+/etc.).
- springbok maintainer (Discussion #225, Jul 2025): **"Rapsodo is not providing this data to any 3rd party app so nothing I can do."**
- GolfForge worklog (Jun 2026): independently concluded the same — "Rapsodo gates third-party access behind a subscription… a homegrown BLE/OCR connector is a dead end."

## 2. What the BLE MEASUREMENT frame actually contains (20 bytes)

| Offset | Field (per mlm2pro.md) | Our decoder | Notes |
|---|---|---|---|
| 0–1 | Club Head Speed (÷10, ×2.2375 mph) | ✅ `clubHeadSpeed` | |
| 2–3 | Ball Speed (÷10) | ✅ `ballSpeed` | |
| 4–5 | HLA launch direction (÷10, signed) | ✅ `launchDirection` | sign verified on-device M4c |
| 6–7 | VLA launch angle (÷10, signed) | ✅ `launchAngle` | |
| 8–9 | Spin Axis (÷10, signed) | ✅ `spinAxis` | sign verified on-device M4c |
| 10–11 | Total Spin (rpm, raw) | ✅ `totalSpin` | |
| 12–13 | "Carry Distance?" — **unconfirmed** | ✅ logged raw as `unknown1` | Duwaynef's speculation, both repos unsure |
| 14–15 | "Total Distance?" — **unconfirmed** | ✅ logged raw as `unknown2` | plausible: Rapsodo calculates distances |
| 16–19 | Unknown (observed 0) | ❓ not read | spec lists trailing zeros |

Offsets 12–15 are very unlikely to be club data: only two int16 slots vs. the AOA+path+face+loft suite, and the maintainers who inspected live data concluded "distance". **Open verification opportunity:** at the next shed capture, compare `unknown1`/`unknown2` against the Rapsodo app's displayed carry/total (convert: likely ×2.2375·…·÷10 or meters) to settle it. Even if confirmed, our own physics ODE computes carry — value is only as a cross-check/sanity metric.

## 3. Transport reality ("direct WiFi")

- **BLE GATT** (service `DAF9B2A4-E4DB-4BE4-816D-298A050F25CD`, AES-256-CBC encrypted notifications) is the **only** live shot channel. Auth requires the `mlm.rapsodo.com` token exchange (already implemented in our stack).
- **WiFi role = phone/monitor internet path for R-Cloud cloud sync + firmware.** Rapsodo FAQ: shots sync to cloud *when internet is available*; without internet they sync later — live capture still works over BLE. Troubleshooting advice is deliberately router-centric.
- No shot-data endpoint over WiFi exists in any repo, wiki, or official page — but the app's "Local mode" video link is real and undocumented (§4). Firmware 4.1.19 (2026-08-31) notes only mention Bluetooth device connectivity.
- springbok's non-BLE path is **OCR of screenshots of the Rapsodo app** (for other monitors) — not networking.
- Android 12+ verdict if we ever needed a WiFi device link: `WifiNetworkSpecifier` (user prompt each connect, per-app network), `WifiNetworkSuggestion`, or P2P APIs — all add fragility, prompts, `MulticastLock` requirements for broadcast UDP, and would yield **zero** new metrics. BLE+WiFi coexistence itself is fine on the tablet.

## 4. Video replays & the alignment camera — CORRECTED (2026-09-26, user ground truth)

> Initial conclusion ("cloud round-trip, no local video transport") was **overturned by user observation**: in the app's WiFi connection mode, shot replays appear within seconds of the ball coming to rest, **all clips are already on the tablet at session end**, and the R-Cloud push happens only after the session (sync UI shows "video uploads"). BLE (~20–100 KB/s) cannot carry 1080p/240 fps clips, so **a local WiFi link carries video in-session**.

Naming that resolved the confusion — Rapsodo's app has two connection modes (App Store release notes v2.7.3: "more stable connection for Direct and Local modes"; springbok issue #227):

- **"Direct" mode** = the BLE GATT link (metrics only — the channel springbok/Duwayne tap, and ours).
- **"Local" mode** = the local WiFi link that carries the **Impact Vision camera stream** and the shot video clips. (The user's "direct wifi mode" is Rapsodo's "Local mode" — inverse naming trapped the earlier research.)

Confirmed:
- The alignment view **is a live camera stream** — Rapsodo release notes v2.9.3: the alignment screen includes "the original alignment line and box in addition to the **Impact Vision camera stream**". (Plus overlay line, accelerometer tilt, top-mounted physical sight.)
- **R-Cloud is the post-session archive**, not the in-session delivery path. Terms §1 "uploaded to and stored by Rapsodo" describes post-capture storage; the FAQ sync language = the post-session upload the sync UI shows. This coexists with "low memory on a brand new iPad" reviews (clips cached locally) and quality-tracking-internet reports (upload speed).

Undocumented publicly (open questions):
- Topology: monitor-as-AP (own SSID?) vs WiFi Direct/P2P vs router-LAN. No SSID pattern, ports, protocol (HTTP/RTSP/TCP?), codec confirmation, or packet captures exist in any reachable source; FCC records are 403-blocked.
- Credentials/auth: how the app gets onto the link (plausibly BLE-provisioned — unconfirmed) and whether the video endpoint is token-gated.

Implication for us: in-session video **may be tappable** by an offline third-party app (Android `WifiNetworkSpecifier` / saved networks / `WifiP2pManager` can join AP or P2P links) — a potential offline swing-video feature within the single-device constraint. Blockers: topology + credentials + endpoint, all undocumented. **Legal note**: Rapsodo Terms §6 prohibit reverse engineering and non-authorized access — a conscious decision, not a casual one.

Empirical discovery plan (ranked by yield/effort) — run during a normal Rapsodo app session in Local mode:
1. **Read the FCC ID off the device label** → fccid.io/fcc.report lookup: certified radio modes (STA vs AP/P2P) and often the WiFi module.
2. **`adb shell dumpsys wifi`** on the tablet mid-session → connected SSID/BSSID, subnet, gateway (topology + SSID pattern).
3. **`adb shell dumpsys netstats` / socket dump while a replay transfers** → the Rapsodo app's local peer IP + ports (video endpoint).
4. **Laptop joins that network + Wireshark** during a shot → protocol, TLS vs cleartext, auth gating, payload rate.
5. **mDNS/Bonjour browse** on that link (`dns-sd -B` / `avahi-browse`) → advertised services often reveal the video service directly.
6. (High effort) BLE sniff at first Local-mode connect → whether AP credentials/token ride BLE.

## 5. Simulator integrations (GSPro / E6 / Awesome Golf) — the complete transport picture

**Every official sim integration talks BLE GATT directly to the monitor — none uses the monitor's WiFi.** The Rapsodo app's only role is *authorization*: it writes a third-party token to the monitor (24 h expiry) via **Play → 3rd Party Apps → \<GSPro | Awesome Golf\> → Authenticate Now**. That is what `/api/simulator/user/{id}` has been all along — *simulator* is Rapsodo's name for sanctioned third-party sim software.

| Platform | Transport | Runs on | Connection sequence (documented) |
|---|---|---|---|
| **GSPro** ("premium partner") | **BLE GATT**, PC ↔ monitor directly via bundled GSPro Connect | Windows 10/11 PC with Bluetooth (USB dongle OK); GSPro license (~$250/yr, distinct "Rapsodo MLM2Pro" SKU) **+ Rapsodo Premium** | authorize in Rapsodo app (every 24 h) → pair monitor in Windows (appears as `MLM2`/`BlueZ`, not `MLM2_BT_`) → launch GSPro, Connect auto-discovers (~30 s first connect) → **close Rapsodo app and disable phone Bluetooth** (PC and phone "fight for the Bluetooth connection") |
| **E6 Connect / E6 Apex** (TruGolf) | **BLE — inferred** (same "3rd Party Apps" auth model; TruGolf's own MLM2PRO guide wasn't publicly retrievable) | Windows PC; E6 license + Rapsodo Premium | assumed same as GSPro — verify against a TruGolf KB article before relying on E6 specifics |
| **Awesome Golf** | **BLE GATT** direct from the sim device, or **Assistant app** bridge (a phone holds the BLE link and QR-pairs to a PC/iPad; must stay foreground all session) | iOS/iPadOS, **Android**, macOS, Windows; AG license + **Rapsodo Premium required** ("unable to connect to Rapsodo Launch Monitors that are not subject to an active Rapsodo Premium Subscription") | authorize (24 h) → close Rapsodo app → Awesome Golf → Rapsodo → *Connect via Bluetooth* (or Assistant QR variant) |

Confirmed operational facts (GSPro KB + Awesome Golf docs — primary sources):

- **24 h token expiry** — daily re-auth via the Rapsodo app. Duwaynef: the app only rewrites the token if it's close to/expired; with an expired token the monitor "may just stay red and reject any connections."
- **Rapsodo Premium is required for third-party connections**, on top of the sim's own license. Community connectors (Duwaynef, springbok) ride the identical device-level token via the "Awesome Golf" slot — no AG subscription needed.
- **One BLE link at a time** — GSPro's troubleshooting doc explicitly tells users to disable phone Bluetooth so it doesn't contend with the PC. Our connect-from-our-app-only flow (Rapsodo app closed) matches the official partner pattern.
- **No ports, no discovery protocols, no LAN requirement for sim shot data.** GSPro Open Connect (`127.0.0.1:921` JSON) is sim-internal — connector → GSPro — never a monitor interface. No sim touches the Local-mode WiFi link.

**Club-data nuance:** GSPro's Open Connect schema does include full `ClubData` (AoA, Path, FaceToTarget, …), so a *sim* protocol can carry club metrics — but there is still no public evidence the MLM2PRO sends club data over BLE even to sanctioned partners: community connectors, authenticating through the same third-party token slot, see the unchanged 20-byte frame with no club fields (springbok leaves GSPro ClubData unpopulated *for MLM2PRO*). §1's verdict stands; revisit only if a GSPro + Premium user's real-shot log ever shows populated ClubData.

**What this means for our app:** (a) the WiFi-for-metrics idea is dead everywhere in the ecosystem — BLE-only is the complete architecture, not a compromise; (b) our stack already implements the officially-sanctioned third-party-client pattern (BLE GATT + `/api/simulator/user/{id}` token) — architecturally identical to GSPro Connect / Awesome Golf; (c) the 24 h token is the one step that needs internet (once/day at auth time) — a still-valid cached token keeps sessions working offline until expiry.

## 6. Options for club data (ranked)

1. **Stay BLE-only (chosen).** Our feature modules (gapping, combines, dispersion, A/B testing) are ball-metric driven; AOA/path are not blocking.
2. **Labeled proxies.** Face angle / face-to-path can be *estimated* from HLA + spin axis (flight-law ratios, calibrated); club path loosely follows HLA for centered strikes. **AOA has no credible proxy from ball flight.** If we ever add these, they must be visibly flagged "estimated" — they are not Rapsodo-equivalent measurements.
3. **R-Cloud API import** (`golf-cloud.rapsodo.com` — does carry `clubPath`, `angleOfAttack`, `faceAngle`). Requires logged-in premium account + internet → **violates the single-device/offline constraint**. Only acceptable as a future explicitly-opt-in online sync lane, never in the live path.
4. **OCR of the Rapsodo app** (springbok's approach): fragile, requires running the Rapsodo app simultaneously — rejected.

## 7. Drift watch

- Rapsodo added club metrics in May 2025 **without** changing the BLE frame — if they ever open it, springbok Discussion #225 / Issue #228 is the canary to watch. Re-verify raw frames per AGENTS.md protocol-drift policy.
- Offsets 12–15 meaning is single-source speculation; treat as distance-*candidates* only until verified against live captures.
- The Local-mode video link (topology, ports, protocol, auth) is undocumented territory; anything we build on it must come from our own empirical discovery (§4). Rapsodo Terms §6 prohibit reverse engineering — weigh that consciously before investing.
- Third-party token/Premium gating is enforced server-side (`mlm.rapsodo.com`): if the monitor ever stays red and rejects connections, check token age (24 h) and Premium status before suspecting our decoder.
- E6's transport is *inferred* (TruGolf's MLM2PRO guide wasn't publicly retrievable); GSPro/Awesome-Golf BLE facts are primary-source confirmed.

## Key sources

| Source | Establishes |
|---|---|
| rapsodo.com/products/mlm2pro | 15 metrics / 8 measured; AOA+path "Measured" + premium; hardware = 1 radar + 2 cameras |
| raw.githubusercontent.com/Duwaynef/MLM2PRO-BT-APP/master/mlm2pro.md | Full 20-byte BLE MEASUREMENT spec; offsets 12–15 "carry/total distance?" |
| springbok/MLM2PRO-GSPro-Connector `src/ball_data.py` | Same 12-byte parse; club fields unpopulated for MLM2PRO only |
| springbok Discussion #225 + Issue #228 | Maintainer: "Rapsodo is not providing this data to any 3rd party app" |
| Duwaynef `OpenConnectClient.cs` | ClubData fields commented out except Speed |
| GolfForge worklog GOL-181/149 (Jun 2026) | Independent confirmation; "dead end" |
| amcheste/golf-coach-agent `rcloud_api.py` | R-Cloud API carries clubPath/angleOfAttack/faceAngle (auth'd premium) |
| rapsodo.com/terms-of-use-mlm §1 | Video "uploaded to and stored by Rapsodo", viewed via the app — cloud path |
| Rapsodo alignment article (blogs/golf/aligning-and-leveling…) | Physical aid + projected line over captured frames + accelerometer leveling |
| Rapsodo app release notes (App Store v2.7.3, v2.9.3) | "Direct and Local modes"; alignment screen includes the "Impact Vision camera stream" |
| springbok issues #227, #83 | Mode names: "direct Bluetooth connection" vs "local network connection" |
| Rapsodo FAQ + firmware notes | WiFi = cloud sync/router; BLE-only device connectivity |
| gsprogolf.com/pages/faq + GSPro KB ("Rapsodo MLM2Pro Connection Guide", "Rapsodo Connection Troubleshooting") | GSPro = Windows BLE via GSPro Connect; 24 h auth via app "3rd Party Apps"; Premium required; disable phone BT; Windows pairing name `MLM2`/`BlueZ` |
| insights.awesome-golf.com/docs/guides/connection/rapsodo-mlm2pro (+ AG license/app reference pages) | Awesome Golf direct BLE + Assistant QR bridge; 24 h auth; Premium required |
| gsprogolf.com/GSProConnectV1.html | GSPro Open Connect — 127.0.0.1:921 sim-internal JSON schema incl. ClubData (not a monitor interface) |
| connect.e6golf.com/support | E6 support portal — no MLM2PRO-specific connection doc publicly retrievable (transport inferred) |
