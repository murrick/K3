# Authentication migration contract

This document defines the compatibility contract for the authentication
stabilization slice.

## Legacy records

Historical `users.conf` entries use this shape:

```text
<legacy-java-hash-token>=<user-id>
```

They remain readable during migration. A successful login with the original
login and password replaces the matching legacy record with a versioned PBKDF2
record. Failed logins never modify the credential file.

## Versioned records

New records use a tab-separated, versioned format:

```text
v2<TAB>base64url(login)<TAB>user-id<TAB>iterations<TAB>base64url(salt)<TAB>base64url(hash)
```

Passwords are not stored. The derived value uses PBKDF2-HMAC-SHA256 with a
per-user random salt and a constant-time comparison.

## Sessions

Application session tokens are independent random 256-bit values encoded with
unpadded base64url. They are unrelated to password records and confirmation
tokens.

## Confirmation

E-mail confirmation tokens are independent, single-purpose random values. A
credential record or session token must never be embedded in a confirmation
link.
