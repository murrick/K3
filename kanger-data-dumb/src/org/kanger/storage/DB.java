package org.kanger.storage;

import org.kanger.enums.Enums;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class DB implements IData {

//    String dbPath = "";
    private static final Object locker = new Object();
    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<>();
    private IUser user = null;

    @Override
    public void init(IUser user) {
        this.user = user;
        user.setData(this);
    }

	private String getDbPath() {
        String dbPath = user.getProperty("database.dir");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = user.getProperty("user.dir") + Enums.FILE_SEPARATOR + "DB";
        }
        if (!dbPath.isEmpty() && !dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
            dbPath += Enums.FILE_SEPARATOR;
        }
        return dbPath;
    }
	
    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }
        storageName = name;
    }

    @Override
    public void close() throws IOException {
        for (IBase b : bases.values()) {
            ((Base) b).close();
        }
        bases.clear();
        storageName = "";
    }

    @Override
    public void flush() throws Exception {
        for (IBase b : bases.values()) {
            ((Base) b).flush();
        }
    }

//    @Override
//    public void remove() throws IOException {
//        if (!isClosed()) {
//            String tmp = dbPath;
//            close();
//            deleteDirectory(new File(tmp));
//        }
//    }

    public void remove() throws Exception {
        if (!isClosed()) {

            String tmp = storageName;
            close();

            String dbPath = getDbPath(); 
            dbPath += tmp;
            String name = Paths.get(dbPath).getFileName().toString();
            dbPath = dbPath.substring(0, dbPath.length() - name.length());


            File[] allContents = new File(dbPath).listFiles();
            if (allContents != null) {
                for (File file : allContents) {
                    if (file.getName().startsWith(name)) {
                        file.delete();
                    }
                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return bases.isEmpty();
    }

    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public IBase getBase(String context) throws Exception {
        if (!bases.containsKey(context)) {
            IBase base = new Base(getDbPath() + storageName, bases.size() + 1, locker, false, user);
            bases.put(context, base);
        }
        return bases.get(context);
    }

    @Override
    public IBase connect(String context) throws Exception {
        if (!isClosed()) {
            return bases.get(context);
//            IBase base = new Base(dbPath + context, true);
//            return base;
        } else {
            return null;
        }
    }

    @Override
    public String getDescription() {
        return "DUMB data model";
    }

    @Override
    public Collection<String> list() {
        List<String> list = new ArrayList<>();
        File[] dir = new File(getDbPath()).listFiles();
        if (dir != null) {
            for (File f : dir) {
                if (!f.isDirectory() && f.getName().contains(".store")) {
                    list.add(f.getName().replaceAll(".store", ""));
                }
            }
        }
        return list;
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
