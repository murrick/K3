#!/usr/bin/env python3
"""Qualify the version-local customer documentation contract.

The checks intentionally derive customer-visible surfaces from their canonical
sources instead of maintaining a second hand-written list in CI.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
PAYLOAD = ROOT / "distribution" / "payload"
ADMIN = PAYLOAD / "README.md"
COMMANDS = PAYLOAD / "docs" / "COMMANDS.md"
UI = PAYLOAD / "docs" / "UI_CONSOLE.md"


def fail(message: str) -> None:
    print(f"DOCUMENTATION_FAIL {message}", file=sys.stderr)
    raise SystemExit(1)


def text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(haystack: str, needle: str, owner: str) -> None:
    if needle not in haystack:
        fail(f"{owner} does not document: {needle}")


def require_ci(haystack: str, needle: str, owner: str) -> None:
    if needle.casefold() not in haystack.casefold():
        fail(f"{owner} does not document: {needle}")


admin = text(ADMIN)
commands = text(COMMANDS)
ui = text(UI)

# The distribution has exactly three customer manual roles. README is the
# installation/admin entry point; the other two live under docs/.
for marker in (
    "Installation, Update and Administration Manual",
    "docs/COMMANDS.md",
    "docs/UI_CONSOLE.md",
    "/etc/kanger/kanger.conf",
    "/etc/kanger/instance.conf",
    "/var/lib/kanger/KANGER/<user-id>/kanger.conf",
    "RegistrationPolicy.TRUSTED",
    "RegistrationPolicy.EMAIL_VERIFIED",
    "update.sh --force",
):
    require(admin, marker, "README.md")

# Every canonical command syntax registered in the live command metadata must
# be present literally in the command manual. A new registry definition cannot
# silently ship without customer documentation.
registry = text(ROOT / "kanger-command" / "src" / "org" / "kanger" / "command" / "CommandRegistry.java")
syntax_pattern = re.compile(
    r"define(?:WithFamilySpellings)?\(\s*CommandIntent\.[A-Z0-9_]+,\s*\"([^\"]+)\"",
    re.S,
)
syntaxes = []
for match in syntax_pattern.finditer(registry):
    syntax = match.group(1)
    if syntax not in syntaxes:
        syntaxes.append(syntax)
if len(syntaxes) < 30:
    fail(f"only {len(syntaxes)} canonical command syntaxes discovered")
for syntax in syntaxes:
    require(commands, syntax, "docs/COMMANDS.md")

# Model defaults/statuses are customer-visible contracts as well as syntax.
# Inspect the model section specifically; an unrelated older limit table is not coverage.
model_section = re.search(r"<!-- MODEL_AUTHORING_BEGIN -->(.*?)<!-- MODEL_AUTHORING_END -->", commands, re.S)
if model_section is None:
    fail("docs/COMMANDS.md missing model-authoring section")
model_limits = re.search(r"<!-- MODEL_LIMIT_DEFAULTS_BEGIN -->(.*?)<!-- MODEL_LIMIT_DEFAULTS_END -->", model_section.group(1), re.S)
if model_limits is None:
    fail("docs/COMMANDS.md missing model limit table")
model_request = text(ROOT / "kanger" / "src" / "org" / "kanger" / "ContextSignedModelRequest.java")
model_defaults = {name: value.replace("_", "") for name, value in re.findall(
    r'\bd\.put\(\s*"([a-z-]+)"\s*,\s*([0-9][0-9_]*)\s*\)', model_request)}
model_default_names = set(re.findall(r'\bd\.put\(\s*"([a-z-]+)"', model_request))
if set(model_defaults) != model_default_names:
    fail("cannot parse every automatic-model default; update documentation qualification")
if len(model_defaults) < 10:
    fail("cannot discover complete automatic-model defaults")
for name, value in model_defaults.items():
    row = r"\|\s*`" + re.escape(name) + r"`\s*\|\s*" + value + r"\s*\|"
    if re.search(row, model_limits.group(1)) is None:
        fail(f"docs/COMMANDS.md model default differs or is missing: {name}={value}")
publication = text(ROOT / "kanger" / "src" / "org" / "kanger" / "ContextSignedModelPublication.java")
status_enum = re.search(r"enum Status\s*\{([^}]+)\}", publication)
if status_enum is None:
    fail("cannot discover model publication outcomes")
model_statuses = [status.strip() for status in status_enum.group(1).split(",")]
for status in model_statuses:
    require(model_section.group(1), "`" + status + "`", "docs/COMMANDS.md model outcomes")

for marker in (
    "Prefix resolution",
    "Confirmation boundary",
    "Canonical `status`",
    "status core transaction",
    "status core levels",
    "status storage",
    "status session",
    "status runtime",
    "unavailable",
    "schema bases",
    "UNQUALIFIED",
    "quiescent",
):
    require_ci(commands, marker, "docs/COMMANDS.md")

# Configuration keys are derived from the production template. Numbered CORS
# origins are documented as the supported N-form rather than forcing one prose
# line per example slot.
config = text(ROOT / "kanger-server" / "deploy" / "kanger.conf.example")
keys = set()
for line in config.splitlines():
    match = re.match(r"\s*#?\s*(server\.[A-Za-z0-9_.]+)\s*=", line)
    if not match:
        continue
    key = match.group(1)
    key = re.sub(r"server\.cors\.allowed\.origin\.\d+$",
                 "server.cors.allowed.origin.N", key)
    keys.add(key)
if len(keys) < 20:
    fail(f"only {len(keys)} production configuration keys discovered")
for key in sorted(keys):
    require(admin, key, "README.md")

# TECH structure/labels are derived from the Browser source. Presentation-local
# and canonical STATUS projections must remain described in the UI manual.
presentation = text(ROOT / "html" / "presentation.js")
tech_status = text(ROOT / "html" / "tech-status.js")
local_sections = re.findall(r"createTechSection\(body,\s*'([^']+)'", presentation)
canonical_sections = re.findall(r"createSection\(body,\s*'([^']+)'", tech_status)
metric_labels = re.findall(r"\blabel:\s*'([^']+)'", tech_status)
for label in local_sections + canonical_sections + metric_labels:
    require_ci(ui, label, "docs/UI_CONSOLE.md")

for marker in (
    "25vw",
    "parent-owned",
    "structured read",
    "no periodic polling",
    "Browser Generation vs Storage generation",
    "Canonical STATUS",
    "Operation = idle",
    "10 bases",
):
    require_ci(ui, marker, "docs/UI_CONSOLE.md")

print(f"DOCUMENTATION_PASS manuals=3 canonical_syntaxes={len(syntaxes)} config_keys={len(keys)}")
print(f"DOCUMENTATION_PASS tech_sections={len(local_sections) + len(canonical_sections)} tech_labels={len(metric_labels)}")
print(f"DOCUMENTATION_PASS model_defaults={len(model_defaults)} model_outcomes={len(model_statuses)}")
print("DOCUMENTATION_OK")
