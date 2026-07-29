from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


# D1: storage operation counters in the bundled DUMB backend.
base_path = Path("kanger-data-dumb/src/org/kanger/storage/Base.java")
base = base_path.read_text(encoding="utf-8")
base = replace_once(
    base,
    "    private volatile long cacheSize = 0L;\n\n    private long lastId = -1;\n",
    "    private volatile long cacheSize = 0L;\n"
    "\n"
    "    private volatile long readRequestCount = 0L;\n"
    "    private volatile long cacheHitCount = 0L;\n"
    "    private volatile long cacheMissCount = 0L;\n"
    "    private volatile long storageReadCount = 0L;\n"
    "    private volatile long writeCount = 0L;\n"
    "    private volatile long deleteCount = 0L;\n"
    "    private volatile long flushCount = 0L;\n"
    "\n"
    "    private long lastId = -1;\n",
    "Base diagnostic fields",
)
base = replace_once(
    base,
    "    public void add(IStep one) throws Exception {\n        synchronized (locker) {\n",
    "    public void add(IStep one) throws Exception {\n"
    "        ++writeCount;\n"
    "        synchronized (locker) {\n",
    "Base write counter",
)
base = replace_once(
    base,
    "    public void flush() throws Exception {\n        synchronized (locker) {\n",
    "    public void flush() throws Exception {\n"
    "        ++flushCount;\n"
    "        synchronized (locker) {\n",
    "Base flush counter",
)
base = replace_once(
    base,
    "    public IStep get(long id) throws Exception {\n        if (CACHE_ENABLE) {\n",
    "    public IStep get(long id) throws Exception {\n"
    "        ++readRequestCount;\n"
    "        if (CACHE_ENABLE) {\n",
    "Base read request counter",
)
base = replace_once(
    base,
    "                if (cache.containsKey(id)) {\n                    timing.remove(id);\n",
    "                if (cache.containsKey(id)) {\n"
    "                    ++cacheHitCount;\n"
    "                    timing.remove(id);\n",
    "Base cache hit counter",
)
base = replace_once(
    base,
    "        synchronized (locker) {\n            Index.IndexOne x = index.getOne(id);\n",
    "        ++cacheMissCount;\n"
    "        synchronized (locker) {\n"
    "            Index.IndexOne x = index.getOne(id);\n",
    "Base cache miss counter",
)
base = replace_once(
    base,
    "            if (x != null) {\n                IStep one = data.get(x.getLong());\n",
    "            if (x != null) {\n"
    "                ++storageReadCount;\n"
    "                IStep one = data.get(x.getLong());\n",
    "Base physical read counter",
)
base = replace_once(
    base,
    "    public void delete(long id) throws Exception {\n        if (CACHE_ENABLE) {\n",
    "    public void delete(long id) throws Exception {\n"
    "        ++deleteCount;\n"
    "        if (CACHE_ENABLE) {\n",
    "Base delete counter",
)
base = replace_once(
    base,
    "    @Override\n    public Iterator<IStep> iterator() {\n",
    "    public long getReadRequestCount() { return readRequestCount; }\n"
    "    public long getCacheHitCount() { return cacheHitCount; }\n"
    "    public long getCacheMissCount() { return cacheMissCount; }\n"
    "    public long getStorageReadCount() { return storageReadCount; }\n"
    "    public long getWriteCount() { return writeCount; }\n"
    "    public long getDeleteCount() { return deleteCount; }\n"
    "    public long getFlushCount() { return flushCount; }\n"
    "\n"
    "    public void resetDiagnosticCounters() {\n"
    "        readRequestCount = 0L;\n"
    "        cacheHitCount = 0L;\n"
    "        cacheMissCount = 0L;\n"
    "        storageReadCount = 0L;\n"
    "        writeCount = 0L;\n"
    "        deleteCount = 0L;\n"
    "        flushCount = 0L;\n"
    "    }\n"
    "\n"
    "    @Override\n"
    "    public Iterator<IStep> iterator() {\n",
    "Base diagnostic getters",
)
base_path.write_text(base, encoding="utf-8")


# D1/D2 and the actual set_03_01 storage harness defect.
test_path = Path("kanger-console/src/org/kanger/test/KangerTest.java")
test = test_path.read_text(encoding="utf-8")
test = replace_once(
    test,
    "import org.kanger.Mind;\n",
    "import org.kanger.Diagnostics;\nimport org.kanger.Mind;\n",
    "KangerTest Diagnostics import",
)
test = replace_once(
    test,
    "        KangerTest cls = new KangerTest(mind);\n        int successCount = 0;\n",
    "        int successCount = 0;\n",
    "KangerTest stale Mind removal",
)
test = replace_once(
    test,
    "            mind = mind = mind.clearWorkspace();\n\n            Method setUp = cls.getClass().getDeclaredMethod(\"setUp\");\n",
    "            mind = mind.clearWorkspace();\n"
    "\n"
    "            // Storage lifecycle operations may return a different root Mind.\n"
    "            // Bind the test instance only after the final context is selected.\n"
    "            KangerTest cls = new KangerTest(mind);\n"
    "\n"
    "            Method setUp = cls.getClass().getDeclaredMethod(\"setUp\");\n",
    "KangerTest bind active Mind",
)
test = replace_once(
    test,
    "                    System.out.println(\"Testing: \" + name);\n"
    "                    long t = System.currentTimeMillis();\n"
    "                    Method method = cls.getClass().getDeclaredMethod(name);\n"
    "                    method.setAccessible(true);\n"
    "                    method.invoke(cls);\n"
    "                    System.out.println(\"Timing: \" + ((System.currentTimeMillis() - t) / 1000.0) + \" sec\");\n",
    "                    System.out.println(\"Testing: \" + name);\n"
    "                    long t = System.currentTimeMillis();\n"
    "                    Diagnostics.resetStorageCounters(cls.mind);\n"
    "                    if (Diagnostics.isEnabled(cls.mind)) {\n"
    "                        System.out.println(Diagnostics.snapshot(cls.mind, \"before \" + name));\n"
    "                    }\n"
    "                    Method method = cls.getClass().getDeclaredMethod(name);\n"
    "                    method.setAccessible(true);\n"
    "                    try (Diagnostics.Watchdog watchdog = Diagnostics.watch(name, cls.mind)) {\n"
    "                        method.invoke(cls);\n"
    "                    }\n"
    "                    if (Diagnostics.isEnabled(cls.mind)) {\n"
    "                        System.out.println(Diagnostics.snapshot(cls.mind, \"after \" + name));\n"
    "                    }\n"
    "                    System.out.println(\"Timing: \" + ((System.currentTimeMillis() - t) / 1000.0) + \" sec\");\n",
    "KangerTest watchdog and snapshots",
)
test_path.write_text(test, encoding="utf-8")


# Manual D1 snapshot from the interactive console: o x
console_path = Path("kanger-console/src/org/kanger/Console.java")
console = console_path.read_text(encoding="utf-8")
console = replace_once(
    console,
    "                case 'T':\n                    String prefix = \"\";\n",
    "                case 'X':\n"
    "                    System.out.println(Diagnostics.snapshot(mind, \"console\"));\n"
    "                    break;\n"
    "                case 'T':\n"
    "                    String prefix = \"\";\n",
    "Console diagnostics option",
)
console_path.write_text(console, encoding="utf-8")
