package org.kanger.storage;

import org.cojen.tupl.Database;
import org.cojen.tupl.DatabaseConfig;
import org.cojen.tupl.DurabilityMode;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DB implements IData {

    DatabaseConfig config = null;
    private String storageName = "";
    private Database db = null;
    private Map<String, IBase> bases = new HashMap<>();

    @Override
    public void init() {
        config = new DatabaseConfig()
                .minCacheSize(100_000_000)
                .durabilityMode(DurabilityMode.NO_FLUSH);
    }

    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }

        String dbPath = System.getProperty("database.dir");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("user.dir");
        }
        if (!dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
            dbPath += File.separatorChar;
        }
        dbPath += name;

        config.baseFilePath(dbPath);
        db = Database.open(config);

        storageName = name;
    }

    @Override
    public void close() throws IOException {
        if (db != null) {
            db.shutdown();
            db = null;
            bases.clear();
            storageName = "";
        }
    }

    @Override
    public void flush() throws IOException {
        if (!isClosed()) {
            db.checkpoint();
        }
    }

    @Override
    public boolean isClosed() {
        return db == null;
    }


    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public IBase getBase(String context) throws IOException, RuntimeErrorException {
        if (db != null) {
            if (!bases.containsKey(context)) {
                IBase base = new Base(db, context);
                bases.put(context, base);
            }
            return bases.get(context);
        } else {
            throw new RuntimeErrorException("Database doesn't opened");
        }
    }

}
