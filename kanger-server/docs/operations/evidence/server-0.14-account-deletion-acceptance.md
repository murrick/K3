# KANGER Server 0.14 — production account deletion acceptance

Date: 2026-08-04
Target: production VPS `94.103.94.41`
Server: `server-0.14`
Operator plane: loopback-only `kanger-admin`

## First deletion and idempotency

The first production acceptance account was deleted through the operator CLI:

```text
sudo kanger-admin delete-user --login <acceptance-login>
Type DELETE to quarantine this account: DELETE
Deletion 2hruVaHKEB6NmBwmt4NisTB9y1jmrVNK_9bb6heeSes reached COMPLETE
```

The command was invoked a second time for the same login and returned the same deletion identifier and `COMPLETE` state.

No password, e-mail address, session token, admin token, or account file content is recorded in this evidence.

## First deletion filesystem result

The canonical account home disappeared from its published location and the corresponding directory appeared below the private `.quarantine` root.

Server 0.14 deletion is intentionally quarantine-based: the canonical account home is moved to the private `.quarantine` area before the persistent forward-only journal advances to `COMPLETE`. This acceptance does not claim that quarantined bytes were purged from storage.

## Re-registration clarification

A later browser registration created a distinct ACTIVE identity because browser autocomplete supplied a different exact login string. Production inspection showed:

```text
old deleted identity: user_id=4, login=<original-login>
new active identity:  user_id=5, login=<different-login>
users.sequence:       6
new credential:       present
new canonical home:   present
new pending record:   absent
```

Authentication attempts made with the original deleted login reached `CredentialStore.authenticate` at the no-matching-login branch. The operator command using the original deleted login therefore correctly returned the old COMPLETE deletion record. This observation is not an identity-reuse or orphan-workspace defect.

The new identity authenticated successfully when its exact login string was used and the browser console opened normally.

## Active-session deletion test

The new ACTIVE identity (`user_id=5`) was then deleted while its authenticated browser console remained open.

Observed production results:

1. the canonical account directory for `user_id=5` disappeared from the published account root;
2. the account directory appeared below `.quarantine` with the expected deletion suffix;
3. the next console command using the previously valid session token was rejected with `Authentication error`;
4. a fresh browser sign-in using the deleted identity was rejected with `Authentication error`.

The rejected session token is not recorded in this evidence.

## Final acceptance result

```text
operator confirmation boundary:       PASS
credential removal:                   PASS
canonical account home withdrawal:    PASS
quarantine transition:                PASS
deletion state progression:           COMPLETE
repeat delete idempotency:             PASS
same deletion identity on repeat:      PASS
monotonic new user-id allocation:      PASS
exact-login authentication:            PASS
active session revocation:             PASS
fresh sign-in rejection:               PASS
quarantine/purge distinction:          preserved
```

## Interpretation

The production account deletion lifecycle is closed.

The forward-only state machine removed public authentication authority, withdrew the canonical workspace, retained the private quarantine evidence, revoked an already-open browser session, and rejected subsequent authentication. Repeated deletion of the already deleted original identity remained idempotent and returned the retained COMPLETE audit record.

## Diagnostic correction

A direct `grep` for the login in `account-deletions.conf` produced no output because deletion journal payloads are Base64URL-encoded. That empty result is not evidence that the deletion record is absent; journal inspection must decode the versioned payload.
