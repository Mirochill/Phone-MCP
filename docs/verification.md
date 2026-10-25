# Verification and remaining limits

## 0.19.0 — Branching trees and independent plans

[Sanitized real-device evidence](verification-0.19.0.json) records the installed S24 and deployed relay, tool-cache refresh, exact plan IDs, action counters, reaction records and final cleanup. No online human match or paid action was started.

- Native chess tree: initial e4 plus three successive prepared replies Nf3/Bb5/Ba4, all effects verified, final witness eight delivered clicks. Local trigger-confirmation-to-input times for the replies: 141/250/123 ms. These exclude model preparation and the rest of app-input/effect latency.
- General UI tree: A → B → final confirmed page, two inputs, both effects verified. A first test used a nonmatching button capitalization; it was refused with zero inputs and the session remained active. Fresh exact observed selectors completed the task without session recreation.
- Four independently armed plans in one session, fifth refused by capacity. Two plans cancelled without stopping the other two; each remaining plan executed exactly one intended action, total two inputs.
- Two same-session plans recognizing the same state both yielded `needs_model/conflicting_ready_plans` before input; witness zero inputs.
- UI tree and chess tree active concurrently on distinct owned displays (292 and 293), then independently completed with two and eight inputs respectively. Reading a plan ID through the other session was refused.
- Cancelling a chess tree after its verified e4 prevented later Nf3 when the e5 trigger appeared. Reusing its request ID returned the cancelled result; it did not restart. Two-input witness stayed unchanged.
- A one-second standby plan expired without input and preserved the app session. Ending one test session cancelled its pending plan while a different session's plan stayed active. All test sessions were then ended: zero active sessions and zero draining displays. The physical keep-on preference remained false.
- ChatGPT actual Refresh completed; the application detail view lists 66 unique tools, Write 44 / Read 22, including `phone_plan_list` and the new tree/persistence descriptions. Other users must update their own relay and client cache; existing conversations may retain older schemas.
- Checks: 143 server tests, 19 Cloudflare tests, 32 Android policy families; release assemble/lint (zero errors, 35 existing warnings), same signing certificate, alignment, local documentation links and generated tools passed.

Goal-persistence is guidance at initialization, tool descriptions and per-result instructions. The runtime does not decide that an arbitrary user goal is complete and cannot force a host model past its own turn/token limits. Prepared branches run only known decisions; no claim of reduced model reasoning time, improved chess strength, exhaustive application coverage, new ad-network validation or battery gain is made. The release package and source provenance are checked separately in [publication checks](../releases/0.19.0/Publication-checks.json).


## 0.18.3 — semantic chess controls, installed and tested

The two user-reported errors were `Fresh source capture unavailable for board move; no input sent.` and `La disposition de la cible ou du trajet a changé. Relisez avant le geste.` The second was reproduced exactly on the real S24 with an offline selection-affordance witness: the old path executed the source, then refused step 2 while all pieces and coordinates stayed fixed. The corrected route received exactly two clicks, verified e2-e4, and returned its final observation/image in 1.069 seconds relay time.

The real Chess.com app accepted e2-e4 against a bot with `inputTransport=accessible_squares`; the resulting pawn position was independently read and captured (2.642 seconds including final observation/image). No new online-human opponent was engaged. An online-human match is not claimed as verified by this test.

Android 0.18.3 then passed native prepared-response tests, physical screen off with local keep-on disabled: initial e2-e4 and a predicted e7-e5 -> g1-f3 response both used named square actions and verified their effects. The fixture received four total clicks; the prepared reply reached the app in 698 ms.

A second native trial deliberately rendered its whole window black for 12 seconds while retaining a complete accessible board. The pre-trial screenshot was confirmed `black_or_empty` after four capture attempts. The native plan still executed and verified initial e4 and prepared Nf3 through exact square controls: four total inputs and 338 ms app receipt for the reply. The final observation still indicated the masked-render interval. After that interval, the visible capture showed the matching final position. This proves semantic control without raster evidence in this bounded fixture; it does not prove universal removal of black rendering in Chess.com or other apps.

The guard remains strict for incomplete/inconsistent boards, changed app/session/window/geometry/pieces, revoked controls and uncertain/partial input. No input-path fallback occurs after a semantic click. Coordinate-only boards still require valid fresh visual evidence. Model thinking was not rebenchmarked in this bug-fix run; quoted figures are relay/local execution only. No battery comparison was made. All 139 server tests, 19 Cloudflare tests and 31 policy families passed; release build/lint/signature/alignment passed, 0 lint errors and 35 existing warnings. [Sanitized evidence](verification-0.18.3.json).

## 0.18.2 relay — black transition capture recovery

The user reported another black frame in an online Chess.com game and clarified that the physical phone subsequently displayed the board while the agent stopped on its earlier black capture. The ended session retained a `lastCapture.blank=true` diagnostic. The original online match was not reproduced and no new online opponent was engaged.

A deterministic full-black transition lasting 900 ms was then exercised on the real S24 Ultra, using an offline fixture with an independently counted input witness. Before the relay change, the action returned a black image after 813 ms. After deployment, the identical action returned a visible image in 1,659 ms total: the capture portion took 1,285 ms, with two black frames followed by a visible third capture. Exactly one action was sent in each trial, and the app input count advanced once per intentional test. This tests the late-render mechanism, not Chess.com's entire online renderer.

A five-second black transition exhausted the bounded recapture budget after 1,989 ms of capture (2,410 ms whole action); it remained explicitly black and its session stayed active. A subsequent healthy direct capture used one native command, 419 ms, without recovery. The deployed live connector was read back as `0.18.2-cloud`, 65 tools; Android remains `0.18.1`. Test sessions ended and displays drained to zero; the local physical-screen keep-on option remained disabled.

All 135 server tests and 19 Cloudflare tests passed. New tests cover success, no extra healthy read, persistent black, scope changes, stop/disconnect/grant loss, legacy session pinning, no write replay, a single chess move after recovery, and refusal when its position changes during recovery. Native plan/ad internal captures are unchanged by this server-only patch. No universal fix for persistent, protected or partially black content is claimed. [Sanitized evidence](verification-0.18.2-relay.json).


## 0.18.1 targeted real-device record

The installed Android 0.18.1 passed screen-off startup after a 29.9-second pause, native UI action/effect/capture, zero-input timeout and a prepared chess response (687 ms observed app receipt). Earlier 0.18.0 tests covered two predicted branches (448/508 ms), unknown-state yield, cancel, no duplicate input, concurrent WeWard/fixture work and a real offline Chess.com response. Model preparation still measured 26.1–27.6 seconds; the UI's 31–37 seconds includes tool/app waiting and is not pure reasoning time. Final ChatGPT response text failed to render in the test chats, although native effects and relay timing records were verified.

The last real WeWard ad was completed and its one-card reward verified, but the first skip required the corrected Java policy running in a desktop test harness. The remaining native closures returned to the roulette in 5.892 seconds. A complete native 0.18.1 real-ad replay remains pending quota availability. Do not infer universal ad success or battery savings. [Raw sanitized timings and limits](performance-verification-0.18.1.json).


Evidence is scoped to the version and scenario actually tested. Successful builds, accepted tool calls, visible app effects and battery improvements are distinct claims.

## 0.17.0 relay performance update

The companion remains Android 0.16.2; no installation or Android source change is part of this relay update. The new owned launch, semantic flow, partial failure and preserved state were exercised on the real S24. A real Chess.com analysis-board move used one two-tap sequence and final full-quality image. See [the measured results](performance.md) and [sanitized timing data](performance-verification-0.17.0.json). The later client trials include model/host time, including unsuccessful final-message rendering, and do not establish faster full games or universal ad completion. The final local suites passed 113 server and 19 Cloudflare checks; the imported ChatGPT inventory was read back as 42 write + 20 read tools.

## 0.16.2 capture release — S24 Ultra / Android 16

| Check | Evidence |
|---|---|
| Distributed APK | `fr.phonemcp`, version 0.16.2, code 65, 3,620,683 bytes; SHA-256 in the release manifest. Signature/alignment/build metadata verified. |
| PNG | Source AI display only, 720×1280, 100,749 bytes. First hardware check on 0.16.0; final image-writing code unchanged. User confirmed correct content. |
| Final silent MP4 | 0.16.2: 148 encoded samples / 2,459 ms / 936,662 bytes and 562 samples / 9,508 ms / 3,884,025 bytes; both 720×1280, no audio. |
| Physical-screen isolation | User confirmed animated video and image contained only the AI app, without viewer controls or physical-screen content. Recording continued while the user was on the physical home screen. |
| Cleanup | Final diagnostics: 0 active recordings, 0 pending images, 0 own test sessions; no local-recording wakelock. |
| Local regression tests in capture development | 26 actual CaptureMirror checks, 16 PendingMedia checks, 27 screen-controller checks; release/lint/signature/alignment checks passed, 0 lint errors / 35 warnings. |

The initial 0.16.0/0.16.1 encoder had no frames and removed empty outputs. The final version fixes the mirrored layer's compositor root. These short recordings are functional checks, not sustained frame-rate, battery or thermal measurements. The exact in-progress session-end auto-finalization path remains unverified on the phone: the session was ended after the observed recording had already stopped.

The sanitized [release verification JSON](../releases/0.16.2/Verification-0.16.2.json) contains sizes, timing and test results. Raw local media URIs and device-session identities are not needed for public use.

## Earlier device regressions retained by current source

- 0.15.4: user confirmed twice that the physical panel stayed black through agent gestures/display recovery, and one side-button press restored it normally. Keep-on was disabled locally; remote enabling was rejected.
- 0.15.5: user confirmed Samsung hardware-button screenshots from calculator and Chess previews, via See app and Recents. The original intermittent refusal was not reproduced universally.
- 0.15.3: repeated captures of an unchanged bot Chess.com board after an idle pause succeeded through the owned source surface. The bot board accepted a real two-tap move. The black-board report in an online-human game was not reproduced; no human match was started for diagnosis.
- 0.15.1/0.15.3: real Macadam ads returned through two Skip controls and one Close in 104.402 s / 114.947 s. First Skip appeared only after 93.429 s / 98.932 s. App-imposed waits were not bypassed. Those observations do not prove universal ad completion or cash rewards.
- 0.12–0.13: independent same-app neutral windows retained distinct state; concurrent requests stayed on distinct displays; an observed graphical action received genuine DOWN/UP events; unrelated animated layout no longer invalidated a stable target.
- Local handoff retained calculator 15, rejected the old session and allowed a fresh same-app session. Explicit Stop remains a different local revocation.

These are historical measured checks, not all repeated for a documentation/publication update. The historical 0.8.2 [device record](live-device-verification.json) and launch media remain labeled as such.

## Checks for this GitHub synchronization

See [publication checks](../releases/0.16.2/Publication-checks.json) for the exact suites and source/APK checks rerun on the public tree. Its Android source must match the maintained 0.16.2 snapshot; public relay-specific native OAuth/tunnel support is preserved. The full file hash of a rebuild can differ with its local signing key; do not infer an identical signed binary from build success.

## Not established

- Universal screen-off interaction, independent navigation or rendering for every Android/OEM/app.
- Universal removal of partly black app content or source-imposed screenshot restrictions.
- Instant rewarded-ad completion, every ad-network close path, or rewards from a return event.
- Every client plan's MCP availability, all host attachment-binding flows, or forced refresh of an existing ChatGPT conversation.
- Measured battery savings, long-duration recording stability, sustained throughput/frame rate, or a controlled energy before/after comparison. The older Samsung battery history is observational and not causal evidence.
- Uninterrupted full-APK updates; Android restarts the app and its sessions. No abandoned control context is silently resumed.
