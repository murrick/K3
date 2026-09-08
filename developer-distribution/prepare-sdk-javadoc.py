#!/usr/bin/env python3
"""Prepare a documentation-only source view for the published KANGER SDK.

The runtime sources remain untouched. Concrete User/Mind implementation
members are marked deprecated in this temporary view and JavaDoc is generated
with -nodeprecated, so the class pages expose constructors plus the contracts
inherited from IUser/IMind without publishing implementation-only public
members that exist for Core compatibility.
"""

from pathlib import Path
import shutil
import sys


USER_DOC = """/**
 * Concrete standalone implementation of {@link org.kanger.interfaces.IUser}.
 *
 * <p>Create a standalone KANGER owner with {@code new User()}, then create the
 * root logical context with {@link Mind#Mind(org.kanger.interfaces.IUser)}.
 * Developer-facing lifecycle and configuration contracts are defined by
 * {@code IUser}; implementation-only compatibility members are intentionally
 * omitted from the published SDK reference.</p>
 */"""

MIND_DOC = """/**
 * Concrete standalone implementation of {@link org.kanger.interfaces.IMind}.
 *
 * <p>Create a root context with
 * {@link #Mind(org.kanger.interfaces.IUser)}. Create an explicit child
 * transaction with {@link #Mind(org.kanger.interfaces.IMind)} and settle it
 * through the parent {@code IMind}. Developer-facing operational contracts are
 * defined by {@code IMind}; implementation-only compatibility members are
 * intentionally omitted from the published SDK reference.</p>
 */"""


def fail(message: str) -> None:
    raise SystemExit("ERROR: " + message)


def replace_class_doc(text: str, marker: str, replacement: str) -> str:
    pos = text.find(marker)
    if pos < 0:
        fail("class declaration not found: " + marker)

    doc_start = text.rfind("/**", 0, pos)
    doc_end = text.rfind("*/", 0, pos)
    if doc_start < 0 or doc_end < doc_start:
        fail("class JavaDoc not found before: " + marker)

    return text[:doc_start] + replacement + text[doc_end + 2 :]


def hide_concrete_members(text: str, class_name: str) -> str:
    class_marker = "public class " + class_name
    constructor_marker = "public " + class_name + "("
    lines = text.splitlines(True)
    out = []

    for line in lines:
        stripped = line.lstrip()
        if stripped.startswith("public "):
            keep = stripped.startswith(class_marker) or stripped.startswith(constructor_marker)
            if not keep:
                indent = line[: len(line) - len(stripped)]
                previous = out[-1].strip() if out else ""
                if previous != "@Deprecated":
                    out.append(indent + "@Deprecated\n")
        out.append(line)

    return "".join(out)


def prepare_concrete(source: Path, destination: Path, class_name: str) -> None:
    text = source.read_text(encoding="utf-8")
    if class_name == "User":
        text = replace_class_doc(text, "public class User implements IUser", USER_DOC)
    elif class_name == "Mind":
        text = replace_class_doc(text, "public class Mind implements IMind", MIND_DOC)
    else:
        fail("unsupported concrete SDK class: " + class_name)

    text = hide_concrete_members(text, class_name)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(text, encoding="utf-8")


def source_relative_path(source: Path, repo_root: Path) -> Path:
    try:
        repository_path = source.resolve().relative_to(repo_root)
    except ValueError:
        fail("SDK JavaDoc source is outside repository: " + str(source))

    parts = repository_path.parts
    try:
        src_index = parts.index("src")
    except ValueError:
        fail("SDK JavaDoc source path has no src component: " + str(repository_path))

    relative = Path(*parts[src_index + 1 :])
    if not relative.parts or relative.parts[0] != "org":
        fail("unexpected Java package path: " + str(repository_path))
    return relative


def main() -> None:
    if len(sys.argv) != 4:
        fail("usage: prepare-sdk-javadoc.py REPO_ROOT MANIFEST TARGET_DIR")

    repo_root = Path(sys.argv[1]).resolve()
    manifest = Path(sys.argv[2]).resolve()
    target = Path(sys.argv[3]).resolve()

    if not repo_root.is_dir():
        fail("repository root not found: " + str(repo_root))
    if not manifest.is_file():
        fail("SDK JavaDoc manifest not found: " + str(manifest))

    if target.exists():
        shutil.rmtree(str(target))
    target.mkdir(parents=True)

    entries = []
    for raw in manifest.read_text(encoding="utf-8").splitlines():
        value = raw.strip()
        if not value or value.startswith("#"):
            continue
        entries.append(value)

    if not entries:
        fail("SDK JavaDoc manifest is empty")
    if len(entries) != len(set(entries)):
        fail("SDK JavaDoc manifest contains duplicate paths")

    for entry in entries:
        if "interfaces/internal" in entry:
            fail("internal interface explicitly present in SDK manifest: " + entry)

        source = repo_root / entry
        if not source.is_file():
            fail("SDK JavaDoc source not found: " + entry)

        relative = source_relative_path(source, repo_root)
        destination = target / relative

        if source.name == "User.java":
            prepare_concrete(source, destination, "User")
        elif source.name == "Mind.java":
            prepare_concrete(source, destination, "Mind")
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(str(source), str(destination))

    produced = sorted(target.rglob("*.java"))
    if len(produced) != len(entries):
        fail(
            "SDK JavaDoc source count mismatch: expected %d, produced %d"
            % (len(entries), len(produced))
        )

    print("SDK_JAVADOC_SOURCE_PASS count=%d" % len(produced))


if __name__ == "__main__":
    main()
