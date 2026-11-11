/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.ContextDependentProductionInspection.CutPart;

/** Exact supplied per-cut recipes. Descriptors/results are diagnostics, not provider or admission authority. */
public final class ContextDependentCutInspection {
    private ContextDependentCutInspection(){}
    public static final class Member {
        public final Mind actual;public final ContextProductionInventory inventory;
        public Member(Mind actual,ContextProductionInventory inventory){if(actual==null||inventory==null)throw new IllegalArgumentException("Actual physical member required");this.actual=actual;this.inventory=inventory;}
    }
    public static final class Cut {
        public final String group,key;public final List<Member> members;
        public Cut(String group,List<Member> members) {
            this.group=TrustGroups.validate(group);if(members==null||members.isEmpty())throw new IllegalArgumentException("Whole physical cut members required");List<Member> copy=new ArrayList<>(members);if(copy.contains(null)||(this.group==null&&copy.size()!=1))throw new IllegalArgumentException("Independent cut has exactly one physical member");copy.sort(Comparator.comparing(m->m.inventory.context.toString()));
            Set<UUID> ids=new HashSet<>();for(Member member:copy)if(member.inventory.localX||!ids.add(member.inventory.context))throw new IllegalArgumentException("Distinct foreign physical members required");
            this.members=Collections.unmodifiableList(copy);key=this.group==null?independent(copy.get(0).inventory.context):trusted(this.group);
        }
    }
    public static String local(UUID context){if(context==null)throw new IllegalArgumentException("Local context required");return "local:"+context;}
    public static String independent(UUID context){if(context==null)throw new IllegalArgumentException("Target context required");return "context:"+context;}
    public static String trusted(String group){String valid=TrustGroups.validate(group);if(valid==null)throw new IllegalArgumentException("Named TRUST group required");return "trust:"+valid;}
    public static final class Ref {
        public final UUID context;public final long rule;
        public Ref(UUID context,long rule){if(context==null||rule<0)throw new IllegalArgumentException("Exact physical original required");this.context=context;this.rule=rule;}
        @Override public boolean equals(Object o){return o instanceof Ref&&context.equals(((Ref)o).context)&&rule==((Ref)o).rule;}
        @Override public int hashCode(){return Objects.hash(context,rule);}
    }
    public static final class Donor {
        public final Ref original;public final int product;
        private Donor(Ref original,int product){this.original=original;this.product=product;}
        public static Donor original(Ref ref){if(ref==null)throw new IllegalArgumentException("Physical donor required");return new Donor(ref,-1);}
        public static Donor product(int index){if(index<0)throw new IllegalArgumentException("Earlier product index required");return new Donor(null,index);}
    }
    public static final class Step {
        public final Ref rule;public final List<Donor> donors;public final int retainedLiteral;
        public Step(Ref rule,List<Donor> donors){this(rule,-1,donors);}
        public Step(Ref rule,int retainedLiteral,List<Donor> donors){if(rule==null||retainedLiteral< -1||donors==null)throw new IllegalArgumentException("Whole physical clause required");List<Donor> copy=new ArrayList<>(donors);if(copy.contains(null))throw new IllegalArgumentException("Exact donors required");this.rule=rule;this.retainedLiteral=retainedLiteral;this.donors=Collections.unmodifiableList(copy);}
    }
    public static final class Request {
        public final Policy policy;public final int capacity;public final Map<String,List<Step>> recipes;public final boolean signed;
        public Request(Policy policy,int capacity,Map<String,List<Step>> recipes){this(policy,capacity,recipes,false);}
        public Request(Policy policy,int capacity,Map<String,List<Step>> recipes,boolean signed) {
            if(policy==null||capacity<1||capacity>100000||recipes==null)throw new IllegalArgumentException("Bounded exact cut recipes required");Map<String,List<Step>> copy=new LinkedHashMap<>();
            for(Map.Entry<String,List<Step>> e:recipes.entrySet()){if(e.getKey()==null||e.getValue()==null)throw new IllegalArgumentException("Exact cut recipe required");List<Step> steps=new ArrayList<>(e.getValue());if(steps.contains(null)||steps.size()>policy.steps)throw new IllegalArgumentException("Bounded complete cut recipe required");copy.put(e.getKey(),Collections.unmodifiableList(steps));}
            this.policy=policy;this.capacity=capacity;this.recipes=Collections.unmodifiableMap(copy);boolean mode=signed;for(List<Step> steps:copy.values())for(Step step:steps)mode|=step.retainedLiteral>=0;this.signed=mode;
        }
    }
    public static final class Result {
        public final Map<String,ContextDependentProductionInspection.Result> cuts;
        public final Map<String,List<UUID>> members;
        private Result(Map<String,ContextDependentProductionInspection.Result> cuts,List<Cut> descriptors,UUID x){this.cuts=Collections.unmodifiableMap(new LinkedHashMap<>(cuts));Map<String,List<UUID>> members=new LinkedHashMap<>();members.put(local(x),Collections.emptyList());for(Cut cut:descriptors){List<UUID> ids=new ArrayList<>();for(Member member:cut.members)ids.add(member.inventory.context);members.put(cut.key,Collections.unmodifiableList(ids));}this.members=Collections.unmodifiableMap(members);}
    }
    private static void current(Member x,List<Cut> cuts,Policy p)throws Exception{ContextDependentReplayInspection.current(x.actual,x.inventory,p);for(Cut cut:cuts)for(Member member:cut.members)ContextDependentReplayInspection.currentMember(member.actual,member.inventory,p);}
    private static List<CutPart> project(List<Member> members,Policy p)throws Exception{List<CutPart> parts=new ArrayList<>();for(Member member:members)parts.add(new CutPart(ContextDependentReplayInspection.projectMember(member.actual,member.inventory,p),member.inventory));return parts;}
    private static long key(Map<Ref,Long> keys,Ref ref){Long key=keys.get(ref);if(key==null)throw new IllegalArgumentException("Original belongs to another cut or is missing");return key;}
    private static ContextDependentProductionInspection.Result inspectOne(Member x,List<Member> foreign,String identity,List<Step> supplied,Request request)throws Exception {
        List<Member> members=new ArrayList<>();members.add(x);members.addAll(foreign);Map<Ref,Long> keys=new LinkedHashMap<>();long next=0;
        for(Member member:members)if(!member.inventory.dependentPrimaryView())throw new IllegalStateException("Whole verified primary projection required");
        for(Member member:members)for(ContextProductionInventory.Row row:member.inventory.primary)keys.put(new Ref(member.inventory.context,row.nativeRule),next++);
        List<ContextDependentProductionInspection.Step> steps=new ArrayList<>();for(Step step:supplied){List<ContextDependentProductionInspection.Donor> donors=new ArrayList<>();for(Donor donor:step.donors)donors.add(donor.original==null?ContextDependentProductionInspection.Donor.product(donor.product):ContextDependentProductionInspection.Donor.original(key(keys,donor.original)));steps.add(new ContextDependentProductionInspection.Step(key(keys,step.rule),step.retainedLiteral,donors));}
        ContextDependentProductionInspection.Request recipe=new ContextDependentProductionInspection.Request(request.policy,request.capacity,steps,request.signed);
        return ContextDependentProductionInspection.inspectCut(project(members,request.policy),project(members,request.policy),identity,recipe);
    }
    public static Result inspect(Member x,List<Cut> supplied,Request request)throws Exception {
        if(x==null||!x.inventory.localX||supplied==null||request==null)throw new IllegalArgumentException("Actual X and complete cut manifest required");List<Cut> cuts=new ArrayList<>(supplied);if(cuts.contains(null))throw new IllegalArgumentException("Exact physical cuts required");cuts.sort(Comparator.comparing(c->c.key));
        Set<String> names=new HashSet<>();names.add(local(x.inventory.context));Set<UUID> contexts=new HashSet<>();contexts.add(x.inventory.context);long rows=x.inventory.rows.size();
        for(Cut cut:cuts){if(!names.add(cut.key))throw new IllegalArgumentException("Split/repeated logical cut");for(Member member:cut.members){if(!contexts.add(member.inventory.context))throw new IllegalArgumentException("Repeated physical member across cuts");rows+=member.inventory.rows.size();}}
        if(rows>request.policy.rows||names.size()>request.policy.steps||contexts.size()>request.policy.steps+1||!names.equals(request.recipes.keySet()))throw new IllegalArgumentException("Complete bounded family recipe manifest required");current(x,cuts,request.policy);
        try {
            Map<String,ContextDependentProductionInspection.Result> results=new LinkedHashMap<>();
            results.put(local(x.inventory.context),inspectOne(x,Collections.emptyList(),local(x.inventory.context),request.recipes.get(local(x.inventory.context)),request));
            for(Cut cut:cuts)results.put(cut.key,inspectOne(x,cut.members,cut.key,request.recipes.get(cut.key),request));
            return new Result(results,cuts,x.inventory.context);
        }finally{current(x,cuts,request.policy);}
    }
}
