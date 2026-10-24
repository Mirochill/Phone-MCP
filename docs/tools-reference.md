# Complete MCP tool reference

Generated from the real MCP `tools/list` response by `npm run docs:tools`; do not hand-edit. See [tool workflows](tools.md) for sessions, gestures, ad jobs and file transfers.

Release **0.17.0** · **62 tools** · API **1** · catalog **c1b6e733309e4029**.

| Tool | Access | Purpose |
|---|---|---|
| [`phone_connection`](#phone-connection) | Read | Check the actual connection and current server/catalog version, including when the phone is offline. Includes recent content-free owner-scoped relay timings for end-to-end speed diagnosis; host/model time is not directly measured. |
| [`phone_catalog`](#phone-catalog) | Read | Live operation catalog. Request an operation for its current arguments schema. The stable gateways automatically use this catalog after server updates. |
| [`phone_read`](#phone-read) | Read | Read through the stable API. Discover operation and arguments with phone_catalog. New read operations are available automatically. |
| [`phone_action`](#phone-action) | Action | Execute an authorized write through the stable API. Discover its current arguments with phone_catalog. New actions are available automatically. |
| [`phone_status`](#phone-status) | Read | Session, permissions, displays, Android and installed app version. |
| [`phone_diagnostics`](#phone-diagnostics) | Read | On-demand battery, CPU, memory and Phone MCP wakelock counters. Does not read app contents. |
| [`phone_diagnostic_probe`](#phone-diagnostic-probe) | Action | Run or retrieve a bounded two-sample battery observation without a wakelock. It can outlive a stopped Phone MCP service. |
| [`phone_session_create`](#phone-session-create) | Action | Create a separately routed app session. requestId is an idempotent UUID; keep the returned sessionId. Does not launch the app. The physical-screen policy is selected only in local Phone MCP settings. keepRendering=true preserves visual background work during pauses at a higher idle cost. After a successful user handoff, create a new requestId/session; the former session stays terminated. Explicit local Stop still requires local resumption. |
| [`phone_start_app`](#phone-start-app) | Action | Fast path: create one owned app session, open the app once and return its first observation plus screenshot in ONE tool call. Prefer this over separate create/open/snapshot/capture calls. A repeated requestId retains the existing session without reopening its app. Inspect launchUncertain and observationError; never replay an uncertain launch. Returns observation with a fresh snapshot after the action. |
| [`phone_ui_flow`](#phone-ui-flow) | Action | Execute 1-8 planned exact semantic clicks/waits with fresh native target guards and ONE final observation/screenshot. Use for already understood navigation; no model round trip between steps. Stops at the first missing/ambiguous/protected target, error or deadline. Inspect completedSteps/failedStep/uncertain; never replay a partial flow. App/session and local stop guards remain enforced. Use action_sequence for already observed Canvas/game gestures. Returns observation with a fresh snapshot after the action. |
| [`phone_board_move`](#phone-board-move) | Action | Play one already-decided chess move such as e2e4 using the boardId from observation.board. Prefer the observed board.piecePlacementFen over manually decoding coordinates. Freshly rechecks all 64 accessible squares and app/session/window geometry, captures the current source, then sends one two-tap sequence with a final observation/image. Refuses changed/incomplete boards before any input. No engine or move selection; no full legality or turn inference. Promotion choices remain separate. Never replay partial/uncertain inputs. Returns observation with a fresh snapshot after the action. |
| [`phone_session_wake`](#phone-session-wake) | Action | Recover only the existing AI display under the screen policy selected locally in Phone MCP settings. This tool cannot enable the physical-screen-on mode or change the local setting. It does not relaunch the app or bypass keyguard. If off-screen recovery fails, report it instead of choosing a lit-screen fallback. |
| [`phone_session_cleanup`](#phone-session-cleanup) | Action | Remove empty abandoned task records for an authorized app. Active and physical tasks are always preserved. Optional taskIds migrate identified legacy AI tasks; no live app is closed. |
| [`phone_session_list`](#phone-session-list) | Read | List this authorization’s active and ended app sessions, their displays, isolation and lease state. |
| [`phone_session_keepalive`](#phone-session-keepalive) | Action | Extend a legitimate thinking pause for an active session. Does not restart an ended or locally stopped session. |
| [`phone_session_end`](#phone-session-end) | Action | Finish this app session and release its display and preview. A different app session continues. |
| [`phone_session_cancel`](#phone-session-cancel) | Action | Interrupt only this app session and reject its queued commands. A locally stopped app requires local resumption. |
| [`phone_notifications`](#phone-notifications) | Read | Read current notifications for an authorized app without opening it. |
| [`phone_reply_notification`](#phone-reply-notification) | Action | Send the requested reply through a freshly observed notification. |
| [`phone_snapshot`](#phone-snapshot) | Read | Read an authorized window. Reuse an action observation. A cursor reads more nodes from the same unchanged snapshot. |
| [`phone_screenshot`](#phone-screenshot) | Read | Capture the authorized window. For black or incomplete images try captureSource=surface: a fresh frame from the owned non-secure virtual display, without changing app state or physical-screen power. Optional labels map through coordinateSpace. |
| [`phone_open_app`](#phone-open-app) | Action | Open an authorized app on the active control display. Returns observation with a fresh snapshot after the action. |
| [`phone_open_url`](#phone-open-url) | Action | Open an HTTPS URL in the chosen authorized app on the active display. Returns observation with a fresh snapshot after the action. |
| [`phone_click`](#phone-click) | Action | Click an observed node. Legacy longPressNodeId and coordinateActions remain supported. Returns observation with a fresh snapshot after the action. |
| [`phone_long_press`](#phone-long-press) | Action | Long press an observed node, for example to open its context menu. Returns observation with a fresh snapshot after the action. |
| [`phone_set_text`](#phone-set-text) | Action | Replace an observed editable field, excluding protected fields. Returns observation with a fresh snapshot after the action. |
| [`phone_scroll`](#phone-scroll) | Action | Scroll an observed scrollable node. Returns observation with a fresh snapshot after the action. |
| [`phone_back`](#phone-back) | Action | Go back within the observed authorized window. Returns observation with a fresh snapshot after the action. |
| [`phone_tap`](#phone-tap) | Action | Tap a freshly observed target. Canvas/game areas require a recent screenshot tied to the same snapshot; unrelated animation does not invalidate a stable target. Returns observation with a fresh snapshot after the action. |
| [`phone_swipe`](#phone-swipe) | Action | Swipe within the freshly observed window and stable geometry. For canvas/game areas, first capture the same snapshot. Returns observation with a fresh snapshot after the action. |
| [`phone_action_sequence`](#phone-action-sequence) | Action | Execute up to 8 already observed taps/swipes locally in one bounded command, with one final observation. Prefer one swipe for a drag or two taps for source/destination. Every step keeps the original session, app, window and target guards. Stops at the first failure; inspect executedCount and failedStep, never replay the whole sequence. Does not bypass app timers, protected content or reward conditions. Returns observation with a fresh snapshot after the action. |
| [`phone_ad_start`](#phone-ad-start) | Action | Start one bounded local ad-closing job on an existing app session. The phone waits and closes available controls across successive ad windows without model round trips. Supply observed app returnTargets; for a graphical game, save its host windowId before the ad, then use by=windowId/exact only after another ad window opens. Available close/skip controls are preferred. Hidden ad controls can be revealed with bounded neutral taps; visual skip icons need positive pixel evidence beside an observed badge. It never skips observed timers or claims rewards; follow ad_status, or ad_cancel. Reuse requestId only for the same job. |
| [`phone_ad_status`](#phone-ad-status) | Read | Read a local ad job with up to 20 seconds of server-side waiting, split into APK-compatible reads. Prefer waitMs=20000; observe=true and includeScreenshot=true return final UI evidence when terminal, avoiding extra model turns. completed confirms an observed returnTarget, not a reward. Keep the exact packageName, sessionId and jobId. Read-only: never clicks or starts another job. |
| [`phone_ad_cancel`](#phone-ad-cancel) | Action | Cancel only this local ad job immediately. It does not end or resume its app session. Actions already delivered may have taken effect; inspect the returned state before continuing. |
| [`phone_set_control_mode`](#phone-set-control-mode) | Action | Explicitly select foreground or parallel, preserving the existing session and permissions. |
| [`phone_files_list`](#phone-files-list) | Read | List Phone MCP transfers and recent installation jobs. Requires Shizuku. |
| [`phone_send_file`](#phone-send-file) | Action | Send a user-selected or generated chat file to the phone. Pass the host file object; no URL needs to be created, copied or published by the user. Follow file_job until completed. |
| [`phone_upload_begin`](#phone-upload-begin) | Action | Prepare a direct binary transfer from a local client, without hosting the file. Reuse requestId only for the same file. Return jobId, offset and an authenticated relative upload path. |
| [`phone_upload_commit`](#phone-upload-commit) | Action | Finish a fully received binary transfer; SHA-256 and exact size must match before the file becomes completed. Follow file_job; incomplete or mismatched bytes are never published as completed. |
| [`phone_download_file`](#phone-download-file) | Action | Download an HTTPS file, up to 512 MiB. Follow its job; reuse requestId after an uncertain result. |
| [`phone_file_job`](#phone-file-job) | Action | Inspect a transfer/install, or cancel a transfer. Only completed confirms success. |
| [`phone_install_apk`](#phone-install-apk) | Action | Install the specified transferred APK via Shizuku, checking its SHA-256 and package. Follow the job. |
| [`phone_stop`](#phone-stop) | Action | Stop the phone session. Restarting requires a local action. |
| [`phone_find_nodes`](#phone-find-nodes) | Read | Find matching accessible elements on the active display, including later pages. |
| [`phone_wait_for`](#phone-wait-for) | Read | Wait for a matching element, returning found=false on timeout. Never repeats an action. |
| [`phone_wait_and_click`](#phone-wait-and-click) | Action | Wait locally up to 8 seconds for one exact visible, enabled, unprotected accessible target, then click once and return its final observation. Timeout returns found=false and performed=false. Ambiguous matches are refused; no coordinate guessing, no timer skipping or action retries. Returns observation with a fresh snapshot after the action. |
| [`phone_wait_for_idle`](#phone-wait-for-idle) | Read | Wait until the observed accessibility content is stable; returns idle=false on timeout. |
| [`phone_node_details`](#phone-node-details) | Read | Read the full text and state of one freshly observed, unprotected node. |
| [`phone_double_tap`](#phone-double-tap) | Action | Double tap inside the freshly observed window and stable target; canvas areas require a recent screenshot. Returns observation with a fresh snapshot after the action. |
| [`phone_pinch`](#phone-pinch) | Action | Pinch or zoom inside the freshly observed window and stable target; canvas areas require a recent screenshot. Returns observation with a fresh snapshot after the action. |
| [`phone_edit_text`](#phone-edit-text) | Action | Append, insert, clear or replace an observed non-protected editable field. Returns observation with a fresh snapshot after the action. |
| [`phone_press_key`](#phone-press-key) | Action | Submit the observed editable field using its Android IME Enter action. Returns observation with a fresh snapshot after the action. |
| [`phone_scroll_to_node`](#phone-scroll-to-node) | Action | Ask Android to bring an observed node into view. Find with includeOffscreen first. Returns observation with a fresh snapshot after the action. |
| [`phone_notification_dismiss`](#phone-notification-dismiss) | Action | Dismiss a freshly observed clearable notification. |
| [`phone_notification_snooze`](#phone-notification-snooze) | Action | Snooze a freshly observed notification. |
| [`phone_storage_locations`](#phone-storage-locations) | Read | List folders explicitly selected on the phone. Works without Shizuku. |
| [`phone_list_files`](#phone-list-files) | Read | List a path inside an explicitly selected folder. |
| [`phone_read_file`](#phone-read-file) | Read | Read a UTF-8 text file inside a selected folder, with line pagination. |
| [`phone_write_file`](#phone-write-file) | Action | Create or explicitly replace a UTF-8 file in a selected folder. Text is bounded to 16,000 JSON characters and 24,000 UTF-8 bytes. Overwrite requires overwrite=true. |
| [`phone_delete_file`](#phone-delete-file) | Action | Delete the requested file inside a selected folder. Directories are refused. |
| [`phone_shared_content`](#phone-shared-content) | Read | List content deliberately shared to Phone MCP through Android Share. |
| [`phone_read_shared_file`](#phone-read-shared-file) | Read | Read a shared text file or image. Does not access other app files. |

## phone-connection

`phone_connection`

Check the actual connection and current server/catalog version, including when the phone is offline. Includes recent content-free owner-scoped relay timings for end-to-end speed diagnosis; host/model time is not directly measured.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-catalog

`phone_catalog`

Live operation catalog. Request an operation for its current arguments schema. The stable gateways automatically use this catalog after server updates.

| Parameter | Required | Schema |
|---|---|---|
| `operation` | No | `{"type":"string","maxLength":80}` |
| `knownVersion` | No | `{"type":"string","maxLength":80}` |

## phone-read

`phone_read`

Read through the stable API. Discover operation and arguments with phone_catalog. New read operations are available automatically.

| Parameter | Required | Schema |
|---|---|---|
| `operation` | Yes | `{"type":"string","minLength":1,"maxLength":80}` |
| `arguments` | No | `{"type":"object","additionalProperties":{},"default":{}}` |

## phone-action

`phone_action`

Execute an authorized write through the stable API. Discover its current arguments with phone_catalog. New actions are available automatically.

| Parameter | Required | Schema |
|---|---|---|
| `operation` | Yes | `{"type":"string","minLength":1,"maxLength":80}` |
| `arguments` | No | `{"type":"object","additionalProperties":{},"default":{}}` |

## phone-status

`phone_status`

Session, permissions, displays, Android and installed app version.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-diagnostics

`phone_diagnostics`

On-demand battery, CPU, memory and Phone MCP wakelock counters. Does not read app contents.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-diagnostic-probe

`phone_diagnostic_probe`

Run or retrieve a bounded two-sample battery observation without a wakelock. It can outlive a stopped Phone MCP service.

| Parameter | Required | Schema |
|---|---|---|
| `probeId` | Yes | `{"type":"string","format":"uuid"}` |
| `action` | Yes | `{"type":"string","enum":["start","result"]}` |
| `seconds` | No | `{"type":"integer","minimum":30,"maximum":900,"default":180}` |
| `startDelaySeconds` | No | `{"type":"integer","minimum":0,"maximum":60,"default":0}` |

## phone-session-create

`phone_session_create`

Create a separately routed app session. requestId is an idempotent UUID; keep the returned sessionId. Does not launch the app. The physical-screen policy is selected only in local Phone MCP settings. keepRendering=true preserves visual background work during pauses at a higher idle cost. After a successful user handoff, create a new requestId/session; the former session stays terminated. Explicit local Stop still requires local resumption.

| Parameter | Required | Schema |
|---|---|---|
| `requestId` | Yes | `{"type":"string","format":"uuid"}` |
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `label` | No | `{"type":"string","minLength":1,"maxLength":80}` |
| `leaseSeconds` | No | `{"type":"integer","minimum":30,"maximum":900,"default":300}` |
| `keepRendering` | No | `{"type":"boolean","default":false}` |
| `sessionId` | No | `{"$ref":"#/properties/requestId"}` |

## phone-start-app

`phone_start_app`

Fast path: create one owned app session, open the app once and return its first observation plus screenshot in ONE tool call. Prefer this over separate create/open/snapshot/capture calls. A repeated requestId retains the existing session without reopening its app. Inspect launchUncertain and observationError; never replay an uncertain launch. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `requestId` | Yes | `{"type":"string","format":"uuid"}` |
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `label` | No | `{"type":"string","minLength":1,"maxLength":80}` |
| `leaseSeconds` | No | `{"type":"integer","minimum":30,"maximum":900,"default":300}` |
| `keepRendering` | No | `{"type":"boolean","default":false}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":true}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-ui-flow

`phone_ui_flow`

Execute 1-8 planned exact semantic clicks/waits with fresh native target guards and ONE final observation/screenshot. Use for already understood navigation; no model round trip between steps. Stops at the first missing/ambiguous/protected target, error or deadline. Inspect completedSteps/failedStep/uncertain; never replay a partial flow. App/session and local stop guards remain enforced. Use action_sequence for already observed Canvas/game gestures. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |
| `steps` | Yes | `{"type":"array","items":{"type":"object","properties":{"op":{"type":"string","enum":["wait_and_click","wait_for"]},"query":{"type":"string","minLength":1,"maxLength":250},"by":{"type":"string","enum":["text","description","viewId"],"default":"text"},"timeoutMs":{"type":"integer","minimum":100,"maximum":8000,"default":3000}},"required":["op","query"],"additionalProperties":false},"minItems":1,"maxItems":8}` |
| `timeoutMs` | No | `{"type":"integer","minimum":100,"maximum":8000,"default":8000}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":true}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-board-move

`phone_board_move`

Play one already-decided chess move such as e2e4 using the boardId from observation.board. Prefer the observed board.piecePlacementFen over manually decoding coordinates. Freshly rechecks all 64 accessible squares and app/session/window geometry, captures the current source, then sends one two-tap sequence with a final observation/image. Refuses changed/incomplete boards before any input. No engine or move selection; no full legality or turn inference. Promotion choices remain separate. Never replay partial/uncertain inputs. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |
| `boardId` | Yes | `{"type":"string","pattern":"^[a-fA-F0-9]{64}$"}` |
| `uci` | Yes | `{"type":"string","pattern":"^[a-h][1-8][a-h][1-8]$"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":true}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-session-wake

`phone_session_wake`

Recover only the existing AI display under the screen policy selected locally in Phone MCP settings. This tool cannot enable the physical-screen-on mode or change the local setting. It does not relaunch the app or bypass keyguard. If off-screen recovery fails, report it instead of choosing a lit-screen fallback.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |
| `reason` | No | `{"type":"string","enum":["noninteractive","black_render","app_requires_screen"],"default":"app_requires_screen"}` |

## phone-session-cleanup

`phone_session_cleanup`

Remove empty abandoned task records for an authorized app. Active and physical tasks are always preserved. Optional taskIds migrate identified legacy AI tasks; no live app is closed.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `taskIds` | No | `{"type":"array","items":{"type":"integer","minimum":1,"maximum":2147483647},"maxItems":64}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |

## phone-session-list

`phone_session_list`

List this authorization’s active and ended app sessions, their displays, isolation and lease state.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-session-keepalive

`phone_session_keepalive`

Extend a legitimate thinking pause for an active session. Does not restart an ended or locally stopped session.

| Parameter | Required | Schema |
|---|---|---|
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |

## phone-session-end

`phone_session_end`

Finish this app session and release its display and preview. A different app session continues.

| Parameter | Required | Schema |
|---|---|---|
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |

## phone-session-cancel

`phone_session_cancel`

Interrupt only this app session and reject its queued commands. A locally stopped app requires local resumption.

| Parameter | Required | Schema |
|---|---|---|
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |

## phone-notifications

`phone_notifications`

Read current notifications for an authorized app without opening it.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |

## phone-reply-notification

`phone_reply_notification`

Send the requested reply through a freshly observed notification.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `notificationId` | Yes | `{"type":"string","format":"uuid"}` |
| `text` | Yes | `{"type":"string","minLength":1,"maxLength":4000}` |

## phone-snapshot

`phone_snapshot`

Read an authorized window. Reuse an action observation. A cursor reads more nodes from the same unchanged snapshot.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `windowId` | No | `{"type":"integer","minimum":0}` |
| `cursor` | No | `{"type":"string","maxLength":100}` |
| `limit` | No | `{"type":"integer","minimum":20,"maximum":350,"default":200}` |
| `includeOffscreen` | No | `{"type":"boolean","default":false}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |

## phone-screenshot

`phone_screenshot`

Capture the authorized window. For black or incomplete images try captureSource=surface: a fresh frame from the owned non-secure virtual display, without changing app state or physical-screen power. Optional labels map through coordinateSpace.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `captureSource` | No | `{"type":"string","enum":["auto","surface"],"default":"auto"}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |
| `snapshotId` | No | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |

## phone-open-app

`phone_open_app`

Open an authorized app on the active control display. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-open-url

`phone_open_url`

Open an HTTPS URL in the chosen authorized app on the active display. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `url` | Yes | `{"type":"string","maxLength":4096,"format":"uri"}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-click

`phone_click`

Click an observed node. Legacy longPressNodeId and coordinateActions remain supported. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-long-press

`phone_long_press`

Long press an observed node, for example to open its context menu. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-set-text

`phone_set_text`

Replace an observed editable field, excluding protected fields. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `text` | Yes | `{"type":"string","maxLength":4000}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-scroll

`phone_scroll`

Scroll an observed scrollable node. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `direction` | Yes | `{"type":"string","enum":["forward","backward"]}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-back

`phone_back`

Go back within the observed authorized window. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-tap

`phone_tap`

Tap a freshly observed target. Canvas/game areas require a recent screenshot tied to the same snapshot; unrelated animation does not invalidate a stable target. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `x` | Yes | `{"type":"integer","minimum":0,"maximum":16383}` |
| `y` | Yes | `{"$ref":"#/properties/x"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-swipe

`phone_swipe`

Swipe within the freshly observed window and stable geometry. For canvas/game areas, first capture the same snapshot. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `fromX` | Yes | `{"type":"integer","minimum":0,"maximum":16383}` |
| `fromY` | Yes | `{"$ref":"#/properties/fromX"}` |
| `toX` | Yes | `{"$ref":"#/properties/fromX"}` |
| `toY` | Yes | `{"$ref":"#/properties/fromX"}` |
| `durationMs` | No | `{"type":"integer","minimum":100,"maximum":1000,"default":350}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-action-sequence

`phone_action_sequence`

Execute up to 8 already observed taps/swipes locally in one bounded command, with one final observation. Prefer one swipe for a drag or two taps for source/destination. Every step keeps the original session, app, window and target guards. Stops at the first failure; inspect executedCount and failedStep, never replay the whole sequence. Does not bypass app timers, protected content or reward conditions. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `steps` | Yes | `{"type":"array","items":{"anyOf":[{"type":"object","properties":{"op":{"type":"string","const":"tap"},"x":{"type":"integer","minimum":0,"maximum":16383},"y":{"$ref":"#/properties/steps/items/anyOf/0/properties/x"}},"required":["op","x","y"],"additionalProperties":false},{"type":"object","properties":{"op":{"type":"string","const":"swipe"},"fromX":{"$ref":"#/properties/steps/items/anyOf/0/properties/x"},"fromY":{"$ref":"#/properties/steps/items/anyOf/0/properties/x"},"toX":{"$ref":"#/properties/steps/items/anyOf/0/properties/x"},"toY":{"$ref":"#/properties/steps/items/anyOf/0/properties/x"},"durationMs":{"type":"integer","minimum":100,"maximum":1000,"default":150}},"required":["op","fromX","fromY","toX","toY"],"additionalProperties":false}]},"minItems":1,"maxItems":8}` |
| `intervalMs` | No | `{"type":"integer","minimum":0,"maximum":250,"default":0}` |
| `timeoutMs` | No | `{"type":"integer","minimum":500,"maximum":8000,"default":4000}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-ad-start

`phone_ad_start`

Start one bounded local ad-closing job on an existing app session. The phone waits and closes available controls across successive ad windows without model round trips. Supply observed app returnTargets; for a graphical game, save its host windowId before the ad, then use by=windowId/exact only after another ad window opens. Available close/skip controls are preferred. Hidden ad controls can be revealed with bounded neutral taps; visual skip icons need positive pixel evidence beside an observed badge. It never skips observed timers or claims rewards; follow ad_status, or ad_cancel. Reuse requestId only for the same job.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |
| `requestId` | Yes | `{"$ref":"#/properties/sessionId"}` |
| `returnTargets` | Yes | `{"type":"array","items":{"type":"object","properties":{"query":{"type":"string","minLength":1,"maxLength":250},"by":{"type":"string","enum":["text","description","viewId","windowId"],"default":"text"},"match":{"type":"string","enum":["exact","contains"],"default":"exact"}},"required":["query"],"additionalProperties":false},"minItems":1,"maxItems":6}` |
| `timeoutMs` | No | `{"type":"integer","minimum":1000,"maximum":180000,"default":90000}` |
| `maxActions` | No | `{"type":"integer","minimum":1,"maximum":16,"default":8}` |
| `visual` | No | `{"type":"boolean","default":true}` |

## phone-ad-status

`phone_ad_status`

Read a local ad job with up to 20 seconds of server-side waiting, split into APK-compatible reads. Prefer waitMs=20000; observe=true and includeScreenshot=true return final UI evidence when terminal, avoiding extra model turns. completed confirms an observed returnTarget, not a reward. Keep the exact packageName, sessionId and jobId. Read-only: never clicks or starts another job.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |
| `jobId` | Yes | `{"$ref":"#/properties/sessionId"}` |
| `waitMs` | No | `{"type":"integer","minimum":0,"maximum":20000,"default":0}` |
| `observe` | No | `{"type":"boolean","default":false}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-ad-cancel

`phone_ad_cancel`

Cancel only this local ad job immediately. It does not end or resume its app session. Actions already delivered may have taken effect; inspect the returned state before continuing.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `sessionId` | Yes | `{"type":"string","format":"uuid"}` |
| `jobId` | Yes | `{"$ref":"#/properties/sessionId"}` |

## phone-set-control-mode

`phone_set_control_mode`

Explicitly select foreground or parallel, preserving the existing session and permissions.

| Parameter | Required | Schema |
|---|---|---|
| `mode` | Yes | `{"type":"string","enum":["foreground","parallel"]}` |
| `panelOff` | No | `{"type":"boolean","default":false}` |

## phone-files-list

`phone_files_list`

List Phone MCP transfers and recent installation jobs. Requires Shizuku.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-send-file

`phone_send_file`

Send a user-selected or generated chat file to the phone. Pass the host file object; no URL needs to be created, copied or published by the user. Follow file_job until completed.

| Parameter | Required | Schema |
|---|---|---|
| `requestId` | Yes | `{"type":"string","format":"uuid"}` |
| `file` | Yes | `{"type":"object","properties":{"download_url":{"type":"string","maxLength":4096,"format":"uri"},"file_id":{"type":"string","minLength":1,"maxLength":512},"mime_type":{"type":"string","maxLength":200},"file_name":{"type":"string","maxLength":500}},"required":["download_url","file_id"],"additionalProperties":false}` |
| `fileName` | No | `{"type":"string","minLength":1,"maxLength":140}` |
| `sha256` | No | `{"type":"string","pattern":"^[a-fA-F0-9]{64}$"}` |
| `maxBytes` | No | `{"type":"integer","minimum":1,"maximum":536870912,"default":536870912}` |

MCP metadata: `{"openai/fileParams":["file"]}`

## phone-upload-begin

`phone_upload_begin`

Prepare a direct binary transfer from a local client, without hosting the file. Reuse requestId only for the same file. Return jobId, offset and an authenticated relative upload path.

| Parameter | Required | Schema |
|---|---|---|
| `requestId` | Yes | `{"type":"string","format":"uuid"}` |
| `fileName` | Yes | `{"type":"string","minLength":1,"maxLength":140}` |
| `sizeBytes` | Yes | `{"type":"integer","minimum":1,"maximum":536870912}` |
| `sha256` | Yes | `{"type":"string","pattern":"^[a-fA-F0-9]{64}$"}` |

## phone-upload-commit

`phone_upload_commit`

Finish a fully received binary transfer; SHA-256 and exact size must match before the file becomes completed. Follow file_job; incomplete or mismatched bytes are never published as completed.

| Parameter | Required | Schema |
|---|---|---|
| `jobId` | Yes | `{"type":"string","format":"uuid"}` |

## phone-download-file

`phone_download_file`

Download an HTTPS file, up to 512 MiB. Follow its job; reuse requestId after an uncertain result.

| Parameter | Required | Schema |
|---|---|---|
| `requestId` | Yes | `{"type":"string","format":"uuid"}` |
| `url` | Yes | `{"type":"string","maxLength":4096,"format":"uri"}` |
| `fileName` | Yes | `{"type":"string","minLength":1,"maxLength":140}` |
| `sha256` | No | `{"type":"string","pattern":"^[a-fA-F0-9]{64}$"}` |
| `maxBytes` | No | `{"type":"integer","minimum":1,"maximum":536870912}` |

## phone-file-job

`phone_file_job`

Inspect a transfer/install, or cancel a transfer. Only completed confirms success.

| Parameter | Required | Schema |
|---|---|---|
| `jobId` | Yes | `{"type":"string","format":"uuid"}` |
| `action` | No | `{"type":"string","enum":["status","cancel"],"default":"status"}` |

## phone-install-apk

`phone_install_apk`

Install the specified transferred APK via Shizuku, checking its SHA-256 and package. Follow the job.

| Parameter | Required | Schema |
|---|---|---|
| `requestId` | Yes | `{"type":"string","format":"uuid"}` |
| `fileId` | Yes | `{"$ref":"#/properties/requestId"}` |
| `sha256` | Yes | `{"type":"string","pattern":"^[a-fA-F0-9]{64}$"}` |
| `expectedPackage` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |

## phone-stop

`phone_stop`

Stop the phone session. Restarting requires a local action.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-find-nodes

`phone_find_nodes`

Find matching accessible elements on the active display, including later pages.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `query` | Yes | `{"type":"string","minLength":1,"maxLength":250}` |
| `by` | No | `{"type":"string","enum":["text","description","viewId","role","any"],"default":"any"}` |
| `match` | No | `{"type":"string","enum":["contains","exact"],"default":"contains"}` |
| `includeOffscreen` | No | `{"type":"boolean","default":false}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |

## phone-wait-for

`phone_wait_for`

Wait for a matching element, returning found=false on timeout. Never repeats an action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `query` | Yes | `{"type":"string","minLength":1,"maxLength":250}` |
| `by` | No | `{"type":"string","enum":["text","description","viewId","role","any"],"default":"any"}` |
| `match` | No | `{"type":"string","enum":["contains","exact"],"default":"contains"}` |
| `includeOffscreen` | No | `{"type":"boolean","default":false}` |
| `timeoutMs` | No | `{"type":"integer","minimum":100,"maximum":8000,"default":5000}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |

## phone-wait-and-click

`phone_wait_and_click`

Wait locally up to 8 seconds for one exact visible, enabled, unprotected accessible target, then click once and return its final observation. Timeout returns found=false and performed=false. Ambiguous matches are refused; no coordinate guessing, no timer skipping or action retries. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `query` | Yes | `{"type":"string","minLength":1,"maxLength":250}` |
| `by` | No | `{"type":"string","enum":["text","description","viewId"],"default":"text"}` |
| `match` | No | `{"type":"string","const":"exact","default":"exact"}` |
| `timeoutMs` | No | `{"type":"integer","minimum":100,"maximum":8000,"default":5000}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-wait-for-idle

`phone_wait_for_idle`

Wait until the observed accessibility content is stable; returns idle=false on timeout.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `timeoutMs` | No | `{"type":"integer","minimum":500,"maximum":8000,"default":5000}` |
| `stableMs` | No | `{"type":"integer","minimum":250,"maximum":1500,"default":600}` |
| `sessionId` | No | `{"type":"string","format":"uuid"}` |

## phone-node-details

`phone_node_details`

Read the full text and state of one freshly observed, unprotected node.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |

## phone-double-tap

`phone_double_tap`

Double tap inside the freshly observed window and stable target; canvas areas require a recent screenshot. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `x` | Yes | `{"type":"integer","minimum":0,"maximum":16383}` |
| `y` | Yes | `{"$ref":"#/properties/x"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-pinch

`phone_pinch`

Pinch or zoom inside the freshly observed window and stable target; canvas areas require a recent screenshot. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `centerX` | Yes | `{"type":"integer","minimum":0,"maximum":16383}` |
| `centerY` | Yes | `{"$ref":"#/properties/centerX"}` |
| `startDistance` | Yes | `{"type":"integer","minimum":20,"maximum":1500}` |
| `endDistance` | Yes | `{"type":"integer","minimum":20,"maximum":1500}` |
| `durationMs` | No | `{"type":"integer","minimum":150,"maximum":1000,"default":400}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-edit-text

`phone_edit_text`

Append, insert, clear or replace an observed non-protected editable field. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `mode` | Yes | `{"type":"string","enum":["append","insert","clear","replace"]}` |
| `text` | No | `{"type":"string","maxLength":4000,"default":""}` |
| `position` | No | `{"type":"integer","minimum":0,"maximum":4000}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-press-key

`phone_press_key`

Submit the observed editable field using its Android IME Enter action. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `key` | No | `{"type":"string","const":"enter","default":"enter"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-scroll-to-node

`phone_scroll_to_node`

Ask Android to bring an observed node into view. Find with includeOffscreen first. Returns observation with a fresh snapshot after the action.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `snapshotId` | Yes | `{"type":"string","format":"uuid"}` |
| `sessionId` | No | `{"$ref":"#/properties/snapshotId"}` |
| `nodeId` | Yes | `{"type":"string","minLength":1,"maxLength":260,"pattern":"^0(?:\\.\\d+)*$"}` |
| `observe` | No | `{"type":"boolean","default":true}` |
| `settleMs` | No | `{"type":"integer","minimum":0,"maximum":800}` |
| `detail` | No | `{"type":"string","enum":["compact","full"],"default":"compact"}` |
| `includeScreenshot` | No | `{"type":"boolean","default":false}` |
| `maxDimension` | No | `{"type":"integer","minimum":320,"maximum":1920,"default":1000}` |
| `quality` | No | `{"type":"integer","minimum":35,"maximum":95,"default":75}` |
| `annotate` | No | `{"type":"boolean","default":false}` |

## phone-notification-dismiss

`phone_notification_dismiss`

Dismiss a freshly observed clearable notification.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `notificationId` | Yes | `{"type":"string","format":"uuid"}` |

## phone-notification-snooze

`phone_notification_snooze`

Snooze a freshly observed notification.

| Parameter | Required | Schema |
|---|---|---|
| `packageName` | Yes | `{"type":"string","minLength":3,"maxLength":240,"pattern":"^(?:android\|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$"}` |
| `notificationId` | Yes | `{"type":"string","format":"uuid"}` |
| `durationMs` | Yes | `{"type":"integer","minimum":1000,"maximum":604800000}` |

## phone-storage-locations

`phone_storage_locations`

List folders explicitly selected on the phone. Works without Shizuku.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-list-files

`phone_list_files`

List a path inside an explicitly selected folder.

| Parameter | Required | Schema |
|---|---|---|
| `locationId` | Yes | `{"type":"string","format":"uuid"}` |
| `path` | No | `{"type":"string","maxLength":500,"default":""}` |
| `offset` | No | `{"type":"integer","minimum":0,"maximum":10000,"default":0}` |
| `limit` | No | `{"type":"integer","minimum":1,"maximum":200,"default":100}` |

## phone-read-file

`phone_read_file`

Read a UTF-8 text file inside a selected folder, with line pagination.

| Parameter | Required | Schema |
|---|---|---|
| `locationId` | Yes | `{"type":"string","format":"uuid"}` |
| `path` | Yes | `{"type":"string","maxLength":500}` |
| `offset` | No | `{"type":"integer","minimum":1,"maximum":100000,"default":1}` |
| `limit` | No | `{"type":"integer","minimum":1,"maximum":200,"default":100}` |

## phone-write-file

`phone_write_file`

Create or explicitly replace a UTF-8 file in a selected folder. Text is bounded to 16,000 JSON characters and 24,000 UTF-8 bytes. Overwrite requires overwrite=true.

| Parameter | Required | Schema |
|---|---|---|
| `locationId` | Yes | `{"type":"string","format":"uuid"}` |
| `path` | Yes | `{"type":"string","maxLength":500}` |
| `text` | Yes | `{"type":"string","maxLength":16000}` |
| `overwrite` | No | `{"type":"boolean","default":false}` |

## phone-delete-file

`phone_delete_file`

Delete the requested file inside a selected folder. Directories are refused.

| Parameter | Required | Schema |
|---|---|---|
| `locationId` | Yes | `{"type":"string","format":"uuid"}` |
| `path` | Yes | `{"type":"string","maxLength":500}` |

## phone-shared-content

`phone_shared_content`

List content deliberately shared to Phone MCP through Android Share.

| Parameter | Required | Schema |
|---|---|---|
| (none) | — | — |

## phone-read-shared-file

`phone_read_shared_file`

Read a shared text file or image. Does not access other app files.

| Parameter | Required | Schema |
|---|---|---|
| `fileId` | Yes | `{"type":"string","format":"uuid"}` |
| `offset` | No | `{"type":"integer","minimum":1,"maximum":100000,"default":1}` |
| `limit` | No | `{"type":"integer","minimum":1,"maximum":200,"default":100}` |
