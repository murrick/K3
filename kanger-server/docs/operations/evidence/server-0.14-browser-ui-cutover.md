# Server 0.14 browser UI cutover evidence

Recorded during VPS acceptance on 2026-08-04.

## Production UI boundary

```text
public symlink: /var/www/html/kanger
previous target: /home/murray/sites/kanger
current target:  /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

The previous production target was preserved unchanged as the rollback boundary.

## Exact Server 0.14 browser artifact

The deployed UI snapshot was extracted from immutable shelf:

```text
develop/server/0.14
e5f9a1bfa47437636705f0935cb659cffb4d179e
```

The staged package contained exactly:

```text
codemirror.css
codemirror.js
config.js
console.html
favicon.ico
index.html
javascript.js
jquery-3.6.0.min.js
```

All package manifest checks passed before publication.

## Guarded cutover result

The operator reported:

```text
KANGER Server 0.14 browser UI cutover complete
UI_DEPLOYMENT_GATE=PASS
PREVIOUS_UI_TARGET=/home/murray/sites/kanger
CURRENT_UI_TARGET=/home/murray/sites/kanger-server-0.14-20260804T181706Z
INDEX_SHA256=747104f9e9a7599679b5329eb098590b190a876ff949f01e232f67a1e1b8baae
CONFIG_SHA256=d44e67c862f12065d62259055043922ff573271dd5fac2f373cc454172757715
CONSOLE_SHA256=24d74b6608d4d49a5464d62dd368354c3f30d5f90b90b6a8717531d8e7719dca
ORIGIN_UI=PASS
PUBLIC_UI=PASS
```

## Verification semantics

- nginx configuration remained valid;
- the symlink switch was atomic;
- the origin was checked against `127.0.0.1` for content identity;
- public HTTPS was checked separately through the normal trust chain;
- the exact Server 0.14 `index.html`, `config.js`, and `console.html` hashes match the immutable shelf;
- the previous UI target remains available for explicit rollback.

## Result

```text
BROWSER UI CUTOVER: PASS
ORIGIN UI: PASS
PUBLIC UI: PASS
SERVER/UI RELEASE IDENTITY: ALIGNED AT 0.14
UI ROLLBACK BOUNDARY: PRESERVED
```

This closes the browser-artifact deployment gate. Real account lifecycle and SMTP transport acceptance remain open under PR #63.
