/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.List;

import static org.kanger.command.CommandParseException.Reason.*;

/**
 * Dependency-free executable conformance runner for the canonical parser.
 *
 * <p>The repository build wires this class into the Maven test phase without
 * introducing a second test framework solely for the command module.</p>
 */
public final class CommandParserConformanceTest {

    private final CommandParser parser = new CommandParser();
    private final CommandFormatter formatter = new CommandFormatter();
    private int assertions;

    public static void main(String[] args) throws Exception {
        CommandParserConformanceTest test = new CommandParserConformanceTest();
        test.run();
        System.out.println("KANGER command parser conformance PASS: "
                + test.assertions + " assertions");
    }

    private void run() throws Exception {
        coreBoundary();
        topLevelPrefixes();
        ruleFamily();
        functionFamily();
        baseFamily();
        valuesFamily();
        solutionFamily();
        whenFamily();
        transactionFamily();
        sourceFamily();
        storageFamily();
        systemFamily();
        canonicalEcho();
        helpRegistry();
    }

    private void coreBoundary() throws Exception {
        expectCore("?father(John,Tom)");
        expectCore("!male(John)");
        expectCore("+native($x)");
        expectCore("-obsolete($x)");
        expectCore("=x+y");
        reject("!!eating(Cat, Mouse);", INVALID_GRAMMAR);
    }

    private void topLevelPrefixes() throws Exception {
        expect("b", CommandIntent.BASE_STATUS);
        expect("d", CommandIntent.SOURCE_DELETE);
        expect("d foo.k", CommandIntent.SOURCE_DELETE);
        expect("e", CommandIntent.ERASE);
        expect("f", CommandIntent.FUNCTIONS);
        expect("g", CommandIntent.SOURCE_GET);
        expect("g foo.k", CommandIntent.SOURCE_GET);
        expect("h", CommandIntent.HELP);
        expect("p foo.k", CommandIntent.SOURCE_PUT);
        expect("q", CommandIntent.QUIT);
        expect("r", CommandIntent.RULE_STATUS);
        expect("so", CommandIntent.SOLUTIONS);
        expect("st", CommandIntent.STORAGE_STATUS);
        expect("t", CommandIntent.TX_STATUS);
        expect("v", CommandIntent.VALUES);
        expect("w", CommandIntent.WHEN_STATUS);
        reject("s", AMBIGUOUS_PREFIX);
        reject("z", UNKNOWN_KEYWORD);
    }

    private void ruleFamily() throws Exception {
        expect("rules", CommandIntent.RULE_ALL);
        expectLong("rule 17", CommandIntent.RULE_SHOW, "id", 17L);
        expect("r a", CommandIntent.RULE_ALL);
        expect("r p", CommandIntent.RULE_PRODUCED);
        expectLong("r l 2", CommandIntent.RULE_LEVEL, "level", 2L);
        expectLong("r t 17", CommandIntent.RULE_TREE, "id", 17L);
        expectLong("r c 17", CommandIntent.RULE_COMMENT_GET, "id", 17L);

        CommandInvocation set = parser.parse("rule comment 17 Important rule");
        check(set.getIntent() == CommandIntent.RULE_COMMENT_SET,
                "rule comment set intent");
        check(Long.valueOf(17L).equals(set.getArgument("id")),
                "rule comment id");
        check("Important rule".equals(set.getArgument("text")),
                "rule comment free tail");

        CommandInvocation clear = parser.parse("r c 17 \"\"");
        check("".equals(clear.getArgument("text")),
                "rule comment explicit empty");

        reject("rules 17", INVALID_GRAMMAR);
        reject("rule show 17", INVALID_GRAMMAR);
        reject("rule tree", MISSING_ARGUMENT);
        reject("rule all 17", EXTRA_ARGUMENT);
    }

    private void functionFamily() throws Exception {
        expect("functions", CommandIntent.FUNCTIONS);
        expectLong("f 8", CommandIntent.FUNCTION_SHOW, "id", 8L);
        expectLong("function source 8", CommandIntent.FUNCTION_SOURCE, "id", 8L);
        reject("function", MISSING_ARGUMENT);
        reject("functions 8", INVALID_GRAMMAR);
        reject("function show 8", INVALID_GRAMMAR);
    }

    private void baseFamily() throws Exception {
        expect("base predicates", CommandIntent.BASE_PREDICATES);
        CommandInvocation byName = parser.parse("b p father");
        check(byName.getIntent() == CommandIntent.BASE_PREDICATE,
                "base predicate by name intent");
        check("father".equals(byName.getArgument("predicate")),
                "base predicate name");
        CommandInvocation byId = parser.parse("base predicate 12");
        check(Long.valueOf(12L).equals(byId.getArgument("predicate")),
                "base predicate id");
        expectLong("b t 314", CommandIntent.BASE_TREE, "statementId", 314L);
        reject("base predicate", MISSING_ARGUMENT);
        reject("base predicates father", INVALID_GRAMMAR);
        reject("base tree", MISSING_ARGUMENT);
    }

    @SuppressWarnings("unchecked")
    private void valuesFamily() throws Exception {
        expect("values", CommandIntent.VALUES);
        CommandInvocation ordered = parser.parse(
                "values order surname, name asc, age desc");
        check(ordered.getIntent() == CommandIntent.VALUES_ORDER,
                "values order intent");
        List<SortKey> keys = (List<SortKey>) ordered.getArgument("keys");
        check(keys.size() == 3, "values order key count");
        check(new SortKey("surname", SortKey.Direction.ASC).equals(keys.get(0)),
                "values default asc");
        check(new SortKey("name", SortKey.Direction.ASC).equals(keys.get(1)),
                "values explicit asc");
        check(new SortKey("age", SortKey.Direction.DESC).equals(keys.get(2)),
                "values desc");

        CommandInvocation abbreviated = parser.parse("v o x d, y a");
        keys = (List<SortKey>) abbreviated.getArgument("keys");
        check(keys.size() == 2, "abbreviated values key count");
        check(keys.get(0).getDirection() == SortKey.Direction.DESC,
                "abbreviated desc");
        check(keys.get(1).getDirection() == SortKey.Direction.ASC,
                "abbreviated asc");

        reject("values order", MISSING_ARGUMENT);
        reject("values order x desc y", INVALID_GRAMMAR);
        reject("values order x sideways", INVALID_GRAMMAR);
        reject("values order x,", INVALID_GRAMMAR);
    }

    private void solutionFamily() throws Exception {
        expect("solutions", CommandIntent.SOLUTIONS);
        expectLong("so 42", CommandIntent.SOLUTION_SHOW, "id", 42L);
        expectLong("so t 42", CommandIntent.SOLUTION_TREE, "id", 42L);
        reject("solution", MISSING_ARGUMENT);
        reject("solutions tree 42", INVALID_GRAMMAR);
        reject("solution show 42", INVALID_GRAMMAR);
    }

    private void whenFamily() throws Exception {
        expect("when", CommandIntent.WHEN_STATUS);
        expectLong("w a 0", CommandIntent.WHEN_ACCEPT, "index", 0L);
        expectLong("when accept 2", CommandIntent.WHEN_ACCEPT, "index", 2L);
        reject("when accept", MISSING_ARGUMENT);
        reject("when accept -1", INVALID_ARGUMENT_SHAPE);
    }

    private void transactionFamily() throws Exception {
        expect("transaction", CommandIntent.TX_STATUS);
        expect("t s", CommandIntent.TX_START);
        expect("t c", CommandIntent.TX_COMMIT);
        expect("t r", CommandIntent.TX_ROLLBACK);
        reject("transaction create", INVALID_GRAMMAR);
        reject("transaction x", UNKNOWN_KEYWORD);
    }

    private void sourceFamily() throws Exception {
        expectArgument("get", CommandIntent.SOURCE_GET,
                "source", "");
        expectArgument("delete", CommandIntent.SOURCE_DELETE,
                "source", "");
        expectArgument("delete foo.k", CommandIntent.SOURCE_DELETE,
                "source", "foo.k");
        expectArgument("get delete", CommandIntent.SOURCE_GET,
                "source", "delete");
        expectArgument("get 123", CommandIntent.SOURCE_GET,
                "source", "123");
        expectArgument("get \"my source.k\"", CommandIntent.SOURCE_GET,
                "source", "my source.k");
        expectArgument("put \"My Source.k\"", CommandIntent.SOURCE_PUT,
                "source", "My Source.k");
        expectArgument("get \"a\\\"b.k\"", CommandIntent.SOURCE_GET,
                "source", "a\"b.k");
        expectArgument("get \"dir\\\\name.k\"", CommandIntent.SOURCE_GET,
                "source", "dir\\name.k");
        reject("delete two names", EXTRA_ARGUMENT);
        reject("get two names", EXTRA_ARGUMENT);
        reject("get \"unfinished", UNTERMINATED_QUOTE);
    }

    private void storageFamily() throws Exception {
        expect("storage", CommandIntent.STORAGE_STATUS);
        expectArgument("st u demo", CommandIntent.STORAGE_USE,
                "name", "demo");
        expectArgument("storage use close", CommandIntent.STORAGE_USE,
                "name", "close");
        expectArgument("storage use \"test base\"", CommandIntent.STORAGE_USE,
                "name", "test base");
        expect("st c", CommandIntent.STORAGE_CLOSE);
        expectArgument("st d demo", CommandIntent.STORAGE_DROP,
                "name", "demo");
        expectArgument("st r demo", CommandIntent.STORAGE_REINDEX,
                "name", "demo");
        reject("storage use", MISSING_ARGUMENT);
        reject("storage demo", INVALID_GRAMMAR);
        reject("storage close foo", EXTRA_ARGUMENT);
    }

    private void systemFamily() throws Exception {
        expect("erase", CommandIntent.ERASE);
        expect("help", CommandIntent.HELP);
        expect("quit", CommandIntent.QUIT);
        reject("help extra", EXTRA_ARGUMENT);
        reject("quit now", EXTRA_ARGUMENT);
    }

    private void canonicalEcho() throws Exception {
        expectCanonical("rules", "rule all");
        expectCanonical("r 17", "rule 17");
        expectCanonical("r c 17", "rule comment 17");
        expectCanonical("f s 8", "function source 8");
        expectCanonical("b p father", "base predicate father");
        expectCanonical("v o x d, y a", "values order x desc, y asc");
        expectCanonical("so t 42", "solution tree 42");
        expectCanonical("w a 0", "when accept 0");
        expectCanonical("t s", "transaction start");
        expectCanonical("st u close", "storage use close");
        expectCanonical("g", "get");
        expectCanonical("d", "delete");
        expectCanonical("g \"my source.k\"", "get \"my source.k\"");
        expectCanonical("?father(John,Tom)", "?father(John,Tom)");
    }

    private void helpRegistry() {
        boolean[] documented = new boolean[CommandIntent.values().length];
        for (CommandRegistry.Definition definition : CommandRegistry.definitions()) {
            documented[definition.getIntent().ordinal()] = true;
        }
        for (CommandIntent intent : CommandIntent.values()) {
            check(documented[intent.ordinal()],
                    "missing registry metadata for " + intent);
        }
        check(CommandRegistry.definitions().size() >= CommandIntent.values().length,
                "registry may contain additional documented syntax aliases");

        String help = new CommandHelpRenderer().render();
        check(help.contains("rule <id>"), "help contains rule object syntax");
        check(help.contains("\n  rules\n"), "help contains plural rules alias");
        check(help.contains("values order <field>"), "help contains values syntax");
        check(help.contains("when accept <index>"), "help contains hypothesis addressing");
        check(help.contains("delete [<source>]"), "help contains safe bare delete syntax");
    }

    private void expect(String source, CommandIntent intent) throws Exception {
        CommandInvocation parsed = parser.parse(source);
        check(!parsed.isCoreLanguage(), source + " must be command");
        check(parsed.getIntent() == intent,
                source + " expected " + intent + " but got " + parsed.getIntent());
    }

    private void expectCore(String source) throws Exception {
        CommandInvocation parsed = parser.parse(source);
        check(parsed.isCoreLanguage(), source + " must bypass command parsing");
        check(source.equals(parsed.getRaw()), source + " raw Core line preserved");
    }

    private void expectLong(String source,
                            CommandIntent intent,
                            String name,
                            long value) throws Exception {
        CommandInvocation parsed = parser.parse(source);
        check(parsed.getIntent() == intent, source + " intent");
        check(Long.valueOf(value).equals(parsed.getArgument(name)),
                source + " argument " + name);
    }

    private void expectArgument(String source,
                                CommandIntent intent,
                                String name,
                                String value) throws Exception {
        CommandInvocation parsed = parser.parse(source);
        check(parsed.getIntent() == intent, source + " intent");
        check(value.equals(parsed.getArgument(name)), source + " argument " + name);
    }

    private void expectCanonical(String source, String expected) throws Exception {
        String actual = formatter.format(parser.parse(source));
        check(expected.equals(actual),
                source + " expected canonical '" + expected + "' but got '" + actual + "'");
    }

    private void reject(String source,
                        CommandParseException.Reason reason) throws Exception {
        try {
            parser.parse(source);
            throw new AssertionError(source + " should reject as " + reason);
        } catch (CommandParseException rejected) {
            check(rejected.getReason() == reason,
                    source + " expected rejection " + reason
                            + " but got " + rejected.getReason());
        }
    }

    private void check(boolean condition, String message) {
        ++assertions;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}