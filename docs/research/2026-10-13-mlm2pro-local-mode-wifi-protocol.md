# Research — MLM2PRO Local-mode WiFi link (static RE of the Rapsodo app)

**Date:** 2026-10-13 · **Method:** static reverse engineering of the Rapsodo Android app v2.25.6 (versionCode 600, Play build 2026-09-07) pulled from our tablet via adb; decompiled with jadx 1.5.3 + string-pool extraction. Full evidence trail (file:line refs, exact payloads, env/deviation log) in the local staging dir `C:\Users\smiles\mlm2pro-re\findings\` (kept outside the repo — proprietary artifacts; ToS §6 personal-use decision recorded in the 2026-09-26 doc §4).
**Question:** can our app connect to the MLM2PRO's Local-mode WiFi link itself — without the Rapsodo app — to get the Impact Vision alignment stream and swing replays?

## TL;DR verdict

**VIABLE, pending live confirmation.** The Local-mode path has no cloud round-trip and no per-app gating we cannot replicate:

1. **The WiFi credentials come FROM the monitor over BLE** — the app does not provision them. The monitor pushes (SSID, password) encrypted over the GATT AP_MODE characteristic; the decryption key derives from the **BLE-advertised device name** (public to any BLE scanner). A BLE client that completes the same sequence receives the same credentials.
2. **Topology is a plain monitor-hosted access point** (gateway `192.168.35.1`, SSID prefix `MLM2_`), joined via Android `WifiNetworkSpecifier` — no Wi-Fi P2P, no mDNS/discovery layer. Single-device constraint holds: the tablet joins the AP itself (per-app network, no location permission needed with specifier + `neverForLocation`).
3. **Video is RTSP** (ZLMediaKit native stack + Media3 1.7.1 `RtspMediaSource`; Impact Vision alignment stream 1536×1440@40fps, GS 480×640) served by an on-monitor "RHost" server (ZeroMQ + CoAP components present). The `rtsp://` URL is assembled in native code — port/auth to be captured live (§Open items).
4. **The one real gate is the BLE auth handshake** (`AuthenticateRequestData{partnerId, apiKey, secretKey}`) — same credential family as our existing `mlm.rapsodo.com` third-party token flow. Whether our partner credentials are accepted over this characteristic is the main open question.

## 1. The BLE surface (service `DAF9B2A4-E4DB-4BE4-816D-298A050F25CD`)

`ApiBLEManager` (extends Nordic BLE library) exposes 11 characteristics beyond the metrics service we already use (all UUIDs captured with file:line refs in staging; MTU 517):

| Purpose | UUID (prefix) | Notes |
|---|---|---|
| AP_MODE — WiFi credentials | `9D448324…` | payload after decrypt: 32 B null-terminated SSID + 64 B null-terminated password |
| AUTHENTICATION | `ACCBABBA…` | `AuthenticateRequestData{partnerId, apiKey, secretKey}`; 30 s timeout |
| ENCRYPTION | `F3E06560…` | session-key exchange; 15 s timeout |
| COMMUNICATION_KEY | (see staging) | `saveCommunicationData(byte[])` — server-side key material |
| AVAILABLE_NETWORKS / CONNECT_NETWORK / CHANGE_DEV_CONN / DEV_CONN_STATUS / CLIENT_IP_ADDRESS | (see staging) | station-mode (router) setup — not needed for Local mode |
| DEVICE_INFO / SERIAL_NUMBER / VERSIONS_AND_BATTERY | (see staging) | incl. FW 3.8.61 references |

**Key derivation:** `generateEncryptionKey(deviceName.getBytes())` — device name straight from the BLE advertisement, cached, applied to all callbacks. If the key isn't set yet, payloads pass through **unencrypted**. The actual KDF + cipher live in `libv2api-jni.so` (native, with `libsodium.so`; companion `libzmq.so`, `libRDCLib.so`) — not recoverable from Java. **Shortcut:** the app logs the device name, derived key (hex), and every encrypt/decrypt input/output pair at debug level — one live session with the Rapsodo app + `logcat` yields key + plaintext/ciphertext samples without touching native code.

## 2. Connection sequence (Local mode)

1. BLE connect → auth handshake over AUTHENTICATION char.
2. ENCRYPTION / COMMUNICATION_KEY exchange → session key from device name.
3. Subscribe AP_MODE → receive encrypted (SSID, password) of `MLM2_*` AP.
4. Join monitor AP via `WifiNetworkSpecifier` (gateway `192.168.35.1`; 45 s WiFi timeout).
5. RHost server on the monitor: live RTSP alignment stream + shot clips (VideoData/Battery/Connections/BallPositionData/ClubDataSummary event models). LAN/station variant exists (`connectTcpDevice()`, CoAP + KMS auth classes) but is separate.

## 3. What this means for our app

- Metrics path unchanged (BLE-only, our decoder stands).
- Feature path: add a Local-mode client = steps 2–5 above on the same GATT service we already speak, then `WifiNetworkSpecifier` join + RTSP pull for alignment/replays. No Rapsodo app needed in the loop.
- Risks: (a) partner-credential gating on the auth characteristic; (b) native KDF/cipher details — mitigated by the logcat shortcut and by cloning behavior rather than deriving math; (c) firmware drift — all findings pinned to app v2.25.6/FW 3.8.61.

## Open items (live session needed — see plan Task 4)

~~1. Exact KDF/cipher (or behavioral clone from logcat key + samples).~~ → moot for LAN mode; see addendum.
~~2. RTSP URL/port/auth (logcat + Wireshark during live stream).~~ → confirmed, addendum.
~~3. RHost port/protocol (ZeroMQ? CoAP?) and whether clips need auth.~~ → RTSP confirmed no-auth; RHost not needed for video.
4. Do our `mlm.rapsodo.com` partner credentials authenticate over the AUTHENTICATION characteristic?
5. BLE GATT byte exchange (AP credentials + session key) via HCI snoop — needed only for pure-AP (no-router) mode.

## Addendum — empirical confirmation (2026-10-13, desk + shed session)

All confirmed against live monitor (FW-era app v2.25.6), evidence in local staging `findings\empirical.md` + 72 MB logcat:

1. **Third-party RTSP access PROVEN, zero auth:** a bare `DESCRIBE rtsp://10.0.0.228:554/rapsodo/live` from a PC on the same LAN (not the tablet, not the Rapsodo app) returned `200 OK` from ZLMediaKit (`rtsp_demo`, build Feb 23 2026) with an H265 SDP. Any LAN client that knows the monitor's IP can watch the Impact Vision alignment stream.
2. **The monitor tells the app its LAN IP over BLE** (CLIENT_IP_ADDRESS characteristic) — that's the whole "discovery" mechanism. In station (router) mode no AP-credential exchange is needed at all.
3. **AP join confirmed:** SSID = `MLM2_` + last 6 BSSID octets (`MLM2_488F4CF089FA` for BSSID `48:8f:4c:f0:89:fa`), WPA_PSK, joined via `WifiNetworkSpecifier` with no user dialog; monitor is the DHCP server (gateway `192.168.35.1`).
4. **Replay delivery model:** a new shot (auto-replay on) triggers a single ~1 s RTSP session on `rtsp://<ip>:1554/rapsodo/impact` (SETUP/PLAY/TEARDOWN) that pushes the clip; the app saves it and every later replay is **local playback** (no network). Port 1554 is closed at all other times (poller: connection refused during manual replays).
5. **Live stream keepalives:** while the alignment view is open, the app sends RTSP OPTIONS every ~10 s on 554.
6. The app's own debug crypto logs (session key, encrypt/decrypt pairs) are R8-stripped from the release build — the logcat shortcut for the native KDF does not work; would need HCI snoop if pure-AP mode is ever needed.

**Net verdict for our app:** for the common case (monitor on the home LAN) the whole feature is: BLE connect → auth handshake → read CLIENT_IP_ADDRESS → open `rtsp://<ip>:554/rapsodo/live` (H265, no auth). Replays: listen on 1554 at shot time to catch the ~1 s push, save app-side, replay locally — exactly the Rapsodo model.

## Addendum 2 — club data DOES ride the WiFi event channel (2026-10-13, session 2)

The 2026-09-26 conclusion ("AOA/club path not obtainable by third parties") holds **for BLE only**. Static RE + live capture show the monitor's RHost event channel carries them:

- **`ShotParameters` event (JSON)**: `AttackAngle`, `ClubPath`, `ClubAoA`, `ClubSpeed`, `ClubType` — plus `Carry`, `CarryTotal`, `SideCarry`, `Apex`, `DecentAngle`, `Rolling`, full ball metrics, and complete trajectory polynomials (`TracePolyCoeffsXYZ/UV/BackSpin`).
- **`ClubDataParameters` event (2021)**: `{AttackAngle, ClubPath, ClubDataEstType}`. Live proof from the app's own logs (2 shed shots): `{"AttackAngle":-2.996,"ClubPath":-0.180,"ClubDataEstType":0}` / `{"AttackAngle":-3.473,"ClubPath":1.711}`.
- **Transport**: persistent ZeroMQ TCP connection to **monitor port 32501**, secured with **CurveZMQ** (server greeting advertises CURVE mechanism). The CURVE keys are almost certainly derived from the BLE session-key exchange — the deliberate gate binding WiFi event access to a completed BLE handshake. Our BLE stack already speaks the metrics side; recovering the native KDF (`libv2api-jni.so`) or capturing key material via BLE HCI snoop is the remaining RE task.
- Also on this channel: command JSON (monitor-joins-router with per-session-encrypted WiFi password, PartnerId = `240aef22-3672-482c-a608-fde9624467e9`), `SwingVideoFileReady`, `BallAlignmentData`, battery/temp, dry-swing summaries.
- RTSP video (554/1554) stays separate and unauthenticated.

**Implication:** club path + attack angle are recoverable in-app once the CURVE handshake is replicated — no Rapsodo Premium dependency in the data path (the monitor computes and transmits them locally; premium gating is an app/cloud-side convention).

## Addendum 3 — CurveZMQ handshake replicated; key lifecycle + client recipe (2026-10-13/14)

Working CurveZMQ access to the RHost event channel is now protocol-complete from a plain Java client; event capture during a live range session is the last unvalidated step (see open items below). Full evidence trail lives in the private RE staging (`mlm2pro-re\findings\protocol-reconstruction.md`).

1. **Greeting "anomaly" resolved.** The monitor's ZMTP preamble `FF 00×7 01 7F` is a valid ZMTP 1.0/2.0-style signature (uint64BE length 1) — JeroMQ 0.6.0 emits the byte-identical preamble itself. The monitor accepts both strict (`FF 00×8 7F`) and JeroMQ-style client greetings. Net: **stock, unpatched JeroMQ completes the full CurveZMQ handshake** (HELLO/WELCOME/INITIATE/READY) against the RHost ROUTER — the earlier "greeting abort" diagnosis was wrong, and no parser patch is needed.
2. **ZMQ plane map (armed monitor on LAN).** RHost CURVE ROUTER `tcp://<ip>:32501` (the app's event DEALER), a second CURVE ROUTER `:64646`, and two plaintext NULL REQ/REP **key services** `:53560` + `:64660`. Key-service protocol (from static RE of `libRDCLib.so` `ZMQKeyManager`, corroborated live): send a 100-byte random `[a-zA-Z0-9]` mask on a plain REQ socket; the reply's first 41 bytes are the server key z85-XOR-masked (`server_key[i] = reply[i] ^ mask[i]`); z85-decode → 32-byte CurveZMQ server key. The Rapsodo app's own DEALER fetches its session key this way at every connect (decompiled `launchConnection<false>` → `getServerKey(ip, key_port)`).
3. **Key lifecycle (live-measured, multiple rounds):**
   - BLE dance → monitor joins LAN, ROUTER armed with a per-dance session key; key services serve it.
   - App live → the app's single DEALER connection holds the 32501 slot; **every other CurveZMQ client — right or wrong key — is closed in <50 ms** (decompiled log string: "ROUTER listening at {} (first client used; further connections ignored)").
   - App disconnect → router re-arms to a fresh unknown key within ~90 s (12 known keys all rejected); key services keep serving the stale dance key (they do not track re-arms).
   - Teardown 6–11 min post-disconnect → the ROUTER re-arms with the persistent **BOOT key** for a ~40 s–2 min "static window" — an outside CURVE client handshakes and holds (proven 3×).
   - No app reconnect → monitor powers off; inactivity sleep preserves keys. Fresh boot → monitor off-LAN until the next BLE dance.
4. **Boot key source.** BLE characteristic `E7BFA27A-EDCC-40E9-B9F3-0DA9B0BD2638` (communication-key char, handle 0x0034) — plain READ returns the 32-byte boot key at every connect cycle (unit-persistent across power cycles; value in private RE staging, not reproduced here). This is the validated static-window key. Its NOTIFY pushes carry additional 32-byte values (session-phase ≠ connect-phase), but those raw pushed values are NOT usable as CurveZMQ keys directly (all tested → rejected; likely encrypted or derived).
5. **Validated client recipe (static window):** JeroMQ SUB, fresh `Curve().keypair()`, `setCurveServerKey(bootKey)` → handshake accepted vs the ROUTER. Fetch-then-connect with the live session key **while the Rapsodo app holds the slot** was rejected in 3/3 connect-phase attempts (single-client policy the leading explanation; the session-phase variant is untested — see open items).
6. **Open items (next validation steps):**
   - (a) Key-fetch + CURVE connect **during an active range session** (session-phase): BLE 0x0034 pushes show a re-key when a session starts; a mid-session fetch from 53560 may serve the then-armed key.
   - (b) Eviction test: hold a static-window connection while the Rapsodo app reconnects — does the dance drop held clients?
   - (c) Offline crack of the encrypted BLE 0x0028 blobs (AES-256-CBC, fixed IV, plusaes) — needed only if the full replace-the-app BLE dance must be replicated byte-for-byte.
7. **Production implication** (single-device constraint intact — `ZMQKeyManager` is a local `zmq_curve_keypair`, no cloud anywhere in the key path): our app can **replace the Rapsodo app as the monitor's client**: BLE dance (HCI-captured writes) → key fetch from 53560 (or boot-key read from 0x0034) → CURVE connect 32501 as first client → receive JSON events (ShotParameters id 2005 with ClubPath/AttackAngle/Carry/Total/Apex/TracePolyCoeffs*; ClubDataParameters id 2021). Coexistence with a live Rapsodo app on the same ROUTER appears architecturally impossible (single-client design).
8. **Tooling note:** the original four BLE "key candidates" were misparsed snoop fragments — ATT push opcodes are NOTIFICATION=0x1a / INDICATION=0x1b (the earlier 0x1d/0x1e assumption was wrong); two of those candidates were prefixes of 144 B / 48 B encrypted blobs on 0x0028. Probe framework (fetch/hunt/capture), fixed snoop parsers, and the analyzed Ghidra project are staged privately for follow-up sessions.

## Drift-watch

- 2026-10-13: app v2.25.6 (vc600), FW 3.8.61 references. BLE char map + key-from-device-name derivation recorded in staging findings. Re-verify characteristic UUIDs and credential payload layout on any app/firmware update.
- 2026-10-14: ZMQ plane (32501/64646 ROUTERs, 53560/64660 key services), key lifecycle timings, and the boot-key characteristic pinned against app v2.25.6 / FW 3.8.61. Re-verify port map + handshake on any monitor firmware update.
