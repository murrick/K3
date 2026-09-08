# KANGER 3.7.0 Developer Distribution

KANGER Developer Distribution is the standalone package for Java developers who want to embed KANGER Core, run the local KANGER Console, and study executable Java examples without KANGER Server or KANGER UI.

## Requirements

- Java 8 or newer to run the SDK and Console.
- A JDK with `javac` to compile the plain-Java examples.
- Maven or Gradle only when you choose those integration paths; neither is required for the normative classpath path.

The distribution is qualified as one canonical artifact on Java 8 and Java 21. The same artifact is also qualified as a Maven and Gradle consumer repository.

## Contents

```text
kanger-developer-3.7.0/
  bin/                  Console launchers
  lib/                  SDK, bootstrap, providers, and Console runtime JARs
  repository/           bundled Maven-compatible org.kanger repository
  examples/             plain Java plus Maven and Gradle consumer examples
  docs/
    SDK.md               normative Developer SDK guide
    CONSOLE.md           standalone Console reference
    api/                 curated JavaDoc for the supported SDK surface
  README.md
  VERSION
```

Server deployment, REST endpoints, UI installation, and browser-client documentation are intentionally outside this distribution.

## Start the Console

POSIX/macOS/Linux:

```sh
bin/kanger-console
```

Windows:

```bat
bin\kanger-console.cmd
```

The bundled launcher starts Console in local single-user mode. Enter:

```text
help
```

for the canonical command reference and:

```text
quit
```

to close the session.

See [`docs/CONSOLE.md`](docs/CONSOLE.md) for the complete developer-oriented Console guide.

## First Java program: classpath path

The normative SDK path uses only the unpacked distribution. From the distribution root:

POSIX/macOS/Linux:

```sh
mkdir -p out
javac -cp "lib/*" -d out examples/BasicQuery.java
java -cp "out:lib/*" BasicQuery
```

Windows:

```bat
mkdir out
javac -cp "lib/*" -d out examples\BasicQuery.java
java -cp "out;lib/*" BasicQuery
```

A successful run prints:

```text
BASIC_QUERY_PASS
```

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

## SDK documentation

Read [`docs/SDK.md`](docs/SDK.md) before integrating KANGER into an application. It defines the supported entry path, Java/KANGER data mapping, query/compile semantics, result model, transaction ownership rules, storage lifecycle and maintenance, bootstrap behavior, concurrency contract, Maven/Gradle adapters, and the executable example suite.

The generated reference is available at [`docs/api/index.html`](docs/api/index.html). JavaDoc is deliberately curated: implementation packages and internal storage/compiler structures are not part of the published Developer SDK contract merely because Java visibility is public.

## Maven and Gradle

The bundle contains a Maven-compatible repository rooted at `repository/`. The convenience coordinate is:

```text
org.kanger:kanger-sdk:3.7.0
```

Ready consumer projects are in `examples/maven/` and `examples/gradle/`. They resolve KANGER from the bundled repository rather than from an external KANGER repository.

Plain Java/classpath remains the normative contract; Maven and Gradle are adapters over the same SDK artifacts.
