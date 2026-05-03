# AAPS Tandem Mobi — Concurrency & Pump-Comm Plan (Final)

**Scope:** `pump/tandem/` — fix the post-v4 race regression, decouple UI tap latency from AAPS queue depth, add comm-suspended handling.

## 1. Problem

Post-v4 merge, Tandem Mobi lost operation-level synchronization. UI workflows bypass AAPS's command queue and can race queue-dispatched commands. `sendCommand`'s message-level lock doesn't protect multi-step operations. `preventConnect` covers only narrow UI paths. The plugin's `busy` field is stale.

## 2. Architecture

### 2.1 Single dispatcher with origin-priority deque

One `PumpOpQueue`. Submission origin determines insertion point; FIFO within origin:

- `submit(op, USER)` → tail of USER section (= ahead of all AAPS ops)
- `submit(op, AAPS)` → tail of queue

UI never waits behind AAPS-queued background work. Dispatch order: head of USER section, else head of AAPS section.

### 2.2 Dedicated single-threaded dispatcher

Dispatcher owns a single-thread executor (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`). All BLE writes happen on this thread. Submitters never run pump I/O on their own thread.

### 2.3 Coroutine-based API

```kotlin
class PumpOpQueue {
    fun <T> submit(op: PumpOp<T>, origin: Origin): Deferred<T>
    fun isBusy(): Boolean   // queue activity only — NOT availability
}

abstract class PumpOp<T> {
    abstract val name: String
    abstract val maxDuration: Duration
    abstract val requiresDelivery: Boolean
    abstract suspend fun run(ctx: PumpOpContext): T
}
```

UI:
```kotlin
lifecycleScope.launch { val r = pumpOps.submit(op, USER).await() }
```

Plugin (called from QueueWorker on `Dispatchers.IO`):
```kotlin
override fun deliverBolus(...): PumpEnactResult =
    runBlocking { pumpOps.submit(BolusOp(...), AAPS).await() }
```

### 2.4 `PumpAvailability` (enum)

```kotlin
enum class PumpAvailability { DeliveryEnabled, DeliveryDisabled, Unknown }
```

- `Unknown` is the initial/post-disconnect state. Treated conservatively — blocks mutating ops the same as `DeliveryDisabled`. Resolves to `DeliveryEnabled` after first successful status read.
- Mutating ops (`requiresDelivery = true`) check the state at the start of `run()`. If not `DeliveryEnabled`, return `success=false` with descriptive comment. The op fast-fails; AAPS Loop re-requests on its next cycle. SMB occurring during a workflow is lost — accepted behavior, consistent with existing AAPS semantics for pump-busy scenarios.
- Set/cleared only via `withAvailability(state, maxDuration) { ... }` helper using try/finally.
- **Watchdog:** tracks `(state, expiresAt)` externally. A periodic tick force-clears any non-`DeliveryEnabled` state past its expiry, logs an alarm event. Prevents permanent lock from a crashed workflow.
- Reason for disabling is logged, not stored in state.

### 2.5 Workflows = compound ops

Multi-step UI workflows (cartridge change, etc.) are a single `PumpOp` whose `run()` does N internal sends. While the workflow is in-flight, the dispatcher serves no other ops. The workflow body is wrapped in `withAvailability(DeliveryDisabled, maxDuration)`. AAPS-queued mutating ops landing during the workflow get queued behind it; when they reach dispatch, they fast-fail on the availability check.

### 2.6 Status reads — same queue, coalesced

Status/read ops use the same queue. At submit time, if an equivalent op is already pending, drop the new one and return its `Deferred`. UI-tap "refresh" submits as USER and naturally jumps ahead.

### 2.7 `CommSuspendGate` at wire send

Single gate inside the dispatcher's send mutex. Pump's "comm suspended / buffer full" event calls `pauseSends(durationMs)`. Gate uses suspending `delay`, never `Thread.sleep`. Independent of queue and availability.

### 2.8 Per-op timeout

Every op declares `maxDuration`. Dispatcher enforces with `withTimeout`. On timeout: op fails, wire mutex released, dispatcher continues. Stuck BLE round-trip cannot freeze the queue beyond the declared duration.

### 2.9 `isBusy()` semantics

Returns true iff there is a pending or in-flight op in the queue. **Does not include availability state** — otherwise a multi-minute cartridge change would freeze AAPS's command queue spinning on `isBusy()`. Mutating ops handle availability via fast-fail, not via `isBusy()`.

## 3. Phased implementation

### Phase A — Queue + availability + dispatcher (2–3 days)

1. Implement `PumpOpQueue`, `PumpOp`, `Origin`, dispatcher loop, single-thread executor, wire mutex.
2. Implement `PumpAvailabilityState` (enum + `withAvailability` + watchdog).
3. Implement `CommSuspendGate`.
4. Migrate plugin mutating methods to submit with `Origin.AAPS` and `requiresDelivery = true`:
   - `deliverBolus`, `stopBolusDelivering`, `setTempBasalPercent`, `setTempBasalAbsolute`, `cancelTempBasal`, `setNewBasalProfile`
   - Remove method-level `@Synchronized`.
5. Replace `isBusy()`. Remove dead `busy` field.
6. Per-op timeouts wired up.

### Phase B — UI migration + workflows (2–3 days)

1. UI single-shot ops migrated to `submit(op, USER)` via `lifecycleScope.launch { ... .await() }`.
2. Cartridge workflow + any multi-step UI flow rewritten as compound `PumpOp`s. Each calls `withAvailability(DeliveryDisabled, ...)` for its body.
3. Remove `preventConnect = true` from concurrency-driven sites. Keep only if its narrow "don't auto-reconnect" semantic is still load-bearing — re-evaluate per site.
4. Status-read coalescing at submit.

### Phase C — Entry-point inventory (gate before merge, 0.5–1 day)

Mandatory checklist: every pump-comm entry point in `pump/tandem` is one of:
1. Submitted via the queue.
2. Intentionally direct (e.g., low-level pairing handshake during connect).
3. Deferred with rationale.

No merge until complete.

### Phase D — Telemetry (alongside A/B)

Counters: ops submitted by origin, dispatch wait time per origin, op duration, suspend-gate pauses, watchdog firings, fast-fails due to availability state. Cheap; answers later "do we need more?" with data.

## 4. File-level changes (expected)

- `pump/tandem/.../common/comm/TandemCommunicationManager.kt` — `CommSuspendGate` integration at `sendCommand`.
- `pump/tandem/.../common/queue/PumpOpQueue.kt` *(new)*, `PumpOp.kt` *(new)*, `PumpAvailabilityState.kt` *(new)*, `CommSuspendGate.kt` *(new)*.
- `pump/tandem/.../common/util/TandemPumpUtil.kt` — wire in queue access if it's the existing utility singleton; otherwise leave alone.
- `pump/tandem/.../mobi/TandemMobiPumpPlugin.kt` — migrate mutating methods, replace `isBusy`, remove dead `busy`.
- `pump/tandem/.../mobi/ui/ActionsActivity.kt`, `DataActivity.kt`, `TandemUiController.kt`, `actions/cartridge/*` — migrate to `submit(op, USER)`, rewrite workflows as compound ops.

## 5. Acceptance criteria

**Functional**

1. UI op submitted while AAPS-queued op is pending dispatches immediately after the current in-flight op completes — never waits behind pending AAPS work.
2. Cartridge workflow holds the dispatcher for its duration; AAPS-queued bolus/TBR landing during it fast-fails with `success=false`. AAPS Loop re-requests on next cycle.
3. Comm-suspended event halts wire sends within one op cycle; resumes cleanly after the suspend window.
4. Duplicate AAPS status refresh requests collapse into one.
5. UI never blocks on the dispatcher thread (verified by inspection: all UI submission sites use `launch { ... .await() }`).

**Safety**

1. No path leaves `PumpAvailability` non-`DeliveryEnabled` without watchdog reset.
2. `try/finally` on every `withAvailability` block; verified by inventory checklist.
3. No deadlocks: gate never holds the wire mutex; lock ordering documented.
4. Stuck BLE op cannot freeze the queue beyond its declared `maxDuration`.
5. Crashed workflow recovers within `maxDuration` via watchdog.

**Scope**

- No multi-lane scheduler.
- No session-token migration.
- No per-characteristic policy registry.

## 6. Verification

1. Compile tandem module + app variant.
2. Manual races:
   - Start bolus, trigger UI refresh concurrently → serialized.
   - Back-to-back AAPS mutating commands → serialized.
   - Cartridge workflow + AAPS-queued bolus → bolus fast-fails, workflow completes, Loop re-requests next cycle.
   - Rapid status-refresh taps during suspension → coalesced to one post-resume refresh.
3. Suspension: inject qualifying event → sends halt → resume → continued completion.
4. Watchdog: simulate workflow crash → state recovers within `maxDuration`.
5. Regression: no stuck busy states, no UI freezes beyond existing BLE timeout, no increased command-failure rate from lock misuse.

## 7. Locked decisions (reference)

| # | Decision |
|---|----------|
| 1 | Single deque, FIFO within origin (USER section ahead of AAPS section). |
| 2 | Status reads in the same queue, coalesced at submit time. |
| 3 | Multi-step workflows = compound ops, single queue entry per workflow. |
| 4 | `PumpAvailability` is a 3-state enum: `DeliveryEnabled`, `DeliveryDisabled`, `Unknown`. |
| 5 | `Unknown` blocks mutating ops same as `DeliveryDisabled`. |
| 6 | Mutating ops fast-fail with `success=false` when not `DeliveryEnabled`. SMB-during-workflow loss is accepted. |
| 7 | Coroutine-based API: `submit() → Deferred`. UI launches; plugin runBlocking on its IO thread. |
| 8 | `isBusy()` reflects only queue activity, not availability. |
| 9 | Per-op `maxDuration` mandatory; dispatcher enforces. |
| 10 | `CommSuspendGate` at wire send, suspending wait (no `Thread.sleep`). |
| 11 | Dedicated single-thread executor for the dispatcher. |
| 12 | Watchdog clears expired non-`DeliveryEnabled` states; prevents permanent lock. |
| 13 | Entry-point inventory checklist is a mandatory pre-merge gate. |

## 8. Out of scope

Multi-lane dispatcher, session token migration, full characteristic registry. Revisit only if telemetry shows persistent issues post-rollout.
