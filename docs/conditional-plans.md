# Prepared conditional responses

Introduced in Android/relay 0.18.0: `phone_plan_start`, `phone_plan_status` and `phone_plan_cancel`. The current relay catalog contains 66 tools, including read-only `phone_plan_list`. A plan runs on its existing owned app session; it does not add a model, chess engine, arbitrary script execution or a new external service.

Prepare a few high-confidence branches while the app or opponent is busy. Once one exact case is observed and confirmed stable, the phone sends its already prepared action without waiting for another model turn. Unknown or ambiguous states stop the plan with `needs_model`. Inspect the returned observation and make a new decision. A plan never invents the missing reply.

## Chess example

Read an accessible complete board once. Keep `board.boardId`, `board.piecePlacementFen`, package and session. Supply your color, an optional initial move, and up to eight opponent/reply pairs:

```json
{
  "operation": "plan_start",
  "arguments": {
    "packageName": "com.chess",
    "sessionId": "OBSERVED_SESSION_UUID",
    "requestId": "NEW_UUID",
    "plan": {
      "kind": "chess",
      "boardId": "OBSERVED_BOARD_SHA256",
      "anchorFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
      "ourColor": "white",
      "initialMove": "e2e4",
      "cases": [
        {"opponent": "e7e5", "reply": "g1f3"},
        {"opponent": "c7c5", "reply": "g1f3"}
      ]
    },
    "timeoutMs": 60000,
    "observe": true,
    "detail": "decision"
  }
}
```

These are API examples, not recommended competitive moves. The agent must select and authorize replies for its actual task. No chess engine is used. The server derives each expected complete 64-square position and binds the anchor to app, session, display, window and geometry. A matching opponent position that appeared during plan preparation can catch up without replaying the initial move. Any other stable position yields to the model. Castling, promotion and en passant require a fresh separate decision using existing tools; this first conditional format does not derive them.

The final `decision` observation contains the full piece-placement FEN and original snapshot ID, omitting the 64 redundant square labels. `detail=full` preserves the full tree. Screenshots remain available at the requested dimension and quality. The compact decision view does not evaluate the position or select a response.

## General application cases

A `kind=ui` plan uses exact `text`, `description` or `viewId` selectors. Each anchor/case condition contains `all` (one to six selectors) and optional `none`. Every case has one `click` or `back`, a distinct `after` condition and optional `terminal=false` for a bounded continuation. Window changes require explicit `allowWindowChange=true`. More than one matching branch or target stops the plan. Keep conditions specific enough to identify the expected page, not just a common Next button.

Android 0.19+ permits four active plans per owned session; an ad job remains exclusive. Other app sessions remain independent. See the tree/concurrency contract below. Android 0.18 supports one flat plan per session. Stop/cancel, disconnection, lease expiry, capture/read/write permission removal and local takeover cancel pending actions. A short input already delivered may have changed the app: inspect `actions` and `inputMayHaveOccurred`; never replay an uncertain plan. Reusing the same request ID returns the same result, and changing its configuration is refused. Use a new ID only for an intentional revised plan.

Use `plan_status(waitMs=20000)` for bounded waiting; native reads are split into at most eight seconds each. Status is read-only and cannot start or repeat a plan. Cancellation bypasses the waiting lane. When inactive, the runner removes its callbacks and restores its prior rendering policy.

## Measure the whole latency

Report model preparation time separately from `actions[].reactionMs` (recognized-case confirmation and local preflight before input), input completion and effect confirmation. A prepared response can avoid a later model turn; its initial preparation is not free, and unknown cases still need normal reasoning. Coverage and correctness must be evaluated along with timing. This does not promise universal instant decisions, unchanged game strength or parallel execution of ChatGPT's internal reasoning.

## Advertisements

Prefer a semantic app return target when one exists (for example an actually observed roulette button). A recreated Android window can have a different ID. Use a host-window ID only for a graphical app with no stable semantic return target. A matched return is not evidence that a reward was granted; verify that separately in the app.

The ad runner recognizes a positively measured enclosed corner double-triangle/bar skip control. Bare X recognition is restricted to an identified embedded store endcard (ad badge, install label, about section and its store container), rather than guessing corner taps. Existing timers and reward conditions remain controlled by the app. Live verification for this release is recorded separately after device testing; synthetic/capture checks alone are not a real-ad success.


## 0.19: trees and independent plans

Every chess or UI case may contain `then: [cases]`. These descendants are eligible only after that parent action's postcondition is actually verified. An alternative sibling is not a continuation: the chosen branch enters its own stage, so its unchosen siblings cannot fire later. The compiler derives descendant chess positions from the parent's verified expected effect, including branch captures. There is no engine, strategy generator, arbitrary code or presumed opponent reply. The model must select the strategy and every prepared action.

Limits: **six branch levels**, **32 total cases**, **eight siblings**, **four active plans per session**, a **12,000-character compiled plan budget**, and the existing **1–180 second plan deadline**, also bounded by session/grant lifetime. Large selectors/conditions may reach the wire budget before the case limit. Every input remains guarded and effects are verified. Castling, en passant and promotion still require a fresh separate decision.

Example: prepare e4, then independently predict e5/Nf3/Nc6/Bb5/a6/Ba4 or a Sicilian reply. These are protocol examples, not competitive move recommendations:

```json
{
  "kind": "chess",
  "boardId": "OBSERVED_BOARD_SHA256",
  "anchorFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
  "ourColor": "white",
  "initialMove": "e2e4",
  "cases": [
    {"opponent":"e7e5","reply":"g1f3","then":[
      {"opponent":"b8c6","reply":"f1b5","then":[
        {"opponent":"a7a6","reply":"b5a4"}
      ]}
    ]},
    {"opponent":"c7c5","reply":"g1f3"}
  ]
}
```

UI cases keep their exact `when`, `action`, and `after` conditions and may use the same `then` structure. A branch with descendants continues even if `terminal` was omitted. Flat UI `terminal=false` retains its prior bounded one-use-case behavior; it does not create a loop.

Install independent plans through separate `phone_plan_start` calls with new request IDs and `waitMs=0`, **then** await selected results. `phone_plan_list(packageName,sessionId)` returns all active and retained ended plans, IDs, stages, case counts, states, actions and active capacity. Cancel one with `phone_plan_cancel`; other plans and the app session remain. Reusing the same request ID retrieves its existing outcome, including cancellation; it cannot restart or replay it.

A session has one navigation context and one input lane. Plans can wait independently, but their writes are serialized. Before any input, the coordinator checks other active ready plans on fresh native evidence. Conflicting ready plans yield `needs_model/conflicting_ready_plans` **without input**. If another plan is verifying its last effect, the next action waits for that verification. Use separate app sessions for independent navigation and concurrent actions on separate displays.

`onUnexpected="yield"` is the default: an unpredicted stable state returns to the model. `onUnexpected="wait"` is an explicit standby choice: the plan stays armed on unrelated states, sends no speculative action, and still expires at its deadline. This is useful for an independent later page. Ambiguous matches, protected/incomplete reads, unexpected window/geometry changes, permission loss and uncertain input still yield/stop; standby cannot override those checks. A tree's inactive descendants never qualify merely because another page looks familiar.

## Objective persistence and cleanup

A local plan outcome is **not** a verdict on the user's final objective. Every result carries `taskGoalStatus="not_evaluated_by_runner"` and `sessionEnded=false`. The MCP instructions, tool descriptions and per-result guidance tell the agent to continue the authorized unfinished objective, recover safely from transient failures, preserve app state during reasoning, and use new decisions/plans after `needs_model` or an exhausted branch. A progress report, difficult game or failed action does not justify stopping midway. The agent must inspect real terminal evidence; it must not call a failed task a success.

Once the final objective is verified, end the owned session to release its app tasks, display, preview and pending work. `session_cleanup` handles eligible leftover empty task records; it is not a reason to close a still-needed app during a pause. Explicit user stop/takeover, permission withdrawal and bounded leases/deadlines remain authoritative. If a hard external blocker prevents progress, report the precise blocker and unmet objective instead of looping or claiming success.

These instructions guide ChatGPT; a plugin cannot force the host model to continue sampling forever, remove its turn/token limits, guarantee a win or guarantee that a multi-app goal is complete. There is no hidden autonomous model or indefinite background loop. Update the installed companion, deployed relay and ChatGPT tool cache separately; an APK alone does not refresh a cached tool schema. Current release: **0.19.0**, **66 tools**, catalog **9536208318cf0507**. Refresh in ChatGPT's Phone MCP settings; a new conversation is safest when an existing one retains old definitions. Agents with cached names can use `phone_catalog` plus stable `phone_read`/`phone_action` with the current schema.

## Real S24 validation

See [0.19 verification](verification.md#0190--branching-trees-and-independent-plans) for the exact tested paths and limits. The native tree tests use a deterministic offline witness, not an online human opponent or a claim of improved chess strength. Report local prepared execution separately from model preparation time.
