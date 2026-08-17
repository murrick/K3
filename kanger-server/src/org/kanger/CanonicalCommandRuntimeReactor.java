/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kanger.command.CommandHelpRenderer;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandRegistry;
import org.kanger.command.SortKey;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns canonical intents that are no longer executed through the legacy
 * QueryProcessor transport.
 *
 * <p>This boundary sits inside {@link WorkspaceStateReactor}, so canonical
 * results receive the same authoritative workspace projection as legacy
 * operations. Existing lifecycle/stop-loss reactors remain below it for every
 * compatibility operation still translated by the ingress adapter.</p>
 *
 * <p>Converged command families delegate semantic state transitions to
 * {@link CanonicalCommandProcessor}. The remaining bindings in this class are
 * narrow read/projection adapters over existing Core capabilities.</p>
 */
final class CanonicalCommandRuntimeReactor implements IReactor<JSONObject> {

    private final IReactor<JSONObject> delegate;
    private final CommandHelpRenderer helpRenderer;
    private final CanonicalCommandProcessor commandProcessor;

    CanonicalCommandRuntimeReactor(IReactor<JSONObject> delegate) {
        this(delegate, new CommandHelpRenderer(), new CanonicalCommandProcessor());
    }

    CanonicalCommandRuntimeReactor(IReactor<JSONObject> delegate,
                                   CommandHelpRenderer helpRenderer) {
        this(delegate, helpRenderer, new CanonicalCommandProcessor());
    }

    CanonicalCommandRuntimeReactor(IReactor<JSONObject> delegate,
                                   CommandHelpRenderer helpRenderer,
                                   CanonicalCommandProcessor commandProcessor) {
        if (delegate == null || helpRenderer == null || commandProcessor == null) {
            throw new IllegalArgumentException(
                    "delegate, helpRenderer and commandProcessor must not be null");
        }
        this.delegate = delegate;
        this.helpRenderer = helpRenderer;
        this.commandProcessor = commandProcessor;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        if (!CanonicalCommandIngressReactor.CANONICAL_CONTEXT.equalsIgnoreCase(
                CanonicalCommandIngressReactor.context(packet))) {
            return delegate.run(packet);
        }

        CommandInvocation invocation = CanonicalCommandIngressReactor.invocation(packet);
        if (invocation == null || invocation.isCoreLanguage()) {
            return error("canonical_invocation_missing",
                    "Canonical invocation metadata is missing");
        }

        IUser user = requireUser(packet);
        if (user == null) {
            return error("authentication_required", "User not logged in");
        }
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }
        IMind mind = user.getCurrentMind();

        JSONObject result;
        switch (invocation.getIntent()) {
            case TX_STATUS:
            case TX_START:
            case TX_COMMIT:
            case TX_ROLLBACK:
            case TX_SQUASH:
            case STORAGE_STATUS:
            case STORAGE_USE:
            case STORAGE_CLOSE:
                result = executeShared(invocation, user);
                break;
            case HELP:
                result = ok()
                        .put("description", helpRenderer.render())
                        .put("dialogue_help", structuredHelp());
                break;
            case RULE_LEVEL:
                result = ruleLevels(packet, mind);
                break;
            case RULE_COMMENT_GET:
                result = ruleComment(mind, invocation, false);
                break;
            case RULE_COMMENT_SET:
                result = ruleComment(mind, invocation, true);
                break;
            case BASE_STATUS:
                result = baseStatus(mind);
                break;
            case BASE_TREE:
                result = baseTree(mind, number(invocation, "statementId"));
                break;
            case VALUES_ORDER:
                result = valuesOrder(mind, invocation);
                break;
            case SOLUTION_SHOW:
                result = solution(mind, number(invocation, "id"), false);
                break;
            case SOLUTION_TREE:
                result = solution(mind, number(invocation, "id"), true);
                break;
            case WHEN_ACCEPT:
                result = whenAccept(mind, number(invocation, "index"));
                break;
            default:
                result = new JSONObject()
                        .put("result", "error")
                        .put("code", "canonical_intent_not_bound")
                        .put("intent", invocation.getIntent().name())
                        .put("description", "Canonical intent has no runtime binding");
                break;
        }
        return decorate(result, user.getCurrentMind());
    }

    private JSONObject executeShared(CommandInvocation invocation,
                                     IUser user) throws Exception {
        try {
            CanonicalCommandProcessor.Result outcome =
                    commandProcessor.execute(invocation, user);
            if (!outcome.isHandled()) {
                return error("canonical_intent_not_bound",
                        "Canonical intent has no shared semantic binding");
            }
            JSONObject result = outcome.isSuccess()
                    ? ok()
                    : new JSONObject().put("result", "error");
            if (!outcome.getDescription().isEmpty()) {
                result.put("description", outcome.getDescription());
            }
            CanonicalCommandProcessor.Rejection rejection = outcome.getRejection();
            if (rejection != null) {
                result.put("code", rejection.getCode())
                        .put("reason", rejection.getReason());
                JSONObject detail = new JSONObject()
                        .put("schema", 1)
                        .put("kind", rejection.getCode())
                        .put("target_level", rejection.getTargetLevel())
                        .put("storage", rejection.getStorage() == null
                                ? JSONObject.NULL : rejection.getStorage());
                JSONArray collisions = new JSONArray();
                for (CanonicalCommandProcessor.CollisionWitness witness
                        : rejection.getCollisions()) {
                    collisions.put(new JSONObject()
                            .put("left", witness.getLeft())
                            .put("right", witness.getRight()));
                }
                detail.put("collisions", collisions);
                JSONArray actions = new JSONArray();
                for (CanonicalCommandProcessor.ResolutionAction action
                        : rejection.getActions()) {
                    JSONObject one = new JSONObject()
                            .put("id", action.getId())
                            .put("description", action.getDescription());
                    if (action.getCommand() != null) {
                        one.put("command", action.getCommand());
                    }
                    actions.put(one);
                }
                detail.put("actions", actions);
                result.put("rejection", detail);
            }
            CanonicalCommandProcessor.StorageStatus storage =
                    outcome.getStorageStatus();
            if (storage != null) {
                JSONArray names = new JSONArray();
                for (String name : storage.getNames()) {
                    names.put(name);
                }
                result.put("size", names.length())
                        .put("list", names)
                        .put("storage", new JSONObject()
                                .put("schema", 1)
                                .put("used", storage.isUsed())
                                .put("current", storage.isUsed()
                                        ? storage.getCurrent() : JSONObject.NULL)
                                .put("names", names));
                if (storage.isUsed()) {
                    result.put("name", storage.getCurrent());
                }
            }
            return result;
        } catch (StorageLifecycleException failure) {
            JSONObject result = error(failure.getCode(), failure.toString());
            if (failure.getRequiredAction() != null) {
                result.put("required_action", failure.getRequiredAction());
            }
            return result;
        }
    }

    private JSONObject structuredHelp() {
        JSONArray sections = new JSONArray();
        for (Map.Entry<String, List<CommandRegistry.Definition>> entry
                : helpRenderer.sections().entrySet()) {
            JSONArray commands = new JSONArray();
            for (CommandRegistry.Definition definition : entry.getValue()) {
                commands.put(new JSONObject()
                        .put("syntax", definition.getSyntax())
                        .put("summary", definition.getSummary()));
            }
            sections.put(new JSONObject()
                    .put("name", entry.getKey())
                    .put("commands", commands));
        }
        return new JSONObject()
                .put("schema", 1)
                .put("sections", sections);
    }

    private JSONObject ruleLevels(JSONObject packet, IMind mind) throws Exception {
        JSONObject sourceParameters = SessionSerializingReactor.parameters(packet);
        String token = sourceParameters.optString("token", "");
        JSONArray levels = new JSONArray();
        int total = 0;

        for (long level = mind.getTransactionLevel(); level >= 0; --level) {
            JSONObject parameters = new JSONObject()
                    .put("token", token)
                    .put("rules", "")
                    .put("level", level);
            JSONObject query = new JSONObject().put("body", new JSONObject()
                    .put("context", "query")
                    .put("parameters", parameters));

            Object raw = delegate.run(query);
            if (!(raw instanceof JSONObject)) {
                return error("rule_level_projection_invalid",
                        "Qualified rule level projection did not return JSON");
            }
            JSONObject projected = (JSONObject) raw;
            if (!"OK".equalsIgnoreCase(projected.optString("result", ""))) {
                return projected;
            }

            JSONArray list = projected.optJSONArray("list");
            if (list == null) {
                list = new JSONArray();
            }
            int size = projected.has("size")
                    ? projected.optInt("size", list.length()) : list.length();
            total += size;
            levels.put(new JSONObject()
                    .put("level", level)
                    .put("size", size)
                    .put("list", list));
        }

        return ok()
                .put("schema", 1)
                .put("levels", levels)
                .put("size", total)
                .put("empty", total == 0);
    }

    private JSONObject ruleComment(IMind mind,
                                   CommandInvocation invocation,
                                   boolean set) throws Exception {
        long id = number(invocation, "id");
        IRule rule = mind.getRules().get(id);
        if (rule == null) {
            return error("rule_not_found", "Rule not found " + id);
        }
        if (set) {
            Object text = invocation.getArgument("text");
            rule.setComment(text == null ? "" : String.valueOf(text));
        }
        return ok()
                .put("id", id)
                .put("comment", rule.getComment());
    }

    private JSONObject baseStatus(IMind mind) throws Exception {
        List<JSONObject> list = new ArrayList<JSONObject>();
        for (IPredicate predicate : mind.getPredicates()) {
            if (predicate.isDeleted(mind)
                    || ((Mind) mind).isSystem(predicate)
                    || predicate.isEmpty(mind)) {
                continue;
            }

            List<JSONObject> statements = new ArrayList<JSONObject>();
            for (IRule rule : mind.getRules()) {
                if (!rule.isDeleted(mind)
                        && rule.isStored()
                        && ((Rule) rule).getPredicateId() == predicate.getId()) {
                    statements.add(new JSONObject(((Rule) rule).createMap(mind)));
                }
            }
            if (!statements.isEmpty()) {
                JSONObject one = new JSONObject(((Predicate) predicate).createMap(mind));
                one.put("statements", statements);
                list.add(one);
            }
        }
        return ok()
                .put("size", list.size())
                .put("list", list);
    }

    private JSONObject baseTree(IMind mind, long id) throws Exception {
        IRule statement = null;
        for (IRule rule : mind.getRules()) {
            if (rule.getId() == id
                    && !rule.isDeleted(mind)
                    && rule.isStored()) {
                statement = rule;
                break;
            }
        }
        if (statement == null) {
            return error("base_statement_not_found",
                    "Base statement not found " + id);
        }
        JSONObject projected = new JSONObject(((Rule) statement).createMap(mind));
        projected.put("causes", recurseCauses(statement, mind));
        return ok()
                .put("size", 1)
                .put("list", Collections.singletonList(projected));
    }

    @SuppressWarnings("unchecked")
    private JSONObject valuesOrder(IMind mind,
                                   CommandInvocation invocation) throws Exception {
        Object rawKeys = invocation.getArgument("keys");
        if (!(rawKeys instanceof List)) {
            return error("values_order_invalid", "Values SortSpec is missing");
        }
        final List<SortKey> keys = (List<SortKey>) rawKeys;
        if (keys.isEmpty()) {
            return error("values_order_invalid", "Values SortSpec is empty");
        }

        List<Map<String, ITerm>> rows = new ArrayList<Map<String, ITerm>>();
        for (Map<String, ITerm> row : mind.getValues()) {
            rows.add(new LinkedHashMap<String, ITerm>(row));
        }

        if (!rows.isEmpty()) {
            for (SortKey key : keys) {
                boolean found = false;
                for (Map<String, ITerm> row : rows) {
                    if (row.containsKey(key.getField())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return error("values_field_not_found",
                            "Values field not found " + key.getField());
                }
            }

            Collections.sort(rows, new Comparator<Map<String, ITerm>>() {
                @Override
                public int compare(Map<String, ITerm> left,
                                   Map<String, ITerm> right) {
                    for (SortKey key : keys) {
                        int compared = compareTerms(
                                left.get(key.getField()),
                                right.get(key.getField()),
                                key.getDirection());
                        if (compared != 0) {
                            return compared;
                        }
                    }
                    return 0;
                }
            });
        }

        List<List<JSONObject>> list = new ArrayList<List<JSONObject>>();
        for (Map<String, ITerm> row : rows) {
            List<JSONObject> one = new ArrayList<JSONObject>();
            for (Map.Entry<String, ITerm> entry : row.entrySet()) {
                one.add(new JSONObject()
                        .put("name", entry.getKey())
                        .put("value", entry.getValue().getValue()));
            }
            list.add(one);
        }

        List<String> order = new ArrayList<String>();
        for (SortKey key : keys) {
            order.add(key.toString());
        }
        return ok()
                .put("size", list.size())
                .put("list", list)
                .put("order", order);
    }

    private int compareTerms(ITerm left,
                             ITerm right,
                             SortKey.Direction direction) {
        int compared;
        if (left == null && right == null) {
            compared = 0;
        } else if (left == null) {
            compared = -1;
        } else if (right == null) {
            compared = 1;
        } else {
            compared = left.compareTo(right);
        }
        return direction == SortKey.Direction.DESC ? -compared : compared;
    }

    private JSONObject solution(IMind mind, long id, boolean tree) throws Exception {
        IRule selected = null;
        for (IRule rule : mind.getSolutions()) {
            if (rule.getId() == id) {
                selected = rule;
                break;
            }
        }
        if (selected == null) {
            return error("solution_not_found", "Solution not found " + id);
        }

        JSONObject projected = new JSONObject(((Rule) selected).createMap(mind));
        if (tree) {
            projected.put("causes", recurseCauses(selected, mind));
        }
        return ok()
                .put("size", 1)
                .put("list", Collections.singletonList(projected));
    }

    private JSONObject whenAccept(IMind mind, long index) throws Exception {
        mind.optimizeHypothesis();
        if (index < 0 || index >= mind.getHypothesis().size()) {
            return error("hypothesis_index_out_of_range",
                    "Hypothesis index out of range " + index);
        }

        IHypothesis selected = mind.getHypothesis().get(index);
        JSONObject projection = new JSONObject(((Hypothesis) selected).createMap(mind));
        String source = ((Hypothesis) selected).toString(mind);
        String statement = String.format("%s;",
                source.replaceAll(String.format("%c", Enums.EOLN), ""));

        Boolean response = ((Mind) mind).queryAccept(statement, null, true);
        JSONObject result = ok()
                .put("index", index)
                .put("hypothesis", projection)
                .put("response", response == null
                        ? "unknown" : (response ? "yes" : "no"));
        ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
        if (log != null) {
            result.put("description", log.getRecord());
        }
        return result;
    }

    private List<JSONObject> recurseCauses(IRule rule, IMind mind) throws Exception {
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (rule == null) {
            return list;
        }
        for (ICause cause : rule.getCauses()) {
            IRule donor = cause.getDonor(mind);
            JSONObject projected = new JSONObject()
                    .put("rule", new JSONObject(
                            ((Rule) cause.getRule(mind)).createMap(mind)))
                    .put("donor", donor != null
                            ? new JSONObject(((Rule) donor).createMap(mind))
                            : new JSONObject(((Cause) cause).getDonor().createMap(mind)))
                    .put("causes", recurseCauses(donor, mind));
            list.add(projected);
        }
        return list;
    }

    private long number(CommandInvocation invocation, String name) {
        return ((Number) invocation.getArgument(name)).longValue();
    }

    private IUser requireUser(JSONObject packet) {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        String token = parameters.optString("token", "");
        if (token.isEmpty()) {
            return null;
        }
        try {
            return UserFactory.getUser(token);
        } catch (Exception rejected) {
            return null;
        }
    }

    private JSONObject decorate(JSONObject result, IMind mind) {
        if (result != null && mind != null) {
            result.put("transaction", mind.getTransactionLevel());
            if (!result.has("empty")) {
                result.put("empty", mind.isEmptyLevel());
            }
        }
        return result;
    }

    private JSONObject ok() {
        return new JSONObject().put("result", "OK");
    }

    private JSONObject error(String code, String description) {
        return new JSONObject()
                .put("result", "error")
                .put("code", code)
                .put("description", description);
    }
}
