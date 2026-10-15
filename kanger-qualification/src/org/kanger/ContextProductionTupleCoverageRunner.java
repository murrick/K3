/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextProductionInputs.Declaration;

/** Independent tuple controls. Multi-variable closure is not certified. */
public final class ContextProductionTupleCoverageRunner {
    private static int checks;
    private static final Limits NORMAL = new Limits(16,10000,250000,64);
    private static List<Declaration> source(boolean x,String... statements) {
        UUID id = UUID.randomUUID(); List<Declaration> result = new ArrayList<>();
        for (int i=0;i<statements.length;++i)
            result.add(new Declaration(new Source(id,1,i,x,"base"),statements[i]));
        return result;
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("tuple-coverage-").toString());
            positions(); joinsAndIsolation(); finiteSingleVariableOracle(); finiteSharedVariableOracle();
            System.out.println("CONTEXT_PRODUCTION_TUPLE_COVERAGE_PASS checks="+checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void positions() throws Exception {
        List<Declaration> x = source(true,"!@x pair(x,John);");
        List<Declaration> a = source(false,"!@x pair(x,John) -> out(John,x,x);");
        Result result = ContextProductionRoundsReference.inspect(x,Collections.singletonList(a),NORMAL);
        require(result.status == Status.SATURATED && result.coverage == Coverage.SINGLE_VARIABLE_HORN,
                "fixed and repeated tuple slots have scoped coverage: "+result.status+" "+result.reason);
        Product out = unit(result,"out",true);
        require(out != null && out.clause.variables.size() == 1,"tuple template keeps one repeated variable");
        for (Proof proof : out.supports)
            require(proof.sources.size() == 2 && proof.sources.contains(x.get(0).source)
                    && proof.sources.contains(a.get(0).source) && proof.depth == 1,"whole tuple proof and depth survive");
        Mind mind = units(result);
        require(Boolean.TRUE.equals(mind.query("?out(John,73,73);",null,false)),"arbitrary numeric instance");
        require(Boolean.TRUE.equals(mind.query("?out(John,Mary,Mary);",null,false)),"arbitrary named instance");
        require(mind.query("?out(John,73,74);",null,false) == null,"repeated variable does not split");
        require(mind.query("?out(73,John,John);",null,false) == null,"fixed position does not move");
        for (boolean match : new boolean[]{false,true}) {
            Result repeated = ContextProductionRoundsReference.inspect(source(true,match ? "!pair(1,1);" : "!pair(1,2);"),
                    Collections.singletonList(source(false,"!@x pair(x,x) -> out(x);")),NORMAL);
            require(repeated.status == Status.SATURATED && repeated.coverage == Coverage.SINGLE_VARIABLE_HORN
                    && (unit(repeated,"out",false) != null) == match,"repeated donor arguments match exactly");
        }
        Result groundHead = ContextProductionRoundsReference.inspect(source(true,"!@x p(x);"),
                Collections.singletonList(source(false,"!@x p(x) -> out(1,John);")),NORMAL);
        require(groundHead.status == Status.SATURATED && unit(groundHead,"out",false) != null
                && unit(groundHead,"out",true) == null,"constant head is proved without generalizing its constants");
        Result negative = ContextProductionRoundsReference.inspect(source(true,"!@x ~q(John,x,x);"),
                Collections.singletonList(source(false,"!@x p(x,1) -> q(John,x,x);")),NORMAL);
        Product notP = unit(negative,"p",true);
        require(negative.status == Status.SATURATED && notP != null && !notP.clause.literals.get(0).positive,
                "negative universal tuple propagation retains polarity");
    }
    private static void joinsAndIsolation() throws Exception {
        List<Declaration> x = source(true,"!@x left(x,1) && right(John,x,x) -> out(x,x,John);");
        List<Declaration> a = source(false,"!@x left(x,1);");
        List<Declaration> b = source(false,"!@x right(John,x,x);");
        for (boolean reverse : new boolean[]{false,true}) {
            Result joined = ContextProductionRoundsReference.inspect(x,reverse ? Arrays.asList(b,a) : Arrays.asList(a,b),NORMAL);
            Product out = unit(joined,"out",true);
            require(joined.status == Status.SATURATED && joined.coverage == Coverage.SINGLE_VARIABLE_HORN && out != null,
                    "single-variable multi-premise tuple join closes in either order: "+joined.reason);
            require(out.supports.stream().anyMatch(proof -> proof.sources.size() == 3
                    && proof.sources.contains(x.get(0).source) && proof.sources.contains(a.get(0).source)
                    && proof.sources.contains(b.get(0).source)),"joined proof retains all three sources");
        }
        List<List<Declaration>> peers = Arrays.asList(a,b,source(false,"!~out(2,2,John);"));
        for (int i=0;i<3;++i) for (int j=0;j<3;++j) if (i != j) {
            Result conflict = ContextProductionRoundsReference.inspect(x,Arrays.asList(peers.get(i),peers.get(j),peers.get(3-i-j)),NORMAL);
            require(conflict.status == Status.COLLISION && conflict.rounds == 3,"tuple join collision respects synchronous barriers in all orders");
        }
        Result privateOnly = ContextProductionRoundsReference.inspect(source(true,"!anchor(1);"),Arrays.asList(
                source(false,"!@x left(x,1);","!@x left(x,1) -> out(John,x,x);"),
                source(false,"!~out(John,2,2);")),NORMAL);
        require(privateOnly.status == Status.SATURATED && privateOnly.products.stream().noneMatch(
                p -> p.clause.literals.stream().anyMatch(t -> t.predicate.equals("out"))),"foreign tuple products remain private");
        Result siblings = ContextProductionRoundsReference.inspect(source(true,"!anchor(1);"),Arrays.asList(
                source(false,"!@x p(x,John);"),source(false,"!@x ~p(x,John);")),NORMAL);
        require(siblings.status == Status.SATURATED,"opposite sibling tuple units are independent");
        Result shallow = ContextProductionRoundsReference.inspect(x,Arrays.asList(a,b),new Limits(16,10000,250000,1));
        require(shallow.status == Status.INCOMPLETE && shallow.reason.contains("depth limit"),"tuple proof depth is carried across rounds");
    }
    /** Reachability oracle over two tuple schemas, independent of native binding. */
    private static void finiteSingleVariableOracle() throws Exception {
        for (int edges=0;edges<4;++edges) for (int roots=0;roots<4;++roots) for (int negatives=0;negatives<4;++negatives) {
            int reachable = roots, previous;
            do {
                previous = reachable;
                if ((edges&1) != 0 && (reachable&1) != 0) reachable |= 2;
                if ((edges&2) != 0 && (reachable&2) != 0) reachable |= 1;
            } while (reachable != previous);
            List<String> x = new ArrayList<>(); x.add("!anchor(1);");
            if ((roots&1) != 0) x.add("!@x p(x,1);");
            if ((roots&2) != 0) x.add("!@x q(John,x,x);");
            List<String> a = new ArrayList<>();
            if ((edges&1) != 0) a.add("!@x p(x,1) -> q(John,x,x);");
            if ((edges&2) != 0) a.add("!@x q(John,x,x) -> p(x,1);");
            if ((negatives&1) != 0) a.add("!~p(2,1);");
            if ((negatives&2) != 0) a.add("!~q(John,2,2);");
            Result actual = ContextProductionRoundsReference.inspect(source(true,x.toArray(new String[0])),
                    Collections.singletonList(source(false,a.toArray(new String[0]))),NORMAL);
            boolean conflict = (reachable&negatives) != 0;
            require(actual.status == (conflict ? Status.COLLISION : Status.SATURATED),
                    "tuple oracle edges="+edges+" roots="+roots+" negatives="+negatives+" observed="+actual.status+" "+actual.reason);
            if (!conflict) require(actual.coverage != Coverage.NONE,"consistent oracle has explicit scoped coverage");
        }
    }
    /** All 16 directed graphs on {1,2}; two-edge paths share the middle variable. */
    private static void finiteSharedVariableOracle() throws Exception {
        for (int graph=0;graph<16;++graph) {
            List<String> facts = new ArrayList<>(); facts.add("!anchor(1);");
            for (int from=0;from<2;++from) for (int to=0;to<2;++to)
                if ((graph & (1 << (2*from+to))) != 0) facts.add("!edge("+(from+1)+","+(to+1)+");");
            Result actual = ContextProductionRoundsReference.inspect(source(true,facts.toArray(new String[0])),
                    Collections.singletonList(source(false,"!@x @y @z edge(x,y) && edge(y,z) -> out(z,x);")),NORMAL);
            require(actual.status == Status.INCOMPLETE && actual.coverage == Coverage.NONE
                    && actual.reason.contains("unproved-symbolic-coverage"),"multi-variable joins retain their certification boundary");
            Mind mind = units(actual);
            for (int from=0;from<2;++from) for (int to=0;to<2;++to) {
                boolean expected = false;
                for (int middle=0;middle<2;++middle) expected |= (graph & (1 << (2*from+middle))) != 0
                        && (graph & (1 << (2*middle+to))) != 0;
                boolean proved = Boolean.TRUE.equals(mind.query("?out("+(to+1)+","+(from+1)+");",null,false));
                require(proved == expected,"shared variable and head permutation graph="+graph+" from="+from+" to="+to);
            }
        }
    }
    private static Product unit(Result result,String predicate,boolean generic) {
        for (Product product : result.products) if (product.clause.literals.size() == 1
                && product.clause.literals.get(0).predicate.equals(predicate)
                && !product.clause.variables.isEmpty() == generic) return product;
        return null;
    }
    private static Mind units(Result result) throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        for (Product product : result.products) if (product.clause.literals.size() == 1)
            mind.compileLine(product.clause.assertion(),false,product.clause.project(mind));
        return mind;
    }
    private static void require(boolean value,String message) { ++checks; if (!value) throw new AssertionError(message); }
}
