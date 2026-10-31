/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.List;

/**
 * Formats a parsed invocation with full canonical command spelling.
 *
 * <p>Formatting occurs after prefix/grammar normalization. The formatter is
 * presentation-only and contains no runtime dispatch behavior.</p>
 */
public final class CommandFormatter {

    public String format(CommandInvocation invocation) {
        if (invocation == null) {
            throw new IllegalArgumentException("invocation must not be null");
        }
        if (invocation.isCoreLanguage()) {
            return invocation.getRaw();
        }

        switch (invocation.getIntent()) {
            case RULE_STATUS:
                return "rule";
            case RULE_SHOW:
                return "rule " + number(invocation, "id");
            case RULE_ALL:
                return "rule all";
            case RULE_PRODUCED:
                return "rule produced";
            case RULE_LEVEL:
                return invocation.getArgument("level") == null
                        ? "rule level"
                        : "rule level " + number(invocation, "level");
            case RULE_TREE:
                return "rule tree " + number(invocation, "id");
            case RULE_STRUCTURE: return "rule structure "+number(invocation,"id");
            case RULE_BASE: return "rule base";
            case RULE_COMMENT_GET:
                return "rule comment " + number(invocation, "id");
            case RULE_COMMENT_SET:
                return "rule comment " + number(invocation, "id") + " "
                        + commentText(invocation.getArgument("text"));

            case FUNCTIONS:
                return "functions";
            case FUNCTION_SHOW:
                return "function " + number(invocation, "id");
            case FUNCTION_SOURCE:
                return "function source " + number(invocation, "id");

            case BASE_STATUS:
                return "base";
            case BASE_PREDICATES:
                return "predicates";
            case BASE_PREDICATE:
                return "predicate " + argument(invocation.getArgument("predicate"));
            case BASE_TREE:
                return "base tree " + number(invocation, "statementId");

            case VALUES:
                return "values";
            case VALUES_ORDER:
                return formatValuesOrder(invocation);

            case SOLUTIONS:
                return "solutions";
            case SOLUTION_SHOW:
                return "solution " + number(invocation, "id");
            case SOLUTION_TREE:
                return "solution tree " + number(invocation, "id");

            case WHEN_STATUS:
                return "when";
            case WHEN_ACCEPT:
                return "when accept " + number(invocation, "index");

            case TX_STATUS:
                return "transaction";
            case TX_START:
                return "transaction start";
            case TX_COMMIT:
                return optionalArgumentCommand("transaction commit", invocation.getArgument("description"));
            case TX_ROLLBACK:
                return "transaction rollback";
            case TX_SQUASH:
                return "transaction squash";

            case SOURCE_GET:
                return optionalArgumentCommand("get", invocation.getArgument("source"));
            case SOURCE_PUT:
                return "put " + argument(invocation.getArgument("source"));
            case SOURCE_DELETE:
                return optionalArgumentCommand("delete", invocation.getArgument("source"));

            case STATUS:
                return formatStatus(invocation);
            case TIMEZONE:
                return optionalArgumentCommand("options timezone", invocation.getArgument("zoneId"));
            case OPTIONS:
                String option = optionalArgumentCommand("options", invocation.getArgument("option"));
                option = optionalArgumentCommand(option, invocation.getArgument("optimization"));
                return optionalArgumentCommand(option, invocation.getArgument("value"));

            case STORAGE_STATUS:
                return "storage";
            case STORAGE_USE:
                return "storage use " + argument(invocation.getArgument("name"));
            case STORAGE_CLOSE:
                return "storage close";
            case STORAGE_DROP:
                return "storage drop " + argument(invocation.getArgument("name"));
            case STORAGE_REINDEX:
                return "storage reindex " + argument(invocation.getArgument("name"));

            case CTX_MODEL_WRITE: {
                org.kanger.ContextSignedModelRequest model=(org.kanger.ContextSignedModelRequest)invocation.getArgument("request");StringBuilder text=new StringBuilder("ctx candidate model write ").append(argument(model.statement));for(java.util.Map.Entry<String,Integer> e:model.limits.entrySet())if(!e.getValue().equals(org.kanger.ContextSignedModelRequest.DEFAULTS.get(e.getKey())))text.append(' ').append(e.getKey()).append('=').append(e.getValue());return text.toString();
            }
            case CTX_CANDIDATE:
            case CTX_CANDIDATE_WRITE:
                org.kanger.ContextUniversalCandidateRequest candidate=(org.kanger.ContextUniversalCandidateRequest)invocation.getArgument("request");
                StringBuilder candidateText=new StringBuilder(invocation.getIntent()==CommandIntent.CTX_CANDIDATE_WRITE?"ctx candidate write ":"ctx candidate ").append(argument(candidate.statement));
                for(java.util.Map.Entry<String,Integer> e:candidate.limits.entrySet())
                    if(!e.getValue().equals(org.kanger.ContextUniversalCandidateRequest.DEFAULTS.get(e.getKey())))
                        candidateText.append(' ').append(e.getKey()).append('=').append(e.getValue());
                return candidateText.toString();
            case CTX_DMZ_FAMILY: return "ctx dmz family"+(invocation.getArgument("number")==null?"":" "+number(invocation,"number"));
            case CTX_DMZ_BRANCH: return "ctx dmz branch "+number(invocation,"number");
            case CTX_DMZ_EXPLAIN: return "ctx dmz explain";
            case CTX_DMZ_CHOOSE: return "ctx dmz choose "+number(invocation,"number")
                    +(invocation.getArgument("limits")==null?"":((DmzLimitOverrides)invocation.getArgument("limits")).format());
            case CTX_OPINION_TREE: return "ctx tree "+number(invocation,"number");
            case CTX_DMZ:
                Object dmzRequest=invocation.getArgument("request");
                if(dmzRequest instanceof NamedDmzRequest) {
                    NamedDmzRequest named=(NamedDmzRequest)dmzRequest;
                    String text="dmz "+argument(named.authority)+" "+argument(named.query);
                    for(String opt:named.options)text+=" "+argument(opt);
                    return text;
                }
                return dmz((org.kanger.interfaces.DmzSourcesQuery.Request)dmzRequest);
            case CTX_OPINIONS:
                String opinions=optionalArgumentCommand("ctx opinions",invocation.getArgument("locator"));
                if(Boolean.TRUE.equals(invocation.getArgument("tree")))opinions+=" tree";
                if(invocation.getArgument("number")!=null)opinions+=" "+number(invocation,"number");
                return opinions;
            case CTX_VALUES:
                return optionalArgumentCommand("ctx values", invocation.getArgument("locator"));
            case CTX_SOLVES:
                return optionalArgumentCommand("ctx solves", invocation.getArgument("locator"));
            case CTX_WHEN:
                return optionalArgumentCommand("ctx when", invocation.getArgument("locator"));
            case CTX_STATUS:
                return "ctx";
            case CTX_FORK:
                return "ctx fork " + argument(invocation.getArgument("locator"));
            case CTX_PUBLISH:
                return optionalArgumentCommand("ctx publish", invocation.getArgument("description"));
            case CTX_RULES:
                String rules=invocation.getArgument("locator")==null ? "ctx rules"
                        : "ctx ask "+argument(invocation.getArgument("locator"))+" rules";
                String selection=String.valueOf(invocation.getArgument("selection"));
                if(!"PRIMARY".equals(selection) && !"SHOW".equals(selection)) rules+=" "+selection.toLowerCase(java.util.Locale.ROOT);
                if(invocation.getArgument("id")!=null) rules+=" "+number(invocation,"id");
                return rules;
            case CTX_CONNECT:
                return "ctx connect " + argument(invocation.getArgument("locator"))
                        + (invocation.getArgument("trustGroup") == null ? ""
                        : " trust " + argument(invocation.getArgument("trustGroup")));
            case CTX_DISCONNECT:
                return "ctx disconnect " + argument(invocation.getArgument("locator"));
            case CTX_SWITCH:
                return "ctx switch "
                        + argument(invocation.getArgument("locator"))
                        + " " + (Boolean.TRUE.equals(invocation.getArgument("current")) ? "current" : number(invocation, "RevisionId"));
            case CTX_VERSION:
                Object versionLocator =
                        invocation.getArgument("locator");
                return versionLocator == null
                        ? "ctx version"
                        : "ctx version "
                                + argument(versionLocator);
            case CTX_EXPLAIN:
                return "ctx explain "
                        + rawQuery(invocation.getArgument("query"));
            case CTX_ISOLATED_QUERY:
                return "ctx ask "
                        + argument(invocation.getArgument("locator"))
                        + " "
                        + contextOperation(invocation.getArgument("query"));
            case ERASE:
                return "erase";
            case HELP:
                return "help";
            case QUIT:
                return "quit";
            default:
                throw new IllegalArgumentException(
                        "No canonical formatter for " + invocation.getIntent());
        }
    }

    private String formatStatus(CommandInvocation invocation) {
        Object section = invocation.getArgument("section");
        if (section == null || String.valueOf(section).isEmpty()) {
            return "status";
        }
        StringBuilder out = new StringBuilder("status ").append(argument(section));
        Object subsection = invocation.getArgument("subsection");
        if (subsection != null && !String.valueOf(subsection).isEmpty()) {
            out.append(' ').append(argument(subsection));
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatValuesOrder(CommandInvocation invocation) {
        Object value = invocation.getArgument("keys");
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("VALUES_ORDER requires SortKey list");
        }
        List<SortKey> keys = (List<SortKey>) value;
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("VALUES_ORDER requires at least one SortKey");
        }
        StringBuilder out = new StringBuilder("values order ");
        for (int i = 0; i < keys.size(); ++i) {
            if (i > 0) {
                out.append(", ");
            }
            SortKey key = keys.get(i);
            out.append(argument(key.getField(), true))
                    .append(' ')
                    .append(key.getDirection() == SortKey.Direction.DESC
                            ? "desc" : "asc");
        }
        return out.toString();
    }

    private String rawQuery(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String query = String.valueOf(value).trim();
        if (query.isEmpty() || query.charAt(0) != '?') {
            throw new IllegalArgumentException(
                    "query must begin with ?");
        }
        return query;
    }

    private String contextOperation(Object value) {
        String source = value == null ? "" : String.valueOf(value).trim();
        if (source.isEmpty() || "?!+-".indexOf(source.charAt(0)) < 0)
            throw new IllegalArgumentException("ctx ask requires a query or an assertion/addition/deletion command");
        return source;
    }

    private String optionalArgumentCommand(String command, Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return command;
        }
        return command + " " + argument(value);
    }

    private String number(CommandInvocation invocation, String name) {
        Object value = invocation.getArgument(name);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        return String.valueOf(((Number) value).longValue());
    }

    private String dmz(org.kanger.interfaces.DmzSourcesQuery.Request request) {
        StringBuilder out=new StringBuilder("ctx dmz ").append(DmzCommandArguments.pin(request.sources.expectedQ))
                .append(' ').append(argument(request.queryStatement));
        for(org.kanger.interfaces.DmzSourcePlan.Selection source:request.sources.external)
            out.append(' ').append(argument("from="+source.locator+"@"+DmzCommandArguments.pin(source.revision)));
        if(request.restrictions!=null) {
            org.kanger.interfaces.DmzSourcesQuery.RestrictionRequest restriction=request.restrictions;
            out.append(" restrict=").append(DmzCommandArguments.pin(restriction.triggerRevision)).append(':').append(restriction.triggerRule);
            for(String choice:restriction.choices)out.append(' ').append(argument("choice="+choice));
            if(restriction.budget!=10000)out.append(" restriction-budget=").append(restriction.budget);
        }
        org.kanger.interfaces.DmzSourcesQuery.Limits l=request.limits;
        String[] keys={"rows","preparation","audit","lookup","recognition","projection","application","query","depth","nodes","work","visits"};
        int[] values={request.sources.maximumRows,l.preparation,l.audit,l.lookup,l.recognition,l.projection,l.application,l.query,l.depth,l.nodes,l.work,l.maximumVisits};
        int[] defaults={10000,10000,10000,10000,10000,10000,10000,10000,8,128,1000000,1000};
        for(int i=0;i<keys.length;++i)if(values[i]!=defaults[i])out.append(' ').append(keys[i]).append('=').append(values[i]);
        return out.toString();
    }

    private String argument(Object value) {
        return argument(value, false);
    }

    private String argument(Object value, boolean commaIsStructural) {
        if (value == null) {
            throw new IllegalArgumentException("argument must not be null");
        }
        String text = String.valueOf(value);
        if (!requiresQuotes(text, commaIsStructural)) {
            return text;
        }
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private boolean requiresQuotes(String text, boolean commaIsStructural) {
        if (text.isEmpty() || (commaIsStructural && text.indexOf(',') >= 0)) {
            return true;
        }
        for (int i = 0; i < text.length(); ++i) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String commentText(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("comment text must not be null");
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? "\"\"" : text;
    }
}
