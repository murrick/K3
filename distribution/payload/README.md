# KANGER 3.7.0 — Installation, Update and Administration Manual

This is the customer-facing host/operator manual shipped with the KANGER 3.7.0 distribution.

It covers first installation, update, system integration, persistent configuration, account-registration topology, user provisioning, persistent user workspaces, health checks, backup boundaries, and operational troubleshooting.

The distribution scripts are authoritative for installation paths and activation behavior. The running Server is authoritative for runtime state.

## Documentation map

The installed distribution contains three customer manuals:

```text
README.md                 Installation, Update and Administration Manual
docs/COMMANDS.md          Command Processor Manual
docs/UI_CONSOLE.md        Browser UI and Server Console Manual
```

Use this document for host/product lifecycle. Use `docs/COMMANDS.md` for exact Console grammar and command semantics. Use `docs/UI_CONSOLE.md` for Browser workspace navigation and the TECH panel.

These documents are version-local product material. Always consult the manuals shipped with the active release under `/opt/kanger/current`; do not assume a manual from another KANGER version describes the installed build.

---

# 1. Distribution artifact

The release archive is:

```text
kanger-3.7.0.tar.gz
```

and expands into:

```text
kanger-3.7.0/
```

The bundle includes at least:

```text
RELEASE
SHA256SUMS
README.md
docs/COMMANDS.md
docs/UI_CONSOLE.md
install.sh
update.sh
bin/kanger-admin
server/kanger-server-thin.jar
server/lib/
server/modules/
server/kanger.conf.example
ui/
nginx/kanger.conf.template
systemd/kanger.service.template
lib/common.sh
```

`RELEASE` records product/distribution/Server build metadata. `SHA256SUMS` covers files in the extracted bundle. Both installer and updater verify the bundle before activation.

Do not delete or alter bundle members before running `install.sh` or `update.sh`.

---

# 2. Prerequisites and ownership boundary

Installation and update run as root, normally through `sudo`.

The customer provides and maintains:

- Java 8 or newer;
- nginx;
- systemd;
- DNS;
- TLS certificates/private keys;
- operating-system updates and package repositories;
- firewall/network policy;
- backup infrastructure.

The installer checks the required host utilities and refuses to proceed when required runtime components are unavailable.

Useful preflight commands:

```bash
java -version
nginx -v
systemctl --version
```

KANGER does not install or upgrade Java/nginx/systemd and does not obtain or renew TLS certificates.

---

# 3. Public names, TLS and network topology

A normal installation uses two HTTPS names:

```text
UI:  kanger.example.com
API: api.kanger.example.com
```

The standard first install is:

```bash
sudo ./install.sh kanger.example.com
```

which defaults the API name to `api.kanger.example.com`.

A custom API name may be supplied explicitly:

```bash
sudo ./install.sh kanger.example.com \
  --api-domain api.example.net
```

Both public names must resolve to the nginx host before normal Browser use.

## 3.1 TLS

Default certificate locations are:

```text
/etc/ssl/kanger/fullchain.pem
/etc/ssl/kanger/privkey.pem
```

When one pair is used for UI and API, it must be valid for both names.

Separate material may be supplied:

```bash
sudo ./install.sh kanger.example.com \
  --ui-cert /etc/ssl/kanger/ui-fullchain.pem \
  --ui-key /etc/ssl/kanger/ui-privkey.pem \
  --api-cert /etc/ssl/kanger/api-fullchain.pem \
  --api-key /etc/ssl/kanger/api-privkey.pem
```

All certificate/key paths must be absolute. Renewal at the same paths is customer-managed; reload nginx after renewal according to the customer's certificate procedure.

## 3.2 Network boundaries

The generated nginx configuration normally:

- listens publicly on TCP 80/443;
- redirects HTTP to HTTPS;
- serves Browser UI from `/opt/kanger/current/ui`;
- proxies the public API to `127.0.0.1:1964`;
- keeps `/ready` restricted to loopback through nginx policy;
- never proxies the local administrative listener.

Default Server listeners:

```text
application/API: 127.0.0.1:1964
operator/admin:  127.0.0.1:1965
```

Port 1965 is a local host-operator security boundary. Do not expose it through nginx, a public firewall rule, a tunnel intended for customers, or another reverse proxy.

The scripts validate nginx with:

```bash
nginx -t
```

before activation/reload.

---

# 4. First installation

## 4.1 Prepare

Before installation verify:

1. Java 8+ is selected by `java`.
2. nginx/systemd are operational.
3. UI/API DNS names are ready.
4. TLS material exists and covers the configured names.
5. nginx includes the selected generated configuration destination.
6. external TCP 80/443 policy is intentional.
7. ports 1964/1965 remain local application/operator boundaries.

## 4.2 Extract and install

```bash
tar -xzf kanger-3.7.0.tar.gz
cd kanger-3.7.0
sudo ./install.sh kanger.example.com
```

For complete installer options:

```bash
./install.sh --help
```

`install.sh` is a first-install operation. It refuses to overwrite an existing KANGER installation identity/current release where `update.sh` is the appropriate path.

## 4.3 Installation sequence

A successful install:

1. verifies bundle layout/checksums;
2. verifies prerequisites;
3. validates domains/TLS/paths;
4. creates the `kanger` system group/user when absent;
5. creates persistent product directories;
6. installs persistent Server configuration when absent;
7. stages the versioned release under `/opt/kanger/releases`;
8. installs the systemd unit;
9. atomically selects `/opt/kanger/current`;
10. renders/validates nginx configuration;
11. enables/restarts `kanger.service`;
12. checks loopback `/health` and `/ready` against the packaged Server version;
13. reloads nginx;
14. writes installation identity used by later updates.

Activation failure cleans up first-install product/integration material created by that attempt while preserving a pre-existing persistent Server configuration.

---

# 5. Installed filesystem layout

Default layout:

| Purpose | Path |
| --- | --- |
| Product root | `/opt/kanger` |
| Versioned releases | `/opt/kanger/releases` |
| Active release | `/opt/kanger/current` |
| Customer manuals | `/opt/kanger/current/README.md`, `/opt/kanger/current/docs/` |
| Persistent configuration root | `/etc/kanger` |
| Main Server configuration | `/etc/kanger/kanger.conf` |
| Generated installation identity | `/etc/kanger/instance.conf` |
| Persistent runtime/user state | `/var/lib/kanger` |
| State-side main-config symlink | `/var/lib/kanger/kanger.conf` |
| Default user data root | `/var/lib/kanger/KANGER` |
| systemd unit | `/etc/systemd/system/kanger.service` |
| nginx config | `/etc/nginx/conf.d/kanger.conf` |
| Server thin JAR | `/opt/kanger/current/server/kanger-server-thin.jar` |
| Runtime libraries | `/opt/kanger/current/server/lib` |
| Runtime provider modules | `/opt/kanger/current/server/modules` |
| Browser UI | `/opt/kanger/current/ui` |
| Operator CLI | `/opt/kanger/current/bin/kanger-admin` |

System account:

```text
user:  kanger
group: kanger
home:  /var/lib/kanger
shell: /usr/sbin/nologin
```

Key ownership/permissions normally include:

```text
/opt/kanger                 root:root     0755
/opt/kanger/releases        root:root     0755
/etc/kanger                 root:kanger   0750
/etc/kanger/kanger.conf     root:kanger   0640
/etc/kanger/instance.conf   root:root     0600
/var/lib/kanger             kanger:kanger 0750
```

Release files are immutable product material. Customer configuration and runtime/user state remain outside the release directory.

---

# 6. systemd service and health

The installed service is:

```text
kanger.service
```

It runs as `kanger` with:

```text
WorkingDirectory=/opt/kanger/current
-Duser.home=/var/lib/kanger
```

The Server runtime classpath uses the thin Server JAR plus `server/lib/*` and `server/modules/*`.

Common checks:

```bash
sudo systemctl status kanger.service
sudo journalctl -u kanger.service
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

The activation scripts require the running Server version to match the version packaged in the candidate release.

---

# 7. Configuration model

KANGER deliberately separates **main Server configuration**, **generated installation identity**, and **per-user configuration**. They have different owners and update semantics.

## 7.1 Main Server configuration

Canonical installed file:

```text
/etc/kanger/kanger.conf
```

State-side lookup link:

```text
/var/lib/kanger/kanger.conf -> /etc/kanger/kanger.conf
```

The service uses `/var/lib/kanger` as `user.home`; the Server therefore resolves this linked main configuration at startup.

`update.sh` preserves `/etc/kanger/kanger.conf`; a release update does not overwrite customer Server settings with the new template.

After changing Server settings, restart:

```bash
sudo systemctl restart kanger.service
```

### Main configuration groups

The shipped `server/kanger.conf.example` is the exact-version key reference. The standard 3.7.0 template contains these groups:

**Application listener / request execution**

```properties
server.bind.address=127.0.0.1
server.port=1964
server.backlog=128
server.maxthreads=32
server.queue.capacity=128
server.request.max.body.bytes=1048576
server.watchdog.period=1000
```

**Local operator plane**

```properties
server.admin.enabled=true
server.admin.bind.address=127.0.0.1
server.admin.port=1965
server.admin.request.max.body.bytes=65536
server.admin.token.file=KANGER/admin.token
```

`server.admin.token.file` is relative to the Server home unless made absolute by a future supported configuration contract. With the standard installation the token is private runtime state under `/var/lib/kanger`.

**Public Browser/API topology**

The installer adds installation-specific values such as:

```properties
server.cors.allowed.origin.1=https://<UI-domain>
server.url=https://<API-domain>
server.confirmation.redirect.url=https://<UI-domain>/
```

Additional exact allowed origins may be configured with numbered `server.cors.allowed.origin.N` entries. `server.cors.allow.credentials` is available in the template and should only be changed with an explicit CORS design requirement.

**Registration policy / mail transport**

```properties
server.email.mode=disabled
server.email.debug=false
server.email.auth=true
server.email.connection.timeout.millis=10000
server.email.read.timeout.millis=10000
server.email.write.timeout.millis=10000
server.email.workers=1
server.email.queue.capacity=64
```

For `starttls` or `smtps`, configure the corresponding mail endpoint/sender/credentials from the exact shipped template:

```properties
server.email.host=...
server.email.port=...
server.email.from=...
server.email.login=...
server.email.password=...
```

**Pending-registration lifecycle**

```properties
server.registration.pending.ttl.hours=168
server.registration.confirmation.ttl.hours=24
server.registration.action.ttl.minutes=15
server.registration.resend.cooldown.seconds=60
server.registration.pending.max.records=10000
```

Do not copy unknown settings from historical development `K3.conf` files into production `kanger.conf`. The shipped Server template for the active release is the supported main-config reference.

## 7.2 Generated installation identity

File:

```text
/etc/kanger/instance.conf
```

This is generated root-owned installation state, not an ordinary application settings file. It records the installation topology needed by `update.sh`, including UI/API domains, TLS material paths, and nginx destination.

Treat it as sensitive operational state and do not casually paste it into public support tickets.

Ordinary version updates reuse this identity. Changing domains, certificate paths, or nginx destination is a controlled reconfiguration, not an ordinary `update.sh` version transition.

## 7.3 Per-user configuration

Each KANGER application user has a numeric workspace under the Server user-data root:

```text
/var/lib/kanger/KANGER/<user-id>/
```

Its per-user properties file is:

```text
/var/lib/kanger/KANGER/<user-id>/kanger.conf
```

The file belongs to the application-user runtime context; it is not the same file as `/etc/kanger/kanger.conf`.

The Server establishes/loads the user workspace and standard path properties:

```properties
user.home=/var/lib/kanger
user.dir=/var/lib/kanger/KANGER/<user-id>/
sources.dir=/var/lib/kanger/KANGER/<user-id>/SRC/
database.dir=/var/lib/kanger/KANGER/<user-id>/DB/
```

When `sources.dir` or `database.dir` is absent, the Server creates the default `SRC/` and `DB/` directories and persists the resulting user properties through the user configuration lifecycle.

A user context may also persist supported user-level runtime properties such as:

```properties
user.history.size=<non-negative history limit>
```

Do not treat the per-user file as an alternate Server configuration. `server.*` listener/mail/admin settings belong in `/etc/kanger/kanger.conf`, not in a user's `kanger.conf`.

Do not add historical/development properties merely because they appear in repository samples. For support and automation, only rely on user-level keys exercised by the exact installed runtime or documented by the exact-version product manual.

### Per-user directories

By default:

```text
<user.dir>/SRC/    server-side source workspace
<user.dir>/DB/     persistent logical storage workspace
```

The Browser TECH `Session` section exposes the effective `Home`, `Database`, and `Sources` paths so an operator/developer can verify the active user context without guessing from filesystem layout.

---

# 8. Registration topology: TRUSTED vs EMAIL_VERIFIED

KANGER resolves account-registration policy from `server.email.mode` once at the configuration boundary.

## 8.1 TRUSTED

Default:

```properties
server.email.mode=disabled
```

resolves to:

```text
RegistrationPolicy.TRUSTED
```

Meaning:

- public self-registration is disabled;
- complete ACTIVE accounts are provisioned through the local operator plane;
- e-mail confirmation is not required because public registration is unavailable;
- users still authenticate normally with KANGER credentials;
- TRUSTED is an **account-provisioning policy**, not TLS trust and not an authentication bypass.

The Browser receives public capability data equivalent to:

```text
registration_policy=TRUSTED
public_registration=false
email_confirmation_required=false
```

This is the intended topology for a controlled/private installation where the host operator decides which users exist.

## 8.2 EMAIL_VERIFIED public self-registration

These modes select `RegistrationPolicy.EMAIL_VERIFIED`:

```properties
server.email.mode=starttls
```

or:

```properties
server.email.mode=smtps
```

Historical `ssl` is accepted as an SMTPS-compatible migration alias; `smtps` is canonical.

Meaning:

- public self-registration is available;
- registration creates transient pending registration state;
- successful e-mail confirmation is required before an ACTIVE account exists;
- confirmation does not itself create an authenticated Browser session;
- pending-registration actions are enabled.

This mode requires a complete working SMTP configuration and appropriate public Server/redirect URLs.

After changing registration/mail topology, restart the Server.

---

# 9. Local operator administration — `kanger-admin`

The active launcher is:

```text
/opt/kanger/current/bin/kanger-admin
```

Use as root:

```bash
sudo /opt/kanger/current/bin/kanger-admin --help
```

`kanger-admin` is a local host-operator client. It is not a public web admin console and does not make ordinary KANGER users administrators.

It calls the running Server's loopback admin listener with an owner-only bearer token. It does **not** directly edit credential files, pending registrations, account homes, or deletion journals.

## 9.1 Create user

Normal TRUSTED provisioning:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user
```

The interactive form requests required login, optional profile fields/privacy consent, and a hidden password.

Explicit example:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user \
  --login alice \
  --email alice@example.org \
  --name 'Alice Example' \
  --country Netherlands \
  --city Amsterdam \
  --privacy-consent true
```

Supported explicit fields include:

```text
--login VALUE
--email VALUE
--name VALUE
--country VALUE
--city VALUE
--privacy-consent true|false
--password-stdin
```

Plaintext `--password` is deliberately forbidden. Automation must explicitly opt into `--password-stdin` and should obtain the value from the customer's secret-management path rather than command history/logs.

An operator-created account is ACTIVE immediately. Supplying an optional e-mail address through the operator plane does not assert that the address was e-mail-verified.

## 9.2 Delete user

By login:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --login alice
```

or numeric id:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --user-id 42
```

Interactive deletion requires typing `DELETE`. Non-interactive deletion requires explicit `--yes`.

Deletion closes runtime/session state, removes authentication/pending state, quarantines the user home, and records deletion progress. Once credentials are removed, recovery is forward-only; deletion is not an authentication “disable/enable” toggle.

## 9.3 Exit codes

```text
0  completed
2  invalid/incomplete operator input
3  admin listener/token/connection failure
4  account lifecycle conflict/rejection
5  deletion incomplete and recovery required
```

Never expose port 1965 as a shortcut for remote administration.

---

# 10. First-user and quick-start roadmap

After a successful first installation:

1. Verify service and loopback health:

   ```bash
   sudo systemctl status kanger.service
   curl --fail http://127.0.0.1:1964/health
   curl --fail http://127.0.0.1:1964/ready
   sudo nginx -t
   ```

2. In the default TRUSTED topology, create the first user:

   ```bash
   sudo /opt/kanger/current/bin/kanger-admin create-user
   ```

3. Log into the Browser UI at the configured UI HTTPS name.

4. Use `help` for current command syntax.

5. Read:

   ```text
   docs/COMMANDS.md
   docs/UI_CONSOLE.md
   ```

6. For a cheap initial runtime check, use `status` or open TECH.

7. Create/open a logical storage only when the application workflow requires persistence; storage and user transaction lifecycle are separate concepts.

---

# 11. Updating KANGER

Use `update.sh` from the **new extracted release**:

```bash
tar -xzf kanger-<new-version>.tar.gz
cd kanger-<new-version>
sudo ./update.sh
```

Ordinary update flow:

1. verifies new bundle/prerequisites;
2. verifies an existing installation;
3. loads `/etc/kanger/instance.conf`;
4. revalidates recorded TLS material;
5. determines active release/version;
6. stages candidate under `/opt/kanger/releases/<version>`;
7. refreshes generated systemd/nginx files;
8. atomically repoints `/opt/kanger/current`;
9. restarts KANGER;
10. verifies `/health` and `/ready` against candidate Server version;
11. validates/reloads nginx.

Persistent `/etc/kanger` and `/var/lib/kanger` are not replaced by release payload.

## 11.1 Activation failure

The updater preserves the previous release/integration state needed for automatic activation rollback. If candidate activation fails, it restores the previous current release and generated integration files and restarts the previous service.

Automatic activation rollback is **not** a backup of customer data.

## 11.2 Same-version replacement

By default, updating to an already-active product version is rejected.

Explicit replacement requires:

```bash
sudo ./update.sh --force
```

The updater preserves the previous canonical release under a snapshot name such as:

```text
/opt/kanger/releases/3.7.0.force.1
```

and atomically rotates current/canonical release paths so a partially rebuilt canonical release is never selected as active.

Use `--force` for an intentional same-version build replacement, not as the normal update path.

## 11.3 Manual rollback/downgrade

KANGER 3.7.0 does not ship a separate rollback command. Automatic rollback belongs to failed `update.sh` activation. A deliberate downgrade should be handled as a controlled administrative operation with customer data/backups understood first.

---

# 12. Backup and persistence boundary

Before planned upgrades or administrative maintenance, include at least:

```text
/etc/kanger
/var/lib/kanger
```

in the customer's backup policy.

These contain persistent installation/configuration and application/user state.

Versioned product material lives under:

```text
/opt/kanger/releases
```

and active product selection is:

```text
/opt/kanger/current -> /opt/kanger/releases/<version>
```

Do not use a copy of `/opt/kanger/current` as the only backup of customer data; persistent user/storage state is outside that tree.

Do not remove `/var/lib/kanger` during ordinary release maintenance.

---

# 13. Operational troubleshooting

## Service and logs

```bash
sudo systemctl status kanger.service
sudo journalctl -u kanger.service
```

## Loopback health/readiness

```bash
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

## nginx

```bash
sudo nginx -t
```

## Active release

```bash
readlink -f /opt/kanger/current
cat /opt/kanger/current/RELEASE
```

## Installation identity

```bash
sudo cat /etc/kanger/instance.conf
```

Do not publish root-only installation state casually.

## Admin plane failure

For `kanger-admin` exit code `3`, verify:

- `kanger.service` is running;
- `server.admin.enabled=true`;
- admin listener is loopback-only at the configured port;
- the Server created its admin token under its state home;
- the command is run through `sudo` from the active release.

## Application/command diagnosis

For logical/Console problems:

1. inspect the command result;
2. use canonical `status` / relevant focused status section;
3. open Browser TECH for formatted Browser + Server telemetry;
4. consult `docs/COMMANDS.md` for field/command semantics;
5. consult `docs/UI_CONSOLE.md` for Browser operation/snapshot interpretation;
6. only then escalate to host journal/storage investigation when evidence points there.

`UNQUALIFIED`, `unavailable`, or a closed storage by themselves are not sufficient evidence of corruption.

---

# 14. Update preservation contract

An ordinary update preserves:

```text
/etc/kanger
/var/lib/kanger
```

It replaces/selects versioned product material under:

```text
/opt/kanger/releases/<version>
/opt/kanger/current
```

and refreshes generated system integration from the new product release:

```text
/etc/systemd/system/kanger.service
<configured nginx destination>
```

The stored installation identity controls domains/TLS/nginx destination reuse.

This separation is the central distribution invariant: **versioned product code and manuals move together; persistent customer configuration and data do not live inside the release directory.**

---

# 15. No automatic uninstall

KANGER 3.7.0 ships `install.sh` and `update.sh`, not an automatic uninstall command.

Permanent decommissioning must be an explicit administrative procedure performed only after preserving any required `/etc/kanger` and `/var/lib/kanger` data.

---

# 16. Final first-install checklist

Before install:

- Java 8+ available;
- nginx/systemd healthy;
- UI/API DNS known;
- TLS files exist and cover required names;
- nginx destination is included;
- TCP 80/443 exposure intentional;
- 1964/1965 kept host-local as designed;
- release archive/checksum verified;
- root/sudo available.

After install:

```bash
sudo systemctl status kanger.service
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
sudo nginx -t
sudo /opt/kanger/current/bin/kanger-admin --help
```

Then provision/login according to the selected registration policy and continue with the exact-version command/UI manuals shipped beside this file.