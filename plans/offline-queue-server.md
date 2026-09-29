# Plan 06a: Offline queue, server side (idempotency keys, conflict check, CSRF endpoint)

Planned against `origin/main` at `0292b67` (latest migration V19, so this adds **V20**).

## How plan 06 is split, and why

The draft is two features, so it becomes two plans and two commits:

- **06a, this plan: server-side safety.**
  - It is useful on its own the day it ships: a double-tapped Save, or a retry after a
    dropped response, can no longer create two shifts or two expenses.
  - It changes nothing for the current pages until they send the new header.
- **06b, `plans/offline-queue-client.md`: the outbox.**
  - The IndexedDB queue, the form changes, the drain, the pending strip and the conflict
    sheet.
  - It depends on 06a being deployed first.

### Where the draft is replaced by a simpler design

1. **Idempotency is stored on the row, not in a response-replay table.**
   - The draft proposed a `request_id` table holding status and body for 24 hours, and a
     servlet filter that replays the stored responses.
   - Every queued write here either **creates one row** or **updates one shift**. So a
     `create_request_id` column on `shift` and `expense`, and a `last_request_id` column on
     `shift`, give the same guarantee.
   - That means no filter, no stored response bodies, no purge job and no second table to
     delete with the account.
2. **Only three endpoints accept a key:**
   - `POST /shifts` (Add shift),
   - `POST /expenses` (Add expense),
   - `PATCH /shifts/{id}/status` (the finish sheet: actual times, odometer, miles, stops,
     and completing a block).

   The draft's `PATCH /shifts/{id}/actual` doesn't exist. The finish sheet is how times and
   miles are saved today. `PUT /shifts/{id}` (the full edit dialog) stays online-only.
3. **The conflict check is opt-in per request.** `expectedUpdatedAt` is a boxed, optional
   field. It is only sent by queued replays, which the 06b outbox adds. The online path
   sends nothing, so today's behaviour is unchanged and a stale open tab never gets a
   surprise 409.

## 1. Goal and scope

- An optional `Idempotency-Key` header, which must be a UUID, on the three endpoints.
  - A repeat create with the same key returns the **row that already exists**, with 200,
    instead of making a second one. That includes a row that has since been moved to the
    trash.
  - A repeat status change with the same key returns the shift as it is now, without
    applying the change again.
- An optional `expectedUpdatedAt` on the status change. If the shift has changed since that
  moment, the server answers **409** with the current shift, and nothing is written.
- `GET /csrf` returns `{headerName, token, accountId}`, so a page or worker can get a fresh
  token after the session has been renewed by the remember-me cookie.

### Out of scope

- Everything in the browser (06b).
- Keys on any other endpoint.
- Background Sync.
- Merging field by field on the server.

## 2. Data model and migration

**New file `V20__request_ids.sql`.** Don't edit any existing migration.

```sql
alter table shift add column create_request_id varchar(36);
alter table shift add column last_request_id varchar(36);
alter table shift add constraint uk_shift_owner_create_request unique (owner_id, create_request_id);

alter table expense add column create_request_id varchar(36);
alter table expense add constraint uk_expense_owner_create_request unique (owner_id, create_request_id);
```

Both Postgres and H2 treat NULLs as distinct in a unique constraint, so the many existing
rows without a key don't collide.

**Entities:**

- `model/Shift.java`:
  - `@Column(name = "create_request_id", length = 36, updatable = false) private String createRequestId;`
  - `@Column(name = "last_request_id", length = 36) private String lastRequestId;`
  - Add `@UniqueConstraint(name = "uk_shift_owner_create_request", columnNames = {"owner_id", "create_request_id"})`
    to the existing `@Table`, alongside its `indexes`. That keeps H2's schema, which is built
    from the entities, matching Postgres.
- `model/Expense.java`:
  - `@Column(name = "create_request_id", length = 36, updatable = false) private String createRequestId;`
  - The same kind of `@UniqueConstraint`.

The `Shift` constructors don't change. The new fields have setters only.

## 3. Backend

### Reading the header

Add a small helper, `controller/RequestIds.java`:

```java
static final String HEADER = "Idempotency-Key";
static String parse(String header)   // null/blank → null; a UUID (8-4-4-4-12 hex) → lower-cased; anything else → InvalidRequestIdException
```

- New `exception/InvalidRequestIdException.java` (extends `RuntimeException`).
- In `GlobalExceptionHandler`, map it to 400 with the body "Idempotency-Key must be a UUID."

### Repositories

**`ShiftRepository`:**

```java
@Query(value = """
        select s.* from shift s join app_users u on u.id = s.owner_id
        where lower(u.email) = lower(:email) and s.create_request_id = :requestId
        """, nativeQuery = true)
Optional<Shift> findByCreateRequestIdIncludingDeleted(@Param("email") String email, @Param("requestId") String requestId);
```

This follows the pattern of `findAllIncludingDeleted`. The native query bypasses
`@SQLRestriction`, so a replay for a shift that was trashed afterwards still finds it.

**`ExpenseRepository`:** the same query over `expense`, called
`findByCreateRequestIdIncludingDeleted`.

### Services

**`ShiftService`:**

- **Add `createShift(String email, CreateShiftRequest request, String requestId)`.** Keep
  the 2-argument method, delegating with `null`, so the 17 `ShiftServiceTest` calls and
  every other caller stay unchanged.
  - If `requestId != null`, look up `findByCreateRequestIdIncludingDeleted(email, requestId)`
    first. If found, return `toResponse(existing, …)`, the same as a fresh create would.
  - Otherwise set `shift.setCreateRequestId(requestId)` and `shift.setLastRequestId(requestId)`,
    then save.
- **Add `changeStatus(String email, Long id, ShiftStatusRequest request, String requestId)`.**
  Keep the 3-argument method, delegating with `null`. After loading the shift:
  1. If `requestId != null && requestId.equals(shift.getLastRequestId())`, return the current
     response. It is a replay of a change already applied.
  2. If `request.expectedUpdatedAt() != null` and
     `!shift.getUpdatedAt().truncatedTo(MICROS).equals(request.expectedUpdatedAt().truncatedTo(MICROS))`,
     throw `ShiftConflictException(toResponse(shift, …))`.

     Both sides are truncated to microseconds. Postgres stores microseconds, but a response
     built straight after a save can carry the JVM's nanoseconds, and without truncation the
     driver's own copy would conflict with itself.
  3. Apply as today, then `shift.setLastRequestId(requestId)` when it isn't null.

  The replay check comes **before** the conflict check. A queued finish whose response was
  lost must come back as a success, not as a 409 against its own write.

**`ExpenseService`:**

- **Add `create(String email, ExpenseRequest request, String requestId)`.** Keep the
  2-argument method, delegating with `null`.
- The same lookup first. Otherwise set `createRequestId` and save.

**A race between two copies of the same request** (two tabs draining at once, which 06b
prevents with a Web Lock, but the server shouldn't depend on that):

- Both miss the lookup, and the second insert hits the unique constraint.
- `ExpenseService.create` is `@Transactional`, so catching and re-reading inside it isn't
  possible.
- Instead, in `GlobalExceptionHandler`, handle `DataIntegrityViolationException` whose root
  cause message names `uk_shift_owner_create_request` or `uk_expense_owner_create_request`.
  Return **409** with `{"code":"DUPLICATE"}`.
- 06b treats `DUPLICATE` as delivered and reloads.
- No handler for `DataIntegrityViolationException` exists today, so every such error
  becomes Spring's default 500. The new handler must keep that for any other constraint:
  return 500 with a generic "The change could not be saved." body. Never return 409 for
  them, and never return the constraint text.

### DTOs and exceptions

- **`dto/ShiftStatusRequest.java`** (record): add a 6th component, `Instant expectedUpdatedAt`,
  documented as "Optional; when present the change is refused with 409 if the shift was
  changed after this moment."
  - Add a 5-component compatibility constructor with the current signature, passing `null`.
  - The existing 1- and 4-component constructors keep delegating. The 4-component one now
    goes through the 5-component one.
  - `ShiftServiceTest` builds it 17 times and all compile unchanged.
- **New `exception/ShiftConflictException.java`**, which carries a `ShiftResponse current`.
  In `GlobalExceptionHandler` it returns **409** with `{"code":"CONFLICT","current": <ShiftResponse>}`.
- **New `dto/ConflictResponse.java`**, a record `(String code, ShiftResponse current)`.
  `current` is null for `DUPLICATE`.

`ShiftResponse` is **not changed**. It already carries `updatedAt`, which 06b stores with each
queued finish.

### Controllers

- `ShiftController.createShift`: add
  `@RequestHeader(value = RequestIds.HEADER, required = false) String key`, and call
  `shiftService.createShift(email, request, RequestIds.parse(key))`.
- `ShiftController.changeStatus`: the same, calling the 4-argument `changeStatus`.
- `ExpenseController.create`: the same, calling the 3-argument `create`. It is written on
  one line today; split it over several lines.

### New `controller/CsrfController.java`

```java
@RestController
public class CsrfController {
    @GetMapping("/csrf")
    public Map<String, Object> csrf(CsrfToken token, Principal principal) { … }
}
```

- It returns `{"headerName": token.getHeaderName(), "token": token.getToken(), "accountId": <the signed-in user's id>}`.
  Look up the id through `AppUserRepository.findByEmailIgnoreCase(principal.getName())`.
- Send `Cache-Control: no-store` with a `ResponseEntity`.
- Security needs no change. It isn't in `permitAll`, so an expired session with a valid
  remember-me cookie is signed in again, and gets a fresh session and token. With neither,
  the request redirects to `/login`, and 06b reads that as "sign in to finish syncing".
- `sw.js` doesn't change: `/csrf` isn't in `DATA_PATHS`, so the worker never caches it.

`accountId` lets 06b refuse to replay a queued item under a **different** account that
signed in on the same device.

## 4. Frontend

**None in this plan.** The current pages send no key and no `expectedUpdatedAt`, so they
behave exactly as today.

The one exception is optional: set `Idempotency-Key: crypto.randomUUID()` on the existing
online Add expense and Add shift saves, so a double-tapped Save can't make two rows. It's a
two-line change in `app.js` (`saveExpense`, and `saveEditedShift` when creating). **Include
it**: it's the user-visible benefit of this commit. The key is made once per form submit,
before the fetch, not per click retry.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse` | **Not touched.** |
| Backup format (`BackupShift`, `BackupSettings`, `AccountBackupFile`, v4) | **Not touched.** Request ids are transport details, not history. A restored row gets null. |
| `ShiftResponse` | **Not touched.** Its `updatedAt` is used as is. |
| `BlockEvaluationResponse` | **Not touched.** |
| Backup, restore, deletion | Nothing new to delete. The columns live on rows that are already deleted with the account. |
| `sw.js` | No change. |
| Boxed request fields | `ShiftStatusRequest.expectedUpdatedAt` is an `Instant`, where null means no check. The header is optional. |
| Phone layout | No UI. |
| Dates | `expectedUpdatedAt` is an instant, compared at microsecond precision. No local dates. |
| Money and tax wording | Not applicable. |

**Existing code that changes:**

| File | Change |
|---|---|
| `Shift`, `Expense` | + columns and unique constraints |
| `ShiftRepository`, `ExpenseRepository` | + one native query each |
| `ShiftService` | + `createShift(…, requestId)` and `changeStatus(…, requestId)`; the old signatures delegate |
| `ExpenseService` | + `create(…, requestId)`; the old signature delegates |
| `ShiftStatusRequest` | + component and a compatibility constructor |
| `ShiftController`, `ExpenseController` | Header parameter, and they call the new signatures |
| `GlobalExceptionHandler` | + 3 handlers: invalid key, conflict, duplicate |
| `app.js` | The optional online key (above) |

**Existing tests that must change** (the controllers now call the longer service methods, so
2- and 3-argument stubs no longer match):

- `ExpenseControllerTest:61` and `:76`: `expenseService.create(eq(EMAIL), any(ExpenseRequest.class))`
  becomes `create(eq(EMAIL), any(ExpenseRequest.class), isNull())`.
- `ShiftControllerTest:438`, `:448` and `:454`: `changeStatus(eq(…), eq(5L), any(…))` gets a
  4th matcher, `isNull()`.
- No controller test stubs `createShift` at `0292b67`, so nothing else changes.

## 6. Tests to add

Expect about 16 new tests.

**`service/ShiftServiceTest.java`**

1. `aRepeatedCreateWithTheSameKeyReturnsTheFirstShiftAndSavesNothing`: the lookup returns a
   shift, the response has its id, and `save` is never called.
2. `aCreateWithAKeyStoresItAsCreateAndLastRequestId`
3. `aCreateWhoseShiftWasTrashedStillReturnsIt`: the lookup includes deleted rows, so the
   trashed shift comes back and nothing is inserted.
4. `aRepeatedStatusChangeWithTheLastRequestIdIsNotAppliedAgain`: the shift already has
   `lastRequestId` K. A request with K and different miles changes nothing, and `save` is
   never called.
5. `aStatusChangeWithAStaleExpectedUpdatedAtIsRefusedWithTheCurrentShift`: the exception
   carries the current response, and nothing is saved.
6. `expectedUpdatedAtIsComparedAtMicrosecondPrecision`: stored `…:00.123456Z`, sent
   `…:00.123456789Z`, and it applies.
7. `aReplayWinsOverTheConflictCheck`: `lastRequestId` is K and `expectedUpdatedAt` is stale.
   A request with K returns the current shift and doesn't throw.
8. `withoutAKeyOrExpectedUpdatedAtNothingChanges`: the existing path. It saves, and
   `lastRequestId` stays null.

**`service/ExpenseServiceTest.java`**

9. `aRepeatedCreateWithTheSameKeyReturnsTheFirstExpense`
10. `aCreateWithAKeyStoresIt`

**`controller/ShiftControllerTest.java`**

11. `anIdempotencyKeyThatIsNotAUuidIsRejected`: `POST /shifts` with the header `abc` returns
    400, and the service is not called.
12. `theKeyIsPassedLowerCased`: the header is an upper-case UUID, and the service gets it in
    lower case.
13. `aConflictIs409WithTheCurrentShift`: the service throws `ShiftConflictException`. The
    response is 409, with `$.code` equal to `CONFLICT`, `$.current.id` equal to 5 and
    `$.current.updatedAt` present.
14. `aDuplicateInsertIs409Duplicate`: the service throws `DataIntegrityViolationException`
    naming `uk_shift_owner_create_request`. The response is 409 with `$.code` equal to
    `DUPLICATE`.

**New `controller/CsrfControllerTest.java`** (`@WebMvcTest`, `@Import(SecurityConfig.class)`)

15. `returnsAFreshTokenAndTheAccountId`: signed in, the response has the header name
    `X-CSRF-TOKEN`, a non-blank token, `accountId` equal to 42, and `Cache-Control: no-store`.
16. `anonymousIsSentToLogin`

**`repository/ShiftRepositoryTest.java`** (add one)

17. `findByCreateRequestIdIncludingDeletedFindsTrashedRowsAndOnlyTheOwners`

`PostgresMigrationTest` (it only runs with `FLEXBUDDY_TEST_POSTGRES_URL` set) validates V20
against the entities.

## 7. Manual checks

There's no UI, apart from the optional online key.

1. **Duplicate create.**
   - Add an expense from DevTools with a fixed header:
     `fetch('/expenses', {method: 'POST', headers: {'Content-Type': 'application/json', 'Idempotency-Key': '11111111-1111-4111-8111-111111111111', [csrfHeader]: csrfToken}, body: JSON.stringify({date: '2026-09-29', category: 'FUEL', amount: 12.34})})`.
   - Run it twice. Both return 200 with the same `id`, and the Expenses list shows **one**
     $12.34 fuel row.
2. **A bad key.** The same call with `Idempotency-Key: nope` returns 400 "Idempotency-Key
   must be a UUID."
3. **Conflict.**
   - Note a completed shift's `updatedAt` from `GET /shifts`.
   - Edit its tips in the dialog.
   - Send `PATCH /shifts/{id}/status` with `{"status":"COMPLETED","miles":10,"expectedUpdatedAt":"<the old value>"}`.
     It returns 409 with `code` equal to `CONFLICT` and the current shift, and the miles are
     unchanged.
   - Send it again with the new `updatedAt`. It returns 200.
4. **Replay.** Send that successful PATCH again with the same `Idempotency-Key`. It returns
   200, and `updatedAt` doesn't move.
5. **CSRF endpoint.**
   - `GET /csrf` returns JSON with a token.
   - In a private window with only a remember-me cookie (after the session times out),
     `GET /csrf` still returns a token.
   - Signed out, it redirects to `/login`.
6. **Double-tap** (with the optional online key). On a throttled "Slow 3G" profile,
   double-tap Add expense. Only one row is created.
7. **Everything else unchanged.** Add shift, Add expense, the finish sheet, edit and delete
   all behave as before.

## 8. Suggested commit message

```
feat(sync): make adding shifts and expenses safe to retry

Adding a shift, adding an expense, and saving a block from the finish
sheet now accept an idempotency key. A repeat with the same key returns
what the first request made instead of making it again, even if the row
was deleted since, so a double-tapped Save or a retry after a lost
response can no longer create a second row. The key is kept on the row
itself, so there is nothing new to store or purge.

A finish-sheet save can also say which version of the block it was made
against. If the block was changed since, the server refuses with the
current block rather than overwriting it, which the offline queue will
use to ask the driver which version to keep. Nothing sends this yet, so
saving online works as before.

A small endpoint returns a fresh security token for the signed-in
account, for requests sent after the session was renewed.
```

## 9. Open questions

1. **Adding the online key in this commit.** This plan recommends it, since it's the only
   visible benefit of 06a on its own. Or should 06a ship with no frontend change at all?
2. **Other endpoints.** Should the full edit dialog (`PUT /shifts/{id}`) also accept a key,
   for double-tap safety online, even though 06b won't queue it?
