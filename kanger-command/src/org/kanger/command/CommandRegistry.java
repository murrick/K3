/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single metadata authority for the canonical KANGER command language.
 *
 * <p>The registry owns command/family spellings, family-local keyword stems,
 * explicit top-level aliases and help metadata. It contains no runtime
 * operation bindings.</p>
 */
public final class CommandRegistry {

    public enum Family {
        BASE,
        DELETE,
        ERASE,
        FUNCTION,
        GET,
        HELP,
        PREDICATE,
        PUT,
        QUIT,
        RULE,
        SOLUTION,
        STATUS,
        STORAGE,
        TRANSACTION,
        VALUES,
        WHEN
    }

    public enum Keyword {
        ALL,
        PRODUCED,
        LEVEL,
        TREE,
        COMMENT,
        SOURCE,
        PREDICATE,
        ORDER,
        ASC,
        DESC,
        ACCEPT,
        START,
        COMMIT,
        ROLLBACK,
        SQUASH,
        USE,
        CLOSE,
        DROP,
        REINDEX,
        CORE,
        OBJECTS,
        TRANSACTION,
        STORAGE,
        SESSION,
        RUNTIME
    }

    public static final class Definition {
        private final CommandIntent intent;
        private final String syntax;
        private final String helpSection;
        private final String summary;
        private final Map<String, String> argumentDescriptions;
        private final List<String> familySpellings;
        private final List<String> aliases;
        private final int displayOrder;

        private Definition(CommandIntent intent,
                           String syntax,
                           String helpSection,
                           String summary,
                           Map<String, String> argumentDescriptions,
                           List<String> familySpellings,
                           List<String> aliases,
                           int displayOrder) {
            this.intent = intent;
            this.syntax = syntax;
            this.helpSection = helpSection;
            this.summary = summary;
            this.argumentDescriptions = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(argumentDescriptions));
            this.familySpellings = Collections.unmodifiableList(
                    new ArrayList<String>(familySpellings));
            this.aliases = Collections.unmodifiableList(
                    new ArrayList<String>(aliases));
            this.displayOrder = displayOrder;
        }

        public CommandIntent getIntent() {
            return intent;
        }

        public String getSyntax() {
            return syntax;
        }

        public String getHelpSection() {
            return helpSection;
        }

        public String getSummary() {
            return summary;
        }

        public Map<String, String> getArgumentDescriptions() {
            return argumentDescriptions;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public List<String> getFamilySpellings() {
            return familySpellings;
        }

        public int getDisplayOrder() {
            return displayOrder;
        }
    }

    private static final Map<Family, List<String>> FAMILY_WORDS =
            new EnumMap<Family, List<String>>(Family.class);
    private static final Map<Family, Map<Keyword, List<String>>> KEYWORDS =
            new EnumMap<Family, Map<Keyword, List<String>>>(Family.class);
    private static final List<Definition> DEFINITIONS = new ArrayList<Definition>();

    static {
        family(Family.BASE, "base");
        family(Family.DELETE, "delete");
        family(Family.ERASE, "erase");
        family(Family.FUNCTION, "function", "functions");
        family(Family.GET, "get");
        family(Family.HELP, "help");
        family(Family.PREDICATE, "predicate", "predicates");
        family(Family.PUT, "put");
        family(Family.QUIT, "quit");
        family(Family.RULE, "rule", "rules");
        family(Family.SOLUTION, "solution", "solutions");
        family(Family.STATUS, "status");
        family(Family.STORAGE, "storage");
        family(Family.TRANSACTION, "transaction");
        family(Family.VALUES, "values");
        family(Family.WHEN, "when");

        keyword(Family.RULE, Keyword.ALL, "all");
        keyword(Family.RULE, Keyword.PRODUCED, "produced");
        keyword(Family.RULE, Keyword.LEVEL, "level");
        keyword(Family.RULE, Keyword.TREE, "tree");
        keyword(Family.RULE, Keyword.COMMENT, "comment");

        keyword(Family.FUNCTION, Keyword.SOURCE, "source");

        keyword(Family.BASE, Keyword.PREDICATE, "predicate", "predicates");
        keyword(Family.BASE, Keyword.TREE, "tree");

        keyword(Family.VALUES, Keyword.ORDER, "order");
        keyword(Family.VALUES, Keyword.ASC, "asc");
        keyword(Family.VALUES, Keyword.DESC, "desc");

        keyword(Family.SOLUTION, Keyword.TREE, "tree");

        keyword(Family.WHEN, Keyword.ACCEPT, "accept");

        keyword(Family.TRANSACTION, Keyword.START, "start");
        keyword(Family.TRANSACTION, Keyword.COMMIT, "commit");
        keyword(Family.TRANSACTION, Keyword.ROLLBACK, "rollback");
        keyword(Family.TRANSACTION, Keyword.SQUASH, "squash");

        keyword(Family.STORAGE, Keyword.USE, "use");
        keyword(Family.STORAGE, Keyword.CLOSE, "close");
        keyword(Family.STORAGE, Keyword.DROP, "drop");
        keyword(Family.STORAGE, Keyword.REINDEX, "reindex");

        keyword(Family.STATUS, Keyword.CORE, "core");
        keyword(Family.STATUS, Keyword.OBJECTS, "objects");
        keyword(Family.STATUS, Keyword.TRANSACTION, "transaction");
        keyword(Family.STATUS, Keyword.LEVEL, "levels");
        keyword(Family.STATUS, Keyword.STORAGE, "storage");
        keyword(Family.STATUS, Keyword.SESSION, "session");
        keyword(Family.STATUS, Keyword.RUNTIME, "runtime");

        int n = 0;
        define(CommandIntent.RULE_STATUS, "rule", "RULE",
                "Show current primary rule context. The rule/rules family spellings are synonymous.",
                noArgs(), n++);
        define(CommandIntent.RULE_SHOW, "rule <id>", "RULE", "Show one rule by runtime ID.",
                args("id", "Rule runtime identifier."), n++);
        define(CommandIntent.RULE_ALL, "rule all", "RULE", "Show rules and produced statements.",
                noArgs(), n++);
        define(CommandIntent.RULE_PRODUCED, "rule produced", "RULE", "Show produced/generated rules.",
                noArgs(), n++);
        define(CommandIntent.RULE_LEVEL, "rule level [<n>]", "RULE",
                "Show rules grouped by published user transaction level, or one level when n is supplied.",
                args("n", "Optional transaction level."), n++);
        define(CommandIntent.RULE_TREE, "rule tree <id>", "RULE", "Show the compiled structural tree of one rule.",
                args("id", "Rule runtime identifier."), n++);
        define(CommandIntent.RULE_COMMENT_GET, "rule comment <id>", "RULE", "Show a rule comment.",
                args("id", "Rule runtime identifier."), n++);
        define(CommandIntent.RULE_COMMENT_SET, "rule comment <id> <text...>", "RULE", "Set or clear a rule comment.",
                args("id", "Rule runtime identifier.", "text", "Free comment text; explicit empty text clears it."), n++);

        define(CommandIntent.FUNCTIONS, "functions", "FUNCTION", "Show defined functions.", noArgs(), n++);
        define(CommandIntent.FUNCTION_SHOW, "function <id>", "FUNCTION", "Show one function by runtime ID.",
                args("id", "Function runtime identifier."), n++);
        define(CommandIntent.FUNCTION_SOURCE, "function source <id>", "FUNCTION", "Show source of one function.",
                args("id", "Function runtime identifier."), n++);

        define(CommandIntent.BASE_STATUS, "base", "BASE", "Show current unambiguous base statements.", noArgs(), n++);
        defineWithFamilySpellings(CommandIntent.BASE_PREDICATES,
                "base predicates", "BASE",
                "Show predicates known in the semantic context.",
                noArgs(), familySpellings("predicate", "predicates"), n++);
        defineWithFamilySpellings(CommandIntent.BASE_PREDICATE,
                "base predicate <id|name>", "BASE",
                "Show base statements for one predicate.",
                args("id|name", "Predicate runtime ID or name."),
                familySpellings("predicate <id|name>",
                        "predicates <id|name>"), n++);
        define(CommandIntent.BASE_TREE, "base tree <statement-id>", "BASE", "Show provenance of one base statement.",
                args("statement-id", "Statement runtime identifier."), n++);

        define(CommandIntent.VALUES, "values", "VALUES", "Show the current Values rowset using configured default ordering.",
                noArgs(), n++);
        define(CommandIntent.VALUES_ORDER,
                "values order <field> [asc|desc] [, <field> [asc|desc]]...",
                "VALUES", "Show the current Values rowset with an invocation-local multi-key order.",
                args("field", "Exported Values field.",
                        "asc|desc", "Direction for the preceding field; omitted means asc."), n++);

        define(CommandIntent.SOLUTIONS, "solutions", "SOLUTION", "Show the complete current Solutions set.", noArgs(), n++);
        define(CommandIntent.SOLUTION_SHOW, "solution <id>", "SOLUTION", "Show one Solution by its actual IRule runtime ID.",
                args("id", "Solution/IRule runtime identifier."), n++);
        define(CommandIntent.SOLUTION_TREE, "solution tree <id>", "SOLUTION", "Show provenance of one Solution.",
                args("id", "Solution/IRule runtime identifier."), n++);

        define(CommandIntent.WHEN_STATUS, "when", "WHEN", "Show the current hypothesis rowset.", noArgs(), n++);
        define(CommandIntent.WHEN_ACCEPT, "when accept <index>", "WHEN", "Accept one hypothesis by zero-based row index.",
                args("index", "Zero-based index in the current hypothesis rowset."), n++);

        define(CommandIntent.TX_STATUS, "transaction", "TRANSACTION", "Show current transaction state.", noArgs(), n++);
        define(CommandIntent.TX_START, "transaction start", "TRANSACTION", "Start a child transaction.",
                noArgs(), aliases("start", "sta"), n++);
        define(CommandIntent.TX_COMMIT, "transaction commit", "TRANSACTION",
                "Commit the current transaction or qualified root checkpoint.",
                noArgs(), aliases("commit"), n++);
        define(CommandIntent.TX_ROLLBACK, "transaction rollback", "TRANSACTION", "Rollback the current child transaction.",
                noArgs(), aliases("rollback"), n++);
        define(CommandIntent.TX_SQUASH, "transaction squash", "TRANSACTION",
                "Collapse all explicit transaction history above U0 into one U1 without changing U0.",
                noArgs(), aliases("squash"), n++);

        define(CommandIntent.SOURCE_GET, "get [<source>]", "SOURCE",
                "List server-side sources when omitted; load and compile one source when named.",
                args("source", "Optional source logical name."), n++);
        define(CommandIntent.SOURCE_PUT, "put <source>", "SOURCE", "Persist the current source under one logical name.",
                args("source", "Source logical name."), n++);
        define(CommandIntent.SOURCE_DELETE, "delete [<source>]", "SOURCE",
                "List server-side sources when omitted; delete one source when named.",
                args("source", "Optional source logical name."), n++);

        define(CommandIntent.STORAGE_STATUS, "storage", "STORAGE", "Show available storages and the current/open storage.",
                noArgs(), aliases("use"), n++);
        define(CommandIntent.STORAGE_USE, "storage use <name>", "STORAGE", "Open or create one storage.",
                args("name", "Storage logical name."), aliases("use <name>"), n++);
        define(CommandIntent.STORAGE_CLOSE, "storage close", "STORAGE", "Close the current storage.",
                noArgs(), aliases("close"), n++);
        define(CommandIntent.STORAGE_DROP, "storage drop <name>", "STORAGE", "Drop one explicitly named storage.",
                args("name", "Storage logical name."), aliases("drop <name>"), n++);
        define(CommandIntent.STORAGE_REINDEX, "storage reindex <name>", "STORAGE", "Reindex one explicitly named storage.",
                args("name", "Storage logical name."), aliases("reindex <name>"), n++);

        define(CommandIntent.STATUS,
                "status [core [objects|transaction|levels]|storage|session|runtime]",
                "STATUS",
                "Show canonical read-only product status or one status projection.",
                args("section", "Optional status section.",
                        "subsection", "Optional core subsection."), n++);

        define(CommandIntent.ERASE, "erase", "SYSTEM / SESSION", "Clear the current workspace using qualified runtime semantics.",
                noArgs(), n++);
        define(CommandIntent.HELP, "help", "SYSTEM / SESSION", "Show canonical command help generated from this registry.",
                noArgs(), n++);
        define(CommandIntent.QUIT, "quit", "SYSTEM / SESSION", "Terminate the current session.", noArgs(), n++);
    }

    private CommandRegistry() {
    }

    public static List<Definition> definitions() {
        return Collections.unmodifiableList(DEFINITIONS);
    }

    public static Definition definition(CommandIntent intent) {
        for (Definition definition : DEFINITIONS) {
            if (definition.intent == intent) {
                return definition;
            }
        }
        return null;
    }

    /**
     * Expands an explicitly registered top-level alias to its canonical command
     * production while preserving the original argument tail verbatim.
     *
     * <p>Aliases and canonical command families share the same minimum-unique-
     * prefix namespace. Exact spellings win over prefixes; ambiguous heads are
     * rejected before canonical parsing.</p>
     */
    public static String expandAlias(String line) throws CommandParseException {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            ++end;
        }
        String token = trimmed.substring(0, end).toLowerCase(Locale.ROOT);

        boolean hasTail = hasTail(trimmed, end);
        Set<Family> exactFamilies = matchingFamilies(token, true);
        List<AliasMatch> exactAliases = matchingAliases(token, true);
        AliasMatch exactAlias = exactAliases.isEmpty()
                ? null : selectAlias(exactAliases, hasTail, token);
        int exactMatches = exactFamilies.size() + (exactAlias == null ? 0 : 1);
        if (exactMatches > 1) {
            throw new CommandParseException(
                    CommandParseException.Reason.AMBIGUOUS_PREFIX,
                    "Ambiguous command head " + token);
        }
        if (exactMatches == 1) {
            return exactAlias == null
                    ? trimmed : expand(trimmed, end, exactAlias);
        }

        Set<Family> familyMatches = matchingFamilies(token, false);
        Map<String, List<AliasMatch>> aliasGroups =
                new LinkedHashMap<String, List<AliasMatch>>();
        for (AliasMatch match : matchingAliases(token, false)) {
            String head = aliasHead(match.syntax);
            List<AliasMatch> group = aliasGroups.get(head);
            if (group == null) {
                group = new ArrayList<AliasMatch>();
                aliasGroups.put(head, group);
            }
            group.add(match);
        }

        Map<Definition, AliasMatch> aliasTargets =
                new LinkedHashMap<Definition, AliasMatch>();
        for (List<AliasMatch> group : aliasGroups.values()) {
            AliasMatch selected = selectAlias(group, hasTail, token);
            if (!aliasTargets.containsKey(selected.definition)) {
                aliasTargets.put(selected.definition, selected);
            }
        }

        int matches = familyMatches.size() + aliasTargets.size();
        if (matches > 1) {
            throw new CommandParseException(
                    CommandParseException.Reason.AMBIGUOUS_PREFIX,
                    "Ambiguous command prefix " + token);
        }
        if (matches == 1 && familyMatches.isEmpty()) {
            return expand(trimmed, end, aliasTargets.values().iterator().next());
        }
        return trimmed;
    }

    private static Set<Family> matchingFamilies(String token, boolean exact) {
        Set<Family> matches = new LinkedHashSet<Family>();
        for (Map.Entry<Family, List<String>> entry : FAMILY_WORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (exact ? word.equals(token) : word.startsWith(token)) {
                    matches.add(entry.getKey());
                    break;
                }
            }
        }
        return matches;
    }

    private static List<AliasMatch> matchingAliases(String token,
                                                     boolean exact) {
        List<AliasMatch> matches = new ArrayList<AliasMatch>();
        for (Definition definition : DEFINITIONS) {
            for (String alias : definition.aliases) {
                String head = aliasHead(alias);
                if (exact ? head.equals(token) : head.startsWith(token)) {
                    matches.add(new AliasMatch(definition, alias));
                }
            }
        }
        return matches;
    }

    /**
     * Resolves several productions sharing one alias head by invocation arity.
     * A single production is always expanded so its canonical parser can retain
     * the precise MISSING_ARGUMENT / EXTRA_ARGUMENT diagnostic.
     */
    private static AliasMatch selectAlias(List<AliasMatch> matches,
                                          boolean hasTail,
                                          String token)
            throws CommandParseException {
        Map<Definition, AliasMatch> distinct =
                new LinkedHashMap<Definition, AliasMatch>();
        for (AliasMatch match : matches) {
            if (!distinct.containsKey(match.definition)) {
                distinct.put(match.definition, match);
            }
        }
        if (distinct.size() == 1) {
            return distinct.values().iterator().next();
        }

        Map<Definition, AliasMatch> arityMatches =
                new LinkedHashMap<Definition, AliasMatch>();
        for (AliasMatch match : matches) {
            if (matchesTail(match.syntax, hasTail)
                    && !arityMatches.containsKey(match.definition)) {
                arityMatches.put(match.definition, match);
            }
        }
        if (arityMatches.size() == 1) {
            return arityMatches.values().iterator().next();
        }
        throw new CommandParseException(
                CommandParseException.Reason.AMBIGUOUS_PREFIX,
                "Ambiguous command alias " + token);
    }

    private static boolean matchesTail(String syntax, boolean hasTail) {
        boolean requiredTail = syntax.indexOf('<') >= 0;
        boolean optionalTail = syntax.indexOf('[') >= 0;
        return hasTail ? requiredTail || optionalTail : !requiredTail;
    }

    private static boolean hasTail(String line, int headEnd) {
        int tail = headEnd;
        while (tail < line.length() && Character.isWhitespace(line.charAt(tail))) {
            ++tail;
        }
        return tail < line.length();
    }

    public static Family resolveFamily(String token) throws CommandParseException {
        return resolve(token, FAMILY_WORDS, null);
    }

    public static Keyword resolveKeyword(Family family,
                                         String token,
                                         Keyword... allowed)
            throws CommandParseException {
        Map<Keyword, List<String>> familyKeywords = KEYWORDS.get(family);
        if (familyKeywords == null) {
            throw new CommandParseException(
                    CommandParseException.Reason.UNKNOWN_KEYWORD,
                    "No subcommands are defined for " + family.name().toLowerCase(Locale.ROOT));
        }
        Set<Keyword> accepted = allowed == null || allowed.length == 0
                ? EnumSet.allOf(Keyword.class)
                : EnumSet.copyOf(Arrays.asList(allowed));
        Map<Keyword, List<String>> scoped =
                new EnumMap<Keyword, List<String>>(Keyword.class);
        for (Map.Entry<Keyword, List<String>> entry : familyKeywords.entrySet()) {
            if (accepted.contains(entry.getKey())) {
                scoped.put(entry.getKey(), entry.getValue());
            }
        }
        return resolve(token, scoped, family);
    }

    public static boolean isExact(String token, String word) {
        return token != null && word != null && token.equalsIgnoreCase(word);
    }

    public static boolean isExactPluralFamilyWord(Family family, String token) {
        return (family == Family.FUNCTION && isExact(token, "functions"))
                || (family == Family.SOLUTION && isExact(token, "solutions"));
    }

    public static boolean isExactSingularFamilyWord(Family family, String token) {
        return (family == Family.FUNCTION && isExact(token, "function"))
                || (family == Family.SOLUTION && isExact(token, "solution"));
    }

    private static <T extends Enum<T>> T resolve(String token,
                                                  Map<T, List<String>> words,
                                                  Family localFamily)
            throws CommandParseException {
        if (token == null || token.isEmpty()) {
            throw new CommandParseException(
                    CommandParseException.Reason.UNKNOWN_KEYWORD,
                    "Empty keyword");
        }
        String probe = token.toLowerCase(Locale.ROOT);

        for (Map.Entry<T, List<String>> entry : words.entrySet()) {
            for (String word : entry.getValue()) {
                if (word.equals(probe)) {
                    return entry.getKey();
                }
            }
        }

        Set<T> matches = new LinkedHashSet<T>();
        for (Map.Entry<T, List<String>> entry : words.entrySet()) {
            for (String word : entry.getValue()) {
                if (word.startsWith(probe)) {
                    matches.add(entry.getKey());
                    break;
                }
            }
        }
        if (matches.size() == 1) {
            return matches.iterator().next();
        }
        if (matches.size() > 1) {
            throw new CommandParseException(
                    CommandParseException.Reason.AMBIGUOUS_PREFIX,
                    "Ambiguous keyword prefix " + token);
        }
        throw new CommandParseException(
                CommandParseException.Reason.UNKNOWN_KEYWORD,
                "Unknown " + (localFamily == null
                        ? "command"
                        : localFamily.name().toLowerCase(Locale.ROOT) + " keyword")
                        + " " + token);
    }

    private static void family(Family family, String... words) {
        FAMILY_WORDS.put(family, lower(words));
    }

    private static void keyword(Family family, Keyword keyword, String... words) {
        Map<Keyword, List<String>> familyKeywords = KEYWORDS.get(family);
        if (familyKeywords == null) {
            familyKeywords = new EnumMap<Keyword, List<String>>(Keyword.class);
            KEYWORDS.put(family, familyKeywords);
        }
        familyKeywords.put(keyword, lower(words));
    }

    private static List<String> lower(String... words) {
        List<String> result = new ArrayList<String>();
        for (String word : words) {
            result.add(word.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableList(result);
    }

    private static void define(CommandIntent intent,
                               String syntax,
                               String section,
                               String summary,
                               Map<String, String> arguments,
                               int order) {
        define(intent, syntax, section, summary, arguments,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(), order);
    }

    private static void define(CommandIntent intent,
                               String syntax,
                               String section,
                               String summary,
                               Map<String, String> arguments,
                               List<String> aliases,
                               int order) {
        define(intent, syntax, section, summary, arguments,
                Collections.<String>emptyList(), aliases, order);
    }

    private static void defineWithFamilySpellings(
                               CommandIntent intent,
                               String syntax,
                               String section,
                               String summary,
                               Map<String, String> arguments,
                               List<String> familySpellings,
                               int order) {
        define(intent, syntax, section, summary, arguments,
                familySpellings, Collections.<String>emptyList(), order);
    }

    private static void define(CommandIntent intent,
                               String syntax,
                               String section,
                               String summary,
                               Map<String, String> arguments,
                               List<String> familySpellings,
                               List<String> aliases,
                               int order) {
        DEFINITIONS.add(new Definition(intent, syntax, section, summary,
                arguments, familySpellings, aliases, order));
    }

    private static List<String> aliases(String... values) {
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, values);
        return result;
    }

    private static List<String> familySpellings(String... values) {
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, values);
        return result;
    }

    private static String aliasHead(String syntax) {
        String trimmed = syntax.trim().toLowerCase(Locale.ROOT);
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String canonicalPrefix(String syntax) {
        int placeholder = syntax.length();
        int angle = syntax.indexOf('<');
        int square = syntax.indexOf('[');
        if (angle >= 0) {
            placeholder = Math.min(placeholder, angle);
        }
        if (square >= 0) {
            placeholder = Math.min(placeholder, square);
        }
        return syntax.substring(0, placeholder).trim();
    }

    private static String expand(String line, int headEnd, AliasMatch match) {
        String canonical = canonicalPrefix(match.definition.syntax);
        int tail = headEnd;
        while (tail < line.length() && Character.isWhitespace(line.charAt(tail))) {
            ++tail;
        }
        return tail >= line.length()
                ? canonical
                : canonical + " " + line.substring(tail);
    }

    private static Map<String, String> noArgs() {
        return Collections.emptyMap();
    }

    private static Map<String, String> args(String... pairs) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static final class AliasMatch {
        private final Definition definition;
        private final String syntax;

        private AliasMatch(Definition definition, String syntax) {
            this.definition = definition;
            this.syntax = syntax;
        }
    }
}
