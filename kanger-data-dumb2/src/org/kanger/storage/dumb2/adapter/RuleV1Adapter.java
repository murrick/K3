package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.ICause;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.Rule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Historical Rule-v1 layout adapter.
 *
 * <p>This layout predates the persisted predicate/term acceleration indexes.
 * It remains readable so a Context may contain Rule-v1 and Rule-v2 records
 * simultaneously. Runtime writes never select this adapter; Rule-v2 is the
 * canonical current layout.</p>
 */
public final class RuleV1Adapter implements KangerUnitAdapter<Rule> {

    public static final String TYPE_NAME = RuleAdapter.TYPE_NAME;
    public static final String LAYOUT_NAME = "Rule-v1";
    public static final RuleV1Adapter INSTANCE = new RuleV1Adapter();

    private static final Descriptor DESCRIPTOR =
            Descriptor.struct(LAYOUT_NAME, Arrays.asList(
                    Descriptor.field("id", Descriptor.INT64),
                    Descriptor.field("mindId", Descriptor.INT64),
                    Descriptor.field("deleted", Descriptor.BOOL),
                    Descriptor.field("origin", Descriptor.ref("dictionary")),
                    Descriptor.field("varIndex", Descriptor.INT32),
                    Descriptor.field("query", Descriptor.BOOL),
                    Descriptor.field("generated", Descriptor.BOOL),
                    Descriptor.field("stored", Descriptor.BOOL),
                    Descriptor.field("substitutable", Descriptor.BOOL),
                    Descriptor.field("abstractive", Descriptor.BOOL),
                    Descriptor.field("tree", Descriptor.list(
                            Descriptor.list(Descriptor.ref("domains")))),
                    Descriptor.field("causes", Descriptor.list(
                            CauseStructuralAdapter.DESCRIPTOR))));

    private RuleV1Adapter() {}

    @Override public UnitType getRuntimeType() { return UnitType.RULE; }
    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public Descriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public StructuralValue project(Rule value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();

        List<StructuralValue> tree = new ArrayList<StructuralValue>();
        for (List<Long> branch : value.getPersistentTreeIds()) {
            List<StructuralValue> ids = new ArrayList<StructuralValue>();
            for (Long id : branch) {
                ids.add(StructuralValue.ref("domains", id.longValue()));
            }
            tree.add(StructuralValue.list(ids));
        }

        List<Cause> causes = new ArrayList<Cause>();
        for (ICause cause : value.getCauses()) {
            if (!(cause instanceof Cause)) {
                throw new IOException(
                        "RULE contains unsupported Cause implementation "
                                + cause.getClass().getName());
            }
            causes.add((Cause) cause);
        }
        Collections.sort(causes, CAUSE_ORDER);

        List<StructuralValue> causeValues =
                new ArrayList<StructuralValue>(causes.size());
        for (Cause cause : causes) {
            causeValues.add(CauseStructuralAdapter.project(cause));
        }

        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("origin",
                StructuralValue.ref("dictionary", value.getOriginId()));
        fields.put("varIndex", StructuralValue.int32(value.getVarIndex()));
        fields.put("query", StructuralValue.bool(value.isQuery()));
        fields.put("generated", StructuralValue.bool(value.isGenerated()));
        fields.put("stored", StructuralValue.bool(value.isStored()));
        fields.put("substitutable",
                StructuralValue.bool(value.isSubstitutable()));
        fields.put("abstractive", StructuralValue.bool(value.isAbstractive()));
        fields.put("tree", StructuralValue.list(tree));
        fields.put("causes", StructuralValue.list(causeValues));
        return StructuralValue.struct(fields);
    }

    @Override
    public Rule materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);

        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId =
                require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted =
                require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();

        StructuralValue origin =
                require(fields, "origin", StructuralValue.Kind.REF);
        if (!"dictionary".equals(origin.getReferenceSchema())) {
            throw new IOException(
                    "Invalid RULE origin reference namespace "
                            + origin.getReferenceSchema());
        }

        int varIndex =
                require(fields, "varIndex", StructuralValue.Kind.INT32).asInt32();
        boolean query =
                require(fields, "query", StructuralValue.Kind.BOOL).asBool();
        boolean generated =
                require(fields, "generated", StructuralValue.Kind.BOOL).asBool();
        boolean stored =
                require(fields, "stored", StructuralValue.Kind.BOOL).asBool();
        boolean substitutable =
                require(fields, "substitutable",
                        StructuralValue.Kind.BOOL).asBool();
        boolean abstractive =
                require(fields, "abstractive",
                        StructuralValue.Kind.BOOL).asBool();

        StructuralValue treeValue =
                require(fields, "tree", StructuralValue.Kind.LIST);
        List<List<Long>> treeIds = new ArrayList<List<Long>>();
        for (StructuralValue branchValue : treeValue.asList()) {
            if (branchValue.getKind() != StructuralValue.Kind.LIST) {
                throw new IOException("RULE tree branch must be LIST");
            }
            List<Long> branch = new ArrayList<Long>();
            for (StructuralValue domainRef : branchValue.asList()) {
                if (domainRef.getKind() != StructuralValue.Kind.REF
                        || !"domains".equals(
                                domainRef.getReferenceSchema())) {
                    throw new IOException(
                            "RULE tree entry must be REF<domains>");
                }
                branch.add(domainRef.getReferenceId());
            }
            treeIds.add(branch);
        }

        StructuralValue causesValue =
                require(fields, "causes", StructuralValue.Kind.LIST);
        List<Cause> causes = new ArrayList<Cause>();
        for (StructuralValue causeValue : causesValue.asList()) {
            if (causeValue.getKind() != StructuralValue.Kind.STRUCT) {
                throw new IOException("RULE cause entry must be STRUCT");
            }
            causes.add(CauseStructuralAdapter.materialize(causeValue));
        }

        Rule result = new Rule(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setPersistentOriginId(origin.getReferenceId());
        result.setVarIndex(varIndex);
        result.setPersistentFlags(
                query, generated, stored, substitutable, abstractive);
        result.setPersistentTreeIds(treeIds);
        /*
         * Deliberately do NOT mark reference indexes complete: Rule-v1 did not
         * persist them. Rule-v2 canonical projection will derive them from the
         * Domain graph if this object is rewritten/reindexed.
         */
        result.getCauses().clear();
        result.getCauses().addAll(causes);
        if (deleted) {
            mind.setUnitDeleted(result, true);
        }
        return result;
    }

    private static final Comparator<Cause> CAUSE_ORDER =
            new Comparator<Cause>() {
                @Override
                public int compare(Cause left, Cause right) {
                    int compared =
                            compareLong(left.getRuleId(), right.getRuleId());
                    if (compared != 0) return compared;
                    return compareSolve(left.getDonor(), right.getDonor());
                }
            };

    private static int compareSolve(Solve left, Solve right) {
        int compared =
                compareLong(left.getPredicateId(), right.getPredicateId());
        if (compared != 0) return compared;
        compared = Integer.compare(left.getRange(), right.getRange());
        if (compared != 0) return compared;
        compared = Boolean.compare(left.isAntc(), right.isAntc());
        if (compared != 0) return compared;

        int count = Math.min(
                left.getArguments().size(), right.getArguments().size());
        for (int i = 0; i < count; ++i) {
            Argument a = (Argument) left.getArguments().get(i);
            Argument b = (Argument) right.getArguments().get(i);
            compared = a.getType().name().compareTo(b.getType().name());
            if (compared != 0) return compared;
            compared = compareLong(a.getId(), b.getId());
            if (compared != 0) return compared;
            compared = Integer.compare(a.getVarOrder(), b.getVarOrder());
            if (compared != 0) return compared;
        }
        return Integer.compare(
                left.getArguments().size(), right.getArguments().size());
    }

    private static int compareLong(long left, long right) {
        return left < right ? -1 : (left == right ? 0 : 1);
    }

    private static Map<String, StructuralValue> requireStruct(
            StructuralValue value) throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT) {
            throw new IOException("RULE structural value must be STRUCT");
        }
        return value.asStruct();
    }

    private static StructuralValue require(
            Map<String, StructuralValue> fields,
            String name,
            StructuralValue.Kind kind) throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind) {
            throw new IOException("Invalid or missing RULE field " + name);
        }
        return value;
    }
}
