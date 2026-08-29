# KANGER 3.7.0 — Server Installation and Administration Guide

This document is the customer-facing installation map for the KANGER 3.7.0 distribution tarball.

It describes the installation and update contract shipped with the distribution itself: prerequisites, DNS and TLS preparation, filesystem layout, systemd and nginx integration, persistent configuration, the local `kanger-admin` operator tool, account creation and deletion, and the TRUSTED registration mode.

The distribution scripts are the authority for paths and runtime behavior. KANGER does not install or upgrade Java, nginx, systemd, DNS, or TLS certificates for the customer.

---

## 1. Distribution artifact

The release archive is named:

```text
kanger-3.7.0.tar.gz
```

It expands into:

```text
kanger-3.7.0/
```

The bundle contains, among other files:

```text
RELEASE
SHA256SUMS
install.sh
update.sh
bin/kanger-admin
server/kanger-server.jar
server/kanger.conf.example
ui/
nginx/kanger.conf.template
systemd/kanger.service.template
lib/common.sh
```

`RELEASE` identifies the product, distribution version, KANGER Server version, Server build date, and packaging time. `SHA256SUMS` covers the files in the extracted bundle. Both `install.sh` and `update.sh` verify the bundle before changing the installed system.

Do not remove files from the extracted bundle before running the installer or updater.

---

## 2. Required platform and privileges

Installation and update must be run as `root`, normally through `sudo`.

The customer must provide these runtime components before installation:

- Java 8 or newer;
- nginx;
- systemd;
- standard Linux account and filesystem utilities used by the installer.

The installer explicitly checks for:

```text
java nginx systemctl curl install getent groupadd useradd
readlink ln mv cp chown awk grep sed find seq sleep dirname
```

Java is resolved from the active `java` command and its major version must be at least 8. nginx must be installed and executable.

KANGER does **not** select an operating-system package repository, install a JDK/JRE, install nginx, or modify the customer's OS update policy. The distribution therefore does not prescribe Debian/Ubuntu, RHEL, or another package-manager-specific installation command.

Useful preflight checks are:

```bash
java -version
nginx -v
systemctl --version
```

---

## 3. DNS and public names

A normal installation uses two HTTPS names:

```text
UI:  kanger.example.com
API: api.kanger.example.com
```

The first positional argument of `install.sh` is the Browser UI domain. Unless explicitly overridden, the API domain is `api.<UI-domain>`.

Example:

```bash
sudo ./install.sh kanger.example.com
```

is interpreted as:

```text
UI domain  = kanger.example.com
API domain = api.kanger.example.com
```

For browser access, both names must resolve to the host running nginx. DNS provisioning is customer-managed and is not performed by KANGER.

A custom API name may be supplied explicitly:

```bash
sudo ./install.sh kanger.example.com \
  --api-domain api.example.net
```

The installer accepts fully qualified domain names and uses them to generate the nginx configuration, Browser UI API endpoint, CORS origin, public Server URL, and confirmation redirect URL.

---

## 4. TLS certificates

KANGER does not obtain, replace, or renew TLS certificates.

### 4.1 Default certificate locations

By default both the UI and API virtual hosts use the same customer-managed certificate pair:

```text
/etc/ssl/kanger/fullchain.pem
/etc/ssl/kanger/privkey.pem
```

When the default pair is used, the certificate must be valid for **both** configured public names, for example:

```text
kanger.example.com
api.kanger.example.com
```

The installer requires the configured certificate and key files to exist and be readable at installation time. Certificate ownership, renewal automation, CA choice, and detailed key-permission policy remain customer responsibilities.

### 4.2 Separate UI and API certificates

Separate certificate material can be supplied when required:

```bash
sudo ./install.sh kanger.example.com \
  --ui-cert /etc/ssl/kanger/ui-fullchain.pem \
  --ui-key /etc/ssl/kanger/ui-privkey.pem \
  --api-cert /etc/ssl/kanger/api-fullchain.pem \
  --api-key /etc/ssl/kanger/api-privkey.pem
```

All certificate and key arguments must be absolute paths.

### 4.3 Certificate renewal

If certificate files are renewed **in place at the same paths**, the stored KANGER installation identity remains valid. Reload nginx using the customer's normal certificate-renewal procedure after replacing the files.

The shipped `update.sh` does not provide a domain or certificate-path migration interface. It reloads the domain, certificate paths, and nginx destination recorded by the original installation. A change of those identities should therefore be treated as a separate controlled reconfiguration, not as an ordinary KANGER version update.

---

## 5. nginx integration and network boundary

The default generated nginx configuration is installed at:

```text
/etc/nginx/conf.d/kanger.conf
```

A different destination can be selected during first installation:

```bash
sudo ./install.sh kanger.example.com \
  --nginx-config /absolute/path/included/by/nginx/kanger.conf
```

The selected destination must be an nginx configuration path that the customer's nginx installation actually includes.

The generated configuration:

- listens publicly on TCP 80 and 443 for the UI and API names;
- redirects HTTP to HTTPS;
- serves the Browser UI from `/opt/kanger/current/ui`;
- proxies the public API to `127.0.0.1:1964`;
- restricts the nginx `/ready` endpoint to loopback clients;
- does **not** proxy the local administrative listener on port 1965.

The KANGER Server application listener is bound by the shipped configuration to:

```text
127.0.0.1:1964
```

The local host-operator listener used by `kanger-admin` is bound to:

```text
127.0.0.1:1965
```

Port 1965 is an administrative security boundary. It must remain loopback-only and must not be published through nginx, a public firewall rule, or a reverse proxy.

Before every nginx activation or reload, the scripts run:

```bash
nginx -t
```

---

## 6. First installation

### 6.1 Prepare the host

Before unpacking the release, ensure that:

1. Java 8+ is installed and available as `java`.
2. nginx is installed and its configuration is valid.
3. systemd is the service manager.
4. the UI and API DNS names are prepared.
5. the required TLS certificate and private-key files are present.
6. TCP 80 and 443 can reach nginx as required by the customer's network policy.

### 6.2 Extract the tarball

```bash
tar -xzf kanger-3.7.0.tar.gz
cd kanger-3.7.0
```

The installer verifies `SHA256SUMS` itself. The customer may additionally verify the archive SHA-256 supplied with the release before extraction.

### 6.3 Run the installer

For the standard two-name configuration and default TLS paths:

```bash
sudo ./install.sh kanger.example.com
```

For all installation options:

```bash
./install.sh --help
```

The installer is intentionally a **first-install** operation. If an existing KANGER installation identity, current release link, systemd unit, or target nginx configuration is already present, it refuses to overwrite it and directs the operator to `update.sh` where appropriate.

### 6.4 What the installer does

A successful installation performs these operations in order:

1. verifies the distribution layout and checksums;
2. verifies Java, nginx, systemd, and required host utilities;
3. validates domains, TLS files, and installation paths;
4. creates the `kanger` system group and service user if absent;
5. creates persistent KANGER directories;
6. installs the persistent Server configuration if it does not already exist;
7. stages the versioned release under `/opt/kanger/releases`;
8. installs the systemd unit;
9. atomically points `/opt/kanger/current` at the release;
10. renders and validates the nginx configuration;
11. enables and restarts `kanger.service`;
12. waits for loopback `/health` and `/ready` checks and verifies the running Server version;
13. reloads nginx;
14. writes the installation identity used by future updates.

If first installation fails during activation, the installer removes the release/current link, service unit, nginx file, and installation identity that it created. A pre-existing Server configuration is preserved.

---

## 7. Installed filesystem layout

Default paths are:

| Purpose | Path |
| --- | --- |
| Product root | `/opt/kanger` |
| Versioned releases | `/opt/kanger/releases` |
| Active release symlink | `/opt/kanger/current` |
| Persistent configuration | `/etc/kanger` |
| Server configuration | `/etc/kanger/kanger.conf` |
| Installation identity | `/etc/kanger/instance.conf` |
| Persistent state | `/var/lib/kanger` |
| State-side config symlink | `/var/lib/kanger/kanger.conf` |
| systemd unit | `/etc/systemd/system/kanger.service` |
| nginx config | `/etc/nginx/conf.d/kanger.conf` |
| Active Server JAR | `/opt/kanger/current/server/kanger-server.jar` |
| Browser UI | `/opt/kanger/current/ui` |
| Admin tool | `/opt/kanger/current/bin/kanger-admin` |

The installer creates a system account:

```text
user:  kanger
group: kanger
home:  /var/lib/kanger
shell: /usr/sbin/nologin
```

Important installed permissions include:

```text
/opt/kanger                 root:root   0755
/opt/kanger/releases        root:root   0755
/etc/kanger                 root:kanger 0750
/etc/kanger/kanger.conf     root:kanger 0640
/etc/kanger/instance.conf   root:root   0600
/var/lib/kanger             kanger:kanger 0750
```

Release files are immutable product material owned by root; persistent runtime state is kept separately under `/var/lib/kanger`.

---

## 8. systemd service

The installer creates and enables:

```text
kanger.service
```

The service runs as the non-login `kanger` account, with:

```text
WorkingDirectory=/opt/kanger/current
-Duser.home=/var/lib/kanger
```

The unit uses the Java executable resolved during installation, restarts on failure, writes stdout/stderr to the systemd journal, and applies systemd hardening including `NoNewPrivileges`, restricted filesystem access, and a write boundary limited to `/var/lib/kanger`.

Common service checks:

```bash
sudo systemctl status kanger.service
sudo journalctl -u kanger.service
```

The installer and updater use these loopback checks during activation:

```bash
curl http://127.0.0.1:1964/health
curl http://127.0.0.1:1964/ready
```

The health response must identify the Server version packaged in the release.

---

## 9. Persistent Server configuration

The canonical configuration file is:

```text
/etc/kanger/kanger.conf
```

The service state contains a symlink:

```text
/var/lib/kanger/kanger.conf -> /etc/kanger/kanger.conf
```

On first installation, KANGER copies the shipped Server configuration template and appends installation-specific values for:

```properties
server.cors.allowed.origin.1=https://<UI-domain>
server.url=https://<API-domain>
server.confirmation.redirect.url=https://<UI-domain>/
```

The configuration is persistent. `update.sh` does not replace `/etc/kanger/kanger.conf` with a fresh template.

The generated installation identity is stored separately in:

```text
/etc/kanger/instance.conf
```

It records the UI/API domains, TLS certificate/key paths, and nginx configuration destination. `update.sh` reads this file to reproduce the installation topology. It is root-owned and mode `0600` and should be treated as generated installation state rather than an ordinary application settings file.

After manually changing Server settings in `/etc/kanger/kanger.conf`, restart the service so the new configuration is loaded:

```bash
sudo systemctl restart kanger.service
```

---

## 10. TRUSTED mode

KANGER has two account-registration topologies. They are derived from `server.email.mode`.

The shipped configuration defaults to:

```properties
server.email.mode=disabled
```

This resolves to:

```text
RegistrationPolicy.TRUSTED
```

### 10.1 Meaning of TRUSTED

In TRUSTED mode:

- public self-registration is disabled;
- e-mail confirmation is not required because public registration is not available;
- complete `ACTIVE` application accounts are provisioned by the host operator through `kanger-admin`;
- the Browser receives the public authentication capability `registration_policy=TRUSTED` and `public_registration=false` from the Server.

TRUSTED mode is therefore an **account provisioning policy**, not a TLS trust setting and not a bypass of normal user authentication. Users still authenticate with their KANGER credentials; the difference is who is allowed to create an account.

For a private, controlled installation where the administrator creates the allowed user population, the default `server.email.mode=disabled` is the intended topology.

### 10.2 EMAIL_VERIFIED mode

Setting:

```properties
server.email.mode=starttls
```

or:

```properties
server.email.mode=smtps
```

selects `RegistrationPolicy.EMAIL_VERIFIED`. In that topology public self-registration is enabled and a registration remains pending until successful e-mail confirmation creates the ACTIVE account.

The historical value:

```properties
server.email.mode=ssl
```

is accepted as an SMTPS-compatible migration alias, but `smtps` is the canonical value.

EMAIL_VERIFIED operation also requires the corresponding SMTP settings (`server.email.host`, port, sender, login/password as applicable). Consult the shipped `server/kanger.conf.example` before enabling mail transport.

After changing registration/mail mode, restart KANGER:

```bash
sudo systemctl restart kanger.service
```

---

## 11. Local administration tool: `kanger-admin`

`kanger-admin` is the local host-operator client for KANGER account lifecycle operations. It is **not** a web administration console and it does not grant an application user a special administrator role.

The distribution installs the launcher in the active release:

```text
/opt/kanger/current/bin/kanger-admin
```

Run it as root:

```bash
sudo /opt/kanger/current/bin/kanger-admin --help
```

The launcher uses:

```text
Server home: /var/lib/kanger
Server JAR:  /opt/kanger/current/server/kanger-server.jar
```

It starts `org.kanger.admin.KangerAdmin` from the currently active Server JAR.

### 11.1 Security model

The CLI does not edit credential files, account homes, pending registrations, or deletion journals directly. It calls the running Server's local admin listener over HTTP on loopback using a bearer token.

Default admin settings are:

```properties
server.admin.enabled=true
server.admin.bind.address=127.0.0.1
server.admin.port=1965
server.admin.request.max.body.bytes=65536
server.admin.token.file=KANGER/admin.token
```

With the installed Server home, the default relative token path resolves under `/var/lib/kanger`. The Server creates the token on startup. Ordinary application users never receive this token.

The admin listener must remain loopback-only.

### 11.2 Create a user — simplest form

For an operator-created ACTIVE account, run:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user
```

The tool interactively requests the required login, optional profile fields and privacy consent, and obtains the password through a hidden terminal prompt.

A user created through this operator plane becomes `ACTIVE` immediately. An optional e-mail address supplied by the operator is not marked as e-mail-verified.

This is the normal provisioning path in TRUSTED mode.

### 11.3 Create a user with explicit fields

Example:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user \
  --login alice \
  --email alice@example.org \
  --name 'Alice Example' \
  --country Netherlands \
  --city Amsterdam \
  --privacy-consent true
```

The password is still requested through the hidden prompt.

Supported `create-user` options are:

```text
--login VALUE
--email VALUE
--name VALUE
--country VALUE
--city VALUE
--privacy-consent true|false
--password-stdin
```

`--login` is required; missing optional values may be entered interactively when a terminal is available.

Plaintext `--password` is deliberately forbidden.

### 11.4 Non-interactive password input

Automation must explicitly opt into password input on standard input with `--password-stdin`.

For an operator shell, avoid placing a password literal in the command line or shell history. One possible pattern is:

```bash
read -rsp 'KANGER password: ' KANGER_PASSWORD; echo
printf '%s\n' "$KANGER_PASSWORD" | \
  sudo /opt/kanger/current/bin/kanger-admin create-user \
    --login alice \
    --email alice@example.org \
    --privacy-consent true \
    --password-stdin
unset KANGER_PASSWORD
```

For unattended automation, supply standard input from the customer's secret-management mechanism rather than embedding the password in the command line, script, ticket, or log.

### 11.5 Delete a user

By login:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --login alice
```

By numeric user id:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --user-id 42
```

Interactive deletion requires the operator to type:

```text
DELETE
```

Non-interactive deletion additionally requires explicit acknowledgement:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user \
  --login alice \
  --yes
```

Deletion is a persistent lifecycle operation. It closes sessions/runtime state, removes credentials and stale pending/confirmation state, quarantines the account home, and records deletion progress. After credential removal, recovery is forward-only; it does not automatically republish authentication.

### 11.6 Admin command exit codes

```text
0  operation completed
2  invalid or incomplete operator input
3  admin listener/token/connection failure
4  account lifecycle conflict or rejection
5  deletion is incomplete and requires recovery
```

The current CLI exposes `create-user` and `delete-user`. It does not expose a public-web admin endpoint.

---

## 12. Updating KANGER

Use the `update.sh` contained in the **new** extracted release bundle.

Example:

```bash
tar -xzf kanger-3.7.0.tar.gz
cd kanger-3.7.0
sudo ./update.sh
```

For an ordinary version transition, `update.sh`:

1. verifies the new bundle and runtime prerequisites;
2. verifies that an existing KANGER installation and systemd unit exist;
3. loads `/etc/kanger/instance.conf`;
4. revalidates the recorded TLS material;
5. determines the currently active release and version;
6. stages the new release under `/opt/kanger/releases/<version>`;
7. refreshes the systemd and nginx generated files;
8. atomically repoints `/opt/kanger/current`;
9. restarts KANGER;
10. checks loopback `/health` and `/ready` against the new Server version;
11. validates and reloads nginx.

Persistent `/etc/kanger/kanger.conf` and `/var/lib/kanger` state are not replaced by the release payload.

### 12.1 Failed update and automatic rollback

Before activation, the updater saves temporary copies of the installed systemd unit and nginx configuration. If activation fails, it restores the previous release link and those generated files, reloads systemd, restarts the previous KANGER service, and reloads nginx if its configuration validates.

This activation rollback is **not** a substitute for a customer backup of persistent data. Before a planned upgrade, back up `/etc/kanger` and `/var/lib/kanger` according to the customer's normal backup policy.

### 12.2 Same-version replacement

Re-running a bundle whose product version is already active is rejected by default.

An explicit same-version replacement requires:

```bash
sudo ./update.sh --force
```

The updater first preserves the existing canonical release under a physical snapshot name such as:

```text
/opt/kanger/releases/3.7.0.force.1
```

and then installs the new build at the canonical:

```text
/opt/kanger/releases/3.7.0
```

The active `/opt/kanger/current` link is moved atomically so that a partial canonical rebuild is never the active release.

### 12.3 Manual rollback

The 3.7.0 distribution does not ship a separate `rollback` command. `update.sh` provides automatic rollback when its own activation fails. A deliberate manual downgrade or rollback should be handled as a controlled administrative operation rather than by editing release directories during a live update.

---

## 13. Operational checks and troubleshooting

### Service

```bash
sudo systemctl status kanger.service
sudo journalctl -u kanger.service
```

### Server loopback health

```bash
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

### nginx configuration

```bash
sudo nginx -t
```

### Active release

```bash
readlink -f /opt/kanger/current
cat /opt/kanger/current/RELEASE
```

### Installation identity

```bash
sudo cat /etc/kanger/instance.conf
```

Treat `instance.conf` as generated root-only installation state. Do not publish its contents casually in support tickets.

### Admin plane

If `kanger-admin` returns exit code `3`, check that:

- `kanger.service` is running;
- `server.admin.enabled=true`;
- the admin listener remains on loopback port 1965;
- the Server has created its admin token under the service state home;
- the command is being run through `sudo` from the active release.

Do not expose port 1965 as a troubleshooting shortcut.

---

## 14. What an update preserves

The product deliberately separates immutable release material from persistent customer state.

An update preserves the persistent configuration and runtime state roots:

```text
/etc/kanger
/var/lib/kanger
```

The active product code/UI is selected through:

```text
/opt/kanger/current -> /opt/kanger/releases/<version>
```

Generated system integration files are refreshed from the new distribution:

```text
/etc/systemd/system/kanger.service
<configured nginx destination, default /etc/nginx/conf.d/kanger.conf>
```

Domains and TLS file paths are recovered from:

```text
/etc/kanger/instance.conf
```

This separation is the core installation/update invariant: product files are versioned, while customer configuration and application state remain outside the release directory.

---

## 15. No automatic uninstall command

The 3.7.0 distribution contains `install.sh` and `update.sh`; it does not ship an `uninstall` command.

Do not remove `/var/lib/kanger` as part of ordinary release maintenance: it is persistent application state. If an installation must be permanently decommissioned, first preserve any required customer data and perform removal as an explicit administrative procedure.

---

## 16. First-install checklist

Before running `install.sh`, verify:

- Java 8+ is installed and selected by `java`;
- nginx and systemd are operational;
- the UI FQDN is known;
- the API FQDN is known or the default `api.<UI-domain>` is acceptable;
- DNS is configured for both names;
- TLS certificate chain and private key exist;
- the certificate covers the required public name(s);
- nginx includes the selected generated configuration destination;
- TCP 80/443 exposure matches customer policy;
- ports 1964 and 1965 remain host-local application/admin boundaries;
- the tarball/archive hash has been verified if supplied by the release channel;
- the install command will be run through `sudo`.

After installation, verify:

```bash
sudo systemctl status kanger.service
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
sudo nginx -t
sudo /opt/kanger/current/bin/kanger-admin --help
```

For a default TRUSTED installation, create the first application user with:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user
```

That completes the normal host-side bootstrap of a KANGER 3.7.0 installation.
