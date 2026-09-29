package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.DataType;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit recursive structural projection for one persistent KANGER Term. */
public final class TermAdapter implements KangerUnitAdapter<Term> {

    public static final String TYPE_NAME = "TERM";
    public static final String LAYOUT_NAME = "Term-v1";
    public static final String KIND_NAME = "TermKind";
    public static final TermAdapter INSTANCE = new TermAdapter();

    private static final Descriptor KIND = Descriptor.enumeration(KIND_NAME, Arrays.asList(
            "VOID",
            "PERIOD",
            "TERM",
            "STRING",
            "NUMERIC",
            "DATE",
            "INTERVAL_TEXT",
            "INTERVAL_ITEMS",
            "SET_TEXT",
            "SET_ITEMS",
            "BLOB"));

    private static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("id", Descriptor.INT64),
            Descriptor.field("mindId", Descriptor.INT64),
            Descriptor.field("deleted", Descriptor.BOOL),
            Descriptor.field("kind", KIND),
            Descriptor.field("hash", Descriptor.INT32),
            Descriptor.field("value", Descriptor.variant("kind", Arrays.asList(
                    Descriptor.variantCase("VOID", Descriptor.NULL),
                    Descriptor.variantCase("PERIOD", Descriptor.UTF8),
                    Descriptor.variantCase("TERM", Descriptor.SELF),
                    Descriptor.variantCase("STRING", Descriptor.UTF8),
                    Descriptor.variantCase("NUMERIC", Descriptor.FLOAT64),
                    Descriptor.variantCase("DATE", Descriptor.INT64),
                    Descriptor.variantCase("INTERVAL_TEXT", Descriptor.UTF8),
                    Descriptor.variantCase("INTERVAL_ITEMS", Descriptor.list(Descriptor.SELF)),
                    Descriptor.variantCase("SET_TEXT", Descriptor.UTF8),
                    Descriptor.variantCase("SET_ITEMS", Descriptor.list(Descriptor.SELF)),
                    Descriptor.variantCase("BLOB", Descriptor.BYTES)))),
            Descriptor.field("index", Descriptor.INT32),
            Descriptor.field("name", Descriptor.conditional(
                    Descriptor.greaterThanInt64("index", 0),
                    Descriptor.ref("dictionary"))),
            Descriptor.field("rule", Descriptor.conditional(
                    Descriptor.greaterThanInt64("index", 0),
                    Descriptor.ref("rules"))),
            Descriptor.field("domini", Descriptor.conditional(
                    Descriptor.greaterThanInt64("index", 0),
                    Descriptor.BOOL))));

    private TermAdapter() {}

    @Override public UnitType getRuntimeType() { return UnitType.TERM; }
    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public Descriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public StructuralValue project(Term value, Mind mind) throws Exception {
        return projectTerm(value, mind);
    }

    @Override
    public Term materialize(StructuralValue value, Mind mind) throws Exception {
        return materializeTerm(value, mind);
    }

    private static StructuralValue projectTerm(Term term, Mind mind) throws IOException {
        if (term == null || mind == null) throw new NullPointerException();
        String kind = kind(term);

        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(term.getId()));
        fields.put("mindId", StructuralValue.int64(term.getMindId()));
        fields.put("deleted", StructuralValue.bool(term.isDeleted(mind)));
        fields.put("kind", StructuralValue.enumeration(KIND_NAME, kind));
        fields.put("hash", StructuralValue.int32(term.getPersistentHash()));
        fields.put("value", projectValue(kind, term.getValue(), mind));
        fields.put("index", StructuralValue.int32(term.getIndex()));
        if (term.getIndex() > 0) {
            fields.put("name", StructuralValue.ref("dictionary", term.getNameId()));
            fields.put("rule", StructuralValue.ref("rules", term.getRuleId()));
            fields.put("domini", StructuralValue.bool(term.isDomini()));
        }
        return StructuralValue.struct(fields);
    }

    private static String kind(Term term) throws IOException {
        Object value = term.getValue();
        switch (term.getType()) {
            case VOID: return "VOID";
            case PERIOD: return "PERIOD";
            case TERM: return "TERM";
            case STRING: return "STRING";
            case NUMERIC: return "NUMERIC";
            case DATE: return "DATE";
            case BLOB: return "BLOB";
            case INTERVAL:
                return value instanceof Collection ? "INTERVAL_ITEMS" : "INTERVAL_TEXT";
            case SET:
                return value instanceof Collection ? "SET_ITEMS" : "SET_TEXT";
            default:
                throw new IOException("Unsupported Term data type " + term.getType());
        }
    }

    private static StructuralValue projectValue(String kind, Object value, Mind mind)
            throws IOException {
        if ("VOID".equals(kind)) {
            if (value != null) throw new IOException("VOID Term must not carry a value");
            return StructuralValue.nullValue();
        }
        if (value == null)
            throw new IOException(kind + " Term must carry a value");

        if ("PERIOD".equals(kind) || "STRING".equals(kind)
                || "INTERVAL_TEXT".equals(kind) || "SET_TEXT".equals(kind))
            return StructuralValue.utf8((String) value);
        if ("NUMERIC".equals(kind))
            return StructuralValue.float64(((Number) value).doubleValue());
        if ("DATE".equals(kind))
            return StructuralValue.int64(((Date) value).getTime());
        if ("BLOB".equals(kind))
            return StructuralValue.bytes((byte[]) value);
        if ("TERM".equals(kind)) {
            if (!(value instanceof Term))
                throw new IOException("TERM payload must be Term");
            return projectTerm((Term) value, mind);
        }
        if ("INTERVAL_ITEMS".equals(kind) || "SET_ITEMS".equals(kind)) {
            if (!(value instanceof Collection))
                throw new IOException(kind + " payload must be Collection");
            List<StructuralValue> items = new ArrayList<StructuralValue>();
            for (Object one : (Collection<?>) value) {
                if (!(one instanceof Term))
                    throw new IOException(kind + " element must be Term");
                items.add(projectTerm((Term) one, mind));
            }
            return StructuralValue.list(items);
        }
        throw new IOException("Unsupported Term kind " + kind);
    }

    private static Term materializeTerm(StructuralValue value, Mind mind)
            throws IOException {
        if (value == null || mind == null) throw new NullPointerException();
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("TERM structural value must be STRUCT");
        Map<String, StructuralValue> fields = value.asStruct();

        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId = require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted = require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();

        StructuralValue kindValue = require(fields, "kind", StructuralValue.Kind.ENUM);
        if (!KIND_NAME.equals(kindValue.getEnumName()))
            throw new IOException("Invalid TERM kind enum " + kindValue.getEnumName());
        String kind = kindValue.getEnumSymbol();

        int hash = require(fields, "hash", StructuralValue.Kind.INT32).asInt32();
        StructuralValue payload = fields.get("value");
        if (payload == null) throw new IOException("Missing TERM field value");
        Object materializedValue = materializeValue(kind, payload, mind);
        DataType type = dataType(kind);

        int index = require(fields, "index", StructuralValue.Kind.INT32).asInt32();
        long nameId = -1L;
        long ruleId = -1L;
        boolean domini = false;
        if (index > 0) {
            StructuralValue name = require(fields, "name", StructuralValue.Kind.REF);
            StructuralValue rule = require(fields, "rule", StructuralValue.Kind.REF);
            if (!"dictionary".equals(name.getReferenceSchema()))
                throw new IOException("Invalid TERM name reference namespace "
                        + name.getReferenceSchema());
            if (!"rules".equals(rule.getReferenceSchema()))
                throw new IOException("Invalid TERM rule reference namespace "
                        + rule.getReferenceSchema());
            nameId = name.getReferenceId();
            ruleId = rule.getReferenceId();
            domini = require(fields, "domini", StructuralValue.Kind.BOOL).asBool();
        } else if (fields.containsKey("name") || fields.containsKey("rule")
                || fields.containsKey("domini")) {
            throw new IOException("Non-C-variable TERM contains conditional metadata");
        }

        Term result = new Term();
        result.setMind(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setPersistentState(
                type, materializedValue, hash, index, nameId, ruleId, domini);
        if (deleted) mind.setUnitDeleted(result, true);
        return result;
    }

    private static Object materializeValue(String kind,
                                           StructuralValue value,
                                           Mind mind) throws IOException {
        if ("VOID".equals(kind)) {
            requireKind(value, StructuralValue.Kind.NULL, kind);
            return null;
        }
        if ("PERIOD".equals(kind) || "STRING".equals(kind)
                || "INTERVAL_TEXT".equals(kind) || "SET_TEXT".equals(kind)) {
            requireKind(value, StructuralValue.Kind.UTF8, kind);
            return value.asUtf8();
        }
        if ("NUMERIC".equals(kind)) {
            requireKind(value, StructuralValue.Kind.FLOAT64, kind);
            return value.asFloat64();
        }
        if ("DATE".equals(kind)) {
            requireKind(value, StructuralValue.Kind.INT64, kind);
            return new Date(value.asInt64());
        }
        if ("BLOB".equals(kind)) {
            requireKind(value, StructuralValue.Kind.BYTES, kind);
            return value.asBytes();
        }
        if ("TERM".equals(kind)) {
            requireKind(value, StructuralValue.Kind.STRUCT, kind);
            return materializeTerm(value, mind);
        }
        if ("INTERVAL_ITEMS".equals(kind) || "SET_ITEMS".equals(kind)) {
            requireKind(value, StructuralValue.Kind.LIST, kind);
            List<ITerm> items = new ArrayList<ITerm>();
            for (StructuralValue one : value.asList()) {
                requireKind(one, StructuralValue.Kind.STRUCT, kind);
                items.add(materializeTerm(one, mind));
            }
            return items;
        }
        throw new IOException("Unknown TERM kind " + kind);
    }

    private static DataType dataType(String kind) throws IOException {
        if ("VOID".equals(kind)) return DataType.VOID;
        if ("PERIOD".equals(kind)) return DataType.PERIOD;
        if ("TERM".equals(kind)) return DataType.TERM;
        if ("STRING".equals(kind)) return DataType.STRING;
        if ("NUMERIC".equals(kind)) return DataType.NUMERIC;
        if ("DATE".equals(kind)) return DataType.DATE;
        if ("INTERVAL_TEXT".equals(kind) || "INTERVAL_ITEMS".equals(kind))
            return DataType.INTERVAL;
        if ("SET_TEXT".equals(kind) || "SET_ITEMS".equals(kind))
            return DataType.SET;
        if ("BLOB".equals(kind)) return DataType.BLOB;
        throw new IOException("Unknown TERM kind " + kind);
    }

    private static void requireKind(StructuralValue value,
                                    StructuralValue.Kind expected,
                                    String termKind) throws IOException {
        if (value.getKind() != expected)
            throw new IOException(termKind + " TERM payload must be " + expected);
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing TERM field " + name);
        return value;
    }
}
