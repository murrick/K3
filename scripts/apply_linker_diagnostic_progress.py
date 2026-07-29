from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")
linker = replace_once(
    linker,
    '''        do {

            if (logging) {
                log.add(LogMode.ANALYZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }
''',
    '''        do {

            ++passCounter;
            if (logging) {
                log.add(LogMode.ANALYZER, String.format("---------- LINKER PASS %03d ---------------", passCounter));
            }
''',
    "Linker pass counter",
)
linker = replace_once(
    linker,
    '''            rotator(leftList, causes, logging);
            rotator(ruleList, causes, logging);

        } while (mind.getRules().isAction()
''',
    '''            rotator(leftList, causes, logging);
            rotator(ruleList, causes, logging);

            if (Boolean.parseBoolean(System.getProperty("kanger.diagnostics", "false"))
                    && (passCounter <= 10 || passCounter % 100 == 0)) {
                System.err.println("[KANGER-LINKER] pass=" + passCounter
                        + " ruleSet=" + ruleSet.size()
                        + " rules=" + mind.getRules().size()
                        + " tvalues=" + mind.getTValues().size()
                        + " actions{rules=" + mind.getRules().isAction()
                        + ",tvalues=" + mind.getTValues().isAction()
                        + ",fvalues=" + mind.getFValues().isAction()
                        + ",tempHypothesis=" + mind.getTempHypothesis().isAction()
                        + ",hypothesis=" + mind.getHypothesis().isAction()
                        + "}");
            }

        } while (mind.getRules().isAction()
''',
    "Linker diagnostic progress",
)
linker_path.write_text(linker, encoding="utf-8")


runner_path = Path("kanger-console/src/org/kanger/KangerDiagnosticRunner.java")
runner = runner_path.read_text(encoding="utf-8")
runner = replace_once(
    runner,
    '''        IMind mind = new Mind(user);
        mind = mind.useStorage("diagnostics/set0301");
        System.out.println(Diagnostics.snapshot(mind, "before set_03_01"));
        boolean success = KangerTest.test(mind, "set_03_01");
        System.out.println(Diagnostics.snapshot(mind, "after set_03_01"));
        try {
            mind.closeStorage();
        } finally {
            System.exit(success ? 0 : 1);
        }
''',
    '''        IMind mind = new Mind(user);
        mind = mind.useStorage("diagnostics/set0301");
        mind = mind.clearWorkspace();
        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after clear"));

        boolean compiled = mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        if (!compiled) {
            throw new IllegalStateException("set_03_01 setup compilation rejected");
        }
        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after compile"));
        Diagnostics.resetStorageCounters(mind);

        Boolean result;
        try (Diagnostics.Watchdog watchdog = Diagnostics.watch("set_03_01 direct query", mind)) {
            result = mind.query("?$x @y a(x,y);");
        }
        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after query"));
        mind.closeStorage();
        System.exit(Boolean.FALSE.equals(result) ? 0 : 1);
''',
    "Direct set_03_01 diagnostic",
)
runner_path.write_text(runner, encoding="utf-8")
