package org.kanger.storage.dumb2.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable structural descriptor for the self-describing DUMB 2.0 format.
 *
 * <p>The model is deliberately small and declarative. It describes storage
 * structure only; it contains no Java class names, reflection metadata,
 * callbacks or executable serializers.</p>
 */
public final class Descriptor {

    public enum Kind {
        NULL,
        BOOL,
        INT32,
        INT64,
        FLOAT64,
        UTF8,
        BYTES,
        SELF,
        REF,
        LIST,
        STRUCT,
        ENUM,
        VARIANT,
        CONDITIONAL
    }

    public enum ConditionOperator {
        EQUALS_INT64,
        GREATER_THAN_INT64
    }

    public static final Descriptor NULL = primitive(Kind.NULL);
    public static final Descriptor BOOL = primitive(Kind.BOOL);
    public static final Descriptor INT32 = primitive(Kind.INT32);
    public static final Descriptor INT64 = primitive(Kind.INT64);
    public static final Descriptor FLOAT64 = primitive(Kind.FLOAT64);
    public static final Descriptor UTF8 = primitive(Kind.UTF8);
    public static final Descriptor BYTES = primitive(Kind.BYTES);
    public static final Descriptor SELF = primitive(Kind.SELF);

    private final Kind kind;
    private final String name;
    private final String target;
    private final Descriptor element;
    private final List<Field> fields;
    private final List<String> symbols;
    private final String discriminatorField;
    private final List<VariantCase> cases;
    private final Condition condition;

    private Descriptor(Kind kind,
                       String name,
                       String target,
                       Descriptor element,
                       List<Field> fields,
                       List<String> symbols,
                       String discriminatorField,
                       List<VariantCase> cases,
                       Condition condition) {
        this.kind = kind;
        this.name = name;
        this.target = target;
        this.element = element;
        this.fields = immutable(fields);
        this.symbols = immutable(symbols);
        this.discriminatorField = discriminatorField;
        this.cases = immutable(cases);
        this.condition = condition;
    }

    private static Descriptor primitive(Kind kind) {
        return new Descriptor(kind, null, null, null, null, null, null, null, null);
    }

    public static Descriptor ref(String schema) {
        return new Descriptor(Kind.REF, null, requireName(schema, "schema"), null,
                null, null, null, null, null);
    }

    public static Descriptor list(Descriptor element) {
        return new Descriptor(Kind.LIST, null, null,
                Objects.requireNonNull(element, "element"), null, null, null, null, null);
    }

    public static Descriptor enumeration(String name, List<String> symbols) {
        String checkedName = requireName(name, "enum name");
        List<String> checkedSymbols = checkedNames(symbols, "enum symbol");
        if (checkedSymbols.isEmpty()) {
            throw new IllegalArgumentException("enum must contain at least one symbol");
        }
        return new Descriptor(Kind.ENUM, checkedName, null, null,
                null, checkedSymbols, null, null, null);
    }

    public static Descriptor struct(String name, List<Field> fields) {
        String checkedName = requireName(name, "struct name");
        if (fields == null) {
            throw new NullPointerException("fields");
        }
        List<Field> checkedFields = new ArrayList<Field>(fields);
        Set<String> seen = new LinkedHashSet<String>();
        for (Field field : checkedFields) {
            if (field == null) {
                throw new NullPointerException("field");
            }
            if (seen.contains(field.getName())) {
                throw new IllegalArgumentException("duplicate struct field: " + field.getName());
            }
            for (String dependency : field.getDescriptor().localDependencies()) {
                if (!seen.contains(dependency)) {
                    throw new IllegalArgumentException("field " + field.getName()
                            + " depends on non-previous field " + dependency);
                }
            }
            seen.add(field.getName());
        }
        return new Descriptor(Kind.STRUCT, checkedName, null, null,
                checkedFields, null, null, null, null);
    }

    public static Descriptor variant(String discriminatorField, List<VariantCase> cases) {
        String discriminator = requireName(discriminatorField, "discriminator field");
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("variant must contain at least one case");
        }
        List<VariantCase> checkedCases = new ArrayList<VariantCase>(cases);
        Set<String> tags = new HashSet<String>();
        for (VariantCase one : checkedCases) {
            if (one == null) {
                throw new NullPointerException("variant case");
            }
            if (!tags.add(one.getTag())) {
                throw new IllegalArgumentException("duplicate variant tag: " + one.getTag());
            }
        }
        return new Descriptor(Kind.VARIANT, null, null, null,
                null, null, discriminator, checkedCases, null);
    }

    public static Descriptor conditional(Condition condition, Descriptor value) {
        return new Descriptor(Kind.CONDITIONAL, null, null,
                Objects.requireNonNull(value, "value"), null, null, null, null,
                Objects.requireNonNull(condition, "condition"));
    }

    public static Field field(String name, Descriptor descriptor) {
        return new Field(name, descriptor);
    }

    public static VariantCase variantCase(String tag, Descriptor descriptor) {
        return new VariantCase(tag, descriptor);
    }

    public static Condition equalsInt64(String fieldName, long value) {
        return new Condition(fieldName, ConditionOperator.EQUALS_INT64, value);
    }

    public static Condition greaterThanInt64(String fieldName, long value) {
        return new Condition(fieldName, ConditionOperator.GREATER_THAN_INT64, value);
    }

    public Kind getKind() { return kind; }
    public String getName() { return name; }
    public String getTarget() { return target; }
    public Descriptor getElement() { return element; }
    public List<Field> getFields() { return fields; }
    public List<String> getSymbols() { return symbols; }
    public String getDiscriminatorField() { return discriminatorField; }
    public List<VariantCase> getCases() { return cases; }
    public Condition getCondition() { return condition; }

    private Set<String> localDependencies() {
        if (kind == Kind.VARIANT) {
            return Collections.singleton(discriminatorField);
        }
        if (kind == Kind.CONDITIONAL) {
            return Collections.singleton(condition.getFieldName());
        }
        return Collections.emptySet();
    }

    private static String requireName(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    private static List<String> checkedNames(List<String> values, String label) {
        if (values == null) {
            throw new NullPointerException(label + "s");
        }
        List<String> result = new ArrayList<String>(values.size());
        Set<String> unique = new HashSet<String>();
        for (String value : values) {
            String checked = requireName(value, label);
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("duplicate " + label + ": " + checked);
            }
            result.add(checked);
        }
        return result;
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(values));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Descriptor)) return false;
        Descriptor that = (Descriptor) other;
        return kind == that.kind
                && Objects.equals(name, that.name)
                && Objects.equals(target, that.target)
                && Objects.equals(element, that.element)
                && fields.equals(that.fields)
                && symbols.equals(that.symbols)
                && Objects.equals(discriminatorField, that.discriminatorField)
                && cases.equals(that.cases)
                && Objects.equals(condition, that.condition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, target, element, fields, symbols,
                discriminatorField, cases, condition);
    }

    public static final class Field {
        private final String name;
        private final Descriptor descriptor;

        private Field(String name, Descriptor descriptor) {
            this.name = requireName(name, "field name");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        }

        public String getName() { return name; }
        public Descriptor getDescriptor() { return descriptor; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Field)) return false;
            Field that = (Field) other;
            return name.equals(that.name) && descriptor.equals(that.descriptor);
        }
        @Override public int hashCode() { return Objects.hash(name, descriptor); }
    }

    public static final class VariantCase {
        private final String tag;
        private final Descriptor descriptor;

        private VariantCase(String tag, Descriptor descriptor) {
            this.tag = requireName(tag, "variant tag");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        }

        public String getTag() { return tag; }
        public Descriptor getDescriptor() { return descriptor; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VariantCase)) return false;
            VariantCase that = (VariantCase) other;
            return tag.equals(that.tag) && descriptor.equals(that.descriptor);
        }
        @Override public int hashCode() { return Objects.hash(tag, descriptor); }
    }

    public static final class Condition {
        private final String fieldName;
        private final ConditionOperator operator;
        private final long operand;

        private Condition(String fieldName, ConditionOperator operator, long operand) {
            this.fieldName = requireName(fieldName, "condition field");
            this.operator = Objects.requireNonNull(operator, "operator");
            this.operand = operand;
        }

        public String getFieldName() { return fieldName; }
        public ConditionOperator getOperator() { return operator; }
        public long getOperand() { return operand; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Condition)) return false;
            Condition that = (Condition) other;
            return operand == that.operand && fieldName.equals(that.fieldName)
                    && operator == that.operator;
        }
        @Override public int hashCode() { return Objects.hash(fieldName, operator, operand); }
    }
}
