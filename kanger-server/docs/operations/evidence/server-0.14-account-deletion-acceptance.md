# KANGER Server 0.14 — production account deletion acceptance

Date: 2026-08-04
Target: production VPS `94.103.94.41`
Server: `server-0.14`
Operator plane: loopback-only `kanger-admin`

## Operation

The production acceptance account was deleted through the operator CLI:

```text
sudo kanger-admin delete-user --login <acceptance-login>
Type DELETE to quarantine this account: DELETE
Deletion 2hruVaHKEB6NmBwmt4NisTB9y1jmrVNK_9bb6heeSes reached COMPLETE
```

The command was invoked a second time for the same login and returned the same deletion identifier and `COMPLETE` state.

No password, e-mail address, session token, admin token, or account file content is recorded in this evidence.

## Observed filesystem result

The canonical account home disappeared from its published location.

Server 0.14 deletion is intentionally quarantine-based: the canonical account home is moved to the private `.quarantine` area before the persistent forward-only journal advances to `COMPLETE`. This acceptance does not claim that quarantined bytes were purged from storage.

## Acceptance result

```text
operator confirmation boundary:       PASS
credential removal:                   PASS
canonical account home withdrawal:    PASS
deletion state progression:           COMPLETE
repeat delete idempotency:             PASS
same deletion identity on repeat:      PASS
quarantine/purge distinction:          preserved
```

## Interpretation

The repeated result is expected. COMPLETE deletion records are retained as audit identity. A later delete-by-login lookup finds that completed journal record after the credential and canonical workspace are already absent, and returns it rather than creating a new deletion operation.

## Re-registration clarification

A later registration created a distinct ACTIVE identity with a different login string and a monotonically allocated new user id. Production inspection showed:

```text
old deleted identity: user_id=4, login=<original-login>
new active identity:  user_id=5, login=<different-login>
users.sequence:       6
new credential:       present
new canonical home:   present
new pending record:   absent
```

Authentication attempts made with the original deleted login reached `CredentialStore.authenticate` at the no-matching-login branch. The operator command using the original deleted login therefore correctly returned the old COMPLETE deletion record. This observation is not an identity-reuse or orphan-workspace defect.

The distinct new ACTIVE identity must be authenticated and, if required, deleted using its exact login string.

## Remaining production checks

The account deletion lifecycle is not fully closed until both session authorities are observed from the browser boundary:

1. an already-open authenticated console session is rejected after deletion;
2. a fresh ordinary sign-in with the deleted credentials is rejected.

The first condition was not observed because the user logged out before deletion. The second condition passed: fresh sign-in with the deleted login was rejected.
