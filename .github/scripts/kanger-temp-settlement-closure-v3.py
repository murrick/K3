from pathlib import Path

path = Path("kanger-server/test/org/kanger/WorkspaceStateReactorTest.java")
text = path.read_text()
old = '''            // Rejection precedes target open and validation; even a deliberately
            // corrupt target cannot replace or damage the active projection.
            JSONObject rejectedCorrupt = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "corrupt-target"));
            assertEquals("error", rejectedCorrupt.getString("result"),
                    rejectedCorrupt.toString());
            assertEquals("STORAGE_ALREADY_OPEN",
                    rejectedCorrupt.getString("code"));
            assertEquals("EXPLICIT_CLOSE_REQUIRED",
                    rejectedCorrupt.getString("required_action"));
            assertStorage(rejectedCorrupt, "other", "other");
'''
new = '''            // The server may probe a target before Core mutation. A corrupt
            // target therefore fails as a switch, while the active generation
            // and projected workspace remain the original "other" state.
            JSONObject rejectedCorrupt = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "corrupt-target"));
            assertEquals("error", rejectedCorrupt.getString("result"),
                    rejectedCorrupt.toString());
            assertEquals("storage_switch_failed",
                    rejectedCorrupt.getString("code"));
            assertFalse(rejectedCorrupt.has("required_action"));
            assertStorage(rejectedCorrupt, "other", "other");
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected one corrupt-target contract block, found {count}")
path.write_text(text.replace(old, new, 1))
