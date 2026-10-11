/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.IContextResults;

/** Real unconnected immutable sources may disagree; preflight must preserve both exact pins. */
public final class DmzSourcePreflightRunner {
    private static int checks;
    public static void main(String[] args) {
        try { run(); System.out.println("DMZ_SOURCE_PREFLIGHT_PASS checks="+checks); System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void run() throws Exception {
        Path directory=Files.createTempDirectory("dmz-source-preflight-");
        System.setProperty("user.home",directory.toString());
        IContextResults.Revision a=create(directory,"A","!@x source(x) -> middle(x); !source(2);");
        IContextResults.Revision b=create(directory,"B","!~source(2);");
        create(directory,"Q","!authority(1);");
        IContextResults.Revision word=create(directory,"Word","!word(item);");
        User user=new User(); user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB(); data.init(user);
        Mind q=new Mind(user); user.setCurrentMind(q); q=(Mind)q.useStorage("Q"); user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(data.getContextId(),data.getRevision());
            List<DmzSourcePlan.Selection> selected=new ArrayList<DmzSourcePlan.Selection>(Arrays.asList(
                    new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b)));
            DmzSourcePlan.Request request=new DmzSourcePlan.Request(pin,selected,1000);
            selected.clear(); require(request.external.size()==2,"request detaches caller selection list");
            require(!data.hasConnectedContexts(),"fixture has no connections or trust groups");
            DmzSourcePlan first=data.prepareDmzSources(q,request);
            require(first.authority.role==DmzSourcePlan.Role.AUTHORITY_Q && first.authority.primaryRules==1,
                    "Q participant role is explicit");
            require(first.external.size()==2 && first.external.get(0).primaryRules==2 && first.external.get(1).primaryRules==1,
                    "opposite foreign claims are acquired without joint collision qualification");
            require(first.external.get(0).revision.getContextId().equals(a.getContextId()) && first.external.get(0).revision.getRevision()==a.getRevision(),"actual immutable A pin");
            require(first.external.get(1).revision.getContextId().equals(b.getContextId()) && first.external.get(1).revision.getRevision()==b.getRevision(),"actual immutable B pin");
            require(first.external.get(0).inspectedRows>first.external.get(0).primaryRules,
                    "generated cache consumes scan budget but is not a primary declaration");
            require(first.inspectedRows==first.authority.inspectedRows+first.external.get(0).inspectedRows+first.external.get(1).inspectedRows,"shared row allowance across participants");
            DmzSourcePlan exact=data.prepareDmzSources(q,new DmzSourcePlan.Request(pin,request.external,first.inspectedRows));
            require(exact.external.get(0).primaryDigest.equals(first.external.get(0).primaryDigest),"exact admitted row boundary");
            reject(data,q,new DmzSourcePlan.Request(pin,request.external,first.inspectedRows-1),"budget exceeded");
            require(user.getCurrentMind()==q && data.getRevision()==pin.getRevision() && !data.hasConnectedContexts(),
                    "preflight and refusal preserve active Q and connections");
            append(directory,"A","!source(3);");
            DmzSourcePlan after=data.prepareDmzSources(q,request);
            require(after.external.get(0).primaryDigest.equals(first.external.get(0).primaryDigest)
                    && after.external.get(0).primaryRules==first.external.get(0).primaryRules,
                    "advancing foreign HEAD never floats the selected immutable revision");
            reject(data,q,new DmzSourcePlan.Request(new IContextResults.Revision(pin.getContextId(),pin.getRevision()+1),request.external,1000),"Q revision mismatch");
            reject(data,q,new DmzSourcePlan.Request(pin,Collections.singletonList(new DmzSourcePlan.Selection("A",new IContextResults.Revision(UUID.randomUUID(),a.getRevision()))),1000),"identity mismatch");
            reject(data,q,new DmzSourcePlan.Request(pin,Collections.singletonList(new DmzSourcePlan.Selection("A",new IContextResults.Revision(a.getContextId(),a.getRevision()+999))),1000),null);
            reject(data,q,new DmzSourcePlan.Request(pin,Collections.singletonList(new DmzSourcePlan.Selection("Word",word)),1000),"ground integer");
            boolean duplicates=false;
            try { new DmzSourcePlan.Request(pin,Arrays.asList(request.external.get(0),request.external.get(0)),1000); }
            catch(IllegalArgumentException expected) { duplicates=true; }
            require(duplicates,"duplicate identities refused before acquisition");
            boolean qAsExternal=false;
            try { new DmzSourcePlan.Request(pin,Collections.singletonList(new DmzSourcePlan.Selection("Q",pin)),1000); }
            catch(IllegalArgumentException expected) { qAsExternal=true; }
            require(qAsExternal,"Q cannot also be a foreign source");
            boolean path=false;
            try { new DmzSourcePlan.Selection("../A",a); }catch(IllegalArgumentException expected) { path=true; }
            require(path,"selection is a catalog name rather than a filesystem traversal");
            Mind wrong=new Mind(new User());
            boolean owner=false;
            try { data.prepareDmzSources(wrong,request); }catch(org.kanger.exception.CommandErrorException expected) { owner=true; }
            require(owner,"foreign User cannot initiate provider preflight");
            try { first.external.clear(); throw new AssertionError("mutable source result"); }
            catch(UnsupportedOperationException expected) { ++checks; }
            try { request.external.clear(); throw new AssertionError("mutable source request"); }
            catch(UnsupportedOperationException expected) { ++checks; }
            require(first.external.get(0).primaryDigest.length()==64,"historical digest remains usable after every foreign runtime closes");
            require(data.prepareDmzSources(q,request).external.size()==2,"failure cleanup permits a subsequent complete request");
            require(user.getCurrentMind()==q && data.getContextId().equals(pin.getContextId()) && data.getRevision()==pin.getRevision(),"all source failures preserve Q revision and User");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static IContextResults.Revision create(Path directory,String name,String statements) throws Exception {
        append(directory,name,statements);
        try(ContextSnapshot snapshot=ContextSnapshot.open(directory.resolve(name))) {
            return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());
        }
    }
    private static void append(Path directory,String name,String statements) throws Exception {
        User user=new User(); user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB(); data.init(user);
        Mind mind=new Mind(user); user.setCurrentMind(mind); mind=(Mind)mind.useStorage(name); user.setCurrentMind(mind);
        try { require(mind.compile(statements,null,false),"persistent source "+name); }
        finally { user.setCurrentMind(mind.closeStorage()); }
    }
    private static void reject(DB data,Mind q,DmzSourcePlan.Request request,String fragment) throws Exception {
        boolean rejected=false;
        try { data.prepareDmzSources(q,request); }
        catch(Exception expected) { if(fragment!=null && !expected.toString().contains(fragment))throw expected; rejected=true; }
        require(rejected,"source request refusal: "+fragment);
    }
    private static void require(boolean value,String reason) { ++checks; if(!value)throw new AssertionError(reason); }
}
