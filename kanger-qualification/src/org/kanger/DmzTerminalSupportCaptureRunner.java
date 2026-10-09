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
        premiseBindings();
        hiddenJoinBindings();
        applications(false);
        applications(true);
        unsupportedApplications();
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

    private static void premiseBindings() throws Exception {
        Mind q = root();
        List<TerminalSupportCapture.Match> matches;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x (a(x) && c(x)) -> male(x); "
                    + "!a(John); !c(John); !a(Mary); !c(Mary);", null, false), "premise program");
            matches = capture.matchSnapshot();
        }
        boolean john = false, mary = false;
        for (TerminalSupportCapture.Match match : matches) {
            if (!match.ruleOrigin.equals("!@x (a(x) && c(x)) -> male(x);")) continue;
            String expected = null;
            if (match.donor.equals("!a(John);") || match.donor.equals("!c(John);")) expected = "John";
            if (match.donor.equals("!a(Mary);") || match.donor.equals("!c(Mary);")) expected = "Mary";
            if (expected == null) continue;
            require(match.bindings.size() == 1, "one partial binding for unary premise");
            TerminalSupportCapture.Binding binding = match.bindings.get(0);
            require(expected.equals(binding.rendering), "binding belongs to donor tuple");
            require(binding.value != null, "detached semantic binding");
            john |= expected.equals("John"); mary |= expected.equals("Mary");
            try { match.bindings.clear(); throw new AssertionError("mutable bindings"); }
            catch (UnsupportedOperationException expectedFailure) { ++checks; }
        }
        require(john && mary, "both separated premise bindings captured");
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "John truth unchanged");
        require(Boolean.TRUE.equals(q.query("?male(Mary);", null, false)), "Mary truth unchanged");
        System.out.println("DMZ_PREMISE_BINDINGS_PASS matches=" + matches.size());
    }

    private static void hiddenJoinBindings() throws Exception {
        Mind q = root();
        String origin = "!@x @y (a(x,y) && c(y)) -> male(x);";
        List<TerminalSupportCapture.Match> matches;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile(origin + " !a(John,One); !c(One); !a(Mary,Two); !c(Two);",
                    null, false), "hidden join program");
            matches = capture.matchSnapshot();
        }
        boolean one = false, two = false;
        int premiseOne = -1, premiseTwo = -1, variableOne = -1, variableTwo = -1;
        for (TerminalSupportCapture.Match match : matches) {
            if (!origin.equals(match.ruleOrigin)) continue;
            if (!"!c(One);".equals(match.donor) && !"!c(Two);".equals(match.donor)) continue;
            require(match.bindings.size() == 1, "join premise has one partial binding");
            TerminalSupportCapture.Binding binding = match.bindings.get(0);
            require("y".equals(binding.name), "hidden join variable retained");
            if ("!c(One);".equals(match.donor)) {
                require("One".equals(binding.rendering), "One join isolated");
                one = true; premiseOne = match.premise; variableOne = binding.variable;
            } else {
                require("Two".equals(binding.rendering), "Two join isolated");
                two = true; premiseTwo = match.premise; variableTwo = binding.variable;
            }
        }
        require(one && two, "both hidden join substitutions retained");
        require(premiseOne == premiseTwo, "same premise across substitutions");
        require(variableOne == variableTwo, "same scoped variable across substitutions");
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "join John proven");
        require(Boolean.TRUE.equals(q.query("?male(Mary);", null, false)), "join Mary proven");
        System.out.println("DMZ_HIDDEN_JOIN_BINDINGS_PASS matches=" + matches.size());
    }

    private static void applications(boolean reverse) throws Exception {
        Mind q = root();
        String join = "!@x @y (a(x,y) && c(y)) -> male(x);";
        String alternative = "!@x b(x) -> male(x);";
        String facts = reverse ? "!c(Two); !a(Mary,Two); !b(John); !c(One); !a(John,One);"
                : "!a(John,One); !c(One); !b(John); !a(Mary,Two); !c(Two);";
        List<TerminalSupportCapture.Application> applications;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile((reverse ? alternative + join : join + alternative) + facts,
                    null, false), "application program");
            applications = capture.applicationSnapshot();
        }
        boolean johnJoin = false, maryJoin = false, johnAlternative = false;
        int joinRule = -1, alternativeRule = -1;
        for (TerminalSupportCapture.Application application : applications) {
            if (!application.conclusion.equals("!male(John);")
                    && !application.conclusion.equals("!male(Mary);")) continue;
            java.util.Set<String> donors = new java.util.HashSet<String>();
            for (TerminalSupportCapture.Support support : application.supports) donors.add(support.donor);
            if (application.ruleOrigin.equals(join)) {
                joinRule = application.rule;
                require(application.bindings.size() == 2, "complete binding retains hidden y");
                require(application.supports.size() == 2 && donors.size() == 2, "two AND premises");
                if (application.conclusion.equals("!male(John);")) {
                    require(donors.contains("!a(John,One);") && donors.contains("!c(One);"), "John AND support exact");
                    johnJoin = true;
                } else {
                    require(donors.contains("!a(Mary,Two);") && donors.contains("!c(Two);"), "Mary AND support exact");
                    maryJoin = true;
                }
            } else if (application.ruleOrigin.equals(alternative)) {
                alternativeRule = application.rule;
                require(application.conclusion.equals("!male(John);"), "alternative belongs to John");
                require(application.supports.size() == 1 && donors.contains("!b(John);"), "OR alternative separate");
                johnAlternative = true;
            } else throw new AssertionError("unexpected target application");
            try { application.supports.clear(); throw new AssertionError("mutable application"); }
            catch (UnsupportedOperationException expected) { ++checks; }
            try { application.bindings.clear(); throw new AssertionError("mutable binding vector"); }
            catch (UnsupportedOperationException expected) { ++checks; }
        }
        require(johnJoin && maryJoin && johnAlternative, "all three complete bounded supports");
        require(joinRule != alternativeRule, "alternative rule identities separate");
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "application John truth");
        require(Boolean.TRUE.equals(q.query("?male(Mary);", null, false)), "application Mary truth");
        System.out.println("DMZ_BOUND_APPLICATIONS_PASS reverse=" + reverse + " events=" + applications.size());
    }

    private static void unsupportedApplications() throws Exception {
        Mind q = root();
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x (a(x) && c(x)) -> male(x); !a(John);", null, false), "missing premise program");
            for (TerminalSupportCapture.Application application : capture.applicationSnapshot())
                require(!"!male(John);".equals(application.conclusion), "missing premise never certified");
            require(q.query("?male(John);", null, false) == null, "missing premise native unknown");
        }
        q = root();
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile("!@x a(x) -> male(x+1); !a(1);", null, false), "function program");
            for (TerminalSupportCapture.Application application : capture.applicationSnapshot())
                require(!"!male(2);".equals(application.conclusion), "function excluded from bounded surface");
            require(capture.applicationGapSnapshot().contains("unsupported-argument"), "unsupported function records gap");
            require(Boolean.TRUE.equals(q.query("?male(2);", null, false)), "function still works natively");
        }
        System.out.println("DMZ_APPLICATION_FAIL_CLOSED_PASS");
    }

    private static void require(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }
}
