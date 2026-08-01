#!/usr/bin/env python3
from pathlib import Path

TOOLS = Path('kanger/src/org/kanger/enums/Tools.java')
PARSER = Path('kanger/src/org/kanger/compiler/Parser.java')
RUNNER = Path('kanger-qualification/src/org/kanger/KangerParserTerminalDelimiterSafetyRunner.java')
WORKFLOW = Path('.github/workflows/kanger-qualification-isolation.yml')
ONE_SHOT = Path('.github/workflows/one-shot-3.5.0.8-parser-boundary.yml')
SELF = Path('tools/apply_parser_terminal_hotfix.py')


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f'Expected exactly one match in {path}: {old!r}')
    path.write_text(text.replace(old, new))


replace_once(
    TOOLS,
    'if (line.length() < t.getPos()) {',
    'if (t.getPos() >= line.length()) {'
)

replace_once(
    PARSER,
    '''        while (current.getPos() < line.length() && isDelimiter(line.charAt(current.getPos()))) {
            current.setPos(current.getPos() + 1);
        }

        Op op = getOp(line, current.getPos(), 0);''',
    '''        while (current.getPos() < line.length() && isDelimiter(line.charAt(current.getPos()))) {
            current.setPos(current.getPos() + 1);
        }
        if (current.getPos() >= line.length()) {
            return null;
        }

        Op op = getOp(line, current.getPos(), 0);'''
)

RUNNER.write_text('''/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.compiler.Parser;
import org.kanger.compiler.Token;
import org.kanger.enums.Tools;

/** Regression gate for terminal delimiter and exact-end token boundaries. */
public final class KangerParserTerminalDelimiterSafetyRunner {

    private KangerParserTerminalDelimiterSafetyRunner() {
    }

    public static void main(String[] args) throws Exception {
        require(Parser.nextToken(" \\t\\r\\n", null) == null,
                "Delimiter-only input must terminate without charAt past end");

        Token exact = Tools.extractLine("!value;", null);
        require(exact != null, "Expected first exact-end statement");
        require(Tools.extractLine("!value;", exact) == null,
                "Exact-end extraction must terminate cleanly");

        Token trailing = Tools.extractLine("!value;\\r\\n", null);
        require(trailing != null, "Expected statement before trailing delimiters");
        require(Tools.extractLine("!value;\\r\\n", trailing) == null,
                "Trailing delimiters must terminate cleanly");

        System.out.println("PARSER_TERMINAL_DELIMITER_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
''')

WORKFLOW.write_text('''name: KANGER qualification isolation

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  isolate-qualification:
    name: Production surface excludes runners
    runs-on: ubuntu-latest

    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up Java 8
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '8'
          cache: maven

      - name: Verify production source roots are clean
        shell: bash
        run: |
          set -euo pipefail
          remaining="$(find \\
            kanger/src \\
            kanger-console/src \\
            kanger-data-dumb/src \\
            kanger-udf/src \\
            -type f -name '*Runner.java' -print)"
          if [ -n "$remaining" ]; then
            echo 'Qualification runners remain in production source roots:'
            printf '%s\\n' "$remaining"
            exit 1
          fi

      - name: Package production artifact
        run: mvn --batch-mode --no-transfer-progress -DskipTests clean package

      - name: Verify production JAR is clean
        shell: bash
        run: |
          set -euo pipefail
          jar_file="$(find target -maxdepth 1 -type f -name '*.jar' | head -n 1)"
          test -n "$jar_file"
          leaked="$(jar tf "$jar_file" | grep -E '(^|/)[^/]*Runner\\.class$' || true)"
          if [ -n "$leaked" ]; then
            echo 'Qualification runners leaked into production JAR:'
            printf '%s\\n' "$leaked"
            exit 1
          fi

      - name: Verify qualification source remains available
        shell: bash
        run: |
          set -euo pipefail
          test -d kanger-qualification/src
          count="$(find kanger-qualification/src -type f -name '*Runner.java' | wc -l | tr -d ' ')"
          test "$count" -eq 37
          mvn --batch-mode --no-transfer-progress -DskipTests test-compile
          java -cp "target/test-classes:target/classes:lib/*:kanger-server/lib/*" \\
            org.kanger.KangerParserTerminalDelimiterSafetyRunner
''')

if ONE_SHOT.exists():
    ONE_SHOT.unlink()
SELF.unlink()
