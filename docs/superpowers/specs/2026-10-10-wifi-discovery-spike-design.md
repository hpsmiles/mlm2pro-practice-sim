# Wi-Fi Discovery Spike — MLM2PRO Local Mode (Club Path / AoA / Spin Vectors)

**Date:** 2026-10-10
**Status:** Approved design (brainstorming session, 2026-10-10; capture-kit revision same day — home PC + tablet, bench-first; work laptop excluded)
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
| Capture environment | **Home PC (same LAN, Wireshark installable) + tablet (PCAPdroid + mDNS browser app).** Work laptop excluded — no app installs permitted. adb demoted to optional fallback |
| Session venue | **Bench-first:** the monitor is portable — discovery, probes, and stream evidence at home on the home LAN; shed only for live-ball replay capture if bench evidence is inconclusive |
| Intrusiveness | Passive observation **+ active probing** (port scan, benign endpoint probes). No MITM/ARP spoofing, no packet injection, no auth bypass |
| Legal posture | Active probing is a deliberate, scope-bounded choice against a device the user owns; Rapsodo Terms §6 (no reverse engineering) consciously accepted and documented |
| Single-device constraint | Untouched — spike is observation only; no cloud/PC dependency enters the app |

## 3. Decision gates (outcomes)

| Outcome | Evidence shape | Pre-committed follow-up |
|---|---|---|
| **A. Metric-bearing frames found** | PCAPdroid/PCAP/probe evidence of structured non-video payloads (small per-shot frames, JSON/binary metric structures, or an endpoint returning club/spin data) | Design `Mlm2ProNetworkService` (mDNS discovery + TCP/UDP listener) as originally requested |
| **B. Video-only link confirmed** | Endpoints resolve to camera stream/clip transfer only (RTSP/HTTP video, large frame bursts, no per-shot metric payloads) | Stand down on club-data-over-Wi-Fi; pivot to spin-vector decomposition build (back/side spin from BLE `totalSpin`+`spinAxis`) as a separate design |
| **C. Link opaque/inaccessible** | No discoverable endpoints, payloads fully TLS-opaque with no client-visible API, or topology not reproducible | Document findings; add to drift watch in the 2026-09-26 research doc; revisit after firmware changes |

## 4. Capture strategy & constraints

**Critical constraint:** no third station can see monitor↔tablet unicast payloads. WPA2 pairwise encryption hides station-to-station traffic from every other host, and APs bridge wireless↔wireless unicast internally — it never reaches the wired LAN (rare router-dependent hairpinning aside). This holds for the home PC whether it is on Ethernet or Wi-Fi. PC Wireshark therefore sees only: mDNS/SSDP/UPnP multicast announcements, ARP, broadcast traffic, and the PC's own active-probe traffic. Absence of unicast payloads in the PC's PCAP is expected, not a failure.

The unicast truth comes from **PCAPdroid on the tablet** (no-root, VPN-based capture of the tablet's own traffic): live connections list (remote IP:port, TCP/UDP, per-app), payload/bytes inspection, TLS handshake/SNI visibility, and pcap export for deep analysis on the PC. It works in every topology — monitor-on-LAN **and** monitor-as-direct-hotspot (where the PC is not even on the stream's network).

Evidence comes from three prongs:

1. **Multicast/discovery traffic** (PC Wireshark, passive): mDNS/SSDP/UPnP announcements, ARP → monitor IP/MAC. On the tablet, PCAPdroid's VPN mode does not see multicast addressed to other hosts, so a dedicated **mDNS/ZeroConf browser app** (e.g. Service Browser) covers service discovery on the tablet side.
2. **Tablet-side payload capture (PCAPdroid)** — primary evidence: everything the Rapsodo app sends/receives in Local mode, with full payload visibility, captured before/during/after session start and shots; exported pcap analysed on the PC. Optional fallback: adb socket-table snapshots (`Get-WifiDiscoverySnapshot.ps1`) if PCAPdroid is unusable — requires adb on the PC (verify in Phase 0).
3. **Active probing from the PC**: concurrent TCP port scan of the monitor IP, then benign probes of open ports — HTTP GET `/`, RTSP `OPTIONS`, TLS handshake with certificate capture (subject/issuer/SAN reveal service identity and whether the endpoint is token-gated).

Supporting context: router admin client list (monitor hostname/IP/MAC → OUI lookup) and FCC ID off the device label → fccid.io/fcc.report (certified radio modes, Wi-Fi module). If multicast capture comes up empty, check AP isolation / multicast filtering on the router.

## 5. Session playbook (bench session at home; optional shed follow-up for live balls)

### Phase 0 — Prep (at home)
1. Read FCC ID off the monitor label → fccid.io lookup: record certified radio modes (STA vs AP/P2P) and Wi-Fi module.
2. Tablet installs: **PCAPdroid** (Play Store, or sideload APK via adb — the tablet is dev-unlocked) and an **mDNS/ZeroConf browser app** (e.g. Service Browser). Sanity-check PCAPdroid: open a browser on the tablet, verify flows appear in its connections list.
3. Dry-run kit scripts from the PC against a harmless target (the tablet's own IP) to verify PowerShell plumbing. Optional: confirm adb on the PC if the snapshot fallback is wanted.
4. Record software context: Rapsodo app version (app About), monitor firmware (app About), tablet model + Android version.
5. Ensure the monitor is on the **home SSID** (Rapsodo app Wi-Fi setup — re-provision if it only knows the shed network).

### Phase 1 — Bench session start (home network, no balls needed)
6. PC on the home LAN; start Wireshark capture → `discovery-captures/<date>/bench-phase1-sessionstart.pcapng`.
7. Tablet: start PCAPdroid capture (capture-all; optionally restrict to the Rapsodo app), then start the Rapsodo app and connect in **Local mode**. Whether the camera stream engages without hitting a ball is itself evidence — record it.
8. PC Wireshark: filter `mdns || ssdp || arp` → record any monitor announcements; `arp -a` / router client list → monitor IP (new device, OUI check). Record monitor IP: `____`.
9. PCAPdroid connections list: record every Rapsodo-app remote endpoint visible with the stream active (remote IP:port, TCP/UDP, byte counts). This alone answers topology even if nothing else works.
10. Optional (only if PCAPdroid unusable): snapshot script with `-Label baseline` / `-Label sessionstart` (adb).

### Phase 2 — Active probing (from the PC)
11. `Invoke-PortScan.ps1 -Target <monitorIP> -Mode Common`, then `-Full` if Common finds anything (full 1–65535).
12. For each open port: `Invoke-EndpointProbe.ps1 -Target <monitorIP> -Port <p>` (auto: HTTP → RTSP → TLS). Record status lines, headers, RTSP responses, cert subject/issuer/SAN.
13. Rate discipline: benign probes only; stop and record behavior if the monitor misbehaves (drops/reboots — itself evidence).

### Phase 3 — Stream traffic + optional live balls
14. With the stream active (or at session start), let PCAPdroid capture ≥1–2 minutes; use its payload/bytes view on the busiest Rapsodo endpoint to classify traffic (large video bursts vs small structured frames; TLS vs plaintext). Export the PCAPdroid pcap → `discovery-captures/<date>/bench-phase3-stream.pcap`.
15. **Shed follow-up (only if needed):** if bench evidence is inconclusive (stream never engaged, or fully TLS-opaque), take the tablet (PCAPdroid running) to the shed, hit 3–5 balls, let replays load in the Rapsodo app; export → `shed-phase3-shots.pcap`. Tablet-only — no PC needed in the shed.

### Phase 4 — Teardown
16. Stop Wireshark and PCAPdroid; copy exported pcaps to the PC's `discovery-captures/<date>/`. Fill the session checklist in the playbook doc (`docs/research/2026-10-10-wifi-discovery-session-plan.md`, §6 kit artifact).
17. Confirm `git status` shows no capture files (folder git-ignored).

### Piggyback — BLE offsets 12–15 (separate later session)
Cannot run in this session (Rapsodo app owns the single BLE link). In a later session: run our debug app capturing live shots (CaptureLog records raw frames incl. `unknown1`/`unknown2`), then compare those raw values against the Rapsodo app's displayed carry/total for the same shots (from its history/cloud view). Settles the offsets-12–15 question per the 2026-09-26 doc §2.

## 6. Kit artifacts & script contracts

All artifacts created before the session:

| Artifact | Contract |
|---|---|
| `docs/research/2026-10-10-wifi-discovery-session-plan.md` | The playbook: §5 phases as a fill-in checklist (checkboxes + blanks for monitor IP, open ports, probe results, PCAPdroid endpoints, PCAP names), Wireshark filter cheat sheet, PCAPdroid setup/usage notes (per-app capture, payload view, pcap export), mDNS browser steps, expected outputs per step |
| **Tablet apps** (installed, not repo artifacts) | PCAPdroid (no-root VPN capture; live connections list; payload view; pcap export) + mDNS/ZeroConf browser (e.g. Service Browser). Install paths and setup steps documented in the playbook |
| `tools/discovery/Get-WifiDiscoverySnapshot.ps1` *(optional fallback)* | Params: `-Label` (mandatory), `-OutDir` (default `discovery-captures\<date>`), `-AdbPath` (default `adb`). Checks `adb devices`; writes timestamped `<ts>-<Label>-<source>.txt` for: `dumpsys wifi`, `dumpsys connectivity`, `dumpsys netstats`, `cat /proc/net/tcp`, `cat /proc/net/tcp6`, `netstat -tun`, local `arp -a`. Per-source failure recorded, never aborts the set. Windows PowerShell 5.1 compatible, no admin rights, no installs. Only used if PCAPdroid is unusable; needs adb on the PC |
| `tools/discovery/Invoke-PortScan.ps1` | Params: `-Target`, `-Mode` (`Common` list: 22,53,80,443,554,1935,3478,5000,5010,5020,6666,7000,8000,8001,8008,8080,8443,8888,9000,9080,9100,9999,10000,12345,49152–49200 / `Full` 1–65535), `-TimeoutMs` (400), `-OutFile`. Runspaces-throttled .NET `TcpClient` connect scan; prints open ports; writes result file. Runs on the PC |
| `tools/discovery/Invoke-EndpointProbe.ps1` | Params: `-Target`, `-Port`, `-Scheme` (`http`/`rtsp`/`tls`/`auto`), `-Path` (`/`), `-OutDir`. HTTP: `Invoke-WebRequest -UseBasicParsing` (status + headers + first 2 KB). RTSP: raw `OPTIONS rtsp://<ip>:<port>/ RTSP/1.0\r\nCSeq: 1` via TcpClient. TLS: `openssl s_client` if on PATH else .NET `SslStream` capturing cert subject/issuer/SAN + negotiated protocol; cert exported to `.cer`. All output to text files. Runs on the PC |
| `.gitignore` | Add `discovery-captures/` |

## 7. Analysis & follow-through

1. User runs the bench session (and optional shed follow-up), drops captures into `discovery-captures/`, returns with the filled checklist.
2. Analysis lane (orchestrator/specialist): correlate PCAPdroid endpoint records ↔ PC Wireshark PCAP ↔ probe outputs; identify protocol (HTTP/RTSP/TLS/custom), auth gating (cert/token), and payload classes.
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
| Local mode won't engage at the bench without a real shot | Bench session still yields discovery + probes + mDNS; shed follow-up (tablet-only, PCAPdroid) captures stream/replays during live shots; engagement behavior recorded as evidence |
| PCAPdroid unusable (no Play Store, install failure, VPN conflict) | Sideload APK via adb (tablet is dev-unlocked); else fall back to adb socket-table snapshots (Phase 0.3 verifies adb); PC probes still map endpoints |
| PCAPdroid VPN mode misses multicast (mDNS/SSDP) | Dedicated mDNS browser app on the tablet + PC Wireshark multicast capture carry discovery |
| AP isolation / multicast filtering on the router blocks discovery | Check router settings if multicast capture is empty; PCAPdroid + active probing still work |
| Monitor invisible via ARP until it talks | Router client list as authoritative IP source; session-start multicast traffic usually forces ARP entries |
| Port scan/probes disturb the device | Benign probes only, low rate, stop-on-misbehavior with behavior recorded as evidence |
| Unicast blind spot on the PC misread as "no traffic" | Expectation stated in playbook (§4); PCAPdroid carries the payload evidence |
| Rapsodo app/firmware drift | App + firmware versions recorded every session (Phase 0.4) |

## 10. Success criteria

- Bench session executed with checklist filled through Phase 3 (or documented blockers per step); optional shed follow-up if bench evidence inconclusive.
- Artifacts present: ≥1 PCAPdroid pcap covering a Local-mode attempt, ≥1 PC Wireshark pcap (multicast/probes), port scan output, probe outputs.
- Findings report appended to the 2026-09-26 research doc; gate outcome (A/B/C) explicitly recorded with its follow-up action started or consciously deferred.
