package org.kanger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Runs an unchanged native runner inside an observational session. */
public final class JournalRunner {
    public static void main(String[] args)throws Exception{
        TValueStateJournal.begin();
        try{Class.forName("org.kanger."+args[0]).getMethod("main",String[].class).invoke(null,(Object)Arrays.copyOfRange(args,1,args.length));}
        finally{List<String> rows=TValueStateJournal.finish();Files.write(Paths.get(System.getProperty("journal.path")),rows,StandardCharsets.UTF_8);System.out.println(rows.get(rows.size()-1));}
    }
}
