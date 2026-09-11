# KANGER 3.7.0 Developer SDK

This guide defines the supported developer-facing contract of the KANGER 3.7.0 Developer Distribution. It is intentionally narrower than all Java-public classes present in the shipped JARs.

The normative integration path is plain Java/classpath. Maven and Gradle are packaging adapters over the same SDK contract.

For exact signatures and inherited members of the curated stable SDK surface, use the generated [Developer SDK JavaDoc](api/index.html). The most frequently used references are [`IUser`](api/org/kanger/interfaces/IUser.html), [`IMind`](api/org/kanger/interfaces/IMind.html), [`ITerm`](api/org/kanger/interfaces/ITerm.html), and [`RuntimeBootstrap`](api/org/kanger/bootstrap/RuntimeBootstrap.html).

## Assembly model

KANGER 3.7.0 is deliberately assembled as one self-sufficient inference engine plus optional peer features.

```text
                         kanger-core
                      /      |       \
                     /       |        \
          kanger-command  storage      UDF
                     \       |        /
                      \  kanger-bootstrap
                       \    optional /
```

Read the diagram as a dependency rule: optional features depend on Core; Core does not depend on the optional features.

### What each artifact means

- `kanger-core` — the necessary and sufficient KANGER inference engine as a Java library. It owns `User`, `Mind`, compile/query/inference semantics, transactions, result inspection, and the Core lifecycle contracts. It does not require Command, Storage, UDF, Bootstrap, Console, Server, or UI.
- `kanger-command` — optional canonical infrastructure-control language and transport-neutral command processing over Core. It depends on `kanger-core`; Core does not depend on it.
- `kanger-data-dumb` — optional DUMB storage provider. It can be attached directly to a `User`; Bootstrap integration is optional.
- `kanger-udf` — optional UDF provider. It can be attached directly to a `User`; Bootstrap integration is optional.
- `kanger-bootstrap` — optional discovery/composition helper. It discovers and attaches available capabilities through Java `ServiceLoader`. It does not own Storage or UDF and is not required by Core.
- `kanger-sdk` — convenience Maven/Gradle coordinate containing Core + Bootstrap + bundled UDF + bundled DUMB storage. It intentionally does **not** depend on `kanger-command`.
- Console — a delivery adapter above the feature boundary. It explicitly uses the command feature and the runtime capabilities required by the Console experience.

The Developer archive ships all of these JARs so a developer can choose an assembly. Presence in `lib/` does not imply that every JAR is mandatory for Core.

Typical assemblies are:

```text
Inference only
    kanger-core

Inference + persistence
    kanger-core + kanger-data-dumb

Inference + UDF
    kanger-core + kanger-udf

Recommended runtime composition
    kanger-core + kanger-bootstrap + selected providers

Custom canonical command/control adapter
    kanger-command + kanger-core + selected runtime features

Convenience embedded SDK
    kanger-sdk = core + bootstrap + bundled UDF + bundled DUMB storage
```

### Core-only startup

The minimum embedded KANGER application is intentionally small:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

Nothing else is required to compile/query in-memory knowledge and use the inference engine.

### Direct provider attachment

An application may attach concrete runtime providers itself and omit Bootstrap completely:

```java
IUser user = new User();

new org.kanger.storage.DB().init(user);
new org.kanger.udf.UDF().init(user);

IMind mind = new Mind(user);
```

This is a valid explicit composition path. It deliberately couples the application to provider implementation classes, so `org.kanger.storage.DB` and `org.kanger.udf.UDF` are not part of the curated Stable SDK surface.

Use this path when the embedding application intentionally owns provider selection and initialization.

### RuntimeBootstrap composition

For normal embedding, the recommended composition path is to let `RuntimeBootstrap` discover and attach available capabilities around an already-created `User`:

```java
IUser user = new User();
RuntimeBootstrap.ensure(user);
IMind mind = new Mind(user);
```

A narrow capability request is also supported:

```java
IUser user = new User();
RuntimeBootstrap.ensureCapabilities(user, RuntimeCapability.STORAGE);
IMind mind = new Mind(user);
```

Bootstrap is therefore a convenience/composition feature, not a prerequisite for Core and not a parent layer for Storage or UDF.

### Canonical command/control feature

Applications that need KANGER's canonical infrastructure-control dialogue without adopting the full Console can use the command feature directly.

A minimal transaction-control example is:

```java
IUser user = new User();
IMind mind = new Mind(user);
user.setCurrentMind(mind);

CommandParser parser = new CommandParser();
CanonicalCommandProcessor processor = new CanonicalCommandProcessor();

CommandInvocation invocation = parser.parse("transaction start");

if (processor.handles(invocation)) {
    CanonicalCommandProcessor.Result result =
            processor.execute(invocation, user);

    if (!result.isSuccess()) {
        throw new IllegalStateException(result.getDescription());
    }

    mind = result.getMind();
}
```

Relevant imports are:

```java
import org.kanger.CanonicalCommandProcessor;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
```

The parser also recognizes Core-language input. Such input deliberately bypasses command dispatch and remains a Core operation:

```java
CommandInvocation invocation = parser.parse("?color(apple, Red);");

if (invocation.isCoreLanguage()) {
    Boolean answer = mind.query(invocation.getRaw());
}
```

`CanonicalCommandProcessor` currently owns the converged transport-neutral infrastructure-control families such as status, transaction control, and storage control. `handles(invocation) == false` means the caller must keep or provide the appropriate higher-level handling path. The processor is therefore useful for custom delivery/control adapters, but is not a promise that every Console presentation command is implemented by this one class.

The command feature is public and usable in 3.7.0, but it is intentionally **not part of the curated Stable Developer SDK JavaDoc surface**. Treat direct command-feature integration as feature-level/advanced API for 3.7.0; the full supported interactive command experience remains the standalone Console documented in [`CONSOLE.md`](CONSOLE.md).

## 1. KANGER Core as a Java library

KANGER Core can be embedded directly into a Java application without KANGER Server, REST, UI, browser sessions, Console account plumbing, or the canonical command layer.

The canonical standalone entry path is:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

`IUser` owns the external user/configuration/storage context. `IMind` represents the active logical context.

A complete executable version of this entry path is shipped as [`../examples/BasicQuery.java`](../examples/BasicQuery.java).

## 2. Supported API boundary

The supported Developer SDK surface is curated, not inferred from Java `public` visibility.

Primary contracts:

- [`IUser`](api/org/kanger/interfaces/IUser.html) — external user/configuration context and canonical storage lifecycle boundary;
- [`IMind`](api/org/kanger/interfaces/IMind.html) — active logical context for compile, query, results, transactions, diagnostics, and maintenance;
- [`ITerm`](api/org/kanger/interfaces/ITerm.html), `IRule`, `IPredicate`, `IHypothesis`, `ILogEntry`, and `IFactory` — developer-facing result and inspection contracts;
- `User`, `Mind`, and `ValuesOrder` — concrete entry/value/configuration types required by the supported contracts;
- public enums and exceptions explicitly included in the generated SDK JavaDoc — stable value and diagnostic types used by those contracts;
- [`RuntimeBootstrap`](api/org/kanger/bootstrap/RuntimeBootstrap.html), [`RuntimeBootstrapResult`](api/org/kanger/bootstrap/RuntimeBootstrapResult.html), and [`RuntimeCapability`](api/org/kanger/bootstrap/RuntimeCapability.html) — optional runtime-capability discovery and attachment.

`org.kanger.interfaces.internal` and implementation packages such as compiler, storage implementation, stores, units, and factories are not part of the supported SDK contract.

The canonical command feature is also outside the curated Stable JavaDoc surface in 3.7.0 even though its integration classes are public and can be used by custom adapters as described above.

The authoritative generated reference for the curated surface is [`api/index.html`](api/index.html).

## 3. Getting started

### 3.1 Plain Java / classpath

For the minimum Core-only path from the unpacked distribution root on POSIX/macOS/Linux:

```sh
mkdir -p out
javac -cp "lib/kanger-core.jar" -d out examples/BasicQuery.java
java -cp "out:lib/kanger-core.jar" BasicQuery
```

On Windows, use `;` as the runtime classpath separator:

```bat
mkdir out
javac -cp "lib/kanger-core.jar" -d out examples\BasicQuery.java
java -cp "out;lib/kanger-core.jar" BasicQuery
```

`lib/*` remains a convenient broad classpath when experimenting with all bundled features, but it is not the minimum Core dependency.

No Server, UI, external KANGER repository, or installation step is involved.

### 3.2 Creating User and Mind

Create one external user context and the root logical context:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

Do not use Console `UserFactory` as the normal embedded SDK entry path. It belongs to Console account/session plumbing.

`IUser.getCurrentMind()/setCurrentMind()` is a caller-managed compatibility/session slot. It is not a transaction-chain resolver and must not be used as lifecycle authority. The canonical command feature uses this slot to track the active Mind of its command session; plain Core applications do not need it for ordinary `IMind` ownership.

See the exact constructor and inherited contracts in [`User`](api/org/kanger/User.html), [`Mind`](api/org/kanger/Mind.html), [`IUser`](api/org/kanger/interfaces/IUser.html), and [`IMind`](api/org/kanger/interfaces/IMind.html).

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

## 4. Java ↔ KANGER data mapping

### 4.1 Parameterized query and compile

When Java values are inserted into KANGER statements, prefer the parameterized overloads rather than manual string construction:

```java
Boolean result = mind.query(
        "!age(?, ?);",
        new Object[]{"John", 37});
```

Supported forms:

- `query(String)` — execute one KANGER operation expressed entirely as source text;
- `query(String, Object[])` — execute one operation with Java values bound to placeholders in source order;
- `compile(String)` — atomically compile one source block into the current `Mind`;
- `compile(String, Object[])` — atomically compile source with Java values bound to placeholders in source order.

Placeholders are consumed from the `Object[]` in source order. A complete executable proof for both parameterized `compile()` and `query()` is [`../examples/ParametersExample.java`](../examples/ParametersExample.java).

Exact overloads: [`IMind`](api/org/kanger/interfaces/IMind.html).

### 4.2 ITerm and DataType

Values exported by a query are represented as [`ITerm`](api/org/kanger/interfaces/ITerm.html). Its public value contract includes:

- `DataType getType()` — return the public KANGER data type of the term;
- `String getId()` — return the term identifier defined by the public contract;
- `Object getValue()` — project the term into its Java value representation;
- `boolean isEmpty(IMind mind)` — test whether the term is empty in the supplied Mind context;
- `boolean isCVariable()` — report whether the term is a C-variable;
- `boolean equalsTo(ITerm value)` — compare two terms using KANGER term equality;
- `boolean isDeleted(IMind mind)` — report whether the term is deleted in the supplied Mind context.

The public [`DataType`](api/org/kanger/enums/DataType.html) enum contains:

- `VOID` — no public value;
- `PERIOD` — period value;
- `TERM` — KANGER term/reference value;
- `STRING` — string/scalar token value;
- `NUMERIC` — numeric value;
- `DATE` — date/time value;
- `INTERVAL` — interval value;
- `SET` — set value;
- `BLOB` — binary value.

Current external Java mappings relevant to normal SDK use include:

- `Number` → `NUMERIC` with canonical Java value `Double`;
- `java.util.Date` → `DATE`;
- `byte[]` → `BLOB`;
- `ITerm[]` → `INTERVAL`;
- `Object[]` → `SET`;
- strings are interpreted using the current KANGER scalar-token rules and exposed through the resulting `ITerm`.

For exact type behavior, treat the generated `ITerm` and `DataType` JavaDoc as the API reference.

### 4.3 Values

Query-local exported values are available through:

- `getValues()` — return the current query Values rows using canonical/default ordering;
- `getValues(ValuesOrder...)` — return the same Values membership using the requested invocation-local ordering.

A Values row maps exported variable names to `ITerm`.

Example:

```java
for (Map<String, ITerm> row : mind.getValues()) {
    Object value = row.get("x").getValue();
}
```

`ValuesOrder.asc(field)` creates an ascending key; `ValuesOrder.desc(field)` creates a descending key. Sorting changes presentation order, not result membership.

Exact references: [`IMind`](api/org/kanger/interfaces/IMind.html) and [`ValuesOrder`](api/org/kanger/ValuesOrder.html).

The executable Values proof is [`../examples/ValuesExample.java`](../examples/ValuesExample.java).

### 4.4 Binary data

Binary data uses the same parameter/result model as other values. A Java `byte[]` passed through a parameterized compile/query becomes `DataType.BLOB`; retrieving that term through Values returns a `byte[]` from `ITerm.getValue()`.

The qualified round-trip is [`../examples/BinaryDataExample.java`](../examples/BinaryDataExample.java).

Do not convert arbitrary binary payloads to textual `#...` literals merely to cross the Java API boundary; parameter binding is the normal application path.

## 5. Queries and compilation

### 5.1 `query()`

`query()` executes one KANGER operation. After the invocation, related result stores describe that invocation: logical answer, Solutions, Values, Hypothesis, and Log.

One call accepts one language operation according to the current `IMind` contract.

### 5.2 `compile()`

`compile()` accepts program text containing multiple statements/rules/comments and is the preferred bulk load path.

For KANGER 3.7.0 the supported atomicity rule is: the supplied program is accepted as a whole or rejected as a whole when conflict/error prevents publication. Do not interpret a rejected compile as partially installed program state.

### 5.3 TRUE / FALSE / UNKNOWN

Keep logical result state separate from execution failure:

```text
TRUE / FALSE / null   = logical result
exception / rejection = execution or contract failure
```

`null` means the result is logically undetermined. It is a valid query result.

### 5.4 Atomicity and failures

Flood/error is not a partial logical answer. `compile()` returning `false` is a compile rejection, not a successful partial compile.

Applications should handle returned logical state and thrown execution/lifecycle errors as different categories.

## 6. Results

The result of one query invocation is a coherent family of views.

### 6.1 Solutions

`getSolutions()` — return the solution rules associated with the current query result. Use the interfaces returned by the curated SDK; do not cast into implementation classes.

### 6.2 Values

`getValues()` — expose exported variable bindings as rows of `Map<String, ITerm>`.

The source-level proof is [`../examples/ValuesExample.java`](../examples/ValuesExample.java).

### 6.3 Ordering Values

`getValues(ValuesOrder...)` — create an invocation-local ordered projection for presentation or application processing:

```java
List<Map<String, ITerm>> rows =
        mind.getValues(ValuesOrder.asc("score"));
```

Multiple order keys are supported. `ValuesOrder.asc(...)` and `ValuesOrder.desc(...)` describe presentation order only. Sorting does not change Values membership or canonical result storage.

### 6.4 Hypotheses

`getHypothesis()` — expose hypotheses generated by the last undetermined inference.

Do not down-cast `IHypothesis` to implementation classes. The public hypothesis contract exposes the predicate, arguments, and antecedent/succedent side required for SDK inspection.

### 6.5 Hypothesis optimization

`optimizeHypothesis()` — remove hypotheses that can already be shown false according to the current contract.

Optimization may be computationally significant; request it when the application actually needs the refined hypothesis set.

The qualified UNKNOWN → optimize → public-interface inspection path is [`../examples/HypothesesExample.java`](../examples/HypothesesExample.java).

## 7. Transactions

A child `Mind` is an isolated transaction overlay over its parent:

```java
IMind root = new Mind(user);
IMind child = new Mind(root);
```

Changes made through `child` belong to the child level until the parent settles it.

### 7.1 Child Mind and visibility

- `getNext()` — follow the child-to-parent direction one transaction level;
- `getTop()` — resolve the root Mind of the transaction chain.

The child sees inherited parent state plus its own overlay. New changes belong to the child level.

### 7.2 Commit

`commit(child)` — apply a direct child to its parent:

```java
boolean committed = root.commit(child);
```

Commit performs the supported conflict/deduplication checks and publishes only a completed child delta. A failed commit must not be treated as partially applied child state.

### 7.3 Release

`release(child)` — discard/roll back a direct child:

```java
root.release(child);
```

### 7.4 Ownership and lifetime

After terminal commit/release lifecycle processing, the child `Mind` must not be reused by caller code. Continue from the active parent/root object required by the lifecycle contract.

The executable transaction proof is [`../examples/TransactionExample.java`](../examples/TransactionExample.java).

Exact transaction signatures: [`IMind`](api/org/kanger/interfaces/IMind.html).

### 7.5 Concurrency

KANGER Core contains internal reservation/locking and supports meaningful parallel transaction workflows. That does not make one mutable `IMind` an unrestricted concurrently callable facade.

Developer rule:

```text
parallel transaction branches != arbitrary concurrent mutation of one IMind
```

Serialize caller workflow where query-local result stores, active-Mind transitions, storage lifecycle, or other mutable invocation state are involved.

## 8. Persistence and storage lifecycle

Transaction lifecycle and physical storage lifecycle are separate contracts.

Core defines the lifecycle contracts; a concrete storage provider is an optional feature. The bundled DUMB provider may be attached directly or through `RuntimeBootstrap`.

### 8.1 Storage setup

For file-backed storage, configure the user, source, and database directories before opening storage. Directory strings accepted by the current `IUser` contract end with the platform file separator.

Direct provider attachment:

```java
IUser user = new User();
new org.kanger.storage.DB().init(user);
IMind mind = new Mind(user);
```

Recommended Bootstrap attachment:

```java
IUser user = new User();
RuntimeBootstrap.ensureCapabilities(user, RuntimeCapability.STORAGE);
IMind mind = new Mind(user);
```

`RuntimeBootstrap.ensureCapabilities(...)` discovers and attaches only the explicitly requested optional capabilities.

`RuntimeBootstrap` discovers runtime modules through Java `ServiceLoader`. Absence of an optional capability is a supported classpath state. If exactly one provider exists for a requested capability it can be selected automatically; multiple providers require explicit configuration.

Open or create storage:

```java
mind = user.use(mind, "example");
```

`IUser.use(mind, name)` — open/create named storage and return the active continuation Mind. It is allowed only while user storage is closed and does not silently replace an already open storage.

### 8.2 Durable checkpoint

```java
mind = user.checkpoint(mind);
```

`IUser.checkpoint(mind)` — durably publish root state without closing storage. It is a physical-storage operation and requires transaction level 0.

### 8.3 Close

```java
mind = user.close(mind);
```

`IUser.close(mind)` — perform the qualified root checkpoint/physical close path and return the continuation Mind. Active child transactions are not silently committed or rolled back; lifecycle preconditions are enforced.

For `use`, `checkpoint`, and `close`, continue with the `IMind` returned by the operation. Lifecycle operations may replace the active object as part of their contract.

The complete qualified persistence sequence, including close and reopen, is [`../examples/StorageExample.java`](../examples/StorageExample.java).

Exact lifecycle signatures: [`IUser`](api/org/kanger/interfaces/IUser.html).

### 8.4 Maintenance

The canonical open/checkpoint/close path above belongs to `IUser`. `IMind` also retains explicit maintenance operations for workspace/database maintenance:

- `clearWorkspace()` — clear the complete current workspace and return the active continuation Mind;
- `reindexStorage(String)` — reindex/compact named storage and return the active continuation Mind;
- `reindexStorage(String, IReactor<String>)` — perform the same maintenance operation while reporting progress through a reactor callback;
- `removeStorage(String)` — remove named physical storage and return the active continuation Mind.

These are destructive/maintenance operations, not alternate everyday transaction primitives.

Important lifecycle consequences from the current contract:

- `clearWorkspace()` clears the complete current workspace, including an open physical database; open child transactions are lost;
- `reindexStorage(...)` performs reindex/compaction and may close/reopen the physical database; open child transactions are lost;
- `removeStorage(...)` removes the named physical database and may close it first; open child transactions are lost;
- each method returns an `IMind`; continue with that returned active object.

Do not call these methods while treating child transactions as durable application state.

Exact maintenance signatures: [`IMind`](api/org/kanger/interfaces/IMind.html).

## 9. Diagnostics and explanation

Developer-facing diagnostics include query results, Solutions, Values, Hypotheses, and inference Log.

- `getLog()` — expose inference log entries for the latest operation;
- `getCurrentLogRecord(LogMode)` — expose the current record for the selected log projection;
- `clearLog()` — clear accumulated inference-log data.

Treat these as developer-facing explanation/diagnostic data, not as Core implementation state.

Console `xplain` is a Console presentation feature. Java applications should use the Core result/log APIs rather than invoking Console commands unless the application intentionally adopts the optional command/control feature.

Exact log signatures: [`IMind`](api/org/kanger/interfaces/IMind.html) and [`LogMode`](api/org/kanger/enums/LogMode.html).

## 10. Advanced inspection API

The curated `IMind` surface exposes read-oriented factory views:

- `getTerms()` — return visible terms in the current transaction context;
- `getPredicates()` — return visible predicates in the current transaction context;
- `getRules()` — return visible rules in the current transaction context;
- `getLibrary()` — return the visible operation/library view.

Each returns an `IFactory<T>` view in the current transaction visibility.

These are primarily inspection surfaces. Normal knowledge-state mutation remains `query()`, `compile()`, transactions, and qualified lifecycle APIs. Do not use Java-public compiler/linker/materializer/storage implementation classes as an undocumented mutation API.

Exact inspection signatures: [`IMind`](api/org/kanger/interfaces/IMind.html) and [`IFactory`](api/org/kanger/interfaces/IFactory.html).

## 11. Runtime limits and configuration

Advanced runtime controls exposed by the curated JavaDoc may be used when an embedding application needs them. Keep flood-control and debug/log controls out of the basic startup path unless your application has a concrete requirement.

`IUser` string properties are external user/runtime configuration. Changing them is not a transaction commit or rollback.

`RuntimeBootstrap` is for attaching optional capabilities around a `User`, not for constructing the core `Mind` entry path.

- `RuntimeBootstrap.ensure(user)` — discover and attach all known optional runtime capabilities;
- `RuntimeBootstrap.ensure(user, classLoader)` — do the same using the supplied discovery class loader;
- `RuntimeBootstrap.ensureCapabilities(user, capabilities...)` — discover and attach only the requested capabilities;
- `RuntimeBootstrap.ensureCapabilities(user, classLoader, capabilities...)` — request a narrow capability set using the supplied discovery class loader;
- `RuntimeBootstrapResult.loaded(capability)` — report whether a requested capability was loaded;
- `RuntimeBootstrapResult.getDescription(capability)` — return its bootstrap/provider description.

The Developer distribution currently includes the UDF and DUMB storage providers. Provider implementation APIs are not part of the Developer SDK contract.

Exact bootstrap reference: [`RuntimeBootstrap`](api/org/kanger/bootstrap/RuntimeBootstrap.html), [`RuntimeBootstrapResult`](api/org/kanger/bootstrap/RuntimeBootstrapResult.html), and [`RuntimeCapability`](api/org/kanger/bootstrap/RuntimeCapability.html).

## 12. Maven and Gradle

The bundled Maven-compatible repository contains the individual KANGER artifacts as well as the convenience `kanger-sdk` coordinate. Choose the narrowest assembly that matches the application.

### 12.1 Maven

Convenience runtime SDK:

```xml
<dependency>
    <groupId>org.kanger</groupId>
    <artifactId>kanger-sdk</artifactId>
    <version>3.7.0</version>
</dependency>
```

Core only:

```xml
<dependency>
    <groupId>org.kanger</groupId>
    <artifactId>kanger-core</artifactId>
    <version>3.7.0</version>
</dependency>
```

Canonical command/control feature:

```xml
<dependency>
    <groupId>org.kanger</groupId>
    <artifactId>kanger-command</artifactId>
    <version>3.7.0</version>
</dependency>
```

`kanger-command` brings Core because Command is implemented over Core. Core does not bring Command.

Other selectable feature coordinates are:

```text
org.kanger:kanger-bootstrap:3.7.0
org.kanger:kanger-data-dumb:3.7.0
org.kanger:kanger-udf:3.7.0
```

`kanger-data-dumb` and `kanger-udf` keep Bootstrap integration available, but Bootstrap is optional for consumers. Direct provider attachment therefore does not force `kanger-bootstrap` into the consumer dependency graph.

A complete convenience-SDK consumer project is shipped as `../examples/maven/`. Its repository URL points to the bundle-local `../../repository` directory.

The `kanger-sdk` convenience artifact resolves Core + Bootstrap + bundled UDF + bundled DUMB storage. It intentionally does not resolve `kanger-command`.

### 12.2 Gradle

Convenience runtime SDK:

```groovy
dependencies {
    implementation 'org.kanger:kanger-sdk:3.7.0'
}
```

Core only:

```groovy
dependencies {
    implementation 'org.kanger:kanger-core:3.7.0'
}
```

Command/control feature:

```groovy
dependencies {
    implementation 'org.kanger:kanger-command:3.7.0'
}
```

A complete bundle-local consumer project is shipped as `../examples/gradle/`. It uses only the bundled KANGER repository for KANGER artifacts.

Plain Java/classpath remains the normative first path; Maven and Gradle are adapters over the same SDK artifacts.

## 13. Executable examples

The Developer distribution ships these qualified source examples:

- [`BasicQuery.java`](../examples/BasicQuery.java) — root `User`/`Mind`, compile, tri-state query;
- [`ValuesExample.java`](../examples/ValuesExample.java) — exported Values, `ITerm.getValue()`, ascending/descending `ValuesOrder`;
- [`ParametersExample.java`](../examples/ParametersExample.java) — parameterized `compile()` and `query()` from Java objects;
- [`HypothesesExample.java`](../examples/HypothesesExample.java) — UNKNOWN result, `getHypothesis()`, `optimizeHypothesis()`, public-interface inspection;
- [`TransactionExample.java`](../examples/TransactionExample.java) — child `Mind`, commit, visibility in parent;
- [`StorageExample.java`](../examples/StorageExample.java) — storage bootstrap, use, checkpoint, close, reopen;
- [`BinaryDataExample.java`](../examples/BinaryDataExample.java) — `byte[]`/BLOB Java ↔ KANGER round-trip.

The canonical distribution qualification compiles and executes these exact source files from the built archive on Java 8 and Java 21. Maven and Gradle consumer qualification execute the storage scenario through `org.kanger:kanger-sdk:3.7.0`.

The consumer qualification also verifies the modular boundary: the normal `kanger-sdk` embedding scenario resolves and runs without resolving `kanger-command` transitively.

See [`../examples/README.md`](../examples/README.md) for command lines and expected PASS markers.

## 14. Supported API / compatibility policy

The Developer SDK contract is the curated surface documented here and in [`api/index.html`](api/index.html), not every public implementation symbol contained in the JARs.

Use these status meanings when discussing SDK surface:

- **Stable** — supported Developer contract for 3.7.0.
- **Experimental / feature-level** — intentionally usable for evaluation or custom adapters; compatibility is not promised as Stable.
- **Internal** — implementation detail, outside the SDK contract.
- **Legacy** — retained for compatibility but not the preferred integration path.

For 3.7.0, prefer interfaces and lifecycle paths documented in this guide. Do not build new integrations against `org.kanger.interfaces.internal` or implementation packages that are absent from the curated JavaDoc.

The canonical command/control classes shown in the Assembly model are feature-level API in 3.7.0: usable, documented here, but not promoted into the curated Stable JavaDoc contract.