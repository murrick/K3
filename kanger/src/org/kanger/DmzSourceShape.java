/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Read-only declaration-shape audit; source acquisition and proof qualification are separate. */
public final class DmzSourceShape {
    private DmzSourceShape() {}
    public static final class Declaration {
        public final long sourceRule;
        public final String statement;
        /** Exact supported ground origin, checked against its native literal; null for other declarations. */
        public final String groundStatement;
        /** Native-checked signed unary implication with one shared variable, or null. */
        public final String unitStatement;
        final String unitSignature;
        final DmzGroundReplay checkedGround;
        private Declaration(long id,String origin,String ground,String unit,String signature,DmzGroundReplay checked) {
            sourceRule=id; statement=origin; groundStatement=ground; unitStatement=unit; unitSignature=signature; checkedGround=checked;
        }
    }
    public static final class Observation {
        public final int primaryRules, inspectedRows;
        public final String primaryDigest;
        public final java.util.List<Declaration> declarations;
        final java.util.List<TerminalSupportCapture.Ground> cachedGrounds;
        final int unsupportedGenerated;
        private final String state;
        public boolean isCurrent(Mind source) throws Exception {
            return state.equals(DmzObservationStateFingerprint.capture(source));
        }
        private Observation(int rows,String digest,java.util.List<Declaration> inputs,
                java.util.List<TerminalSupportCapture.Ground> cached,int unsupported,String state) {
            declarations=java.util.Collections.unmodifiableList(new java.util.ArrayList<Declaration>(inputs));
            primaryRules=declarations.size(); inspectedRows=rows; primaryDigest=digest;
            cachedGrounds=java.util.Collections.unmodifiableList(new java.util.ArrayList<TerminalSupportCapture.Ground>(cached));
            unsupportedGenerated=unsupported; this.state=state;
        }
    }
    public static Observation inspect(Mind source,int maximumRows) throws Exception {
        if(source==null || maximumRows<0 || maximumRows>10000)throw new IllegalArgumentException("Bounded source inspection required");
        java.util.SortedMap<Long,Rule> primary=new java.util.TreeMap<Long,Rule>();
        int rows=0,unsupportedGenerated=0;
        java.util.List<TerminalSupportCapture.Ground> cached=new java.util.ArrayList<TerminalSupportCapture.Ground>();
        for(IRule item:source.getRules()) {
            if(++rows>maximumRows)throw new IllegalStateException("DMZ source row budget exceeded");
            if(item.isDeleted(source))continue;
            Rule rule=(Rule)item;
            if(rule.isQuery())throw new IllegalArgumentException("DMZ source query rule is not qualified");
            if(source.getRules().isGenerated(rule)) {
                TerminalSupportCapture.Ground ground=cachedGround(rule,source);
                if(ground==null)++unsupportedGenerated; else cached.add(ground);
                continue;
            }
            if(rule.getTree().size()!=1)throw new IllegalArgumentException("DMZ source branch shape is not qualified");
            java.util.List<Domain> clause=rule.getTree().get(0);
            if(clause.size()<1 || clause.size()>2)throw new IllegalArgumentException("DMZ source unit clause required");
            long variable=-1;
            for(Domain literal:clause) {
                if(literal.isSystem(source) || literal.isCalculated(source) || literal.isQuery(source) || literal.getArguments().size()!=1)
                    throw new IllegalArgumentException("DMZ source ordinary unary domain required");
                IArgument argument=literal.getArguments().get(0);
                if(clause.size()==2) {
                    if(argument.getType()!=ArgumentType.TVARIABLE || variable>=0 && variable!=argument.getId())
                        throw new IllegalArgumentException("DMZ source shared-variable unit production required");
                    variable=argument.getId();
                } else {
                    if(argument.getType()!=ArgumentType.TERM)throw new IllegalArgumentException("DMZ source ground integer required");
                    Object value=argument.getValue(source).getValue();
                    if(!(value instanceof Number))throw new IllegalArgumentException("DMZ source ground integer required");
                    try { new java.math.BigDecimal(value.toString()).toBigIntegerExact(); }
                    catch(ArithmeticException | NumberFormatException unsupported) { throw new IllegalArgumentException("DMZ source ground integer required",unsupported); }
                }
            }
            if(rule.getOrigin()==null || rule.getOrigin().trim().isEmpty())throw new IllegalArgumentException("DMZ source declaration origin required");
            primary.put(rule.getId(),rule);
        }
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        java.util.List<Declaration> inputs=new java.util.ArrayList<Declaration>();
        for(Rule rule:primary.values()) {
            String ground=null;
            if(rule.getTree().get(0).size()==1) {
                String text=rule.getOrigin().replaceAll("\\s+", "");
                if(text.matches("!~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);")) {
                    Domain literal=rule.getDomain();
                    int open=text.indexOf('('); boolean positive=!text.startsWith("!~");
                    String predicate=text.substring(positive ? 1 : 2,open);
                    java.math.BigDecimal value=new java.math.BigDecimal(text.substring(open+1,text.length()-2));
                    Object actual=literal.getArguments().get(0).getValue(source).getValue();
                    if(literal.isAntc()!=positive || !literal.getPredicate().getName(source).equals(predicate)
                            || value.compareTo(new java.math.BigDecimal(actual.toString()))!=0)
                        throw new IllegalArgumentException("DMZ source ground origin disagrees with native literal");
                    ground=text;
                }
            }
            String unit=rule.getTree().get(0).size()==2 ? checkedUnitStatement(rule,source) : null;
            inputs.add(new Declaration(rule.getId(),rule.getOrigin(),ground,unit,unit==null ? null : unitSignature(rule,source),
                    ground==null ? null : DmzGroundReplay.prepareCapturedPrimary(source,rule)));
            byte[] origin=rule.getOrigin().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(12).putLong(rule.getId()).putInt(origin.length).array());
            digest.update(origin);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return new Observation(rows,hex.toString(),inputs,cached,unsupportedGenerated,DmzObservationStateFingerprint.capture(source));
    }
    private static String checkedUnitStatement(Rule rule,Mind source) throws Exception {
        java.util.regex.Matcher parsed=java.util.regex.Pattern.compile(
                "!\\s*@([A-Za-z_][A-Za-z_0-9]*)\\s+(~?)([A-Za-z_][A-Za-z_0-9]*)\\s*\\(\\s*\\1\\s*\\)\\s*->\\s*(~?)([A-Za-z_][A-Za-z_0-9]*)\\s*\\(\\s*\\1\\s*\\)\\s*;")
                .matcher(rule.getOrigin().trim());
        if(!parsed.matches())return null;
        java.util.List<String> expected=new java.util.ArrayList<String>();
        expected.add((parsed.group(2).equals("~") ? "1:" : "0:")+parsed.group(3));
        expected.add((parsed.group(4).equals("~") ? "0:" : "1:")+parsed.group(5));
        java.util.Collections.sort(expected);
        if(!expected.toString().equals(unitSignature(rule,source)))
            throw new IllegalArgumentException("DMZ source unit origin disagrees with native clause");
        String variable=parsed.group(1);
        return "!@"+variable+" "+parsed.group(2)+parsed.group(3)+"("+variable+") -> "
                +parsed.group(4)+parsed.group(5)+"("+variable+");";
    }
    static String unitSignature(Rule rule,Mind source) throws Exception {
        if(rule.isQuery() || rule.getTree().size()!=1 || rule.getTree().get(0).size()!=2)return null;
        long variable=-1;
        java.util.List<String> literals=new java.util.ArrayList<String>();
        for(Domain literal:rule.getTree().get(0)) {
            if(literal.isSystem(source) || literal.isCalculated(source) || literal.isQuery(source)
                    || literal.getArguments().size()!=1)return null;
            IArgument argument=literal.getArguments().get(0);
            if(argument.getType()!=ArgumentType.TVARIABLE || variable>=0 && variable!=argument.getId())return null;
            variable=argument.getId();
            literals.add((literal.isAntc() ? "1:" : "0:")+literal.getPredicate().getName(source));
        }
        java.util.Collections.sort(literals); return literals.toString();
    }
    private static TerminalSupportCapture.Ground cachedGround(Rule rule,Mind source) throws Exception {
        if(rule.getTree().size()!=1 || rule.getTree().get(0).size()!=1)return null;
        Domain literal=rule.getDomain();
        if(literal.isSystem(source) || literal.isCalculated(source) || literal.isQuery(source)
                || literal.getArguments().size()!=1 || literal.getArguments().get(0).getType()!=ArgumentType.TERM)return null;
        Object value=literal.getArguments().get(0).getValue(source).getValue();
        if(!(value instanceof Number))return null;
        try { new java.math.BigDecimal(value.toString()).toBigIntegerExact(); }
        catch(ArithmeticException | NumberFormatException unsupported) { return null; }
        return TerminalSupportCapture.Ground.capture(new org.kanger.primitives.Solve(literal.getPredicate(),
                literal.isAntc(),literal.getArguments().convertBase(source)),source);
    }
}
