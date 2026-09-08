# KANGER 3.7.0 Developer Examples

These examples are part of the Developer Distribution contract, not illustrative snippets copied only into documentation. The canonical distribution qualification compiles and executes the shipped Java source files directly from the built archive.

Plain Java/classpath is the normative first path. Maven and Gradle consumers use the same SDK artifacts through the bundle-local `repository/` directory.

## Plain Java / classpath

From the unpacked distribution root on POSIX/macOS/Linux:

```sh
mkdir -p out
javac -cp "lib/*" -d out \
  examples/BasicQuery.java \
  examples/ValuesExample.java \
  examples/ParametersExample.java \
  examples/HypothesesExample.java \
  examples/TransactionExample.java \
  examples/StorageExample.java \
  examples/BinaryDataExample.java

java -cp "out:lib/*" BasicQuery
java -cp "out:lib/*" ValuesExample
java -cp "out:lib/*" ParametersExample
java -cp "out:lib/*" HypothesesExample
java -cp "out:lib/*" TransactionExample
java -cp "out:lib/*" StorageExample
java -cp "out:lib/*" BinaryDataExample
```

On Windows use `;` as the runtime classpath separator:

```bat
mkdir out
javac -cp "lib/*" -d out examples\BasicQuery.java examples\ValuesExample.java examples\ParametersExample.java examples\HypothesesExample.java examples\TransactionExample.java examples\StorageExample.java examples\BinaryDataExample.java

java -cp "out;lib/*" BasicQuery
java -cp "out;lib/*" ValuesExample
java -cp "out;lib/*" ParametersExample
java -cp "out;lib/*" HypothesesExample
java -cp "out;lib/*" TransactionExample
java -cp "out;lib/*" StorageExample
java -cp "out;lib/*" BinaryDataExample
```

Successful runs print these markers:

```text
BASIC_QUERY_PASS
VALUES_PASS rows=3
PARAMETERS_PASS
HYPOTHESES_PASS count=<n>
TRANSACTION_PASS
STORAGE_PASS
BINARY_DATA_PASS bytes=4
```

The exact hypothesis count is an inference result and is not an API compatibility constant; the example verifies the public hypothesis contract rather than freezing an implementation row count.

## BasicQuery.java

`BasicQuery.java` is the minimum standalone embedded-Core example. It demonstrates the canonical entry path:

```java
IUser user = new User();
IMind mind = new Mind(user);
```

It then loads several facts with `compile(String)` and performs a tri-state query with `query(String)`.

## ValuesExample.java

`ValuesExample.java` demonstrates exported query values through `getValues(ValuesOrder...)`.

It verifies:

- query variables are exposed as `Map<String, ITerm>` rows;
- Java values are obtained through `ITerm.getValue()`;
- `ValuesOrder.asc(...)` and `ValuesOrder.desc(...)` change presentation order without changing result membership.

## ParametersExample.java

`ParametersExample.java` demonstrates Java object binding through:

```text
compile(String, Object[])
query(String, Object[])
```

Parameters are substituted in source order. Use this API instead of constructing KANGER statements by concatenating application values into source text.

## HypothesesExample.java

`HypothesesExample.java` demonstrates the logical UNKNOWN path:

1. `query()` returns `null`;
2. `optimizeHypothesis()` refines the hypothesis set;
3. the application inspects hypotheses only through `IHypothesis` and related public interfaces.

The example deliberately does not cast to KANGER implementation classes.

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

## BinaryDataExample.java

`BinaryDataExample.java` demonstrates the general Java/KANGER value-mapping path for binary payloads:

1. pass a Java `byte[]` through parameterized `compile()`;
2. verify the same `byte[]` through parameterized `query()`;
3. project the value through a query variable;
4. verify `DataType.BLOB`;
5. recover the Java `byte[]` through `ITerm.getValue()` and compare its bytes.

No text/base64 conversion is required at the Java API boundary.

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

## Qualification rule

A source example is normative Developer documentation only when it is:

1. present as a real source file in this directory;
2. copied into the canonical archive;
3. compiled from that archive;
4. executed from that archive on the supported qualification JDKs.

Historical API examples remain semantic source material, but old class names, storage paths, signatures, or assumptions are not automatically KANGER 3.7.0 contract.
