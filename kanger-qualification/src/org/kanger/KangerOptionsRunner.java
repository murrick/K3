package org.kanger;

import org.kanger.udf.UDF;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IMind;
import org.kanger.test.KangerVisualTestHarness;
import org.kanger.test.KangerStabilizationTest;
import java.nio.file.Files;

/** Live toggling, session isolation and logical parity in one JVM. */
public final class KangerOptionsRunner {
    public static void main(String[] args) throws Exception {
        User user = new User();
        java.nio.file.Path root = Files.createTempDirectory("kanger-options-");
        user.setUserDir(root.toString() + "/");
        user.setSourceDir(root.resolve("SRC").toString() + "/");
        user.setDatabaseDir(root.resolve("DB").toString() + "/");
        Files.createDirectories(root.resolve("SRC"));
        Files.createDirectories(root.resolve("DB"));
        new UDF().init(user);
        new org.kanger.storage.dumb2.DB().init(user);
        IMind mind = new Mind(user);
        mind = mind.clearWorkspace();
        user.setCurrentMind(mind);
        if (args.length > 0) { mind = mind.useStorage("options"); user.setCurrentMind(mind); }
        User other = new User();
        IMind otherMind = new Mind(other);
        boolean otherDefault = OptimizationOptions.enabled(otherMind, "singleTValueLookup");
        CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
        CommandParser parser = new CommandParser();
        try {
            for (String state : new String[] {"no", "yes"}) {
                processor.execute(parser.parse("opt optimize " + state), user);
                for (boolean enabled : OptimizationOptions.snapshot(user.getCurrentMind()).values()) {
                    require(enabled == "yes".equals(state), "all-options did not switch");
                }
                processor.execute(parser.parse("start"), user);
                IMind child = user.getCurrentMind();
                require(OptimizationOptions.enabled(child, "singleTValueLookup") == "yes".equals(state), "child lost session settings");
                processor.execute(parser.parse("rollback"), user);
                require(OptimizationOptions.enabled(otherMind, "singleTValueLookup") == otherDefault, "another user changed");
                processor.execute(parser.parse("options timezone Europe/Moscow"), user);
                require("Europe/Moscow".equals(user.getTimeZone()), "timezone did not change");
                try {
                    processor.execute(parser.parse("options timezone Invalid/Zone"), user);
                    throw new AssertionError("invalid timezone accepted");
                } catch (org.kanger.exception.CommandErrorException expected) { }
                require("Europe/Moscow".equals(user.getTimeZone()), "invalid timezone mutated session");
                require(KangerVisualTestHarness.test(user.getCurrentMind(), "set_"), "historical logic failed " + state);
                require(KangerStabilizationTest.test(user.getCurrentMind().clearWorkspace(), "set_s5a_03"), "structured logic failed " + state);
                System.out.println("Options logical parity PASS: " + state);
            }
            processor.execute(parser.parse("opt optimize no"), user);
            processor.execute(parser.parse("opt singleTValueLookup yes"), user);
            String report = processor.execute(parser.parse("options optimize"), user).getDescription();
            require(report.contains("optimize: mixed"), "mixed state missing");
            require(!processor.execute(parser.parse("options help"), user).getDescription().contains("test"), "developer hook leaked into help");
            System.out.println("KANGER options PASS");
        } finally {
            if (user.getCurrentMind() != null) user.getCurrentMind().closeStorage();
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root)) {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
