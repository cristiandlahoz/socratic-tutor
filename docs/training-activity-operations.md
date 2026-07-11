# Training Activity Operations

The training-activity workers publish Micrometer signals through Spring Boot Actuator's configured `MeterRegistry`. This application does **not** install production alert rules, notification destinations, or escalation routing. Exporting metrics and routing alerts to an external monitoring system are deployment responsibilities.

## Quick response path

1. Check the notification backlog and the recent `poll.failures`, `delivery.failure`, and `delivery.exhausted` increases.
2. Inspect only safe persisted error codes and the provider's protected logs; application worker logs deliberately exclude recipient addresses, payloads, and identifiers.
3. For a `FAILED` or `UNCERTAIN` delivery, an authorized professor can request manual replay. Confirm the assignment is still available first; never alter a `SENT` delivery.

## Notification delivery worker signals

All names below are stable. The only worker-owned tag is `state` on `training.activity.notification.backlog`, whose values are the bounded set `pending` and `retryable`. No event ID, delivery ID, activity ID, actor ID, or email address is used as a metric tag.

| Metric | Meaning | Suggested alert | First diagnosis |
|---|---|---|---|
| `training.activity.notification.polls` | Every scheduler poll invocation. | No increase for 5 minutes while backlog is nonzero. | Check scheduler/application health. |
| `training.activity.notification.poll.failures` | A poll or an event-processing unit threw; it was contained so later polls continue. | Any increase for 5 minutes. | Check safe error codes and database/provider availability. |
| `training.activity.notification.events.claimed` | Events successfully leased by this worker. | Backlog grows while this stays flat for 5 minutes. | Check leases, worker scheduling, and database access. |
| `training.activity.notification.delivery.success` | Recipient delivery accepted by the transport. | Informational throughput signal. | Compare against claimed events and backlog. |
| `training.activity.notification.delivery.failure` | A retryable, exhausted, or uncertain delivery outcome. | `>= 3` in 5 minutes. | Separate retryable provider failures from terminal/uncertain states. |
| `training.activity.notification.delivery.retries` | A failure was known before SMTP acceptance and scheduled for retry. | `>= 3` in 5 minutes. | Check SMTP availability, DNS, and timeout settings. |
| `training.activity.notification.delivery.exhausted` | A delivery exhausted automatic retry attempts and its parent event settled as `FAILED`. | `>= 1` in 5 minutes. | Locate the safe failure record and use authorized replay if appropriate. |
| `training.activity.notification.backlog{state="pending"}` | All pending deliveries, including first attempts and scheduled retries. | Positive and rising for 10 minutes. | Compare with polls and events claimed. |
| `training.activity.notification.backlog{state="retryable"}` | Pending deliveries with one or more prior attempts. This is a subset of `pending`. | Positive and rising for 10 minutes. | Investigate recurring provider or network failures. |
| `training.activity.notification.processing.duration` | Duration of a complete scheduler poll, including claim and delivery coordination. | Sustained p95 above 80% of the poll interval. | Check database latency, provider latency, and batch throughput. |

## Backlog and replay state machine

| Record | State | Operational meaning | Replay behavior |
|---|---|---|---|
| Delivery | `PENDING` | Claimable now or at `available_at`; retries remain bounded. | A duplicate replay request is a no-op. |
| Delivery | `PROCESSING` | A worker holds a pre-send lease. | Never manually regress it. Wait for lease recovery. |
| Delivery | `SENDING` | The persisted send boundary was crossed. | Never automatically retry. Expired/unknown completion becomes `UNCERTAIN`. |
| Delivery | `SENT` | Transport acceptance was recorded. | Never replay; this prevents duplicate logical email. |
| Delivery | `FAILED` | Automatic retries were exhausted before acceptance. | Authorized replay resets it to `PENDING`. |
| Delivery | `UNCERTAIN` | SMTP acceptance cannot be proven absent. | Authorized replay is explicit and auditable; a provider may still have accepted the original send. |
| Parent event | `FAILED` | No retryable recipient deliveries remained. | The same replay transaction resets it to claimable `PENDING`, clears its lease, and records a safe replay code. |
| Parent event | `PROCESSING` | Another worker owns the event lease. | Never manually regress it. |
| Parent event | `PUBLISHED` | All recipient deliveries completed successfully. | Ineligible for replay and left unchanged. |

## Manual replay checklist

- [ ] Confirm the professor has the activity's current class context and update permission.
- [ ] Confirm the target delivery is `FAILED` or `UNCERTAIN`, not `SENT`, `SENDING`, or `PROCESSING`.
- [ ] Confirm the assignment remains visible; notification recovery never changes publication or assignment state.
- [ ] Request replay once. Repeated requests are idempotent while the delivery is already `PENDING`.
- [ ] Watch `events.claimed`, `delivery.success`, and backlog reduction after the next poll.

## Alert ownership and routing

Recommended rules and thresholds above are operational guidance, not deployed policy. The deployment owner must configure Actuator metric export, alert evaluation, recipients, paging/escalation, retention, and protected correlation from an alert to the internal delivery record. Do not place delivery IDs or email addresses into metric labels or application logs to make routing easier.

## Instruction review worker

| Metric | Alert threshold | First action |
|---|---|---|
| `training.activity.instruction-review.error` | `>= 3` in 5 minutes | Check model availability and job failure codes. |
| `training.activity.instruction-review.retry` | `>= 3` in 5 minutes | Inspect recurring model failures before retries are exhausted. |
| `training.activity.instruction-review.timeout` | `>= 1` in 5 minutes | Check model latency against the configured deadline. |
| `training.activity.instruction-review.saturation` | `> 0` in 5 minutes | Scale or investigate blocked worker/model execution. |
| `training.activity.instruction-review.worker.queue.depth` | Above 80% of capacity for 5 minutes | Investigate worker throughput; configured capacity is 8. |
| `training.activity.instruction-review.model.queue.depth` | Above 80% of capacity for 5 minutes | Investigate model throughput; configured capacity is 4. |
| `training.activity.instruction-review.model.latency` | Sustained latency above 80% of the configured deadline | Check model capacity and timeout configuration. |
