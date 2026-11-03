/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.kanger.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.ContextDependentProductionReplay.Saved;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Original-only Horn packets, exited writer/fresh readers, corrupt recipes and graph digests. */
public final class ContextDependentProductionReplayRunner {
    private static int checks;
    private static final UUID CONTEXT=new UUID(0,510);
    private static final int CAPACITY=2000;
    private static Policy policy(){return new Policy(2000000,1000,100,64000000,200000);}
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}require(no,why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);for(int i=0;i<17;i++){u.nextId("rules");m.getTerms().nextVarIndex();}return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,CONTEXT,4,true,Collections.emptyList(),1000);}
    private static String pin(byte[] packet)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(packet))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static Request recipe(Rule h,Rule next,Rule seed){return new Request(policy(),CAPACITY,Arrays.asList(new Step(h.getId(),Collections.singletonList(Donor.original(seed.getId()))),new Step(next.getId(),Collections.singletonList(Donor.product(0)))));}
    private static Saved packet(int kind,boolean reverse)throws Exception {
        Mind m=memory();String[] prefix={"@a $u @b","@a @b $u","$u @a @b"};Rule p,h,next,c;
        if(reverse){c=raw(m,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");next=raw(m,"!@a @u @b mid(a,u,b)->tagged(a,u,b);");h=raw(m,"!@a @u @b rel(a,u,b)->mid(a,u,b);");p=raw(m,"!"+prefix[kind]+" rel(a,u,b);");}
        else{p=raw(m,"!"+prefix[kind]+" rel(a,u,b);");h=raw(m,"!@a @u @b rel(a,u,b)->mid(a,u,b);");next=raw(m,"!@a @u @b mid(a,u,b)->tagged(a,u,b);");c=raw(m,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");}
        raw(m,"!unused(99);");ContextProductionInventory before=inventory(m);Saved saved=ContextDependentProductionReplay.capture(m,before,recipe(h,next,p));
        require(before.fingerprint.equals(inventory(m).fingerprint),"capture does not install generated facts or alter originals");
        Result reopened=ContextDependentProductionReplay.reopen(saved.bytes(),saved.pin,policy(),CAPACITY);validate(reopened,kind);require(reopened.auditDigest.equals(saved.evidence.auditDigest),"entire old product/proof graph reconstructed from original knowledge");
        byte[] detached=saved.bytes();detached[0]^=1;require(detached[0]!=saved.bytes()[0],"saved artifact bytes detached");return saved;
    }
    private static void validate(Result result,int kind)throws Exception {
        require(result.products.size()==2&&result.originals.size()==5&&result.originalUnitRefutations.isEmpty(),"all original rows retained, both generated rows rebuilt");
        require(result.finalUnitRefutations.size()==(kind==1?0:1),"dependent/global/later scope survives original-only replay");
        Row row=result.products.get(1);TermView function=row.arguments.get(1);require(row.depth==2&&row.sources.size()==3,"generated donor ancestry and generation depth restored");
        require(function.kind==Kind.APPLICATION&&function.arguments.size()==(kind==2?0:kind==1?2:1),"function parameter list rebuilt, never decoded as an ordinary value");
        require(function.symbolSource.rule>=17&&result.originals.stream().allMatch(s->s.rule>=17&&s.context.equals(CONTEXT)&&s.revision==4),"saved Source occurrences rebound across fresh native IDs");
        if(kind!=1)require(result.finalUnitRefutations.get(0).depth==3&&result.finalUnitRefutations.get(0).sources.size()==4,"actual-final local refutation rebuilt, not persisted as a claim");
    }
    private static String worker(String... args)throws Exception {
        List<String> command=new ArrayList<>(Arrays.asList(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-XX:-UsePerfData","-cp",System.getProperty("java.class.path"),ContextDependentProductionReplayRunner.class.getName()));command.addAll(Arrays.asList(args));Process process=new ProcessBuilder(command).redirectErrorStream(true).start();if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Original-only replay worker timeout");}
        ByteArrayOutputStream log=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=process.getInputStream().read(buffer))!=-1)log.write(buffer,0,n);String text=new String(log.toByteArray(),StandardCharsets.UTF_8);if(process.exitValue()!=0)throw new AssertionError(text);return text;
    }
    private static void cold(Saved saved,int kind)throws Exception {cold(saved,Integer.toString(kind));}
    private static void cold(Saved saved,String mode)throws Exception {Path p=Files.createTempFile("horn-original-only-",".bin");Files.write(p,saved.bytes());require(worker("--read",p.toString(),saved.pin,mode).contains("HORN_ORIGINAL_ONLY_READER_OK"),"fresh reader reconstructs without writer products/arena");Files.delete(p);}
    private static final class Layout {
        int rows,capacity,stepCount,digest;final List<Integer> ids=new ArrayList<>(),origins=new ArrayList<>(),rules=new ArrayList<>(),tags=new ArrayList<>(),references=new ArrayList<>();
        Layout(byte[] data)throws Exception{java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(data);b.position(8+16+8);int n=b.getInt();b.position(b.position()+n);rows=b.position();n=b.getInt();for(int i=0;i<n;i++){ids.add(b.position());b.getLong();int length=b.getInt();origins.add(b.position());b.position(b.position()+length);}capacity=b.position();b.getInt();stepCount=b.position();n=b.getInt();for(int i=0;i<n;i++){rules.add(b.position());b.getLong();int count=b.getInt();for(int j=0;j<count;j++){tags.add(b.position());b.get();references.add(b.position());b.getLong();}}int length=b.getInt();digest=b.position();require(length==64&&digest+length==data.length,"packet ends in digest, contains originals and recipe only");}
    }
    private static byte[] replace(byte[] data,int start,String value)throws Exception{int old=java.nio.ByteBuffer.wrap(data).getInt(start-4);byte[] replacement=value.getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.write(data,0,start-4);out.writeInt(replacement.length);out.write(replacement);out.write(data,start+old,data.length-start-old);return bytes.toByteArray();}
    private static void corruption(Saved saved)throws Exception {
        byte[] data=saved.bytes();Layout l=new Layout(data);byte[] changed=data.clone();changed[changed.length-1]=(byte)(changed[changed.length-1]=='0'?'1':'0');byte[] wrong=changed;
        reject(()->ContextDependentProductionReplay.reopen(wrong,saved.pin,policy(),CAPACITY),"external pin detects altered file");reject(()->ContextDependentProductionReplay.reopen(wrong,pin(wrong),policy(),CAPACITY),"new pin cannot make a false claimed product graph pass reconstruction");
        changed=data.clone();java.nio.ByteBuffer.wrap(changed).putLong(l.references.get(1),1);byte[] self=changed;reject(()->ContextDependentProductionReplay.reopen(self,pin(self),policy(),CAPACITY),"forward/self generated donor refuses before inference");
        changed=data.clone();changed[l.tags.get(0)]=7;byte[] tag=changed;reject(()->ContextDependentProductionReplay.reopen(tag,pin(tag),policy(),CAPACITY),"unknown donor type refuses");
        changed=data.clone();java.nio.ByteBuffer.wrap(changed).putInt(l.rows,Integer.MAX_VALUE);byte[] count=changed;reject(()->ContextDependentProductionReplay.reopen(count,pin(count),policy(),CAPACITY),"unbounded original count refuses before allocation");
        changed=data.clone();java.nio.ByteBuffer.wrap(changed).putLong(l.ids.get(1),java.nio.ByteBuffer.wrap(data).getLong(l.ids.get(0)));byte[] duplicate=changed;reject(()->ContextDependentProductionReplay.reopen(duplicate,pin(duplicate),policy(),CAPACITY),"duplicate Source occurrence cannot be collapsed");
        changed=data.clone();java.nio.ByteBuffer.wrap(changed).putInt(l.origins.get(0)-4,Integer.MAX_VALUE);byte[] length=changed;reject(()->ContextDependentProductionReplay.reopen(length,pin(length),policy(),CAPACITY),"invalid original text length refuses");
        changed=data.clone();changed[l.origins.get(0)]=(byte)255;byte[] utf8=changed;reject(()->ContextDependentProductionReplay.reopen(utf8,pin(utf8),policy(),CAPACITY),"malformed UTF8 cannot change original knowledge silently");
        changed=Arrays.copyOf(data,data.length+1);byte[] trailing=changed;reject(()->ContextDependentProductionReplay.reopen(trailing,pin(trailing),policy(),CAPACITY),"extra generated-data tail cannot become knowledge");
        changed=Arrays.copyOf(data,data.length-1);byte[] truncated=changed;reject(()->ContextDependentProductionReplay.reopen(truncated,pin(truncated),policy(),CAPACITY),"truncated audit digest refuses");
        changed=data.clone();java.nio.ByteBuffer.wrap(changed).putInt(4,2);byte[] version=changed;reject(()->ContextDependentProductionReplay.reopen(version,pin(version),policy(),CAPACITY),"unknown Horn replay version refuses");
        int unused=-1;for(int i=0;i<l.origins.size();i++){int n=java.nio.ByteBuffer.wrap(data).getInt(l.origins.get(i)-4);if(new String(data,l.origins.get(i),n,StandardCharsets.UTF_8).equals("!unused(99);"))unused=i;}require(unused>=0,"unused whole original retained");byte[] extra=replace(data,l.origins.get(unused),"!~newa(1)&&~newb(2);");reject(()->ContextDependentProductionReplay.reopen(extra,pin(extra),policy(),CAPACITY),"compiler auxiliary rows cannot enlarge restored view behind unchanged graph");
        int seed=-1;for(int i=0;i<l.origins.size();i++){int n=java.nio.ByteBuffer.wrap(data).getInt(l.origins.get(i)-4);if(new String(data,l.origins.get(i),n,StandardCharsets.UTF_8).equals("!@a $u @b rel(a,u,b);"))seed=i;}require(seed>=0,"dependent original located");byte[] scope=replace(data,l.origins.get(seed),"!@a @b $u rel(a,u,b);");reject(()->ContextDependentProductionReplay.reopen(scope,pin(scope),policy(),CAPACITY),"changed scope and final refutation do not inherit old graph certificate");
        reject(()->ContextDependentProductionReplay.reopen(data,saved.pin,new Policy(data.length-1,1000,100,64000000,200000),CAPACITY),"input byte limit before copy");reject(()->ContextDependentProductionReplay.reopen(data,saved.pin,policy(),CAPACITY-1),"reader owns search capacity ceiling");reject(()->ContextDependentProductionReplay.reopen(data,saved.pin,new Policy(2000000,1000,1,64000000,200000),CAPACITY),"reader owns recipe/depth ceiling");reject(()->ContextDependentProductionReplay.reopen(data,saved.pin,new Policy(2000000,1000,100,1,200000),CAPACITY),"work exhaustion returns no restored prefix");reject(()->ContextDependentProductionReplay.reopen(data,saved.pin,new Policy(2000000,1000,100,64000000,1),CAPACITY),"node exhaustion returns no restored prefix");
    }
    private static Step body(Mind m,Rule rule,Map<String,Donor> donors)throws Exception {
        List<Donor> selected=new ArrayList<>();for(org.kanger.units.Domain domain:rule.getTree().get(0))if(!domain.isAntc())selected.add(donors.get(domain.getPredicate(m).getName(m)));return new Step(rule.getId(),selected);
    }
    private static void validateTyped(Result result)throws Exception {
        Row row=result.products.get(0);require(result.products.size()==1&&row.arguments.get(0).kind==Kind.VALUE,"typed scalar reconstructed through Horn body");require(row.arguments.get(1).arguments.get(0)==row.arguments.get(0),"late body binding still refines original witness after original-only replay");
    }
    private static void validateWide(Result result,int width)throws Exception {
        Row row=result.products.get(0);TermView function=row.arguments.get(width);require(function.kind==Kind.APPLICATION&&function.arguments.size()==width&&result.finalUnitRefutations.size()==1,"wide original-only dependency graph and final constraint reconstructed");for(int i=0;i<width;i++)require(function.arguments.get(i)==row.arguments.get(i),"reconstructed dependency coordinate "+i);
    }
    private static void wider()throws Exception {
        for(String value:new String[]{"2","\"2\"","\"ёж\""}) {
            Mind m=memory();Rule p=raw(m,"!@a $u @b rel(a,u,b);"),q=raw(m,"!choose("+value+");"),h=raw(m,"!@a @u @b rel(a,u,b)&&choose(a)->tagged(a,u,b);");Map<String,Donor> donors=new HashMap<>();donors.put("rel",Donor.original(p.getId()));donors.put("choose",Donor.original(q.getId()));
            Saved saved=ContextDependentProductionReplay.capture(m,inventory(m),new Request(policy(),CAPACITY,Collections.singletonList(body(m,h,donors))));Result result=ContextDependentProductionReplay.reopen(saved.bytes(),saved.pin,policy(),CAPACITY);validateTyped(result);require(result.products.get(0).arguments.get(0).value.semanticallyEquals(saved.evidence.products.get(0).arguments.get(0).value),"exact numeric/string/Unicode payload survives file recipe");cold(saved,"typed");
        }
        for(int width:new int[]{7,17,65}) {
            Mind m=memory();StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<width;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append('x').append(i);}
            Rule p=raw(m,"!"+prefix+"$u rel("+args+",u);"),h=raw(m,"!"+prefix+"@u rel("+args+",u)->out("+args+",u);");raw(m,"!"+prefix+"@u ~out("+args+",u);");Saved saved=ContextDependentProductionReplay.capture(m,inventory(m),new Request(policy(),CAPACITY,Collections.singletonList(new Step(h.getId(),Collections.singletonList(Donor.original(p.getId()))))));validateWide(ContextDependentProductionReplay.reopen(saved.bytes(),saved.pin,policy(),CAPACITY),width);cold(saved,"wide:"+width);
        }
        Mind m=memory();Rule p=raw(m,"!@a $u @b rel(a,u,b);"),h=raw(m,"!@a @u @b rel(a,u,b)->tagged(a,u,b);"),aux=raw(m,"!aux();"),again=raw(m,"!@a @u @b tagged(a,u,b)&&aux()->tagged(a,u,b);");raw(m,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");Map<String,Donor> donors=new HashMap<>();donors.put("tagged",Donor.product(0));donors.put("aux",Donor.original(aux.getId()));Saved saved=ContextDependentProductionReplay.capture(m,inventory(m),new Request(policy(),CAPACITY,Arrays.asList(new Step(h.getId(),Collections.singletonList(Donor.original(p.getId()))),body(m,again,donors))));Result alternatives=ContextDependentProductionReplay.reopen(saved.bytes(),saved.pin,policy(),CAPACITY);Set<Integer> sizes=new HashSet<>();for(Refutation proof:alternatives.finalUnitRefutations)sizes.add(proof.sources.size());require(alternatives.finalUnitRefutations.size()==4&&sizes.equals(new HashSet<>(Arrays.asList(3,5))),"all Source superset alternatives rebuilt, not reloaded as proofs");
        m=memory();Saved empty=ContextDependentProductionReplay.capture(m,inventory(m),new Request(policy(),CAPACITY,Collections.emptyList()));Result none=ContextDependentProductionReplay.reopen(empty.bytes(),empty.pin,policy(),CAPACITY);require(none.products.isEmpty()&&none.originals.isEmpty(),"empty original ledger round trip invents no facts");
    }
    private static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path dir,String name)throws Exception{user.setDatabaseDir(dir+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static void owning()throws Exception {
        Path dir=Files.createTempDirectory("horn-original-provider-");for(String n:new String[]{"A","B"})try(Fixture f=new Fixture(dir,n)){require(f.root.compile("!"+n.toLowerCase()+"(1);",null,false),"foreign fixture");}
        for(boolean trust:new boolean[]{false,true}) {String name="X"+trust;Saved saved;long revision;String before;
            try(Fixture f=new Fixture(dir,name)){require(f.root.compile("!anchor(1);",null,false),"physical parent");f.db.connectContext("A",trust?"t":null);f.db.connectContext("B",trust?"t":null);revision=f.db.getRevision();before=ContextProductionInventory.inspect(f.root,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;Mind child=new Mind(f.root);Rule seed=raw(child,"!@a $u @b rel(a,u,b);"),h=raw(child,"!@a @u @b rel(a,u,b)->mid(a,u,b);"),next=raw(child,"!@a @u @b mid(a,u,b)->tagged(a,u,b);");raw(child,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");Request request=recipe(h,next,seed);String proposed=ContextProductionInventory.inspect(child,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;
                try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){saved=lease.captureDependentProductions(request);require(saved.evidence.products.size()==2&&saved.evidence.finalUnitRefutations.size()==1,"owning capture records knowledge/recipe and checks produced local conflict");require(proposed.equals(ContextProductionInventory.inspect(child,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"capturing original-only packet never writes products into child");raw(child,"!later(1);");reject(()->lease.captureDependentProductions(request),"stale lease cannot recapture historical bytes as fresh authority");}finally{f.root.release(child);}
                require(f.db.getRevision()==revision&&before.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"no candidate/publication after release");
            }
            try(Fixture f=new Fixture(dir,name)){require(f.db.getRevision()==revision&&before.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"real DB cold reopen contains original parent only");Result r=ContextDependentProductionReplay.reopen(saved.bytes(),saved.pin,policy(),CAPACITY);require(r.auditDigest.equals(saved.evidence.auditDigest)&&r.finalUnitRefutations.size()==1,"historical generated graph reconstructs after provider close without writer authority");}
        }
    }
    private static void exitedWriter()throws Exception{Path packet=Files.createTempFile("horn-exited-writer-",".bin"),pin=Files.createTempFile("horn-exited-writer-",".sha256");require(worker("--write",packet.toString(),pin.toString()).contains("HORN_ORIGINAL_ONLY_WRITER_EXIT"),"writer exits with only originals/recipe/digest on disk");require(worker("--read",packet.toString(),new String(Files.readAllBytes(pin),StandardCharsets.US_ASCII),"0").contains("HORN_ORIGINAL_ONLY_READER_OK"),"new reader starts after writer exits and rebuilds typed productions");Files.delete(packet);Files.delete(pin);}
    public static void main(String[] args){try{
        System.setProperty("user.home",Files.createTempDirectory("horn-original-replay-").toString());
        if(args.length>0&&args[0].equals("--write")){Saved saved=packet(0,false);Files.write(Paths.get(args[1]),saved.bytes());Files.write(Paths.get(args[2]),saved.pin.getBytes(StandardCharsets.US_ASCII));System.out.println("HORN_ORIGINAL_ONLY_WRITER_EXIT");return;}
        if(args.length>0&&args[0].equals("--read")){if(Files.size(Paths.get(args[1]))>policy().bytes)throw new AssertionError("Packet file bound");Result result=ContextDependentProductionReplay.reopen(Files.readAllBytes(Paths.get(args[1])),args[2],policy(),CAPACITY);if(args[3].equals("typed"))validateTyped(result);else if(args[3].startsWith("wide:"))validateWide(result,Integer.parseInt(args[3].substring(5)));else validate(result,Integer.parseInt(args[3]));System.out.println("HORN_ORIGINAL_ONLY_READER_OK checks="+checks);return;}
        Saved sample=null;for(int kind=0;kind<3;kind++)for(boolean reverse:new boolean[]{false,true}){Saved saved=packet(kind,reverse);cold(saved,kind);if(kind==0&&!reverse)sample=saved;}
        corruption(sample);wider();exitedWriter();owning();System.out.println("CONTEXT_DEPENDENT_PRODUCTION_REPLAY_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
