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

1. Exact KDF/cipher (or behavioral clone from logcat key + samples).
2. RTSP URL/port/auth (logcat + Wireshark during live stream).
3. RHost port/protocol (ZeroMQ? CoAP?) and whether clips need auth.
4. Do our `mlm.rapsodo.com` partner credentials authenticate over the AUTHENTICATION characteristic?

## Drift-watch

- 2026-10-13: app v2.25.6 (vc600), FW 3.8.61 references. BLE char map + key-from-device-name derivation recorded in staging findings. Re-verify characteristic UUIDs and credential payload layout on any app/firmware update.
