#!/usr/bin/env python3
"""Inventory public top-level Java types and classify their class Javadoc."""

from __future__ import print_function

import os
import re
import sys

ROOTS = (
    "kanger/src",
    "kanger-udf/src",
    "kanger-data-dumb/src",
    "kanger-console/src",
)

TYPE_PATTERN = re.compile(
    r"(?m)^public\s+(?:(?:abstract|final|strictfp)\s+)*"
    r"(class|interface|enum)\s+([A-Za-z_$][A-Za-z0-9_$]*)\b"
)
JAVADOC_PATTERN = re.compile(r"/\*\*(.*?)\*/\s*$", re.DOTALL)
TAG_PATTERN = re.compile(r"\{@(?:link|code|literal)\s+([^}]+)\}")
HTML_PATTERN = re.compile(r"<[^>]+>")
SPACE_PATTERN = re.compile(r"\s+")


def iter_java_files():
    for root in ROOTS:
        if not os.path.isdir(root):
            continue
        for directory, _, files in os.walk(root):
            for name in sorted(files):
                if name.endswith(".java"):
                    yield os.path.join(directory, name)


def normalize_javadoc(raw):
    lines = []
    for line in raw.splitlines():
        line = re.sub(r"^\s*\* ?", "", line)
        lines.append(line)
    text = "\n".join(lines)
    text = TAG_PATTERN.sub(r"\1", text)
    text = HTML_PATTERN.sub(" ", text)
    text = re.sub(r"(?m)^\s*@(?:see|since|author|version|param|return|throws)\b.*$", "", text)
    return SPACE_PATTERN.sub(" ", text).strip()


def classify(documentation):
    if documentation is None:
        return "missing"
    normalized = normalize_javadoc(documentation)
    if "Created by" in normalized:
        return "placeholder"
    if len(normalized) < 160:
        return "brief"
    return "documented"


def main():
    rows = []
    counts = {"missing": 0, "placeholder": 0, "brief": 0, "documented": 0}
    for path in iter_java_files():
        with open(path, "r", encoding="utf-8") as source_file:
            source = source_file.read()
        declaration = TYPE_PATTERN.search(source)
        if declaration is None:
            continue
        prefix = source[:declaration.start()]
        match = JAVADOC_PATTERN.search(prefix)
        documentation = match.group(1) if match is not None else None
        status = classify(documentation)
        counts[status] += 1
        rows.append((status, declaration.group(1), declaration.group(2), path))

    print("status\tkind\ttype\tpath")
    for row in sorted(rows, key=lambda item: (item[0], item[3])):
        print("\t".join(row))
    print("SUMMARY\tmissing={missing}\tplaceholder={placeholder}\tbrief={brief}\tdocumented={documented}".format(**counts))

    return 0


if __name__ == "__main__":
    sys.exit(main())
