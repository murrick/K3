# Building KANGER Server

Requirements:

- JDK 8 or JDK 21;
- Apache Maven 3.9 or later;
- a Git checkout, so build metadata can identify the source branch.

Build from the repository root:

```bash
mvn -B -ntp -f kanger-server/pom.xml clean verify
```

For detached or exported sources, provide the branch explicitly:

```bash
mvn -B -ntp -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=server/stabilization-from-3.5.0.7 \
  clean verify
```

Output:

```text
kanger-server/target/kanger-server.jar
```
