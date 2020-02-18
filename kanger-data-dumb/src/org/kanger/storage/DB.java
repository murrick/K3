package org.kanger.storage;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DB implements IData {

    String dbPath = "";
    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<>();

    @Override
    public void init() {

    }

    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }

        dbPath = System.getProperty("database.dir");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("user.dir");
        }
        if (!dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
            dbPath += File.separatorChar;
        }
        dbPath += name + File.separatorChar;
        storageName = name;

    }

    @Override
    public void close() throws IOException {
        for (IBase b : bases.values()) {
            ((Base) b).close();
        }
        bases.clear();
    }

    @Override
    public void flush() throws IOException {
        for (IBase b : bases.values()) {
            ((Base) b).flush();
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
    public IBase getBase(String context) throws IOException, RuntimeErrorException {
        if (!bases.containsKey(context)) {
            IBase base = new Base(dbPath + context);
            bases.put(context, base);
        }
        return bases.get(context);
    }
}
