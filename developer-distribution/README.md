# KANGER 3.7.0 Developer Distribution

KANGER Developer Distribution is the standalone package for Java developers who want to embed KANGER Core, compose optional KANGER features, run the local KANGER Console, and study executable Java examples without KANGER Server or KANGER UI.

## Requirements

- Java 8 or newer to run the SDK and Console.
- A JDK with `javac` to compile the plain-Java examples.
- Maven or Gradle only when you choose those integration paths; neither is required for the normative classpath path.

The distribution is qualified as one canonical artifact on Java 8 and Java 21. The same artifact is also qualified as a Maven and Gradle consumer repository.

## Contents

```text
kanger-developer-3.7.0/
  bin/                  Console launchers
  lib/                  Core and optional feature/runtime JARs
  repository/           bundled Maven-compatible org.kanger repository
  examples/             plain Java plus Maven and Gradle consumer examples
  docs/
    SDK.md               normative Developer SDK guide and assembly manual
    CONSOLE.md           standalone Console reference
    api/                 curated JavaDoc for the supported SDK surface
  README.md
  VERSION
```

Server deployment, REST endpoints, UI installation, and browser-client documentation are intentionally outside this distribution.

## Assembly model

KANGER 3.7.0 is assembled around one self-sufficient inference-engine library plus optional features.

```text
                         kanger-core
                      /      |       \
                     /       |        \
          kanger-command  storage      UDF
                     \       |        /
                      \  kanger-bootstrap
                       \    optional /
```

The important rule is that the arrows point **toward Core**: optional features depend on the Core contract; Core does not depend on those features.

The modules shipped in the Developer Distribution have these roles:

- `kanger-core` — the necessary and sufficient KANGER inference engine as a Java library: `User`, `Mind`, compiler/query/inference semantics, transactions, result inspection, and the lifecycle contracts used by optional capabilities. It does **not** require Command, Storage, UDF, Bootstrap, Console, Server, or UI.
- `kanger-command` — optional canonical infrastructure-control language and transport-neutral command processing over Core. It depends on `kanger-core`; Core does not depend on it.
- `kanger-data-dumb` — optional DUMB storage provider. It can be attached explicitly to a `User`; its Bootstrap integration is optional.
- `kanger-udf` — optional UDF provider. It can be attached explicitly to a `User`; its Bootstrap integration is optional.
- `kanger-bootstrap` — optional discovery/composition helper. It discovers and attaches available capabilities through `ServiceLoader`; it does not own Storage or UDF and is not required by Core.
- `kanger-sdk` — convenience Maven/Gradle coordinate containing the normal embedded runtime set: Core + Bootstrap + bundled UDF + bundled DUMB storage. It intentionally does **not** pull `kanger-command`; add the command feature only when your application actually needs the canonical command/control layer.
- Console — a delivery adapter above these features. It explicitly uses the command feature and the runtime capabilities needed by the local Console experience.

All of these JARs are present in the unpacked Developer Distribution so a developer can choose the desired assembly. Presence in `lib/` does not mean that every module is a mandatory dependency of Core.

Typical assemblies are therefore:

```text
Inference engine only
    kanger-core

Inference + persistence
    kanger-core + kanger-data-dumb

Inference + UDF
    kanger-core + kanger-udf

Recommended composed runtime
    kanger-core + kanger-bootstrap + selected providers

Custom command/control application
    kanger-command + kanger-core + any selected runtime features

Bundled convenience SDK
    kanger-sdk = core + bootstrap + bundled UDF + bundled DUMB storage
```

See [`docs/SDK.md`](docs/SDK.md) for concrete Maven/Gradle coordinates and Java examples for each path.

## Start the Console

POSIX/macOS/Linux:

```sh
bin/kanger-console
```

Windows:

```bat
bin\kanger-console.cmd
```

With no options, the bundled launcher starts the normal interactive authentication path and prompts for `login` and `password`. The launcher itself does not choose a user or force single-user mode.

For the explicit local single-user mode:

```sh
bin/kanger-console -S
```

For an explicitly selected existing user, the same Console options can be passed through the launcher, for example:

```sh
bin/kanger-console -U <login> -P <password>
```

After authentication, enter:

```text
help
```

for the canonical command reference and:

```text
quit
```

to close the session.

See [`docs/CONSOLE.md`](docs/CONSOLE.md) for the complete developer-oriented Console guide and launcher options.

## First Java program: Core-only classpath path

The minimal embedded entry path is plain Core. From the distribution root:

POSIX/macOS/Linux:

```sh
mkdir -p out
javac -cp "lib/kanger-core.jar" -d out examples/BasicQuery.java
java -cp "out:lib/kanger-core.jar" BasicQuery
```

Windows:

```bat
mkdir out
javac -cp "lib/kanger-core.jar" -d out examples\BasicQuery.java
java -cp "out;lib/kanger-core.jar" BasicQuery
```

A successful run prints:

```text
BASIC_QUERY_PASS
```

Using `lib/*` is also convenient when experimenting with all bundled features, but it is deliberately broader than the minimum Core-only assembly.

The archive also ships executable examples for Values, Java parameter binding, hypotheses, transactions, storage lifecycle, and binary data:

```text
ValuesExample.java
ParametersExample.java
HypothesesExample.java
TransactionExample.java
StorageExample.java
BinaryDataExample.java
```

See [`examples/README.md`](examples/README.md) for the complete commands and expected PASS markers.

## Core, runtime providers, and RuntimeBootstrap

KANGER Core does **not** require `RuntimeBootstrap`. The minimal embedded Core entry path remains a plain `User` plus `Mind`, and it is valid to run that Core without storage or UDF capabilities when the application does not need them.

Optional runtime providers enrich an existing `User`; they do not define Core semantics. A developer therefore has two composition paths.

### Direct provider attachment

An embedding application may select and initialize concrete runtime providers itself. This gives the application complete control over provider choice, initialization order, class loading, and lifecycle, but deliberately couples the application to provider-specific implementation APIs.

For the providers bundled with the 3.7.0 Developer Distribution, the current low-level form is:

```java
IUser user = new User();

new org.kanger.storage.DB().init(user);
new org.kanger.udf.UDF().init(user);

IMind mind = new Mind(user);
```

This is a valid explicit composition path, but `org.kanger.storage.DB` and `org.kanger.udf.UDF` are implementation classes, not part of the curated stable Developer SDK surface. Use direct attachment when the application intentionally wants to own that provider-specific coupling.

### RuntimeBootstrap: recommended composition path

For normal embedding, `RuntimeBootstrap` is the recommended composition tool. It is shipped separately from Core and enriches an already-created `User` with optional runtime capabilities discovered through Java `ServiceLoader`:

```java
IUser user = new User();
RuntimeBootstrap.ensure(user);
IMind mind = new Mind(user);
```

Applications that need only selected capabilities can request them explicitly:

```java
RuntimeBootstrap.ensureCapabilities(user, RuntimeCapability.STORAGE);
```

`RuntimeBootstrap` provides a single composition point, avoids provider-specific startup code, keeps module discovery and selection consistent with the standard KANGER runtime, supports narrow capability requests, and allows an embedding application to supply its own discovery `ClassLoader` when required. If a requested capability has exactly one available provider it can be selected automatically; when multiple providers are present, selection is made explicitly through the corresponding `User` runtime property.

The architectural rule is intentional:

```text
Core does not depend on RuntimeBootstrap.
RuntimeBootstrap is the recommended, but optional, composition layer around Core.
```

An application may therefore use bare Core, compose providers explicitly, or use `RuntimeBootstrap` as the canonical convenience layer. Choosing not to use `RuntimeBootstrap` does not disable Core; it only makes the embedding application responsible for attaching any optional capabilities it needs.

See [`docs/SDK.md`](docs/SDK.md) for the exact bootstrap methods, direct/provider examples, command-feature integration, configuration rules, and supported API boundary.

## SDK documentation

Read [`docs/SDK.md`](docs/SDK.md) before integrating KANGER into an application. It defines the assembly model, supported entry paths, Java/KANGER data mapping, query/compile semantics, result model, transaction ownership rules, storage lifecycle and maintenance, bootstrap behavior, optional command processing, concurrency contract, Maven/Gradle adapters, and the executable example suite.

The generated reference is available at [`docs/api/index.html`](docs/api/index.html). JavaDoc is deliberately curated: implementation packages and optional feature APIs are not automatically part of the published stable Developer SDK contract merely because Java visibility is public.

## Maven and Gradle

The bundle contains a Maven-compatible repository rooted at `repository/`. The convenience coordinate is:

```text
org.kanger:kanger-sdk:3.7.0
```

Ready consumer projects are in `examples/maven/` and `examples/gradle/`. They resolve KANGER from the bundled repository rather than from an external KANGER repository.

For a narrower assembly, depend directly on the individual artifact you need, for example `org.kanger:kanger-core:3.7.0` for the inference engine alone or `org.kanger:kanger-command:3.7.0` when building a custom canonical command/control adapter.

Plain Java/classpath remains the normative contract; Maven and Gradle are adapters over the same SDK artifacts.
