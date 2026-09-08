# KANGER 3.7.0 Developer SDK

This guide defines the supported developer-facing contract of the KANGER 3.7.0 Developer Distribution. It is intentionally narrower than all Java-public classes present in the Core JAR.

The normative integration path is plain Java/classpath. Maven and Gradle are packaging adapters over the same SDK contract.

## 1. KANGER Core as a Java library

The canonical standalone entry path is:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

`IUser` owns the external user/configuration/storage context. `IMind` represents the active logical context. KANGER Server, REST, UI, browser sessions, and Console account plumbing are not required for embedded Core use.

A complete executable version of this entry path is shipped as [`../examples/BasicQuery.java`](../examples/BasicQuery.java).

## 2. Supported API boundary

The supported Developer SDK surface is curated, not inferred from Java `public` visibility.

Primary contracts:

- `org.kanger.interfaces.IUser`
- `org.kanger.interfaces.IMind`
- developer-facing interfaces required by their supported result and inspection APIs, including `ITerm`, `IRule`, `IPredicate`, `IHypothesis`, `ILogEntry`, and `IFactory`
- concrete entry/value/configuration types required by those contracts, including `User`, `Mind`, and `ValuesOrder`
- public enums and exceptions explicitly included in the generated SDK JavaDoc
- `org.kanger.bootstrap.RuntimeBootstrap`, `RuntimeBootstrapResult`, and `RuntimeCapability` for optional runtime capabilities

`org.kanger.interfaces.internal` and implementation packages such as compiler, storage implementation, stores, units, and factories are not part of the supported SDK contract.

The authoritative generated reference for the curated surface is [`api/index.html`](api/index.html).

## 3. Getting started

### 3.1 Plain Java / classpath

From the unpacked distribution root on POSIX/macOS/Linux:

```sh
mkdir -p out
javac -cp "lib/*" -d out examples/BasicQuery.java
java -cp "out:lib/*" BasicQuery
```

On Windows, use `;` as the runtime classpath separator:

```bat
mkdir out
javac -cp "lib/*" -d out examples\BasicQuery.java
java -cp "out;lib/*" BasicQuery
```

No Server, UI, external KANGER repository, or installation step is involved.

### 3.2 Creating User and Mind

Create one external user context and the root logical context:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

Do not use Console `UserFactory` as the normal embedded SDK entry path. It belongs to Console account/session plumbing.

`IUser.getCurrentMind()/setCurrentMind()` is a caller-managed compatibility slot. It is not a transaction-chain resolver and must not be used as lifecycle authority.

### 3.3 First compile and query

`compile(String)` is the normal bulk program-loading path:

```java
boolean accepted = mind.compile(
        "!color(apple, Red);"
      + "!color(cucumber, Green);"
      + "!color(lemon, Yellow);");
```

Then execute one language operation with `query(String)`:

```java
Boolean answer = mind.query("?color(apple, Red);");
```

The answer is tri-state:

- `Boolean.TRUE` — logically true;
- `Boolean.FALSE` — logically false;
- `null` — logically undetermined/unknown.

`null` is not an exception and does not mean runtime failure.

See [`../examples/BasicQuery.java`](../examples/BasicQuery.java).

## 4. Java to KANGER data mapping

### 4.1 Parameterized query and compile

When Java values are inserted into language text, prefer the parameterized overloads rather than manual string construction:

```java
Boolean result = mind.query(
        "!age(?, ?);",
        new Object[]{"John", 37});
```

The supported forms are:

```text
query(String)
query(String, Object[])
compile(String)
compile(String, Object[])
```

Placeholder substitution follows the current `IMind` contract. Consult the generated `IMind` JavaDoc for the exact supported Java value mapping.

### 4.2 KANGER values in Java

Query-local exported values are available through:

```text
getValues()
getValues(ValuesOrder...)
```

A Values row maps exported variable names to `ITerm`. The Java value of a term is exposed through the supported `ITerm` contract, including `getValue()` where applicable.

`getValues(ValuesOrder...)` returns an invocation-local ordered projection. Ordering does not change Values membership or canonical result storage.

### 4.3 Binary data

Binary values belong to the general Java/KANGER value-mapping model. Use only mappings present in the current curated JavaDoc. Historical binary-data examples are not treated as 3.7.0 contract until separately qualified as executable Developer examples.

## 5. Queries and compilation

### 5.1 `query()`

`query()` executes one KANGER operation. After the invocation, related result stores describe that invocation: logical answer, Solutions, Values, Hypothesis, and Log.

One call accepts one language operation according to the current `IMind` contract.

### 5.2 `compile()`

`compile()` accepts program text containing multiple statements/rules/comments and is the preferred bulk load path.

For KANGER 3.7.0 the supported atomicity rule is: the supplied program is accepted as a whole or rejected as a whole when conflict/error prevents publication. Do not interpret a rejected compile as partially installed program state.

### 5.3 Logical result versus failure

Keep these categories separate:

```text
TRUE / FALSE / null   = logical result
exception / rejection = execution or contract failure
```

Flood/error is not a partial logical answer. `compile()` returning `false` is a compile rejection, not a successful partial compile.

## 6. Results

The result of one query invocation is a coherent family of views.

### 6.1 Solutions

`getSolutions()` exposes the solution rules associated with the current query result. Use the interfaces returned by the curated SDK; do not cast into implementation classes.

### 6.2 Values

`getValues()` exposes exported variable bindings as rows. `getValues(ValuesOrder...)` creates a stable ordered projection for presentation or application processing.

### 6.3 Hypotheses

When a result is undetermined, `getHypothesis()` exposes the hypotheses generated by the last inference. `optimizeHypothesis()` removes hypotheses that can already be shown false according to the current contract.

Optimization may be computationally significant; request it when the application actually needs the refined hypothesis set.

### 6.4 Log and explanation data

`getLog()` exposes inference log entries for the latest operation. `getCurrentLogRecord(LogMode)` exposes the current record for a selected log projection. Treat these as developer-facing explanation/diagnostic data, not as Core implementation state.

## 7. Transactions

A child `Mind` is an isolated transaction overlay over its parent:

```java
IMind root = new Mind(user);
IMind child = new Mind(root);
```

Changes made through `child` belong to the child level until the parent settles it.

### 7.1 Commit

The parent applies its direct child with:

```java
boolean committed = root.commit(child);
```

Commit performs the supported conflict/deduplication checks and publishes only a completed child delta. A failed commit must not be treated as partially applied child state.

### 7.2 Release

Discard a direct child with:

```java
root.release(child);
```

### 7.3 Ownership and lifetime

After terminal commit/release lifecycle processing, the child `Mind` must not be reused by caller code. Continue from the active parent/root object required by the lifecycle contract.

The executable transaction proof is [`../examples/TransactionExample.java`](../examples/TransactionExample.java).

### 7.4 Concurrency

KANGER Core contains internal reservation/locking and supports meaningful parallel transaction workflows. That does not make one mutable `IMind` an unrestricted concurrently callable facade.

Developer rule:

```text
parallel transaction branches != arbitrary concurrent mutation of one IMind
```

Serialize caller workflow where query-local result stores, active-Mind transitions, storage lifecycle, or other mutable invocation state are involved.

## 8. Persistence and storage lifecycle

Transaction lifecycle and physical storage lifecycle are separate contracts.

The canonical Developer SDK storage path in 3.7.0 is `IUser` lifecycle management combined with runtime capability bootstrap.

### 8.1 Configure user directories

For file-backed storage, configure the user, source, and database directories before opening storage. Directory strings accepted by the current `IUser` contract end with the platform file separator.

### 8.2 Attach the storage capability

The Developer bundle includes one storage provider. Request only the capability you need:

```java
RuntimeBootstrap.ensureCapabilities(user, RuntimeCapability.STORAGE);
```

`RuntimeBootstrap` discovers runtime modules through Java `ServiceLoader`. Absence of an optional capability is a supported classpath state. If exactly one provider exists for a requested capability it can be selected automatically; multiple providers require explicit configuration.

### 8.3 Open or create storage

```java
mind = user.use(mind, "example");
```

`use()` is allowed only while user storage is closed. It does not silently close/replace an already open storage.

### 8.4 Durable checkpoint

```java
mind = user.checkpoint(mind);
```

`checkpoint()` durably publishes the root state without closing storage. It is a physical-storage operation and requires transaction level 0.

### 8.5 Close

```java
mind = user.close(mind);
```

`close()` performs the qualified root checkpoint/physical close path. Active child transactions are not silently committed or rolled back; lifecycle preconditions are enforced.

### 8.6 Continue with returned Mind

For `use`, `checkpoint`, and `close`, continue with the `IMind` returned by the operation. Lifecycle operations may replace the active object as part of their contract.

The complete qualified persistence sequence, including close and reopen, is [`../examples/StorageExample.java`](../examples/StorageExample.java).

## 9. Diagnostics and inspection

Developer-facing diagnostics include query results, Solutions, Values, Hypotheses, and inference Log. For structural inspection, the curated `IMind` surface also exposes views such as terms, predicates, rules, and library operations.

These are primarily inspection surfaces. Normal knowledge-state mutation remains `query()`, `compile()`, transactions, and qualified lifecycle APIs. Java-public inference/linker/materializer implementation classes are not part of the SDK inspection contract.

## 10. Runtime limits and configuration

Advanced runtime controls exposed by the curated JavaDoc may be used when an embedding application needs them. Keep configuration such as flood-control and debug/log controls out of the basic startup path unless your application has a concrete requirement.

`IUser` string properties are external user/runtime configuration. Changing them is not a transaction commit or rollback.

## 11. Optional runtime capabilities

`RuntimeBootstrap` is for attaching optional capabilities around a `User`, not for constructing the core `Mind` entry path.

Use:

```text
RuntimeBootstrap.ensure(user)
```

to request all known capabilities, or:

```text
RuntimeBootstrap.ensureCapabilities(user, ...)
```

to request a narrow capability set.

The Developer distribution currently includes the UDF and DUMB storage providers. Provider implementation APIs are not part of the Developer SDK contract.

## 12. Maven

The bundle contains a Maven-compatible repository at `repository/` and a convenience coordinate:

```xml
<dependency>
    <groupId>org.kanger</groupId>
    <artifactId>kanger-sdk</artifactId>
    <version>3.7.0</version>
</dependency>
```

A complete consumer project is shipped as `../examples/maven/`. Its repository URL points to the bundle-local `../../repository` directory.

The `kanger-sdk` convenience artifact resolves the canonical 3.7.0 SDK/runtime graph. It does not change the Java API described above.

## 13. Gradle

The equivalent Gradle dependency is:

```groovy
dependencies {
    implementation 'org.kanger:kanger-sdk:3.7.0'
}
```

A complete bundle-local consumer project is shipped as `../examples/gradle/`. It uses only the bundled KANGER repository for KANGER artifacts.

## 14. Examples

Current executable Developer examples:

- [`BasicQuery.java`](../examples/BasicQuery.java) — root `User`/`Mind`, compile, tri-state query;
- [`TransactionExample.java`](../examples/TransactionExample.java) — child `Mind`, commit, visibility in parent;
- [`StorageExample.java`](../examples/StorageExample.java) — storage bootstrap, use, checkpoint, close, reopen.

The canonical distribution qualification compiles and executes these source files from the built archive on Java 8 and Java 21. Maven and Gradle consumer qualification execute the storage example through `org.kanger:kanger-sdk:3.7.0`.

Additional example families such as Values, parameters, hypotheses, and binary data become normative only after they are added as executable examples and qualified against the live 3.7.0 contract.

## 15. Compatibility policy

The Developer SDK contract is the curated surface documented here and in `docs/api`, not every public implementation symbol contained in the JARs.

Use these status meanings when discussing SDK surface:

- **Stable** — supported Developer contract for 3.7.0.
- **Experimental** — intentionally exposed for evaluation; compatibility is not promised as Stable.
- **Internal** — implementation detail, outside the SDK contract.
- **Legacy** — retained for compatibility but not the preferred integration path.

For 3.7.0, prefer interfaces and lifecycle paths documented in this guide. Do not build new integrations against `org.kanger.interfaces.internal` or implementation packages that are absent from the curated JavaDoc.
