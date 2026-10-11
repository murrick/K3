/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.kanger.interfaces.*;
import org.kanger.storage.dumb2.DB;
import org.kanger.udf.UDF;

/** Native family restoration and every result of a multi-statement Console line. */
public final class CoreResultWorkflowRunner {
    private static int checks;
    private static final String FAMILY="!@x @z @e (father(x,z) || mother(x,z)), family(e), (x:e || length(e + [])=0) -> family(e + [x,z]);";
    public static void main(String[] args) {
        try { test();System.out.println("CORE_RESULT_WORKFLOW_PASS checks="+checks);System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    public static boolean test() throws Exception {
        Path dir=Files.createTempDirectory("core-result-workflow-");
        User user=new User();user.setDatabaseDir(dir.toString()+File.separator);
        user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());
        new UDF().init(user);new DB().init(user);
        Mind root=new Mind(user);user.setCurrentMind(root);
        root=(Mind)root.useStorage("native");user.setCurrentMind(root);
        Path source=Paths.get("kanger-qualification/test-data/core-workflow/natives.k");
        if(!Files.exists(source))source=Paths.get("test-data/core-workflow/natives.k");
        require(root.compile(new String(Files.readAllBytes(source),StandardCharsets.UTF_8),null,false),"native source accepted");
        Mind work=new Mind(root);user.setCurrentMind(work);
        try {
            String batch=process(work,"!~left(2); !~authority(1);");
            require(count(batch,"SUCCESS: New rule accepted")==2,"both mutation summaries displayed");
            require(batch.contains("left(2") && batch.contains("authority(1"),"both mutation solutions displayed");
            String queries=process(work,"?left(2); ?authority(1);");
            require(count(queries,"Result: FALSE")==2,"both query answers displayed");
            require(count(queries,"Solutions (1):")==2,"both query solutions displayed");
            String unknown=process(work,"?absent(1); ?absent(2);");
            require(count(unknown,"No hypothesis found")==2,"both unresolved query reports displayed");
            String mixed=process(work,"!temporary(9); ?temporary(9);");
            require(mixed.contains("SUCCESS: New rule accepted") && mixed.contains("Result: TRUE"),"mixed line retains both results");
            require(!hasRule(work,"!temporary(9);"),"query-containing line retains its rollback semantics");
            try {process(work,"!rollbackbatch(9); !broken(;");throw new AssertionError("Malformed batch accepted");}
            catch(org.kanger.exception.ParseErrorException expected){++checks;}
            require(!hasRule(work,"!rollbackbatch(9);"),"failed later statement rolls back earlier mutation");
            process(work,"!family([]);");process(work,FAMILY);expectRows(work,7);
            long production=rule(work,"family(e + [x,z])"),seed=rule(work,"!family([]);");
            process(work,"-rule("+production+");");expectRows(work,1);
            require(work.getProductions(work.getRules().get(production)).isEmpty(),"deleted productions excluded from reporting");
            String restored=process(work,FAMILY);expectRows(work,7);
            require(rule(work,"family(e + [x,z])")==production,"existing production identity restored");
            require(count(restored,"Produced ")==6,"only actual restored products reported");
            process(work,"-rule("+seed+");");expectRows(work,0);
            process(work,"!family([]);");expectRows(work,7);
            require(rule(work,"!family([]);")==seed,"existing seed identity restored");
            Mind rollback=new Mind(work);user.setCurrentMind(rollback);
            process(rollback,"-rule("+production+");");expectRows(rollback,1);
            work.release(rollback);user.setCurrentMind(work);expectRows(work,7);
            require(root.commit(work),"restored layer commits");work=null;user.setCurrentMind(root);expectRows(root,7);
            root=(Mind)root.closeStorage();user.setCurrentMind(root);
            root=(Mind)root.useStorage("native");user.setCurrentMind(root);expectRows(root,7);
            return true;
        } finally {
            if(work!=null){root.release(work);user.setCurrentMind(root);}
            if(root.isStorageUsed())user.setCurrentMind(root.closeStorage());
        }
    }
    private static boolean hasRule(Mind mind,String text) throws Exception {
        for(IRule r:mind.getRules())if(!r.isDeleted(mind) && r.toString().contains(text))return true;
        return false;
    }
    private static long rule(Mind mind,String text) throws Exception {
        for(IRule r:mind.getRules())if(!r.isDeleted(mind) && r.toString().contains(text))return r.getId();
        throw new AssertionError("Rule missing: "+text);
    }
    private static void expectRows(Mind mind,int expected) throws Exception {
        Mind query=new Mind(mind);
        try {
            Boolean answer=query.query("?$x family(x);",null,false);
            require(expected==0 ? answer==null : Boolean.TRUE.equals(answer),"family truth after transition");
            require(query.getValues().size()==expected,"expected "+expected+" family rows, got "+query.getValues().size());
            require(query.getSolutions().size()==expected,"family solution rows agree");
        } finally {mind.release(query);}
    }
    private static String process(Mind mind,String source) throws Exception {
        Method method=CanonicalConsole.class.getDeclaredMethod("processCore",String.class,IMind.class);method.setAccessible(true);
        PrintStream previous=System.out;ByteArrayOutputStream output=new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output,true,"UTF-8"));
            try {method.invoke(null,source,mind);}catch(InvocationTargetException failure){
                Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;
                if(cause instanceof Error)throw (Error)cause;throw failure;
            }
        } finally {System.setOut(previous);}
        return output.toString("UTF-8");
    }
    private static int count(String text,String needle){int n=0,p=0;while((p=text.indexOf(needle,p))>=0){++n;p+=needle.length();}return n;}
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
