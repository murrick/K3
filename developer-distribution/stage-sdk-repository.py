#!/usr/bin/env python3
"""Stage canonical 3.7.x Maven metadata and consumer examples into a Developer bundle."""

from pathlib import Path
import hashlib
import shutil
import sys
import zipfile

GROUP_ID = "org.kanger"


def fail(message: str) -> None:
    raise SystemExit("ERROR: " + message)


def pom(artifact_id: str, version: str, name: str, dependencies) -> str:
    deps = ""
    if dependencies:
        rendered = []
        for dependency in dependencies:
            rendered.append(
                "        <dependency>\n"
                f"            <groupId>{GROUP_ID}</groupId>\n"
                f"            <artifactId>{dependency}</artifactId>\n"
                f"            <version>{version}</version>\n"
                "        </dependency>"
            )
        deps = "\n    <dependencies>\n" + "\n".join(rendered) + "\n    </dependencies>\n"

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<project xmlns="http://maven.apache.org/POM/4.0.0"\n'
        '         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n'
        '         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 '
        'https://maven.apache.org/xsd/maven-4.0.0.xsd">\n'
        '    <modelVersion>4.0.0</modelVersion>\n'
        f'    <groupId>{GROUP_ID}</groupId>\n'
        f'    <artifactId>{artifact_id}</artifactId>\n'
        f'    <version>{version}</version>\n'
        '    <packaging>jar</packaging>\n'
        f'    <name>{name}</name>\n'
        f'{deps}'
        '</project>\n'
    )


def artifact_dir(bundle: Path, version: str, artifact_id: str) -> Path:
    destination = bundle / "repository" / "org" / "kanger" / artifact_id / version
    destination.mkdir(parents=True, exist_ok=True)
    return destination


def stage_artifact(bundle: Path, version: str, artifact_id: str, jar_name: str,
                   name: str, dependencies) -> None:
    source = bundle / "lib" / jar_name
    if not source.is_file():
        fail(f"SDK source JAR missing: {source}")

    destination = artifact_dir(bundle, version, artifact_id)
    shutil.copyfile(source, destination / f"{artifact_id}-{version}.jar")
    (destination / f"{artifact_id}-{version}.pom").write_text(
        pom(artifact_id, version, name, dependencies), encoding="utf-8"
    )


def stage_sdk_artifact(bundle: Path, version: str) -> None:
    destination = artifact_dir(bundle, version, "kanger-sdk")
    marker = destination / f"kanger-sdk-{version}.jar"
    manifest = (
        "Manifest-Version: 1.0\r\n"
        "Implementation-Title: KANGER Developer SDK\r\n"
        f"Implementation-Version: {version}\r\n\r\n"
    )
    with zipfile.ZipFile(marker, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        info = zipfile.ZipInfo("META-INF/MANIFEST.MF", date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, manifest.encode("utf-8"))

    (destination / f"kanger-sdk-{version}.pom").write_text(
        pom(
            "kanger-sdk",
            version,
            "KANGER Developer SDK",
            ["kanger-core", "kanger-bootstrap", "kanger-udf", "kanger-data-dumb"],
        ),
        encoding="utf-8",
    )


def write_checksums(repository: Path) -> None:
    for artifact in sorted(list(repository.rglob("*.pom")) + list(repository.rglob("*.jar"))):
        payload = artifact.read_bytes()
        (artifact.with_name(artifact.name + ".sha1")).write_text(
            hashlib.sha1(payload).hexdigest() + "\n", encoding="ascii"
        )
        (artifact.with_name(artifact.name + ".sha256")).write_text(
            hashlib.sha256(payload).hexdigest() + "\n", encoding="ascii"
        )


def stage_consumers(bundle: Path, version: str) -> None:
    source = bundle / "examples" / "StorageExample.java"
    if not source.is_file():
        fail(f"StorageExample missing: {source}")

    maven = bundle / "examples" / "maven"
    maven_source = maven / "src" / "main" / "java"
    maven_source.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, maven_source / "StorageExample.java")
    (maven / "pom.xml").write_text(
        f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>kanger-sdk-maven-example</artifactId>
    <version>1.0.0</version>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <kanger.repository>${{project.basedir}}/../../repository</kanger.repository>
    </properties>

    <repositories>
        <repository>
            <id>kanger-bundled-sdk</id>
            <url>file://${{kanger.repository}}</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.kanger</groupId>
            <artifactId>kanger-sdk</artifactId>
            <version>{version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
            </plugin>
        </plugins>
    </build>
</project>
''',
        encoding="utf-8",
    )

    gradle = bundle / "examples" / "gradle"
    gradle_source = gradle / "src" / "main" / "java"
    gradle_source.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, gradle_source / "StorageExample.java")
    (gradle / "settings.gradle").write_text(
        "rootProject.name = 'kanger-sdk-gradle-example'\n", encoding="utf-8"
    )
    (gradle / "build.gradle").write_text(
        f'''plugins {{
    id 'java'
    id 'application'
}}

repositories {{
    maven {{
        url = uri(findProperty('kangerRepository') ?: file('../../repository'))
    }}
}}

dependencies {{
    implementation 'org.kanger:kanger-sdk:{version}'
}}

java {{
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

application {{
    mainClass = 'StorageExample'
}}
''',
        encoding="utf-8",
    )


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: stage-sdk-repository.py <bundle-dir> <version>")

    bundle = Path(sys.argv[1]).resolve()
    version = sys.argv[2].strip()
    if not bundle.is_dir():
        fail(f"Bundle directory not found: {bundle}")
    if not version:
        fail("Version must not be empty")

    artifacts = [
        ("kanger-command", "kanger-command.jar", "KANGER canonical command language", []),
        ("kanger-core", "kanger-core.jar", "KANGER shared semantic core", ["kanger-command"]),
        ("kanger-bootstrap", "kanger-bootstrap.jar", "KANGER runtime bootstrap", ["kanger-core"]),
        ("kanger-udf", "kanger-udf.jar", "KANGER UDF plugin", ["kanger-core", "kanger-bootstrap"]),
        ("kanger-data-dumb", "kanger-data-dumb.jar", "KANGER DUMB storage plugin", ["kanger-core", "kanger-bootstrap"]),
    ]

    for artifact in artifacts:
        stage_artifact(bundle, version, *artifact)
    stage_sdk_artifact(bundle, version)
    stage_consumers(bundle, version)

    repository = bundle / "repository"
    forbidden = ("3.3-SNAPSHOT", "kanger-server", "org.jline")
    for path in repository.rglob("*.pom"):
        text = path.read_text(encoding="utf-8")
        for token in forbidden:
            if token in text:
                fail(f"Forbidden token {token!r} leaked into canonical SDK POM {path}")

    pom_count = len(list(repository.rglob("*.pom")))
    jar_count = len(list(repository.rglob("*.jar")))
    if pom_count != 6 or jar_count != 6:
        fail(f"Unexpected SDK repository shape: {pom_count} POMs, {jar_count} JARs")

    write_checksums(repository)
    checksum_count = len(list(repository.rglob("*.sha1"))) + len(list(repository.rglob("*.sha256")))
    if checksum_count != 24:
        fail(f"Unexpected SDK checksum count: {checksum_count}")

    print(
        f"SDK_REPOSITORY_STAGE_PASS version={version} "
        f"poms={pom_count} jars={jar_count} checksums={checksum_count}"
    )


if __name__ == "__main__":
    main()
