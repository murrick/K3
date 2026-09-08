#!/usr/bin/env python3
"""Prepare and verify the documentation-only source view for the KANGER SDK.

The runtime sources remain untouched. The temporary publication view serves two
purposes:

* concrete User/Mind implementation members are hidden from public JavaDoc;
* legacy non-English JavaDoc is replaced with concise English SDK summaries.

The script can also verify the generated JavaDoc tree with ``--verify-output``.
"""

from pathlib import Path
import re
import shutil
import sys


CYRILLIC_RE = re.compile(r"[\u0400-\u04FF]")
JAVADOC_RE = re.compile(r"/\*\*.*?\*/", re.DOTALL)

TYPE_DOCS = {
    "IArgument": "Argument abstraction used by predicates, rules, and hypotheses.",
    "ICause": "Cause relation exposed by the SDK inspection API.",
    "IFactory": "Iterable SDK collection/factory abstraction returned by inspection APIs.",
    "IHypothesis": "Candidate assertion produced when a query cannot yet be decided.",
    "IList": "List abstraction exposed by the Developer SDK.",
    "ILogEntry": "Execution log record exposed by Mind inspection.",
    "IMind": "KANGER execution context for compilation, queries, values, transactions, and storage maintenance.",
    "IOperation": "Operation or function abstraction exposed by the Developer SDK.",
    "IPredicate": "Predicate abstraction exposed by the Developer SDK.",
    "IReactor": "Progress/reactor callback abstraction exposed by long-running SDK operations.",
    "IRule": "Rule abstraction exposed by the Developer SDK.",
    "ITerm": "Typed KANGER term exposed through values, predicates, rules, and hypotheses.",
    "IUser": "User context and canonical storage-lifecycle boundary for embedded KANGER applications.",
    "User": "Concrete standalone implementation of the IUser developer contract.",
    "Mind": "Concrete standalone implementation of the IMind developer contract.",
    "ValuesOrder": "Ordering requested when reading query Values rows.",
    "ArgumentType": "Kinds of arguments used by KANGER predicates and rules.",
    "DataType": "Public KANGER term data types.",
    "LibMode": "Library mode used by the public operation/library contract.",
    "LogMode": "Inference-log projection mode.",
    "StorageLifecycleErrorCode": "Stable structured error code for storage-lifecycle failures.",
    "RuntimeErrorException": "Developer-facing exception for KANGER runtime failures.",
    "StorageLifecycleException": "Storage-lifecycle exception carrying a structured error code.",
    "RuntimeBootstrap": "Entry point for discovering and attaching optional runtime capabilities.",
    "RuntimeBootstrapResult": "Result of optional runtime-capability discovery and attachment.",
    "RuntimeCapability": "Optional runtime capability that an embedded application may request.",
}

MEMBER_DOCS = {
    "query": "Execute one KANGER operation and return TRUE, FALSE, or null when the result is undetermined.",
    "compile": "Atomically compile KANGER source into the current Mind.",
    "getValues": "Return exported query-value rows, optionally using invocation-local ordering.",
    "getSolutions": "Return solutions produced by the latest query.",
    "getHypothesis": "Return hypotheses produced by the latest undetermined query.",
    "optimizeHypothesis": "Remove hypotheses that can already be shown false in the current context.",
    "getLog": "Return inference-log entries for the latest operation.",
    "getCurrentLogRecord": "Return the current log record for the requested log projection.",
    "clearLog": "Clear accumulated inference-log data.",
    "getTerms": "Return the visible term inspection view.",
    "getPredicates": "Return the visible predicate inspection view.",
    "getRules": "Return the visible rule inspection view.",
    "getLibrary": "Return the visible operation/library inspection view.",
    "commit": "Commit a direct child transaction into its parent Mind.",
    "release": "Release or roll back a direct child transaction.",
    "getNext": "Return the parent Mind of this transaction level.",
    "getTop": "Return the root Mind of the current transaction chain.",
    "use": "Open or create named storage and return the active continuation Mind.",
    "checkpoint": "Durably checkpoint root storage state and return the active continuation Mind.",
    "close": "Checkpoint and close active storage, returning the continuation Mind.",
    "clearWorkspace": "Clear the current workspace and return the active continuation Mind.",
    "reindexStorage": "Reindex or compact named storage and return the active continuation Mind.",
    "removeStorage": "Remove named storage and return the active continuation Mind.",
    "getType": "Return the public KANGER data type of this term.",
    "getId": "Return this object's runtime or canonical identifier as defined by its contract.",
    "getValue": "Project this term into its public Java value representation.",
    "isEmpty": "Report whether this value is empty in the supplied Mind context.",
    "isCVariable": "Report whether this term is a C-variable.",
    "equalsTo": "Compare this term with another term using KANGER term equality.",
    "isDeleted": "Report whether this term is deleted in the supplied Mind context.",
    "ensure": "Discover and attach all known optional runtime capabilities for the user.",
    "ensureCapabilities": "Discover and attach only the requested optional runtime capabilities.",
    "loaded": "Report whether the requested runtime capability was loaded.",
    "getDescription": "Return the bootstrap description for the requested runtime capability.",
    "asc": "Create an ascending Values ordering key.",
    "desc": "Create a descending Values ordering key.",
}

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


def english_doc(summary: str) -> str:
    return "/**\n * " + summary + "\n */"


def declaration_after(text: str, end: int) -> str:
    tail = text[end : end + 900]
    stop = len(tail)
    for token in (";", "{"):
        pos = tail.find(token)
        if pos >= 0:
            stop = min(stop, pos + 1)
    return re.sub(r"\s+", " ", tail[:stop]).strip()


def summary_for_declaration(declaration: str) -> str:
    type_match = re.search(r"\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)", declaration)
    if type_match:
        name = type_match.group(1)
        return TYPE_DOCS.get(name, "Developer SDK type %s." % name)

    method_matches = re.findall(r"\b([A-Za-z_$][\w$]*)\s*\(", declaration)
    if method_matches:
        ignored = {"if", "for", "while", "switch", "catch", "new", "return"}
        for name in reversed(method_matches):
            if name not in ignored:
                return MEMBER_DOCS.get(name, "Developer SDK operation %s()." % name)

    constant_match = re.search(r"\b([A-Z][A-Z0-9_]*)\b", declaration)
    if constant_match:
        return "Developer SDK constant %s." % constant_match.group(1)

    return "Developer SDK contract element."


def replace_non_english_javadocs(text: str) -> str:
    out = []
    cursor = 0
    for match in JAVADOC_RE.finditer(text):
        out.append(text[cursor : match.start()])
        block = match.group(0)
        if CYRILLIC_RE.search(block):
            block = english_doc(summary_for_declaration(declaration_after(text, match.end())))
        out.append(block)
        cursor = match.end()
    out.append(text[cursor:])
    result = "".join(out)

    leaking = [block.group(0) for block in JAVADOC_RE.finditer(result) if CYRILLIC_RE.search(block.group(0))]
    if leaking:
        fail("Cyrillic text remains in prepared JavaDoc source")
    return result


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


def prepare_source(source: Path, destination: Path) -> None:
    text = source.read_text(encoding="utf-8")
    if source.name == "User.java":
        text = replace_class_doc(text, "public class User implements IUser", USER_DOC)
        text = hide_concrete_members(text, "User")
    elif source.name == "Mind.java":
        text = replace_class_doc(text, "public class Mind implements IMind", MIND_DOC)
        text = hide_concrete_members(text, "Mind")

    text = replace_non_english_javadocs(text)
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


def verify_generated_output(target: Path) -> None:
    if not target.is_dir():
        fail("generated JavaDoc directory not found: " + str(target))

    checked = 0
    leaking = []
    for path in sorted(target.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".html", ".js", ".css", ".txt"}:
            continue
        checked += 1
        text = path.read_text(encoding="utf-8", errors="replace")
        if CYRILLIC_RE.search(text):
            leaking.append(str(path.relative_to(target)))

    if checked == 0:
        fail("generated JavaDoc contains no text files to verify")
    if leaking:
        fail("Cyrillic text leaked into generated JavaDoc: " + ", ".join(leaking[:10]))

    print("SDK_JAVADOC_ENGLISH_PASS files=%d" % checked)


def prepare(repo_root: Path, manifest: Path, target: Path) -> None:
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
        prepare_source(source, target / relative)

    produced = sorted(target.rglob("*.java"))
    if len(produced) != len(entries):
        fail(
            "SDK JavaDoc source count mismatch: expected %d, produced %d"
            % (len(entries), len(produced))
        )

    print("SDK_JAVADOC_SOURCE_PASS count=%d english-only" % len(produced))


def main() -> None:
    if len(sys.argv) == 3 and sys.argv[1] == "--verify-output":
        verify_generated_output(Path(sys.argv[2]).resolve())
        return

    if len(sys.argv) != 4:
        fail("usage: prepare-sdk-javadoc.py REPO_ROOT MANIFEST TARGET_DIR | --verify-output JAVADOC_DIR")

    prepare(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), Path(sys.argv[3]).resolve())


if __name__ == "__main__":
    main()
