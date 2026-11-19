/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.Mind;
/** Observe the default native ingress without the candidate interception hook. */
public final class ContextOrdinaryMaskedBaselineWitness {
 public static void main(String[] args) throws Exception {
  if (args.length != 1) throw new IllegalArgumentException("A fresh data directory is required");
  Path dir=Files.createDirectory(Paths.get(args[0]).toAbsolutePath());
  for (String name : new String[]{"A","B"}) {
   Mind m=ContextPrivatePublicationWitness.open(new DB(),dir,name);
   if(!m.compile(name.equals("A")?"!left(1); !p(1);":"!right(1); !~p(1);",null,false)) throw new AssertionError(name);
   m.closeStorage();
  }
  for(boolean reverse:new boolean[]{false,true}) {
   DB db=new DB();
   Mind m=ContextPrivatePublicationWitness.open(db,dir,reverse?"Reverse":"Forward");
   if(!m.compile("!anchor(1);",null,false)) throw new AssertionError();
   db.connectContext(reverse?"B":"A");db.connectContext(reverse?"A":"B");
   long before=db.getRevision();
   try{
    Object outcome=m.query("!@x left(x)&&right(x)->p(x);",null,false);
    System.out.println("reverse="+reverse+" result="+outcome+" before="+before+" after="+db.getRevision());
    if (!Boolean.TRUE.equals(outcome) || db.getRevision()!=before+1)
     throw new AssertionError("The baseline counterexample outcome changed");
   }catch(org.kanger.exception.StorageLifecycleException failure){
    throw new AssertionError("The baseline now rejects the masked rule, reclassify this witness", failure);
   }
   m.closeStorage();
  }
 }
}
