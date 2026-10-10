# Wi-Fi Discovery Spike — MLM2PRO Local Mode (Club Path / AoA / Spin Vectors)

**Date:** 2026-10-10
**Status:** Approved design (brainstorming session, 2026-10-10)
**Related:** `docs/research/2026-09-26-mlm2pro-club-data-and-transports.md` (§4 discovery plan), `AGENTS.md`

---

## 1. Problem & objective

The user wants `Club Path`, `Angle of Attack`, and full spin vector components (`Spin Axis`, `Back Spin`, `Side Spin`). Verified research (2026-09-26) established:

- Club path / AoA are measured on-device but **do not ride BLE** and are not provided to any third party over any known channel (three independent open-source projects concur).
- The monitor's Wi-Fi serves internet/R-Cloud sync plus the undocumented **Local mode** link (Impact Vision camera stream + shot clips in-session). Topology, ports, protocol, and auth for that link are unknown; it is believed video-only.
- Spin Axis + Total Spin are **already decoded over BLE** (`MeasurementParser` offsets 8–9, 10–11); back/side spin are pure derivations. Only Club Path + AoA are genuinely missing.

**Spike objective:** establish with captured evidence what the Local-mode Wi-Fi link actually carries — topology, transport, protocol, auth, and whether any metric-bearing frames exist — before building any network service. Zero app code changes in this spike.

## 2. Decisions already made

| Decision | Choice |
|---|---|
| Sequence | Discovery spike **before** any network service design or code |
| Capture environment | Full kit: laptop (Wireshark) + tablet with USB debugging (adb), both on the shed/home Wi-Fi |
| Intrusiveness | Passive observation **+ active probing** (port scan, benign endpoint probes). No MITM/ARP spoofing, no packet injection, no auth bypass |
| Legal posture | Active probing is a deliberate, scope-bounded choice against a device the user owns; Rapsodo Terms §6 (no reverse engineering) consciously accepted and documented |
| Single-device constraint | Untouched — spike is observation only; no cloud/PC dependency enters the app |

## 3. Decision gates (outcomes)

| Outcome | Evidence shape | Pre-committed follow-up |
|---|---|---|
| **A. Metric-bearing frames found** | Socket-table/PCAP/probe evidence of structured non-video payloads (small per-shot frames, JSON/binary metric structures, or an endpoint returning club/spin data) | Design `Mlm2ProNetworkService` (mDNS discovery + TCP/UDP listener) as originally requested |
| **B. Video-only link confirmed** | Endpoints resolve to camera stream/clip transfer only (RTSP/HTTP video, large frame bursts, no per-shot metric payloads) | Stand down on club-data-over-Wi-Fi; pivot to spin-vector decomposition build (back/side spin from BLE `totalSpin`+`spinAxis`) as a separate design |
| **C. Link opaque/inaccessible** | No discoverable endpoints, payloads fully TLS-opaque with no client-visible API, or topology not reproducible | Document findings; add to drift watch in the 2026-09-26 research doc; revisit after firmware changes |

## 4. Capture strategy & constraints

**Critical constraint:** a laptop Wi-Fi NIC in managed mode **cannot see monitor↔tablet unicast payloads** (WPA2 pairwise encryption + unicast frame filtering at 802.11). Passive Wireshark on the laptop therefore sees only: mDNS/SSDP/UPnP multicast announcements, ARP, broadcast traffic, and the laptop's own active-probe traffic. Absence of unicast payloads in the PCAP is expected, not a failure.

Evidence therefore comes from three prongs:

1. **Multicast/discovery traffic** (laptop Wireshark, passive): mDNS/SSDP/UPnP announcements, ARP → monitor IP/MAC.
2. **Tablet-side socket truth (adb)**: `dumpsys wifi` (SSID/subnet/IP), `dumpsys netstats` (per-UID byte counts during replay transfer), `/proc/net/tcp` (+tcp6) and `netstat -tun` remote IP:port tables sampled before, during, and after shots. `/proc/net/tcp` may be permission-restricted on newer Android — failure is recorded and treated as evidence; fallbacks: `dumpsys connectivity`, netstats per-UID totals.
3. **Active probing from the laptop**: concurrent TCP port scan of the monitor IP, then benign probes of open ports — HTTP GET `/`, RTSP `OPTIONS`, TLS handshake with certificate capture (subject/issuer/SAN reveal service identity and whether the endpoint is token-gated).

Supporting context: router admin client list (monitor hostname/IP/MAC → OUI lookup) and FCC ID off the device label → fccid.io/fcc.report (certified radio modes, Wi-Fi module).

## 5. Session playbook (one 20–30 min shed or bench session)

### Phase 0 — Prep (desk, before session)
1. Read FCC ID off the monitor label → fccid.io lookup: record certified radio modes (STA vs AP/P2P) and Wi-Fi module.
2. Dry-run all kit scripts against a harmless target (the tablet's own IP) to verify PowerShell + adb plumbing.
3. Record software context: Rapsodo app version (app About or `adb shell pm list packages | findstr -i rapsodo` + `dumpsys package <pkg> | findstr versionName`), monitor firmware (app About), tablet model + Android version.

### Phase 1 — Session start (no balls needed)
4. Laptop joins the same SSID; start Wireshark on the Wi-Fi adapter; save as `discovery-captures/<date>/phase1-sessionstart.pcapng`.
5. Run snapshot script with `-Label baseline` (tablet plugged in via USB, pre-session).
6. Tablet: start Rapsodo app, connect in **Local mode** (camera stream comes up).
7. Run snapshot script with `-Label sessionstart`. Note SSID/subnet/gateway from dumpsys wifi.
8. Wireshark: apply display filter `mdns || ssdp || arp` → record any monitor announcements; `arp -a` / router client list → monitor IP (new device, OUI check). Record monitor IP: `____`.

### Phase 2 — Active probing
9. `Invoke-PortScan.ps1 -Target <monitorIP> -Mode Common`, then `-Full` if Common finds anything (full 1–65535).
10. For each open port: `Invoke-EndpointProbe.ps1 -Target <monitorIP> -Port <p>` (auto: HTTP → RTSP → TLS). Record status lines, headers, RTSP responses, cert subject/issuer/SAN.
11. Rate discipline: benign probes only; stop and record behavior if the monitor misbehaves (drops/reboots — itself evidence).

### Phase 3 — Shots
12. Hit 3–5 balls; let replays load in the Rapsodo app.
13. During a replay transfer: run snapshot script with `-Label midshot` (this is the money shot — remote IP:port of the transfer endpoints). Note wall-clock time to correlate with PCAP.
14. Repeat once with `-Label midshot2` for consistency.

### Phase 4 — Teardown
15. Stop Wireshark; save `phase3-shots.pcapng`. Fill the session checklist in the playbook doc (`docs/research/2026-10-10-wifi-discovery-session-plan.md`, §6 kit artifact).
16. Confirm `git status` shows no capture files (folder git-ignored).

### Piggyback — BLE offsets 12–15 (separate later session)
Cannot run in this session (Rapsodo app owns the single BLE link). In a later session: run our debug app capturing live shots (CaptureLog records raw frames incl. `unknown1`/`unknown2`), then compare those raw values against the Rapsodo app's displayed carry/total for the same shots (from its history/cloud view). Settles the offsets-12–15 question per the 2026-09-26 doc §2.

## 6. Kit artifacts & script contracts

All artifacts created before the session:

| Artifact | Contract |
|---|---|
| `docs/research/2026-10-10-wifi-discovery-session-plan.md` | The playbook: §5 phases as a fill-in checklist (checkboxes + blanks for monitor IP, open ports, probe results, mid-shot endpoints, PCAP names), Wireshark filter cheat sheet, expected outputs per step |
| `tools/discovery/Get-WifiDiscoverySnapshot.ps1` | Params: `-Label` (mandatory), `-OutDir` (default `discovery-captures\<date>`), `-AdbPath` (default `adb`). Checks `adb devices`; writes timestamped `<ts>-<Label>-<source>.txt` for: `dumpsys wifi`, `dumpsys connectivity`, `dumpsys netstats`, `cat /proc/net/tcp`, `cat /proc/net/tcp6`, `netstat -tun`, local `arp -a`. Per-source failure recorded, never aborts the set. Windows PowerShell 5.1 compatible, no admin rights, no installs |
| `tools/discovery/Invoke-PortScan.ps1` | Params: `-Target`, `-Mode` (`Common` list: 22,53,80,443,554,1935,3478,5000,5010,5020,6666,7000,8000,8001,8008,8080,8443,8888,9000,9080,9100,9999,10000,12345,49152–49200 / `Full` 1–65535), `-TimeoutMs` (400), `-OutFile`. Runspaces-throttled .NET `TcpClient` connect scan; prints open ports; writes result file |
| `tools/discovery/Invoke-EndpointProbe.ps1` | Params: `-Target`, `-Port`, `-Scheme` (`http`/`rtsp`/`tls`/`auto`), `-Path` (`/`), `-OutDir`. HTTP: `Invoke-WebRequest -UseBasicParsing` (status + headers + first 2 KB). RTSP: raw `OPTIONS rtsp://<ip>:<port>/ RTSP/1.0\r\nCSeq: 1` via TcpClient. TLS: `openssl s_client` if on PATH else .NET `SslStream` capturing cert subject/issuer/SAN + negotiated protocol; cert exported to `.cer`. All output to text files |
| `.gitignore` | Add `discovery-captures/` |

## 7. Analysis & follow-through

1. User runs the session, drops captures into `discovery-captures/`, returns with the filled checklist.
2. Analysis lane (orchestrator/specialist): correlate socket-table remote endpoints ↔ PCAP traffic ↔ probe outputs; identify protocol (HTTP/RTSP/TLS/custom), auth gating (cert/token), and payload classes.
3. Findings appended to `docs/research/2026-09-26-mlm2pro-club-data-and-transports.md` (§4 gets its empirical answers).
4. **Decision gate fires** (§3): outcome A → brainstorm a `Mlm2ProNetworkService` design; outcome B → brainstorm spin-vector decomposition design; outcome C → drift-watch entry and stop.

## 8. Out of scope

- No app code changes (no `Mlm2ProNetworkService`, no `core/net` module, no manifest changes) — those depend on the gate outcome.
- No ARP spoofing/MITM, packet injection, firmware interaction, or auth bypass.
- No cloud/PC dependency added to the app; single-device constraint untouched.
- Video feature work (beyond protocol discovery) — a separate future effort.

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `/proc/net/tcp` permission-blocked on the tablet's Android version | Fallbacks in script order; per-UID netstats still yields byte counts; active probing becomes primary endpoint source; the failure itself is recorded |
| Monitor invisible via ARP until it talks | Router client list as authoritative IP source; session-start multicast traffic usually forces ARP entries |
| Port scan/probes disturb the device | Benign probes only, low rate, stop-on-misbehavior with behavior recorded as evidence |
| Wireshark unicast blind spot misread as "no traffic" | Expectation stated in playbook (§4); socket tables + probes carry the payload evidence |
| Rapsodo app/firmware drift | App + firmware versions recorded every session (Phase 0.3) |

## 10. Success criteria

- One session executed with checklist filled through Phase 3 (or documented blockers per step).
- Artifacts present: ≥1 PCAP, ≥3 snapshot sets (baseline/sessionstart/midshot), port scan output, probe outputs.
- Findings report appended to the 2026-09-26 research doc; gate outcome (A/B/C) explicitly recorded with its follow-up action started or consciously deferred.
