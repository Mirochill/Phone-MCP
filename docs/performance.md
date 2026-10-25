# Speed workflows and measurements

The current 0.19.0 release pairs Android code 69 and relay 0.19.0, exposing 66 tools / 62 operations. Trees and multiple armed plans require the matching companion. Update/deploy the relay, install the companion, refresh ChatGPT and start a new chat as described in [updating](updating.md).

The 0.17.0 measurements below are historical server-only results with Android 0.16.2; they are not new 0.19.0 benchmarks.

## Reduce repeated model turns

- Use `phone_start_app` with a fresh request UUID, authorized package and optional label. One call creates its owned session, opens once and returns observation plus a screenshot by default. Reusing that UUID retains the existing session; inspect `launchUncertain` and never repeat an uncertain launch. Keep the returned session ID and end it when done.
- Use `phone_ui_flow` for a short already-understood plan: 1–8 exact `wait_and_click` or `wait_for` steps, each with `query`, optional `by` (`text`, `description`, `viewId`) and a bounded wait. The whole plan is limited to 8 seconds. Native target checks run for every step; the model receives one final observation/image. Inspect `completedSteps`, `performedActions`, `failedStep` and `uncertain`. Never replay a partial flow. It stops on ambiguous, missing or protected targets; it does not guess buttons or branches.
- For a visually observed board/game move, use the existing `phone_action_sequence` with two taps (source/destination) or one drag and one final image. Do not separate each tap with another model turn or request another snapshot when the last observation is sufficient. A new position still requires observation and a new decision. Arbitrary future opponent moves cannot be executed safely from stale coordinates.
- A non-clickable label can now include `clickNodeId` identifying its clickable ancestor in the same compact observation. Use that observed target instead of repeatedly clicking a text-only node. This is a hint, never an automatic retry.
- Start independent requested ad jobs in their separate app sessions before awaiting them. Android runs the close-control loop while the agent works elsewhere. Use `phone_ad_status` with `waitMs:20000, observe:true, includeScreenshot:true` for a bounded wait and final evidence when terminal. The server divides the wait into existing APK-compatible reads; it sends no new click. Active jobs defer captures. Never interleave manual inputs with a running job; completion is observed return, not proof of reward.

All existing app/session/window guards, stop behavior, screen policy and image quality options remain in place. No shell/script execution endpoint or external model service was introduced. `keepRendering:true` is available for intentional visual work through thinking pauses at a higher idle cost; it does not change physical-screen keep-on and is not enabled silently.

### Semantic plan shape

Use the live schema and real observed text; these placeholders are not executable targets:

```json
{
  "operation": "ui_flow",
  "arguments": {
    "packageName": "com.example.authorizedapp",
    "sessionId": "REPLACE_WITH_REAL_SESSION_UUID",
    "steps": [
      {"op": "wait_and_click", "query": "EXACT_OBSERVED_NEXT_BUTTON"},
      {"op": "wait_for", "query": "EXACT_EXPECTED_DESTINATION"}
    ],
    "timeoutMs": 6000,
    "includeScreenshot": true,
    "maxDimension": 1920,
    "quality": 95
  }
}
```

Pass this envelope to `phone_action` if the host has cached old named tools. Do not pass invented arguments to a stale named schema. Host-native file inputs still require metadata refresh.

## Structured chess input and algebraic moves

When all 64 visible squares have recognized French/English accessible descriptions and consistent grid geometry, snapshots and final observations now include `board`: `piecePlacementFen`, `boardId`, orientation and explicit nulls for unknown side-to-move/castling/en-passant. The original nodes and image remain available. Partial, duplicate, hidden or inconsistent squares produce no board summary; no pixel guessing is used.

Choose the move in the model, then use `phone_board_move` with the real app/session, the observed `boardId` and four-character `uci` such as `e2e4`. This removes repeated coordinate calculations and lets the model spend its time deciding a move. The relay rereads the current 64 squares, refuses changed position/window/geometry, captures the fresh source and emits one guarded two-tap sequence. A thinking pause does not require a second **model** turn merely to refresh a now-old image if the actual position is unchanged. The relay still refreshes its native evidence before the input.

The final observation/image is unchanged in quality; `moveVerified` means the source is observed empty and the same piece at the destination. It does not prove full game legality, a win or move strength. No engine, opening book or prediction of an opponent is added. Promotion choice is a separate observed action. Unsupported boards are refused before input. Since 0.18.3, a complete accessible board with clickable squares uses semantic source/destination actions and a full position/geometry reread between them, without requiring a raster image. Coordinate fallback still refuses black source images. Keep the same security and stop rules as any other gesture; never replay a partial move.

## Measure the complete path

Every executed operation returns `relayTiming`:

- `totalMs`: elapsed inside this MCP execution, including its session queue and final evidence.
- `deviceMs`: cumulative guarded native command round trips, including network and Android processing; not device CPU time.
- `deviceCommands` and `queueMs`: native call count and initial queue wait.
- `modelTimeIncluded:false`: these values never claim to measure ChatGPT reasoning.

`phone_connection.recentToolCalls` reports up to 48 recent content-free calls for this OAuth owner, with server start/end timestamps and the same duration fields. It exposes only the last five minutes in a bounded history (48 calls per owner, at most 32 owners), without background polling. Node keeps it in RAM; Cloudflare stores this content-free history in the existing Durable Object so hibernation does not erase a measurement between two model turns. Expired records are filtered on access/restore and replaced by subsequent bounded writes. Other OAuth owners cannot read it. Discovery calls are not included. `success` means the relay returned an operation response, not proof that an app accepted an intended effect; inspect partial/uncertain results separately.

For a genuine end-to-end benchmark, record the user-turn start and completion in the client, actual model/effort labels, native effects and the corresponding relay events. The time outside those relay intervals includes model reasoning, host processing, network/tool import/approval and response delivery. Do not label that entire remainder as pure model thinking. Repeat warm and cold trials on equivalent tasks and report outliers, failures and decision quality separately.

## S24 checks on 2026-10-24

Three paired runs on an offline neutral app clicked the same fixed button three times and requested one final source image in both methods. Direct orchestration deliberately omitted interposed model reasoning. Image settings were identical: maxDimension 1920, quality 95, actual source 720x1280.

| Pair | Three separate calls | One semantic flow | Verified inputs |
|---|---:|---:|---|
| 1 | 4,482 ms | 1,362 ms | 3 / 3 |
| 2 | 4,890 ms | 1,347 ms | 3 / 3 |
| 3 | 3,443 ms | 5,476 ms | 3 / 3 |

The median was 4,482 versus 1,362 ms; the arithmetic mean was 4,272 versus 2,728 ms. The third flow was slower: its native action phase took 4,284 ms, with zero relay queue time. That cause was not isolated. These three pairs are too small for a sustained speed guarantee. The app counter went from 3 to the expected 21. A missing first target performed no later input; reusing a start request preserved state without reopening.

A separate real Chess.com **analysis board** accepted e2-e4 using one two-tap sequence and final full-quality image: 2,629 ms host elapsed / 1,671 ms in the relay. Source e2 was empty and the white pawn was observed on e4. This does not measure the time to choose a strategic move, play a full game or compete online; no human match was started for this check.

[Sanitized timing data](performance-verification-0.17.0.json) records raw paired durations and limitations. ChatGPT-inclusive trials are recorded separately in that evidence when available. Neither the local benchmark nor a successful compilation proves faster model reasoning, equivalent chess strength, universal ad closing or battery savings. Native ad closing is unchanged in this relay update; imposed ad waits remain enforced.

## Client turns including reasoning

The same ChatGPT conversation then performed each three-click task through Phone MCP on the real device. The observed selector labels were **GPT-6 / Moyen**; that UI did not identify separate Sol and Luna variants. No model or effort was switched. Client turn start/completion timestamps were read independently from the desktop's ChatGPT thread, rather than inferred from the relay or the model's own prose.

| Phase | Three separate calls, entire turn | Semantic flow, entire turn | Actual counter |
|---|---:|---:|---|
| First use | 15,796 ms | 31,320 ms | 21 -> 24 -> 27 |
| Repeat in the same chat | 15,944 ms | 12,459 ms | 0 -> 3 -> 6 |

The first grouped turn was slower; the repeated grouped turn was about 22% faster in this single pair. These results **do not establish a reliable dramatic reasoning speedup**. They include the entire turn: model work, host processing, tools and response delivery. The client's displayed thought-duration label is not an isolated CPU/latency measurement of internal reasoning.

The ChatGPT web page also showed **Impossible de charger cette réponse** for these final messages, including after a reload. The phone effects were independently verified, so the actions ran, but correct final-answer rendering was not validated. This host-side symptom is recorded rather than hidden or presented as a successful user-visible finish. It was not reproduced by the direct MCP test client.

Phone MCP can execute an already-chosen bounded plan while the model handles other work, and can remove many decision/tool turns from repetitive navigation. It cannot set ChatGPT's internal inference scheduling, make dependent chess decisions concurrently before the new position exists, or promise unchanged strategic strength at a lower reasoning budget. The update does not lower that budget or replace the selected model.

Before structured input, the ChatGPT analysis-board turn took **31,039 ms**, including three relay calls totaling **4,262 ms** (two snapshots plus a two-tap sequence). The remaining **26,777 ms** was outside those relay executions and includes model/host/network overhead; it is not an isolated measure of pure thought. The actual e2-e4 move was independently verified. This trace motivated the structured-board and algebraic-move path rather than treating fast direct taps as proof of fast model decisions.

### Structured-board check with ChatGPT

On another initial analysis board, ChatGPT chose and played e2-e4 using one semantic snapshot and one `board_move` call. Independent phone inspection confirmed the pawn on e4. The relay calls totaled **3,444 ms**. From the client-turn start to the final move-tool completion, UTC timestamps give approximately **18,308 ms**, versus **29,626 ms** for the coordinate path above. These cross-service time-to-move estimates include the model/host gaps but their clocks were not independently calibrated. This is a single simple opening check on an already-open board, not a game-throughput guarantee.

The client displayed 19 seconds for that turn, but the desktop reader supplied an invalid zero-length completion timestamp; its exact full response duration is therefore unavailable. Do not turn that invalid value into a zero-latency claim. Final web-message rendering still failed.

A following black reply reused the already returned observation with one `board_move`: **e7-e5** was independently verified. It took approximately **10,434 ms until the move response**, including **2,231 ms in the relay**; the complete client turn was **14,137 ms**. This demonstrates a working short path, not subsecond internal reasoning. No unsupported assertion about faster full games or unchanged playing strength follows from two opening moves.

Finally, the direct MCP client tried a stale board ID: it was refused before input. Using the current observed ID, **g1-f3** was delivered once and `moveVerified:true` matched the resulting position. Host time was **2,308 ms**, relay **1,563 ms**, with a 720x1280 final source image at the requested quality 95 / maxDimension 1920. All own test sessions were then ended.

## 0.18.1: count preparation, then local reaction

Prepared cases move the next decision before the event. They do not remove model preparation. Three ChatGPT trials at unchanged Moyen effort required 27.554, 27.262 and 26.092 seconds until the plan response. Fresh-chat preparation spent 2.742 seconds in Phone MCP calls and 23.350 seconds outside those calls (model/host/orchestration, not separately observable pure reasoning). App receipt after the predicted event was 439, 797 and 453 ms. UI thought indicators were 36, 37 and 31 seconds and include waits. The completion API timestamp was invalid (equal to start); it was not treated as zero. The final assistant text did not render. These are individual trials, not a controlled model comparison or chess-strength evaluation.

On native 0.18.1 a UI plan started after 29.938 seconds idle, completed its effect and returned a visible final image in one host call (2.252 seconds including read/capture). A separate predicted board response reached the fixture in 687 ms. A real offline Chess.com catch-up test on 0.18.0 had input completion at 1.370 seconds, effect confirmation at 2.146 seconds and full call plus image at 3.753 seconds. No online/rated opponent was involved.

Decision view reduced the fixture from 83 to 7 nodes while preserving the complete 64-square position and board ID. This reduced the payload but did not establish a material preparation speedup in these trials. Unknown states, cancellation, duplicate requests and timeout paths were verified without extra reply inputs. See [conditional plans](conditional-plans.md) and [evidence](performance-verification-0.18.1.json).

### 0.18.2: avoid a model turn for a transient black capture

The relay retries only black screenshot reads within the same call, retaining its scoped app/session/display/snapshot. No strategic reasoning or input is repeated. The tested 900 ms black transition on the real S24 returned a visible image in 1.659 seconds total after the patch, instead of a black result in 0.813 seconds before it. This extra bounded wait replaces an otherwise necessary model/tool round trip; it is not a faster renderer or faster model. Healthy direct capture required one native command (419 ms in this trial). See [the verification scope and limits](verification.md#0182-relay--black-transition-capture-recovery).

### 0.18.3: named chess squares instead of fragile coordinate selection

When every square exposes a real clickable accessibility control, `board_move` uses its exact observed source node, rereads all 64 squares, and uses the fresh destination node. Piece placement, window, session and geometry must remain unchanged between selection and destination. Normal selection/highlight/action-flag changes do not invalidate a named square; a changed position or uncertain click stops the partial move without replay or coordinate fallback. Ordinary graphical gestures retain their existing capture/target checks. Native prepared chess responses use the same approach starting with Android 0.18.3.

Real S24 regression witness: the previous coordinate route executed only the source and refused step 2 with the exact reported layout error. The corrected semantic route received exactly two inputs, verified e2-e4 and returned its final image in 1.069 seconds relay time. A real Chess.com bot board also accepted e2-e4 through named squares and independently showed the pawn on e4 (2.642 seconds including final observation/image). A native prepared e5-to-Nf3 reply on Android 0.18.3 reached the fixture in 698 ms; the native action records confirm accessible-square transport for both initial e4 and Nf3. These are relay/local execution figures, not faster model reasoning or an online-human game benchmark.


### 0.19.0: several future steps without a model turn at each step

Nested `then` trees compile finite prepared decisions to native stages; a child can run only after its parent's effect is verified. Four plans may wait independently per session; simultaneous ready conflicts yield without input. Separate app sessions run on their own displays. The real S24 chess witness received initial e4 and three descendant replies, eight inputs total. For those three replies, recognition-confirmation-to-input measurements were 141, 250 and 123 ms. End-to-end local receipt also includes detection, dispatch and application handling; the final reply witness measured 334 ms from its simulated opponent trigger. These exclude initial model preparation, do not assert faster model thinking, and do not establish competitive chess quality. [Exact scenario record](verification-0.19.0.json).
