# Release history

## 0.19.0 — Branching trees, independent plans and objective persistence

- Add nested `then` branches for chess and exact semantic UI flows: six levels, 32 total cases, eight siblings, finite stage transitions only after verified effects; retain flat-plan compatibility.
- Allow four independent armed plans per owned session. Serialize same-session inputs, wait for effect confirmation, and yield conflicting ready plans before any input. Preserve independent session/display routing.
- Add read-only `phone_plan_list`; cancelling one plan preserves the others. Explicit standby `onUnexpected=wait` never acts on unpredicted pages and remains deadline bounded.
- Reinforce MCP initialization, tool descriptions and result guidance: a completed plan is not task completion; preserve unfinished session state, continue/recover safely, clean after verified user-goal completion or explicit stop. Host model/turn limits remain outside the plugin's control.
- Catalog: 66 tools / 62 operations. APK and deployed relay updates plus ChatGPT tool refresh are separate requirements.


## 0.18.3 — Exact accessible chess square actions

- Prefer complete, clickable accessible square controls for `board_move`; reread all 64 cells between source and destination. No source raster is required for semantic actions. Keep guarded coordinate fallback for other supported boards.
- Handle legal selection/highlight changes without confusing them with a moved board. Stop on changed pieces/window/geometry, unavailable destination, local stop or uncertain input; never replay or fall back after a partial move.
- Apply semantic chess controls to native prepared response plans, preserving exact case matching, cancellation and effect verification.
- Real S24: reproduced the exact old step-2 layout refusal, then verified two-input e2-e4 on the same fixture; verified e2-e4 in Chess.com against a bot; native initial e4 and prepared Nf3 both verified, 698 ms app receipt for the response, physical screen off and keep-on disabled. No new online-human match was started.
- Server: 139 tests; Cloudflare: 19; Android: 31 policy families; release build/lint, signature and alignment checks passed (0 lint errors, 35 existing warnings). APK 0.18.3 uses the same signing certificate.



## 0.18.2 relay — transient black capture recovery (Android remains 0.18.1)

- Recapture fully black transition frames inside the same MCP call: at most four additional read-only captures, adaptive waits and an 1.8-second retry-start budget. Healthy captures incur no extra command or quality reduction. No app relaunch, injected input, physical-screen wake or protected-capture bypass.
- Keep retries pinned to the original app/session/display/snapshot; stop on lost grants, session cancellation, disconnect or changed identity. Persistently black output remains explicitly unusable rather than becoming a false success.
- Apply the same recovery to direct captures and final observations of actions, ad jobs and prepared plans. Native job-internal capture behavior is unchanged by this relay-only update.
- Recheck the full chess position after successful black-frame recovery before sending one move. A changed position is refused without input.
- Update live agent guidance: one black frame does not establish an ended/unusable session. No additional MCP tool/schema is needed (65 tools / 61 operations).


## 0.18.1 — Prepared conditional responses and real-device fixes

- Add `phone_plan_start`, `phone_plan_status` and `phone_plan_cancel`: native exact UI/chess cases, guarded input, postconditions, bounded waits, idempotent IDs, cancellation and yield on unknown states. The catalog now contains 65 tools / 61 operations.
- Add explicit `detail=decision` board observations, keeping all 64 squares in piece-placement FEN and the position/window hash while omitting redundant square nodes. Full trees and requested capture quality remain available. Preserve move verification after compaction.
- Fix final plan captures when the native image response omits a redundant packageName but retains its required session/snapshot binding.
- Resume the owned display before starting a plan/ad job after idle; publish terminal plan status consistently.
- Recognize measured double-triangle/bar skip controls, including low-contrast SDK corner containers, and bare X controls on positively identified embedded store endcards. No timer bypass or reward guarantee.
- Installed/tested on the S24 Ultra with the physical panel off: 0.18.1 UI start after 29.9 seconds idle, result capture, timeout with zero input, and a predicted chess response received in 687 ms. Earlier native 0.18.0 scenarios covered alternate branches, unexpected states, stop, duplicate IDs and a real offline Chess.com move.
- Three real ChatGPT trials include preparation: 26.1–27.6 seconds until plan ready, then 439–797 ms app receipt for the predicted case. No faster internal reasoning or equivalent full-game strength is established.
- Real WeWard test: native 0.18.0 missed a translucent skip and expired; the corrected Java policy was applied to two fresh real frames by a desktop test harness, then native closing finished the two later pages in 5.892 seconds. The app confirmed/opened a one-card pack. A complete automatic replay from the beginning on native 0.18.1 is still unverified because the last available turn was consumed.

See [conditional plans](docs/conditional-plans.md) and [sanitized measured evidence](docs/performance-verification-0.18.1.json). Local checks: 126 server tests, 19 Cloudflare tests, 31 Android policy families, release build/lint (0 errors, 35 warnings), signature and ZIP alignment. APK signing remains the same developer certificate.


## 0.17.0 relay — 2026-10-24 (Android remains 0.16.2)

- Add `phone_start_app`: owned session creation, one app launch and first observation/image in a single call. Reusing the request retains the existing app; uncertain launches are never replayed.
- Add `phone_board_move`: derive an exact complete accessible chess position, accept a chosen algebraic move, recheck the current position/window before fresh gestures and verify observed piece movement. No engine, guessed pieces or inferred full FEN.
- Add `phone_ui_flow`: 1–8 exact semantic click/wait steps, fresh guards per step and one final observation/image. Missing/ambiguous targets, deadlines and cancellation stop subsequent inputs.
- Extend read-only `phone_ad_status` to a bounded 20-second wait and optional final UI evidence, using existing APK-compatible native reads. Start independent local jobs before waiting; ad timers remain unchanged.
- Add observed `clickNodeId` parent hints for non-clickable labels, per-call relay/device/queue timing, and a bounded content-free owner-scoped timing history. Relay durations explicitly exclude model time; compare them with full client-turn timestamps.
- Expose 62 tools / 58 operations, API 1. Preserve image quality and existing Android screen policy; no APK replacement is needed. See [performance evidence and limits](docs/performance.md).

## 0.16.2 — 2026-10-24

- Source-only PNG and silent MP4 capture from the local AI preview. Continue recording outside the viewer, use its notification to stop, and save to the PhoneMCP Gallery album.
- Fix the encoder mirror's compositor root on Samsung: 0.16.0/0.16.1 produced no encoded samples; 0.16.2 saved animated videos successfully and removes empty/failed files.
- Typed owner-only Binder capture, renewable source-specific leases, pending MediaStore publication and cleanup. No new remote capture tool or microphone permission.
- Current Android source, both relays, complete 59-tool documentation, updated setup and release evidence are published together. Preserve public native desktop OAuth and portable tunnel support.

## 0.15.4–0.15.5

- Physical keep-on mode belongs exclusively to local Android settings, disabled by default. Remove remote/automatic selection of a lit-screen fallback; preserve normal side-button restoration.
- Permit screenshots of the preview and its content-free Recents router; add content-free preview diagnostics. Original intermittent source-app refusals were not universally reproduced.

## 0.14–0.15.3

- Bounded on-device ad-closing jobs for successive pages, Skip/Close controls, hidden controls and supported visual evidence. Timers and reward conditions remain app-controlled.
- Owned-surface capture for black/incomplete-frame diagnosis; retain unchanged frames through idle pauses. Real bot chess and Macadam paths tested; original online-human black-board incident was not reproduced.
- A successful handoff allows a fresh same-app session without resurrecting the old session. Remove the terminal-job history cap that blocked later transfers.

## 0.12–0.13

- Session-specific app displays and command routing, independent same-app navigation where supported, task-preserving handoff and owned-task cleanup.
- Physical-panel-off native touch path, audio handling, display transition/readiness recovery and target-specific animated-layout validation.
- Bounded gesture sequences, exact wait-and-click, final observations/screenshots and first-read stabilization.

## 0.9–0.11

- Shared operation registry with stable discovery/read/action gateways; expanded app, text, gesture, notification and selected-folder tools.
- Direct authenticated local-file streaming and host-native chat-file inputs without user-created public URLs; paired APK update manifests and checksums.
- Multilingual Android interface, setup improvements and annotated capture fixes.

## 0.8.2 — first public release

The first public repository/release, original Android source, 21-tool relay, setup guides and launch media remain available at [v0.8.2](https://github.com/Mirochill/Phone-MCP/releases/tag/v0.8.2). Its binaries, decompiler output and build evidence are historical and do not describe the current version.
