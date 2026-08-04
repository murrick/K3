from pathlib import Path

OLD = "develop/server/0.12"
NEW = "develop/server/0.13"

roots = [
    Path("kanger-server"),
]

changed = []
for root in roots:
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            text = path.read_text()
        except UnicodeDecodeError:
            continue
        if OLD in text:
            path.write_text(text.replace(OLD, NEW))
            changed.append(str(path))

if not changed:
    raise SystemExit(f"no occurrences of {OLD!r} found")

print("updated shelf default in:")
for path in changed:
    print(path)
