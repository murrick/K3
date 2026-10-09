/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.Files;
import java.util.List;

/** Bounded terminal-journal qualification, not full DMZ support certification. */
public final class DmzTerminalSupportCaptureRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-terminal-").toString());
        scopeLifecycle();
        alternatives();
        conjunction();
        characterizeBindingGroups();
        System.out.println("DMZ_TERMINAL_SUPPORT_CAPTURE_PASS checks=" + checks);
    }

    private static void scopeLifecycle() throws Exception {
        Mind q = root();
        TerminalSupportCapture outer = TerminalSupportCapture.begin();
        TerminalSupportCapture inner = TerminalSupportCapture.begin();
        try {
            try { outer.close(); throw new AssertionError("out-of-order close"); }
            catch (IllegalStateException expected) { ++checks; }
            require(q.compile("!@x a(x) -> male(x); !a(John);", null, false), "inner inference");
            require(!inner.snapshot().isEmpty(), "inner receives events");
            require(outer.snapshot().isEmpty(), "outer isolated during inner scope");
        } finally { inner.close(); }
        require(q.compile("!a(Mary);", null, false), "restored outer inference");
        List<TerminalSupportCapture.Event> detached = outer.snapshot();
        require(!detached.isEmpty(), "outer restored");
        outer.close();
        outer.close();
        int count = detached.size();
        require(q.compile("!a(Tom);", null, false), "inactive inference");
        require(outer.snapshot().size() == count, "closed journal does not collect");
        try { detached.clear(); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { ++checks; }
    }

    private static Mind root() throws Exception {
        User user = new User();
        new UDF().init(user);
        Mind q = new Mind(user);
        user.setCurrentMind(q);
        return q;
    }

    private static void alternatives() throws Exception {
        Mind q = root();
        List<TerminalSupportCapture.Event> events;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x a(x) -> male(x); !@x c(x) -> male(x); "
                    + "!a(John); !c(John);", null, false), "alternative program");
            require(!q.compile("!~male(John);", null, false), "conflict program");
            events = capture.snapshot();
        }
        require(has(events, "!male(John);", "!@x a(x) -> male(x);", "!a(John);"),
                "a alternative retained before canonical suppression");
        require(has(events, "!male(John);", "!@x c(x) -> male(x);", "!c(John);"),
                "c alternative retained before canonical suppression");
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "capture leaves truth unchanged");
        System.out.println("TERMINAL_ALTERNATIVES_PASS events=" + events.size());
    }

    private static void conjunction() throws Exception {
        Mind q = root();
        List<TerminalSupportCapture.Event> events;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x (a(x) && c(x)) -> male(x); !a(John); !c(John);",
                    null, false), "conjunction program");
            events = capture.snapshot();
        }
        boolean grouped = false;
        for (TerminalSupportCapture.Event event : events) {
            if (event.conclusion.equals("!male(John);")
                    && event.donors.contains("!a(John);") && event.donors.contains("!c(John);")) {
                grouped = true;
                require(event.donors.size() == 2, "conjunction has two donors in one event");
                try { event.donors.clear(); throw new AssertionError("mutable donor group"); }
                catch (UnsupportedOperationException expected) { ++checks; }
            }
        }
        require(grouped, "conjunction not flattened to independent alternatives");
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "conjunction truth unchanged");
        System.out.println("TERMINAL_CONJUNCTION_PASS events=" + events.size());
    }

    private static boolean has(List<TerminalSupportCapture.Event> events,
            String conclusion, String origin, String donor) {
        for (TerminalSupportCapture.Event event : events) {
            if (conclusion.equals(event.conclusion) && origin.equals(event.ruleOrigin)
                    && event.donors.contains(donor)) return true;
        }
        return false;
    }

    private static void characterizeBindingGroups() throws Exception {
        Mind q = root();
        boolean john = false;
        boolean mary = false;
        boolean mixed = false;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x (a(x) && c(x)) -> male(x); "
                    + "!a(John); !c(John); !a(Mary); !c(Mary);", null, false), "two tuples");
            for (TerminalSupportCapture.Event event : capture.snapshot()) {
                if ("!male(John);".equals(event.conclusion)) {
                    john = true;
                    mixed |= event.donors.contains("!a(Mary);") || event.donors.contains("!c(Mary);");
                }
                if ("!male(Mary);".equals(event.conclusion)) {
                    mary = true;
                    mixed |= event.donors.contains("!a(John);") || event.donors.contains("!c(John);");
                }
            }
        }
        require(john && mary, "both tuple candidates observed");
        require(mixed, "raw accumulated causes expose foreign tuple donors; uncertified");
        System.out.println("DMZ_OPEN_REQUIREMENT terminal_binding_certified=false mixed_donor_groups=" + mixed);
    }

    private static void require(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }
}
