# Release history

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
