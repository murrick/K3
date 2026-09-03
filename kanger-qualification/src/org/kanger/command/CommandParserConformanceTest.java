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
        predicateFamilySpellings();
        valuesFamily();
        solutionFamily();
        whenFamily();
        transactionFamily();
        sourceFamily();
        storageFamily();
        aliasVocabulary();
        systemFamily();
        canonicalEcho();
        helpRegistry();
        sharedClientVocabulary();
    }

    private void sharedClientVocabulary() throws Exception {
        for (ClientVocabularyCorpus.Case one : ClientVocabularyCorpus.load()) {
            if (one.isAccepted()) {
                CommandInvocation invocation = parser.parse(one.getLine());
                check(one.getResult().equals(invocation.getIntent().name()),
                        one + " intent " + invocation.getIntent());
                check(one.getCanonical().equals(formatter.format(invocation)),
                        one + " canonical echo " + formatter.format(invocation));
                if (one.getArgumentName() != null) {
                    check(one.getArgumentValue().equals(String.valueOf(
                                    invocation.getArgument(one.getArgumentName()))),
                            one + " argument " + one.getArgumentName());
                }
                continue;
            }
            try {
                parser.parse(one.getLine());
                check(false, one + " unexpectedly accepted");
            } catch (CommandParseException rejected) {
                check(one.getResult().equals(rejected.getReason().name()),
                        one + " reason " + rejected.getReason());
            }
        }
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
        reject("c", AMBIGUOUS_PREFIX);
        expect("co", CommandIntent.TX_COMMIT);
        expect("cl", CommandIntent.STORAGE_CLOSE);
        reject("d", AMBIGUOUS_PREFIX);
        expect("de", CommandIntent.SOURCE_DELETE);
        expect("de foo.k", CommandIntent.SOURCE_DELETE);
        expectArgument("dr demo", CommandIntent.STORAGE_DROP,
                "name", "demo");
        expect("e", CommandIntent.ERASE);
        expect("f", CommandIntent.FUNCTIONS);
        expect("g", CommandIntent.SOURCE_GET);
        expect("g foo.k", CommandIntent.SOURCE_GET);
        expect("h", CommandIntent.HELP);
        reject("p", AMBIGUOUS_PREFIX);
        expect("pr", CommandIntent.BASE_PREDICATES);
        expectArgument("pr father", CommandIntent.BASE_PREDICATE,
                "predicate", "father");
        expectArgument("pu foo.k", CommandIntent.SOURCE_PUT,
                "source", "foo.k");
        expect("q", CommandIntent.QUIT);
        reject("r", AMBIGUOUS_PREFIX);
        expect("ru", CommandIntent.RULE_STATUS);
        expect("ro", CommandIntent.TX_ROLLBACK);
        expectArgument("re demo", CommandIntent.STORAGE_REINDEX,
                "name", "demo");
        expect("so", CommandIntent.SOLUTIONS);
        expect("sq", CommandIntent.TX_SQUASH);
        reject("st", AMBIGUOUS_PREFIX);
        reject("sta", AMBIGUOUS_PREFIX);
        expect("star", CommandIntent.TX_START);
        expect("stat", CommandIntent.STATUS);
        expect("sto", CommandIntent.STORAGE_STATUS);
        expect("t", CommandIntent.TX_STATUS);
        expect("u", CommandIntent.STORAGE_STATUS);
        expectArgument("u demo", CommandIntent.STORAGE_USE, "name", "demo");
        expect("v", CommandIntent.VALUES);
        expect("w", CommandIntent.WHEN_STATUS);
        reject("s", AMBIGUOUS_PREFIX);
        reject("z", UNKNOWN_KEYWORD);
    }

    private void ruleFamily() throws Exception {
        expect("rule", CommandIntent.RULE_STATUS);
        expect("rules", CommandIntent.RULE_STATUS);
        expectLong("rule 17", CommandIntent.RULE_SHOW, "id", 17L);
        expectLong("rules 17", CommandIntent.RULE_SHOW, "id", 17L);
        expect("ru a", CommandIntent.RULE_ALL);
        expect("rules all", CommandIntent.RULE_ALL);
        expect("ru p", CommandIntent.RULE_PRODUCED);
        expect("rules produced", CommandIntent.RULE_PRODUCED);

        CommandInvocation aggregate = parser.parse("ru l");
        check(aggregate.getIntent() == CommandIntent.RULE_LEVEL,
                "bare rule level aggregate intent");
        check(!aggregate.getArguments().containsKey("level"),
                "bare rule level must not synthesize transaction level");
        expect("rules level", CommandIntent.RULE_LEVEL);

        expectLong("ru l 2", CommandIntent.RULE_LEVEL, "level", 2L);
        expectLong("rules level 2", CommandIntent.RULE_LEVEL, "level", 2L);
        expectLong("ru t 17", CommandIntent.RULE_TREE, "id", 17L);
        expectLong("rules tree 17", CommandIntent.RULE_TREE, "id", 17L);
        expectLong("ru c 17", CommandIntent.RULE_COMMENT_GET, "id", 17L);
        expectLong("rules comment 17", CommandIntent.RULE_COMMENT_GET, "id", 17L);

        CommandInvocation set = parser.parse("rule comment 17 Important rule");
        check(set.getIntent() == CommandIntent.RULE_COMMENT_SET,
                "rule comment set intent");
        check(Long.valueOf(17L).equals(set.getArgument("id")),
                "rule comment id");
        check("Important rule".equals(set.getArgument("text")),
                "rule comment free tail");

        CommandInvocation pluralSet = parser.parse("rules comment 17 Important rule");
        check(pluralSet.getIntent() == CommandIntent.RULE_COMMENT_SET,
                "rules comment set synonym intent");
        check("Important rule".equals(pluralSet.getArgument("text")),
                "rules comment free tail");

        CommandInvocation clear = parser.parse("ru c 17 \"\"");
        check("".equals(clear.getArgument("text")),
                "rule comment explicit empty");

        reject("rule show 17", INVALID_GRAMMAR);
        reject("rules show 17", INVALID_GRAMMAR);
        reject("rule tree", MISSING_ARGUMENT);
        reject("rule all 17", EXTRA_ARGUMENT);
        reject("rules all 17", EXTRA_ARGUMENT);
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
        reject("tree 314", UNKNOWN_KEYWORD);
    }

    private void predicateFamilySpellings() throws Exception {
        expect("predicate", CommandIntent.BASE_PREDICATES);
        expect("predicates", CommandIntent.BASE_PREDICATES);
        expectArgument("predicate father", CommandIntent.BASE_PREDICATE,
                "predicate", "father");
        expectArgument("predicates father", CommandIntent.BASE_PREDICATE,
                "predicate", "father");

        CommandInvocation byId = parser.parse("predicates 12");
        check(byId.getIntent() == CommandIntent.BASE_PREDICATE,
                "predicates family spelling by id intent");
        check(Long.valueOf(12L).equals(byId.getArgument("predicate")),
                "predicates family spelling by id");

        reject("predicate father extra", EXTRA_ARGUMENT);
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
        expect("t st", CommandIntent.TX_START);
        expect("t c", CommandIntent.TX_COMMIT);
        expect("t r", CommandIntent.TX_ROLLBACK);
        expect("t sq", CommandIntent.TX_SQUASH);
        reject("t s", AMBIGUOUS_PREFIX);
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
        expectArgument("get foo", CommandIntent.SOURCE_GET,
                "source", "foo.k");
        expectArgument("get foo.K", CommandIntent.SOURCE_GET,
                "source", "foo.k");
        expectArgument("put foo.txt", CommandIntent.SOURCE_PUT,
                "source", "foo.txt.k");
        expectArgument("get delete", CommandIntent.SOURCE_GET,
                "source", "delete.k");
        expectArgument("get 123", CommandIntent.SOURCE_GET,
                "source", "123.k");
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
        expectArgument("sto u demo", CommandIntent.STORAGE_USE,
                "name", "demo");
        expectArgument("storage use close", CommandIntent.STORAGE_USE,
                "name", "close");
        expectArgument("storage use \"test base\"", CommandIntent.STORAGE_USE,
                "name", "test base");
        expect("sto c", CommandIntent.STORAGE_CLOSE);
        expectArgument("sto d demo", CommandIntent.STORAGE_DROP,
                "name", "demo");
        expectArgument("sto r demo", CommandIntent.STORAGE_REINDEX,
                "name", "demo");
        reject("storage use", MISSING_ARGUMENT);
        reject("storage demo", INVALID_GRAMMAR);
        reject("storage close foo", EXTRA_ARGUMENT);
    }

    private void aliasVocabulary() throws Exception {
        expect("start", CommandIntent.TX_START);
        expect("commit", CommandIntent.TX_COMMIT);
        expect("co", CommandIntent.TX_COMMIT);
        expect("rollback", CommandIntent.TX_ROLLBACK);
        expect("squash", CommandIntent.TX_SQUASH);
        reject("c", AMBIGUOUS_PREFIX);
        reject("commit now", EXTRA_ARGUMENT);

        expect("use", CommandIntent.STORAGE_STATUS);
        expectArgument("use demo", CommandIntent.STORAGE_USE,
                "name", "demo");
        expectArgument("us demo", CommandIntent.STORAGE_USE,
                "name", "demo");
        expectArgument("u demo", CommandIntent.STORAGE_USE,
                "name", "demo");
        expectArgument("use \"test base\"", CommandIntent.STORAGE_USE,
                "name", "test base");
        expect("close", CommandIntent.STORAGE_CLOSE);
        expectArgument("drop demo", CommandIntent.STORAGE_DROP,
                "name", "demo");
        expectArgument("reindex demo", CommandIntent.STORAGE_REINDEX,
                "name", "demo");
        reject("drop", MISSING_ARGUMENT);
        reject("reindex", MISSING_ARGUMENT);

        CommandInvocation commit = parser.parse("co");
        check("co".equals(commit.getRaw()),
                "commit alias preserves original raw input");
        CommandInvocation use = parser.parse("u \"test base\"");
        check("u \"test base\"".equals(use.getRaw()),
                "storage-use alias preserves original raw input");
    }

    private void systemFamily() throws Exception {
        expect("erase", CommandIntent.ERASE);
        expect("help", CommandIntent.HELP);
        expect("quit", CommandIntent.QUIT);
        reject("help extra", EXTRA_ARGUMENT);
        reject("quit now", EXTRA_ARGUMENT);
    }

    private void canonicalEcho() throws Exception {
        expectCanonical("rule", "rule");
        expectCanonical("rules", "rule");
        expectCanonical("rules all", "rule all");
        expectCanonical("rules 17", "rule 17");
        expectCanonical("ru l", "rule level");
        expectCanonical("rules level 2", "rule level 2");
        expectCanonical("ru c 17", "rule comment 17");
        expectCanonical("f s 8", "function source 8");
        expectCanonical("b p father", "base predicate father");
        expectCanonical("v o x d, y a", "values order x desc, y asc");
        expectCanonical("so t 42", "solution tree 42");
        expectCanonical("w a 0", "when accept 0");
        expectCanonical("t st", "transaction start");
        expectCanonical("t sq", "transaction squash");
        expectCanonical("star", "transaction start");
        expectCanonical("stat", "status");
        expectCanonical("co", "transaction commit");
        expectCanonical("ro", "transaction rollback");
        expectCanonical("sq", "transaction squash");
        expectCanonical("pr", "base predicates");
        expectCanonical("predicates father", "base predicate father");
        expectCanonical("sto u close", "storage use close");
        expectCanonical("u", "storage");
        expectCanonical("u close", "storage use close");
        expectCanonical("cl", "storage close");
        expectCanonical("dr demo", "storage drop demo");
        expectCanonical("re demo", "storage reindex demo");
        expectCanonical("g", "get");
        expectCanonical("de", "delete");
        expectCanonical("g foo", "get foo.k");
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
                "registry may contain additional documented syntax variants");

        CommandRegistry.Definition commit = CommandRegistry.definition(CommandIntent.TX_COMMIT);
        check(commit != null && commit.getAliases().contains("commit"),
                "transaction commit alias metadata");
        CommandRegistry.Definition start = CommandRegistry.definition(CommandIntent.TX_START);
        check(start != null && start.getAliases().contains("start"),
                "transaction start alias metadata");
        CommandRegistry.Definition rollback = CommandRegistry.definition(CommandIntent.TX_ROLLBACK);
        check(rollback != null && rollback.getAliases().contains("rollback"),
                "transaction rollback alias metadata");
        CommandRegistry.Definition squash = CommandRegistry.definition(CommandIntent.TX_SQUASH);
        check(squash != null && squash.getAliases().contains("squash"),
                "transaction squash alias metadata");
        CommandRegistry.Definition storage = CommandRegistry.definition(CommandIntent.STORAGE_STATUS);
        check(storage != null && storage.getAliases().contains("use"),
                "storage status alias metadata");
        CommandRegistry.Definition use = CommandRegistry.definition(CommandIntent.STORAGE_USE);
        check(use != null && use.getAliases().contains("use <name>"),
                "storage use alias metadata");
        CommandRegistry.Definition close = CommandRegistry.definition(CommandIntent.STORAGE_CLOSE);
        check(close != null && close.getAliases().contains("close"),
                "storage close alias metadata");
        CommandRegistry.Definition drop = CommandRegistry.definition(CommandIntent.STORAGE_DROP);
        check(drop != null && drop.getAliases().contains("drop <name>"),
                "storage drop alias metadata");
        CommandRegistry.Definition reindex = CommandRegistry.definition(CommandIntent.STORAGE_REINDEX);
        check(reindex != null && reindex.getAliases().contains("reindex <name>"),
                "storage reindex alias metadata");
        CommandRegistry.Definition predicates = CommandRegistry.definition(CommandIntent.BASE_PREDICATES);
        check(predicates != null && predicates.getAliases().isEmpty(),
                "predicate/predicates are family spellings, not aliases");

        String help = new CommandHelpRenderer().render();
        check(help.contains("rule <id>"), "help contains rule object syntax");
        check(help.contains("rule/rules family spellings are synonymous"),
                "help explains rule/rules synonymy");
        check(help.contains("rule level [<n>]"), "help contains optional rule level syntax");
        check(help.contains("values order <field>"), "help contains values syntax");
        check(help.contains("when accept <index>"), "help contains hypothesis addressing");
        check(help.contains("delete [<source>]"), "help contains safe bare delete syntax");
        check(help.contains("base predicates  (family spellings: predicate, predicates)"),
                "help exposes predicate/predicates family spellings");
        check(help.contains("base predicate <id|name>  (family spellings: predicate <id|name>, predicates <id|name>)"),
                "help exposes predicate/predicates argument spellings");
        check(help.contains("transaction start  (alias: start)"),
                "help contains start alias");
        check(help.contains("transaction commit  (alias: commit)"),
                "help contains commit alias");
        check(help.contains("transaction rollback  (alias: rollback)"),
                "help contains rollback alias");
        check(help.contains("transaction squash  (alias: squash)"),
                "help contains squash alias");
        check(help.contains("storage  (alias: use)"),
                "help contains storage status alias");
        check(help.contains("storage use <name>  (alias: use <name>)"),
                "help contains storage use alias");
        check(help.contains("storage close  (alias: close)"),
                "help contains close alias");
        check(help.contains("storage drop <name>  (alias: drop <name>)"),
                "help contains drop alias");
        check(help.contains("storage reindex <name>  (alias: reindex <name>)"),
                "help contains reindex alias");
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
