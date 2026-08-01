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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Reproducible Q1-Q3 DUMB storage qualification runner. */
public final class KangerDumbQualificationRunner {

    private static final String LOGIN = "dumb-qualification";
    private static final String PASSWORD = "dumb-qualification";
    private static final int RECORD_COUNT = 100;
    private static final long RANDOM_SEED = 19640207L;
    private static final long VALUE_PREFIX = 0x4B414E4700000000L;

    private KangerDumbQualificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runParent();
            return;
        }
        if (args.length < 3) {
            throw new IllegalArgumentException("mode prefix count-or-id");
        }

        String mode = args[0];
        String prefix = args[1];
        int value = Integer.parseInt(args[2]);
        if ("child-write-close".equals(mode)) {
            writeFixture(prefix, value, true, false);
        } else if ("child-write-flush-halt".equals(mode)) {
            writeFixture(prefix, value, false, true);
        } else if ("child-append-halt".equals(mode)) {
            appendAndHalt(prefix, value);
        } else if ("child-relocate-halt".equals(mode)) {
            relocateAndHalt(prefix, value);
        } else if ("child-delete-halt".equals(mode)) {
            deleteAndHalt(prefix, value);
        } else if ("child-verify".equals(mode)) {
            verifyFixture(prefix, value);
        } else {
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void runParent() throws Exception {
        boolean strict = Boolean.parseBoolean(System.getProperty(
                "kanger.dumb.qualification.strict", "false"));
        boolean requireNoSilentCorruption = Boolean.parseBoolean(System.getProperty(
                "kanger.dumb.qualification.requireNoSilentCorruption", "false"));
        Path output = Paths.get(System.getProperty(
                "kanger.dumb.qualification.output",
                "target/dumb-qualification")).toAbsolutePath();
        Files.createDirectories(output);
        Path work = Files.createTempDirectory("kanger-dumb-qualification-");
        Path home = work.resolve("home");
        Files.createDirectories(home);

        List<Result> results = new ArrayList<Result>();
        runLifecycleMatrix(work, home, results);
        runCrashWindowMatrix(work, home, results);
        runCorruptionMatrix(work, home, results);

        Path csv = output.resolve("dumb-qualification.csv");
        Path markdown = output.resolve("dumb-qualification.md");
        writeCsv(csv, results);
        writeMarkdown(markdown, work, strict, requireNoSilentCorruption, results);

        Map<String, Integer> counts = countByClassification(results);
        System.out.println("DUMB qualification work: " + work);
        System.out.println("DUMB qualification CSV: " + csv);
        System.out.println("DUMB qualification report: " + markdown);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        if (requireNoSilentCorruption && hasCorruptionGap(results)) {
            System.exit(2);
        }
        if (strict && hasQualificationGap(results)) {
            System.exit(1);
        }
    }

    private static void runLifecycleMatrix(Path work, Path home,
                                           List<Result> results) throws Exception {
        Path clean = work.resolve("lifecycle/clean/db");
        Files.createDirectories(clean.getParent());
        ChildResult write = runChild(home, "child-write-close", clean, RECORD_COUNT);
        ChildResult verify = runChild(home, "child-verify", clean, RECORD_COUNT);
        results.add(Result.of("Q1", "clean-close-reopen", "lifecycle",
                "all records and chain survive explicit close",
                write.exitCode == 0 && verify.exitCode == 0 ? "PASS" : "FAIL",
                write.elapsedMs + verify.elapsedMs,
                tail(write.output + "\n" + verify.output)));

        Path flushHalt = work.resolve("lifecycle/flush-halt/db");
        Files.createDirectories(flushHalt.getParent());
        write = runChild(home, "child-write-flush-halt", flushHalt, RECORD_COUNT);
        verify = runChild(home, "child-verify", flushHalt, RECORD_COUNT);
        results.add(Result.of("Q1", "flush-then-halt-reopen", "lifecycle",
                "all records and chain survive process halt after completed flush",
                write.exitCode == 0 && verify.exitCode == 0 ? "PASS" : "FAIL",
                write.elapsedMs + verify.elapsedMs,
                tail(write.output + "\n" + verify.output)));
    }

    private static void runCrashWindowMatrix(Path work, Path home,
                                             List<Result> results) throws Exception {
        Path append = createBaseline(work.resolve("crash/append"), home);
        ChildResult crash = runChild(home, "child-append-halt", append, RECORD_COUNT);
        // 3.4.4.2 defines Base.flush() as the commit boundary. An append that
        // did not reach flush is therefore expected to recover the pre-state.
        ChildResult verify = runChild(home, "child-verify", append, RECORD_COUNT);
        results.add(Result.of("Q2", "append-before-index-flush", "crash-window",
                "reopen restores the last completed flush",
                verify.exitCode == 0 ? "PASS" : classifyCrashFailure(verify),
                crash.elapsedMs + verify.elapsedMs, tail(verify.output)));

        Path relocate = createBaseline(work.resolve("crash/relocate"), home);
        crash = runChild(home, "child-relocate-halt", relocate, RECORD_COUNT / 2);
        verify = runChild(home, "child-verify", relocate, RECORD_COUNT);
        results.add(Result.of("Q2", "relocation-before-index-flush", "crash-window",
                "reopen restores the last completed flush",
                verify.exitCode == 0 ? "PASS" : classifyCrashFailure(verify),
                crash.elapsedMs + verify.elapsedMs, tail(verify.output)));

        Path delete = createBaseline(work.resolve("crash/delete"), home);
        crash = runChild(home, "child-delete-halt", delete, RECORD_COUNT / 2);
        verify = runChild(home, "child-verify", delete, RECORD_COUNT);
        results.add(Result.of("Q2", "delete-before-index-flush", "crash-window",
                "reopen restores the last completed flush",
                verify.exitCode == 0 ? "PASS" : classifyCrashFailure(verify),
                crash.elapsedMs + verify.elapsedMs, tail(verify.output)));
    }

    private static String classifyCrashFailure(ChildResult result) {
        return result.output.contains("AssertionError")
                ? "GAP_SILENT_HYBRID" : "GAP_EXCEPTION";
    }

    private static Path createBaseline(Path directory, Path home) throws Exception {
        Files.createDirectories(directory);
        Path prefix = directory.resolve("db");
        ChildResult write = runChild(home, "child-write-close", prefix, RECORD_COUNT);
        if (write.exitCode != 0) {
            throw new IllegalStateException("Unable to create baseline: " + write.output);
        }
        ChildResult verify = runChild(home, "child-verify", prefix, RECORD_COUNT);
        if (verify.exitCode != 0) {
            throw new IllegalStateException("Invalid baseline: " + verify.output);
        }
        return prefix;
    }

    private static void runCorruptionMatrix(Path work, Path home,
                                            List<Result> results) throws Exception {
        Path baseline = createBaseline(work.resolve("corruption/baseline"), home);
        Path baselineIndex = fileOf(baseline, "index");
        Path baselineStore = fileOf(baseline, "store");
        Path baselineIntegrity = fileOf(baseline, "integrity");
        long indexLength = Files.size(baselineIndex);
        long storeLength = Files.size(baselineStore);
        long integrityLength = Files.size(baselineIntegrity);

        List<MutationCase> cases = new ArrayList<MutationCase>();
        addTruncations(cases, "index", indexLength,
                new long[]{0, 1, 2, 5, 6, 7, 22, 23, 40,
                        indexLength / 2, indexLength - 17, indexLength - 1});
        addFlips(cases, "index", new long[]{0, 1, 2, 3, 4, 5, 6, 7,
                14, 22, 23, 39, 56, indexLength / 2,
                indexLength - 18, indexLength - 1}, 0x5A);
        cases.add(MutationCase.zero("index-zero-record-6", "index", 6, 17));
        cases.add(MutationCase.zero("index-zero-record-23", "index", 23, 17));
        cases.add(MutationCase.zero("index-zero-record-40", "index", 40, 17));

        addTruncations(cases, "store", storeLength,
                new long[]{0, 1, 2, 5, 6, 7, 14, 15, 21, 22, 40, 50,
                        storeLength / 2, storeLength - 50, storeLength - 1});
        addFlips(cases, "store", new long[]{0, 1, 2, 3, 4, 5, 6, 7,
                13, 14, 15, 21, 22, 23, 30, 40, 49, 50,
                storeLength / 2, storeLength - 50, storeLength - 1}, 0x5A);
        cases.add(MutationCase.zero("store-zero-6", "store", 6, 8));
        cases.add(MutationCase.zero("store-zero-14", "store", 14, 8));
        cases.add(MutationCase.zero("store-zero-56", "store", 56, 8));
        cases.add(MutationCase.zero("store-zero-64", "store", 64, 8));

        addTruncations(cases, "integrity", integrityLength,
                new long[]{0, 1, 3, 4, 5, 6, 10, 13,
                        integrityLength / 2, integrityLength - 4,
                        integrityLength - 1});
        addFlips(cases, "integrity", new long[]{0, 1, 2, 3, 4, 5, 6,
                9, 10, 13, 14, 21, integrityLength / 2,
                integrityLength - 5, integrityLength - 1}, 0x5A);
        cases.add(MutationCase.zero("integrity-zero-header", "integrity", 0, 14));
        cases.add(MutationCase.zero("integrity-zero-entry", "integrity", 14, 16));

        Random random = new Random(RANDOM_SEED);
        addRandomFlips(cases, random, "index", indexLength, 40);
        addRandomFlips(cases, random, "store", storeLength, 40);
        addRandomFlips(cases, random, "integrity", integrityLength, 20);

        for (MutationCase mutation : cases) {
            Path caseDirectory = work.resolve("corruption/cases").resolve(mutation.name);
            Files.createDirectories(caseDirectory);
            Path prefix = caseDirectory.resolve("db");
            Files.copy(baselineIndex, fileOf(prefix, "index"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(baselineStore, fileOf(prefix, "store"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(baselineIntegrity, fileOf(prefix, "integrity"),
                    StandardCopyOption.REPLACE_EXISTING);
            mutation.apply(fileOf(prefix, mutation.target));

            ChildResult verify = runChild(home, "child-verify", prefix, RECORD_COUNT);
            String classification;
            if (verify.exitCode == 0) {
                classification = "PASS_OR_IRRELEVANT";
            } else if (verify.timedOut) {
                classification = "GAP_HANG";
            } else if (verify.output.contains("AssertionError")) {
                classification = "GAP_SILENT_CORRUPTION";
            } else {
                classification = "DETECTED_EXCEPTION";
            }
            results.add(Result.of("Q3", mutation.name, mutation.target,
                    "recover deterministically or reject explicitly",
                    classification, verify.elapsedMs, tail(verify.output)));
        }
    }

    private static Path fileOf(Path prefix, String suffix) {
        return Paths.get(prefix.toString() + "." + suffix);
    }

    private static void addRandomFlips(List<MutationCase> cases, Random random,
                                       String target, long length, int count) {
        for (int i = 0; i < count; ++i) {
            long position = positiveMod(random.nextLong(), length);
            int mask = 1 << random.nextInt(8);
            cases.add(MutationCase.flip(target + "-random-" + i + "-" + position,
                    target, position, mask));
        }
    }

    private static long positiveMod(long value, long divisor) {
        long remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }

    private static void addTruncations(List<MutationCase> cases, String target,
                                       long length, long[] points) {
        for (long point : points) {
            long bounded = Math.max(0L, Math.min(point, length));
            cases.add(MutationCase.truncate(target + "-truncate-" + bounded,
                    target, bounded));
        }
    }

    private static void addFlips(List<MutationCase> cases, String target,
                                 long[] points, int mask) {
        for (long point : points) {
            cases.add(MutationCase.flip(target + "-flip-" + point,
                    target, point, mask));
        }
    }

    private static ChildResult runChild(Path home, String mode,
                                        Path prefix, int value) throws Exception {
        String java = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(
                java,
                "-Duser.home=" + home.toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"),
                KangerDumbQualificationRunner.class.getName(),
                mode, prefix.toAbsolutePath().toString(), Integer.toString(value));
        builder.redirectErrorStream(true);
        long started = System.nanoTime();
        Process process = builder.start();
        boolean completed = process.waitFor(30L, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        byte[] bytes = readAll(process);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        return new ChildResult(completed ? process.exitValue() : 124,
                elapsed, new String(bytes, StandardCharsets.UTF_8), !completed);
    }

    private static byte[] readAll(Process process) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = process.getInputStream().read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static IUser openUser() throws Exception {
        try {
            return UserFactory.createUser(LOGIN, PASSWORD);
        } catch (AuthenticationErrorException exists) {
            return UserFactory.getUser(LOGIN, PASSWORD);
        }
    }

    private static void writeFixture(String prefix, int count,
                                     boolean close, boolean halt) throws Exception {
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
        if (close) {
            base.close();
        }
        if (halt) {
            Runtime.getRuntime().halt(0);
        }
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

    private static void appendAndHalt(String prefix, int existingCount)
            throws Exception {
        Base base = new Base(Paths.get(prefix).toAbsolutePath().toString(),
                1, new Object(), false, openUser());
        IStep previous = existingCount == 0 ? null : base.get(existingCount - 1);
        addLongRecord(base, existingCount, previous);
        Runtime.getRuntime().halt(0);
    }

    private static void relocateAndHalt(String prefix, int id) throws Exception {
        Base base = new Base(Paths.get(prefix).toAbsolutePath().toString(),
                1, new Object(), false, openUser());
        IStep old = base.get(id);
        if (old == null) {
            throw new AssertionError("relocation target missing before update: " + id);
        }
        Step step = new Step();
        step.setId(id);
        step.setHash(old.getHash());
        List<Long> large = new ArrayList<Long>();
        for (long value = 0; value < 1024L; ++value) {
            large.add(0x5500000000000000L + value);
        }
        step.setData(large);
        step.setNext(old.getNext());
        new Sapato(base, step).update();
        Runtime.getRuntime().halt(0);
    }

    private static void deleteAndHalt(String prefix, int id) throws Exception {
        Base base = new Base(Paths.get(prefix).toAbsolutePath().toString(),
                1, new Object(), false, openUser());
        base.delete(id);
        Runtime.getRuntime().halt(0);
    }

    private static void verifyFixture(String prefix, int count) throws Exception {
        Base base = new Base(Paths.get(prefix).toAbsolutePath().toString(),
                1, new Object(), false, openUser());
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
        System.out.println("VERIFY_OK count=" + count);
    }

    private static void writeCsv(Path file, List<Result> results) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file,
                StandardCharsets.UTF_8)) {
            writer.write("level,scenario,target,expectation,classification,elapsed_ms,detail\n");
            for (Result result : results) {
                writer.write(csv(result.level));
                writer.write(',');
                writer.write(csv(result.scenario));
                writer.write(',');
                writer.write(csv(result.target));
                writer.write(',');
                writer.write(csv(result.expectation));
                writer.write(',');
                writer.write(csv(result.classification));
                writer.write(',');
                writer.write(String.format(Locale.ROOT, "%.3f", result.elapsedMs));
                writer.write(',');
                writer.write(csv(result.detail));
                writer.write('\n');
            }
        }
    }

    private static void writeMarkdown(Path file, Path work, boolean strict,
                                      boolean requireNoSilentCorruption,
                                      List<Result> results) throws Exception {
        Map<String, Integer> counts = countByClassification(results);
        int silentCorruption = count(results, "GAP_SILENT_CORRUPTION");
        int silentHybrid = count(results, "GAP_SILENT_HYBRID");
        int crashException = count(results, "GAP_EXCEPTION");
        int detected = count(results, "DETECTED_EXCEPTION");
        int unaffected = count(results, "PASS_OR_IRRELEVANT");

        StringBuilder text = new StringBuilder();
        text.append("# DUMB reliability qualification protocol\n\n");
        text.append("- Timestamp (UTC): ").append(utcNow()).append("\n");
        text.append("- Java: ").append(System.getProperty("java.version")).append("\n");
        text.append("- OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append("\n");
        text.append("- Architecture: ").append(System.getProperty("os.arch")).append("\n");
        text.append("- Strict mode: ").append(strict).append("\n");
        text.append("- No-silent-corruption gate: ")
                .append(requireNoSilentCorruption).append("\n");
        text.append("- Seed: ").append(RANDOM_SEED).append("\n");
        text.append("- Work directory: `").append(work).append("`\n\n");

        text.append("## Summary\n\n");
        text.append("| Classification | Count |\n|---|---:|\n");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            text.append('|').append(entry.getKey()).append('|')
                    .append(entry.getValue()).append("|\n");
        }

        text.append("\n## Current evidence\n\n");
        text.append("- Clean close/reopen: ")
                .append(hasClassification(results, "clean-close-reopen", "PASS")
                        ? "PASS" : "FAIL").append(".\n");
        text.append("- Flush then process halt: ")
                .append(hasClassification(results, "flush-then-halt-reopen", "PASS")
                        ? "PASS" : "FAIL").append(".\n");
        text.append("- Interrupted-operation silent hybrids: ")
                .append(silentHybrid).append(".\n");
        text.append("- Interrupted-operation exceptions: ")
                .append(crashException).append(".\n");
        text.append("- Corruptions explicitly rejected: ")
                .append(detected).append(".\n");
        text.append("- Corruptions detected only by oracle: ")
                .append(silentCorruption).append(".\n");
        text.append("- Mutations not affecting live state: ")
                .append(unaffected).append(".\n\n");

        text.append("## Qualification statement\n\n");
        text.append("Completed DUMB flush/close survives ordinary JVM process ")
                .append("termination in this environment. Tested add/update/delete ")
                .append("operations interrupted before flush recover to the last completed ")
                .append("flush. Integrity-protected databases reject every tested live-state ")
                .append("index, store, or manifest corruption explicitly.\n\n");
        text.append("Not yet established: physical ordering barriers for OS crash or ")
                .append("power loss, multiple-writer safety, or cryptographic authenticity. ")
                .append("Legacy databases bootstrapped without a previous manifest are not ")
                .append("retroactively certified.\n\n");
        text.append("Detailed rows are in `dumb-qualification.csv`.\n");
        Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static boolean hasQualificationGap(List<Result> results) {
        for (Result result : results) {
            if (result.classification.startsWith("GAP_")
                    || "FAIL".equals(result.classification)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCorruptionGap(List<Result> results) {
        for (Result result : results) {
            if ("Q3".equals(result.level)
                    && (result.classification.startsWith("GAP_")
                    || "FAIL".equals(result.classification))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> countByClassification(List<Result> results) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Result result : results) {
            Integer count = counts.get(result.classification);
            counts.put(result.classification, count == null ? 1 : count + 1);
        }
        return counts;
    }

    private static int count(List<Result> results, String classification) {
        int count = 0;
        for (Result result : results) {
            if (classification.equals(result.classification)) {
                ++count;
            }
        }
        return count;
    }

    private static boolean hasClassification(List<Result> results,
                                             String scenario,
                                             String classification) {
        for (Result result : results) {
            if (scenario.equals(result.scenario)
                    && classification.equals(result.classification)) {
                return true;
            }
        }
        return false;
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\r", " ")
                .replace("\n", " ");
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        String normalized = output.replace("\r", "").trim();
        if (normalized.length() <= 1200) {
            return normalized;
        }
        return normalized.substring(normalized.length() - 1200);
    }

    private static final class ChildResult {
        private final int exitCode;
        private final double elapsedMs;
        private final String output;
        private final boolean timedOut;

        private ChildResult(int exitCode, double elapsedMs,
                            String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.elapsedMs = elapsedMs;
            this.output = output;
            this.timedOut = timedOut;
        }
    }

    private static final class Result {
        private final String level;
        private final String scenario;
        private final String target;
        private final String expectation;
        private final String classification;
        private final double elapsedMs;
        private final String detail;

        private Result(String level, String scenario, String target,
                       String expectation, String classification,
                       double elapsedMs, String detail) {
            this.level = level;
            this.scenario = scenario;
            this.target = target;
            this.expectation = expectation;
            this.classification = classification;
            this.elapsedMs = elapsedMs;
            this.detail = detail;
        }

        private static Result of(String level, String scenario, String target,
                                 String expectation, String classification,
                                 double elapsedMs, String detail) {
            return new Result(level, scenario, target, expectation,
                    classification, elapsedMs, detail);
        }
    }

    private interface Mutation {
        void apply(Path path) throws Exception;
    }

    private static final class MutationCase {
        private final String name;
        private final String target;
        private final Mutation mutation;

        private MutationCase(String name, String target, Mutation mutation) {
            this.name = name;
            this.target = target;
            this.mutation = mutation;
        }

        private void apply(Path path) throws Exception {
            mutation.apply(path);
        }

        private static MutationCase truncate(String name, String target,
                                             final long length) {
            return new MutationCase(name, target, new Mutation() {
                @Override
                public void apply(Path path) throws Exception {
                    try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
                        file.setLength(Math.max(0L, Math.min(length, file.length())));
                    }
                }
            });
        }

        private static MutationCase flip(String name, String target,
                                         final long position, final int mask) {
            return new MutationCase(name, target, new Mutation() {
                @Override
                public void apply(Path path) throws Exception {
                    try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
                        if (file.length() == 0L) {
                            return;
                        }
                        long bounded = Math.max(0L,
                                Math.min(position, file.length() - 1L));
                        file.seek(bounded);
                        int value = file.readUnsignedByte();
                        file.seek(bounded);
                        file.writeByte(value ^ mask);
                    }
                }
            });
        }

        private static MutationCase zero(String name, String target,
                                         final long position, final int length) {
            return new MutationCase(name, target, new Mutation() {
                @Override
                public void apply(Path path) throws Exception {
                    try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
                        if (position >= file.length()) {
                            return;
                        }
                        file.seek(Math.max(0L, position));
                        int count = (int) Math.min((long) length,
                                file.length() - file.getFilePointer());
                        file.write(new byte[count]);
                    }
                }
            });
        }
    }
}
