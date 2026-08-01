#!/usr/bin/env python3
"""Inventory caller-visible KANGER methods and classify their Javadoc contracts."""

from __future__ import print_function

import os
import re
import sys

ROOTS = (
    "kanger/src",
    "kanger-data-dumb/src",
    "kanger-console/src",
)
EXPLICIT_FILES = (
    "kanger-udf/src/org/kanger/udf/UDF.java",
)
EXCLUDED_PATH_PARTS = (
    "/org/mozilla/",
    "/test/",
)
EXCLUDED_BASENAME = re.compile(
    r"(?:Runner|Diagnostic|Qualification|Safety|Profile|Performance|Telemetry)\w*\.java$"
)
TYPE_PATTERN = re.compile(
    r"(?m)^public\s+(?:(?:abstract|final|strictfp)\s+)*"
    r"(class|interface|enum)\s+([A-Za-z_$][A-Za-z0-9_$]*)\b"
)
TAG_PATTERN = re.compile(r"\{@(?:link|code|literal)\s+([^}]+)\}")
HTML_PATTERN = re.compile(r"<[^>]+>")
SPACE_PATTERN = re.compile(r"\s+")
METHOD_NAME_PATTERN = re.compile(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(")
CONTROL_WORDS = {
    "if", "for", "while", "switch", "catch", "synchronized",
    "return", "throw", "new", "assert", "do", "try",
}


def iter_java_files():
    seen = set()
    for root in ROOTS:
        if not os.path.isdir(root):
            continue
        for directory, _, files in os.walk(root):
            for name in sorted(files):
                if not name.endswith(".java"):
                    continue
                path = os.path.join(directory, name)
                normalized = path.replace("\\", "/")
                if any(part in normalized for part in EXCLUDED_PATH_PARTS):
                    continue
                if EXCLUDED_BASENAME.search(name):
                    continue
                seen.add(path)
                yield path
    for path in EXPLICIT_FILES:
        if os.path.isfile(path) and path not in seen:
            yield path


def sanitize_for_braces(source):
    """Replace comments and literals with spaces while preserving newlines."""
    chars = list(source)
    i = 0
    state = "normal"
    while i < len(chars):
        c = chars[i]
        n = chars[i + 1] if i + 1 < len(chars) else ""
        if state == "normal":
            if c == "/" and n == "/":
                chars[i] = chars[i + 1] = " "
                i += 2
                state = "line"
                continue
            if c == "/" and n == "*":
                chars[i] = chars[i + 1] = " "
                i += 2
                state = "block"
                continue
            if c == '"':
                chars[i] = " "
                i += 1
                state = "string"
                continue
            if c == "'":
                chars[i] = " "
                i += 1
                state = "char"
                continue
        elif state == "line":
            if c == "\n":
                state = "normal"
            else:
                chars[i] = " "
        elif state == "block":
            if c == "*" and n == "/":
                chars[i] = chars[i + 1] = " "
                i += 2
                state = "normal"
                continue
            if c != "\n":
                chars[i] = " "
        elif state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if c == "\\":
                chars[i] = " "
                if i + 1 < len(chars):
                    if chars[i + 1] != "\n":
                        chars[i + 1] = " "
                    i += 2
                    continue
            if c == quote:
                chars[i] = " "
                state = "normal"
            elif c != "\n":
                chars[i] = " "
        i += 1
    return "".join(chars)


def line_depths(sanitized):
    depths = []
    depth = 0
    for line in sanitized.splitlines(True):
        depths.append(depth)
        for c in line:
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
    return depths


def normalize_javadoc(raw):
    if raw is None:
        return ""
    lines = []
    for line in raw.splitlines():
        line = re.sub(r"^\s*/?\*+ ?", "", line)
        line = re.sub(r"\s*\*/\s*$", "", line)
        lines.append(line)
    text = "\n".join(lines)
    text = TAG_PATTERN.sub(r"\1", text)
    text = HTML_PATTERN.sub(" ", text)
    return SPACE_PATTERN.sub(" ", text).strip()


def previous_javadoc(lines, start_index):
    i = start_index - 1
    annotations = []
    while i >= 0 and not lines[i].strip():
        i -= 1
    while i >= 0 and lines[i].lstrip().startswith("@"):
        annotations.append(lines[i].strip())
        i -= 1
        while i >= 0 and not lines[i].strip():
            i -= 1
    inherited = any(annotation.startswith("@Override") for annotation in annotations)
    if i < 0 or "*/" not in lines[i]:
        return None, inherited
    end = i
    while i >= 0 and "/**" not in lines[i]:
        i -= 1
    if i < 0:
        return None, inherited
    return "\n".join(lines[i:end + 1]), inherited


def declaration_candidate(stripped, kind):
    if not stripped or stripped.startswith(("//", "/*", "*", "@")):
        return False
    if "(" not in stripped:
        return False
    lowered = stripped.split("(", 1)[0].strip()
    name_match = METHOD_NAME_PATTERN.search(stripped)
    if name_match and name_match.group(1) in CONTROL_WORDS:
        return False
    if re.search(r"\b(class|interface|enum)\b", lowered):
        return False
    if kind == "interface":
        return not stripped.startswith("private ")
    return bool(re.match(r"^(?:public|protected)\b", stripped))


def collect_members(source, kind):
    lines = source.splitlines()
    sanitized = sanitize_for_braces(source)
    sanitized_lines = sanitized.splitlines()
    depths = line_depths(sanitized)
    members = []
    i = 0
    while i < len(lines):
        if i >= len(depths) or depths[i] != 1:
            i += 1
            continue
        stripped = sanitized_lines[i].strip() if i < len(sanitized_lines) else ""
        if not declaration_candidate(stripped, kind):
            i += 1
            continue
        start = i
        signature_lines = [lines[i].strip()]
        paren_depth = stripped.count("(") - stripped.count(")")
        terminated = (";" in stripped or "{" in stripped) and paren_depth <= 0
        while not terminated and i + 1 < len(lines):
            i += 1
            piece = sanitized_lines[i].strip()
            signature_lines.append(lines[i].strip())
            paren_depth += piece.count("(") - piece.count(")")
            terminated = (";" in piece or "{" in piece) and paren_depth <= 0
        signature = SPACE_PATTERN.sub(" ", " ".join(signature_lines)).strip()
        signature = signature.split("{", 1)[0].split(";", 1)[0].strip()
        name_match = METHOD_NAME_PATTERN.search(signature)
        if name_match is None or name_match.group(1) in CONTROL_WORDS:
            i += 1
            continue
        doc, inherited = previous_javadoc(lines, start)
        members.append((start + 1, name_match.group(1), signature, doc, inherited))
        i += 1
    return members


def classify(type_name, member_name, signature, documentation, inherited):
    if documentation is None:
        return "inherited" if inherited else "missing"
    normalized = normalize_javadoc(documentation)
    if "Created by" in normalized:
        return "placeholder"
    if len(normalized) < 80:
        return "brief"
    if "(" in signature and ")" in signature:
        parameters = signature.split("(", 1)[1].rsplit(")", 1)[0].strip()
        if parameters and "@param" not in documentation:
            return "incomplete"
    prefix = signature.split("(", 1)[0]
    is_constructor = member_name == type_name
    is_void = bool(re.search(r"\bvoid\s+" + re.escape(member_name) + r"\s*$", prefix))
    if not is_constructor and not is_void and "@return" not in documentation:
        return "incomplete"
    if "throws" in signature and "@throws" not in documentation:
        return "incomplete"
    return "documented"


def main():
    rows = []
    counts = {
        "missing": 0,
        "placeholder": 0,
        "brief": 0,
        "incomplete": 0,
        "inherited": 0,
        "documented": 0,
    }
    for path in iter_java_files():
        with open(path, "r", encoding="utf-8") as source_file:
            source = source_file.read()
        type_match = TYPE_PATTERN.search(source)
        if type_match is None:
            continue
        kind = type_match.group(1)
        type_name = type_match.group(2)
        for line, member_name, signature, documentation, inherited in collect_members(source, kind):
            status = classify(type_name, member_name, signature, documentation, inherited)
            counts[status] += 1
            rows.append((status, type_name, member_name, str(line), path, signature))

    print("status\ttype\tmember\tline\tpath\tsignature")
    for row in sorted(rows, key=lambda item: (item[0], item[4], int(item[3]))):
        print("\t".join(row))
    print(
        "SUMMARY\tmissing={missing}\tplaceholder={placeholder}\tbrief={brief}"
        "\tincomplete={incomplete}\tinherited={inherited}\tdocumented={documented}".format(**counts)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
