#!/usr/bin/env python3

import collections
import re
from pathlib import Path

SOURCE = Path("kanger-perf-execution-samples.txt")
TARGET = Path("kanger-perf-hotspots.txt")

text = SOURCE.read_text(encoding="utf-8")
blocks = text.split("jdk.ExecutionSample {")[1:]
leaf = collections.Counter()
kanger = collections.Counter()

for block in blocks:
    match = re.search(r"stackTrace = \[\s*([^\n]+)", block)
    if match:
        leaf[match.group(1).strip()] += 1

    frames = re.findall(r"^\s+([^\s].*?)$", block, re.MULTILINE)
    for frame in frames:
        if frame.startswith("org.kanger."):
            kanger[frame.strip()] += 1
            break

with TARGET.open("w", encoding="utf-8") as output:
    output.write("Top sampled leaf frames\n")
    for frame, count in leaf.most_common(40):
        output.write(f"{count:6d}  {frame}\n")

    output.write("\nTop first KANGER frames\n")
    for frame, count in kanger.most_common(60):
        output.write(f"{count:6d}  {frame}\n")
