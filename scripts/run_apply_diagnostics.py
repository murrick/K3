from pathlib import Path
import runpy

path = Path("scripts/apply_diagnostics.py")
source = path.read_text(encoding="utf-8")
old = (
    '    "                if (cache.containsKey(id)) {\\n                    timing.remove(id);\\n",\n'
    '    "                if (cache.containsKey(id)) {\\n"\n'
    '    "                    ++cacheHitCount;\\n"\n'
    '    "                    timing.remove(id);\\nn",\n'
)
# The generated source uses a normal newline escape in the final fragment.
if old not in source:
    old = (
        '    "                if (cache.containsKey(id)) {\\n                    timing.remove(id);\\n",\n'
        '    "                if (cache.containsKey(id)) {\\n"\n'
        '    "                    ++cacheHitCount;\\n"\n'
        '    "                    timing.remove(id);\\n",\n'
    )
new = (
    '    "        ++readRequestCount;\\n"\n'
    '    "        if (CACHE_ENABLE) {\\n"\n'
    '    "            synchronized (cache) {\\n"\n'
    '    "                if (cache.containsKey(id)) {\\n"\n'
    '    "                    timing.remove(id);\\n",\n'
    '    "        ++readRequestCount;\\n"\n'
    '    "        if (CACHE_ENABLE) {\\n"\n'
    '    "            synchronized (cache) {\\n"\n'
    '    "                if (cache.containsKey(id)) {\\n"\n'
    '    "                    ++cacheHitCount;\\n"\n'
    '    "                    timing.remove(id);\\n",\n'
)
if old not in source:
    raise RuntimeError("Could not locate Base cache-hit patch block")
path.write_text(source.replace(old, new, 1), encoding="utf-8")
runpy.run_path(str(path), run_name="__main__")
