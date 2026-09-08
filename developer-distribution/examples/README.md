# KANGER 3.7.0 Developer Examples

These examples are part of the Developer Distribution contract, not illustrative snippets copied only into documentation. The canonical distribution qualification compiles and executes the shipped Java source files directly from the built archive.

Plain Java/classpath is the normative first path. Maven and Gradle consumers use the same SDK artifacts through the bundle-local `repository/` directory.

## Plain Java / classpath

From the unpacked distribution root on POSIX/macOS/Linux:

```sh
mkdir -p out
javac -cp "lib/*" -d out \
  examples/BasicQuery.java \
  examples/TransactionExample.java \
  examples/StorageExample.java

java -cp "out:lib/*" BasicQuery
java -cp "out:lib/*" TransactionExample
java -cp "out:lib/*" StorageExample
```

On Windows use `;` as the runtime classpath separator:

```bat
mkdir out
javac -cp "lib/*" -d out examples\BasicQuery.java examples\TransactionExample.java examples\StorageExample.java

java -cp "out;lib/*" BasicQuery
java -cp "out;lib/*" TransactionExample
java -cp "out;lib/*" StorageExample
```

Successful runs print:

```text
BASIC_QUERY_PASS
TRANSACTION_PASS
STORAGE_PASS
```

## BasicQuery.java

`BasicQuery.java` is the minimum standalone embedded-Core example. It demonstrates the canonical entry path:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

It then loads several facts with `compile(String)` and performs a tri-state query with `query(String)`.

## TransactionExample.java

`TransactionExample.java` creates a child transaction overlay with:

```java
IMind transaction = new Mind(root);
```

The parent settles the child with `root.commit(transaction)`. The example then verifies that the committed fact is visible from the parent.

Do not reuse a child `Mind` after terminal commit/release settlement.

## StorageExample.java

`StorageExample.java` exercises the canonical 3.7.0 persistence path:

1. configure temporary user/source/database directories;
2. attach `RuntimeCapability.STORAGE` with `RuntimeBootstrap`;
3. open/create storage through `IUser.use()`;
4. compile data;
5. publish a durable `IUser.checkpoint()` without closing;
6. close through `IUser.close()`;
7. reopen and verify persistence;
8. close and remove the temporary files.

The example always continues with the `IMind` returned by storage lifecycle operations.

## Maven consumer

The built Developer Distribution contains a complete Maven project in `examples/maven/`. Its KANGER dependency is:

```text
org.kanger:kanger-sdk:3.7.0
```

and its KANGER repository points to the bundle-local `../../repository` directory.

From `examples/maven/`:

```sh
mvn --batch-mode compile
```

builds the consumer against the bundled KANGER 3.7.0 repository. The canonical CI consumer qualification additionally verifies that the resolved KANGER JAR/POM payloads match the files shipped in that repository and executes the storage scenario.

Maven itself may obtain build plugins from the user's normal Maven plugin sources/cache; KANGER SDK dependencies are resolved from the bundle-local repository.

## Gradle consumer

The built Developer Distribution also contains `examples/gradle/`, using the same coordinate and bundle-local KANGER repository.

From `examples/gradle/` with Gradle installed:

```sh
gradle run
```

compiles and runs `StorageExample`. The project does not require an external repository for KANGER artifacts.

## Adding examples

A new example becomes normative Developer documentation only after it is added as source, shipped in the canonical archive, and exercised by distribution qualification. Historical API examples are semantic source material, but their old class names, storage paths, and signatures are not automatically 3.7.0 contract.

Current qualified families are Basic Query, Transactions, and Storage. Values, parameter binding, hypotheses, and binary-data examples should be added as separate executable qualification increments rather than as untested listings in `SDK.md`.
