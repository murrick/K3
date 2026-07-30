/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.Base;
import org.kanger.storage.Sapato;
import org.kanger.storage.Step;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Strict acceptance runner for 3.4.4.2 DUMB process-crash recovery. */
public final class KangerDumbQ2RecoveryRunner {

    private static final String LOGIN = "dumb-q2-recovery";
    private static final String PASSWORD = "dumb-q2-recovery";
    private static final String FAULT_PROPERTY = "kanger.dumb.fault.haltAt";
    private static final int FAULT_EXIT = 86;
    private static final int RECORD_COUNT = 100;
    private static final long VALUE_PREFIX = 0x4B414E4700000000L;

    private KangerDumbQ2RecoveryRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runParent();
            return;
        }
        if (args.length < 3) {
            throw new IllegalArgumentException("mode prefix value");
        }
        String mode = args[0];
        String prefix = args[1];
        int value = Integer.parseInt(args[2]);

        if ("child-write-close".equals(mode)) {
            writeFixture(prefix, value);
        } else if ("child-append-halt".equals(mode)) {
            appendAndHalt(prefix, value, false);
        } else if ("child-append-flush-halt".equals(mode)) {
            appendAndHalt(prefix, value, true);
        } else if ("child-relocate-halt".equals(mode)) {
            relocateAndHalt(prefix, value);
        } else if ("child-delete-halt".equals(mode)) {
            deleteAndHalt(prefix, value);
        } else if ("child-mixed-halt".equals(mode)) {
            mixedAndHalt(prefix, value);
        } else if ("child-repeat-halt".equals(mode)) {
            repeatedAndHalt(prefix, value);
        } else if ("child-verify-pre".equals(mode)) {
            verifyFixture(prefix, value, true);
        } else if ("child-verify-post".equals(mode)) {
            verifyFixture(prefix, value, false);
        } else {
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void runParent() throws Exception {
        Path output = Paths.get(System.getProperty(
                "kanger.dumb.q2.output", "target/dumb-q2-recovery"))
                .toAbsolutePath();
        Files.createDirectories(output);
        Path work = Files.createTempDirectory("kanger-dumb-q2-");
        Path home = work.resolve("home");
        Files.createDirectories(home);

        List<Result> results = new ArrayList<Result>();
        addOperationLevelScenarios(work, home, results);
        addMutationFailpointScenarios(work, home, results);
        addFlushFailpointScenarios(work, home, results);
        addRecoveryFailpointScenarios(work, home, results);

        int passed = 0;
        StringBuilder protocol = new StringBuilder();
        protocol.append("# DUMB Q2 recovery protocol\n\n");
        protocol.append("- Timestamp (UTC): ").append(utcNow()).append("\n");
        protocol.append("- Java: ").append(System.getProperty("java.version")).append("\n");
        protocol.append("- OS: ").append(System.getProperty("os.name"))
                .append(' ').append(System.getProperty("os.version")).append("\n");
        protocol.append("- Scenarios: ").append(results.size()).append("\n\n");
        protocol.append("| Scenario | Result | Detail |\n|---|---|---|\n");
        for (Result result : results) {
            if (result.passed) {
                ++passed;
            }
            protocol.append('|').append(result.name).append('|')
                    .append(result.passed ? "PASS" : "FAIL").append('|')
                    .append(escape(result.detail)).append("|\n");
        }
        Files.write(output.resolve("q2-recovery.md"),
                protocol.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("DUMB Q2 work: " + work);
        System.out.println("DUMB Q2 protocol: " + output.resolve("q2-recovery.md"));
        System.out.println("Q2_TOTAL: " + results.size());
        System.out.println("Q2_PASS: " + passed);
        System.out.println("Q2_FAIL: " + (results.size() - passed));
        if (passed != results.size()) {
            System.exit(1);
        }
    }

    private static void addOperationLevelScenarios(Path work, Path home,
                                                    List<Result> results) throws Exception {
        results.add(runScenario(work, home, "append-unflushed",
                "child-append-halt", RECORD_COUNT,
                "child-verify-pre", RECORD_COUNT));
        results.add(runScenario(work, home, "relocation-unflushed",
                "child-relocate-halt", RECORD_COUNT / 2,
                "child-verify-pre", RECORD_COUNT));
        results.add(runScenario(work, home, "delete-unflushed",
                "child-delete-halt", RECORD_COUNT / 2,
                "child-verify-pre", RECORD_COUNT));
        results.add(runScenario(work, home, "mixed-unflushed",
                "child-mixed-halt", RECORD_COUNT,
                "child-verify-pre", RECORD_COUNT));
        results.add(runScenario(work, home, "repeated-id-unflushed",
                "child-repeat-halt", RECORD_COUNT / 2,
                "child-verify-pre", RECORD_COUNT));
        results.add(runScenario(work, home, "append-committed",
                "child-append-flush-halt", RECORD_COUNT,
                "child-verify-post", RECORD_COUNT + 1));
    }

    private static void addMutationFailpointScenarios(Path work, Path home,
                                                       List<Result> results) throws Exception {
        String[] upsertPoints = new String[]{
                "upsert-after-wal", "upsert-after-data",
                "upsert-after-index", "upsert-after-integrity"};
        for (String point : upsertPoints) {
            results.add(runFaultScenario(work, home, "append-" + point,
                    "child-append-halt", RECORD_COUNT, point,
                    "child-verify-pre", RECORD_COUNT));
            results.add(runFaultScenario(work, home, "relocate-" + point,
                    "child-relocate-halt", RECORD_COUNT / 2, point,
                    "child-verify-pre", RECORD_COUNT));
        }

        String[] deletePoints = new String[]{
                "delete-after-wal", "delete-after-index",
                "delete-after-data", "delete-after-integrity"};
        for (String point : deletePoints) {
            results.add(runFaultScenario(work, home, "delete-" + point,
                    "child-delete-halt", RECORD_COUNT / 2, point,
                    "child-verify-pre", RECORD_COUNT));
        }
    }

    private static void addFlushFailpointScenarios(Path work, Path home,
                                                    List<Result> results) throws Exception {
        String[] rollbackPoints = new String[]{
                "flush-after-index", "flush-after-data", "flush-after-integrity"};
        for (String point : rollbackPoints) {
            results.add(runFaultScenario(work, home, "append-" + point,
                    "child-append-flush-halt", RECORD_COUNT, point,
                    "child-verify-pre", RECORD_COUNT));
        }
        results.add(runFaultScenario(work, home, "append-flush-after-checkpoint",
                "child-append-flush-halt", RECORD_COUNT, "flush-after-checkpoint",
                "child-verify-post", RECORD_COUNT + 1));
    }

    private static void addRecoveryFailpointScenarios(Path work, Path home,
                                                       List<Result> results) throws Exception {
        String[] points = new String[]{
                "recovery-after-rollback", "recovery-after-index",
                "recovery-after-data", "recovery-after-integrity",
                "recovery-after-checkpoint"};
        for (String point : points) {
            String name = "restart-" + point;
            Path directory = work.resolve(name);
            Files.createDirectories(directory);
            Path prefix = directory.resolve("db");
            ChildResult baseline = runChild(home, "child-write-close",
                    prefix, RECORD_COUNT, null);
            if (baseline.exitCode != 0) {
                results.add(new Result(name, false,
                        "baseline: " + tail(baseline.output)));
                continue;
            }

            ChildResult mutationCrash = runChild(home, "child-append-halt",
                    prefix, RECORD_COUNT, "upsert-after-data");
            ChildResult recoveryCrash = runChild(home, "child-verify-pre",
                    prefix, RECORD_COUNT, point);
            ChildResult verify = runChild(home, "child-verify-pre",
                    prefix, RECORD_COUNT, null);
            boolean passed = mutationCrash.exitCode == FAULT_EXIT
                    && recoveryCrash.exitCode == FAULT_EXIT
                    && verify.exitCode == 0;
            results.add(new Result(name, passed,
                    "mutation=" + mutationCrash.exitCode
                            + "; recovery=" + recoveryCrash.exitCode
                            + "; verify=" + verify.exitCode
                            + "; " + tail(verify.output)));
        }
    }

    private static Result runScenario(Path work, Path home, String name,
                                      String crashMode, int crashValue,
                                      String verifyMode, int verifyValue) throws Exception {
        Path directory = work.resolve(name);
        Files.createDirectories(directory);
        Path prefix = directory.resolve("db");
        ChildResult baseline = runChild(home, "child-write-close",
                prefix, RECORD_COUNT, null);
        if (baseline.exitCode != 0) {
            return new Result(name, false, "baseline: " + tail(baseline.output));
        }
        ChildResult crash = runChild(home, crashMode, prefix, crashValue, null);
        ChildResult verify = runChild(home, verifyMode, prefix, verifyValue, null);
        boolean passed = !crash.timedOut && verify.exitCode == 0;
        return new Result(name, passed,
                "crash=" + crash.exitCode + "; verify=" + verify.exitCode
                        + "; " + tail(verify.output));
    }

    private static Result runFaultScenario(Path work, Path home, String name,
                                           String crashMode, int crashValue,
                                           String faultPoint,
                                           String verifyMode, int verifyValue) throws Exception {
        Path directory = work.resolve(name);
        Files.createDirectories(directory);
        Path prefix = directory.resolve("db");
        ChildResult baseline = runChild(home, "child-write-close",
                prefix, RECORD_COUNT, null);
        if (baseline.exitCode != 0) {
            return new Result(name, false, "baseline: " + tail(baseline.output));
        }
        ChildResult crash = runChild(home, crashMode, prefix, crashValue, faultPoint);
        ChildResult verify = runChild(home, verifyMode, prefix, verifyValue, null);
        boolean passed = crash.exitCode == FAULT_EXIT && verify.exitCode == 0;
        return new Result(name, passed,
                "fault=" + faultPoint + "; crash=" + crash.exitCode
                        + "; verify=" + verify.exitCode
                        + "; " + tail(verify.output));
    }

    private static ChildResult runChild(Path home, String mode,
                                        Path prefix, int value,
                                        String faultPoint) throws Exception {
        String java = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + "java";
        List<String> command = new ArrayList<String>();
        command.add(java);
        command.add("-Duser.home=" + home.toAbsolutePath());
        if (faultPoint != null) {
            command.add("-D" + FAULT_PROPERTY + "=" + faultPoint);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(KangerDumbQ2RecoveryRunner.class.getName());
        command.add(mode);
        command.add(prefix.toAbsolutePath().toString());
        command.add(Integer.toString(value));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean completed = process.waitFor(30L, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        byte[] output = readAll(process);
        return new ChildResult(completed ? process.exitValue() : 124,
                new String(output, StandardCharsets.UTF_8), !completed);
    }

    private static byte[] readAll(Process process) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = process.getInputStream().read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static IUser openUser() throws Exception {
        try {
            return UserFactory.createUser(LOGIN, PASSWORD);
        } catch (AuthenticationErrorException exists) {
            return UserFactory.getUser(LOGIN, PASSWORD);
        }
    }

    private static void writeFixture(String prefix, int count) throws Exception {
        Path path = Paths.get(prefix).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Base base = new Base(path.toString(), 1, new Object(), false, openUser());
        IStep previous = null;
        for (int i = 0; i < count; ++i) {
            previous = addLongRecord(base, i, previous);
        }
        base.flush();
        base.close();
    }

    private static IStep addLongRecord(Base base, int id, IStep previous)
            throws Exception {
        Step step = new Step();
        step.setId(id);
        step.setHash(1000 + id);
        step.setData(Long.valueOf(VALUE_PREFIX + id));
        step.setNext(previous);
        Sapato persisted = new Sapato(base, step);
        persisted.append();
        return persisted;
    }

    private static void appendAndHalt(String prefix, int existingCount,
                                      boolean flush) throws Exception {
        Base base = openBase(prefix);
        IStep previous = existingCount == 0 ? null : base.get(existingCount - 1);
        addLongRecord(base, existingCount, previous);
        if (flush) {
            base.flush();
        }
        Runtime.getRuntime().halt(0);
    }

    private static void relocateAndHalt(String prefix, int id) throws Exception {
        Base base = openBase(prefix);
        updateLarge(base, id, 0x5500000000000000L);
        Runtime.getRuntime().halt(0);
    }

    private static void deleteAndHalt(String prefix, int id) throws Exception {
        Base base = openBase(prefix);
        base.delete(id);
        Runtime.getRuntime().halt(0);
    }

    private static void mixedAndHalt(String prefix, int existingCount) throws Exception {
        Base base = openBase(prefix);
        addLongRecord(base, existingCount, base.get(existingCount - 1));
        updateLarge(base, existingCount / 2, 0x6600000000000000L);
        base.delete(existingCount / 4);
        Runtime.getRuntime().halt(0);
    }

    private static void repeatedAndHalt(String prefix, int id) throws Exception {
        Base base = openBase(prefix);
        updateLarge(base, id, 0x7700000000000000L);
        updateLarge(base, id, 0x7800000000000000L);
        base.delete(id);
        Runtime.getRuntime().halt(0);
    }

    private static void updateLarge(Base base, int id, long prefix) throws Exception {
        IStep old = base.get(id);
        if (old == null) {
            throw new AssertionError("missing update target id=" + id);
        }
        Step step = new Step();
        step.setId(id);
        step.setHash(old.getHash());
        List<Long> large = new ArrayList<Long>();
        for (long value = 0; value < 1024L; ++value) {
            large.add(prefix + value);
        }
        step.setData(large);
        step.setNext(old.getNext());
        new Sapato(base, step).update();
    }

    private static Base openBase(String prefix) throws Exception {
        return new Base(Paths.get(prefix).toAbsolutePath().toString(),
                1, new Object(), false, openUser());
    }

    private static void verifyFixture(String prefix, int count,
                                      boolean requireNoFollowingId) throws Exception {
        Base base = openBase(prefix);
        for (int i = 0; i < count; ++i) {
            IStep step = base.get(i);
            if (step == null) {
                throw new AssertionError("missing id=" + i);
            }
            Object data = step.getData();
            long expected = VALUE_PREFIX + i;
            if (!(data instanceof Long) || ((Long) data).longValue() != expected) {
                throw new AssertionError("bad data id=" + i + " actual=" + data);
            }
        }
        if (requireNoFollowingId && base.get(count) != null) {
            throw new AssertionError("unexpected uncommitted id=" + count);
        }

        IStep current = base.getRoot();
        int expectedId = count - 1;
        int seen = 0;
        while (current != null) {
            if (current.getId() != expectedId) {
                throw new AssertionError("chain mismatch expected=" + expectedId
                        + " actual=" + current.getId());
            }
            ++seen;
            --expectedId;
            current = current.getNext();
            if (seen > count + 1) {
                throw new AssertionError("chain cycle");
            }
        }
        if (seen != count || expectedId != -1) {
            throw new AssertionError("chain incomplete seen=" + seen
                    + " count=" + count);
        }
        base.close();
        if (Files.exists(Paths.get(prefix + ".wal.1"))) {
            throw new AssertionError("recovery log remains after verification");
        }
        System.out.println("Q2_VERIFY_OK count=" + count);
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("|", "\\|")
                .replace("\r", " ").replace("\n", " ");
    }

    private static String tail(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r", "").trim();
        return normalized.length() <= 800 ? normalized
                : normalized.substring(normalized.length() - 800);
    }

    private static final class ChildResult {
        private final int exitCode;
        private final String output;
        private final boolean timedOut;

        private ChildResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }
    }

    private static final class Result {
        private final String name;
        private final boolean passed;
        private final String detail;

        private Result(String name, boolean passed, String detail) {
            this.name = name;
            this.passed = passed;
            this.detail = detail;
        }
    }
}
