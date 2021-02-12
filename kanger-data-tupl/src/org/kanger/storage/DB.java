package org.kanger.storage;

import org.cojen.tupl.Database;
import org.cojen.tupl.DatabaseConfig;
import org.cojen.tupl.DurabilityMode;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class DB implements IData {

    DatabaseConfig config = null;
    private String storageName = "";
    private Database db = null;
    private Map<String, IBase> bases = new HashMap<>();
    private IUser user = null;

    @Override
    public void init(IUser user) {
        this.user = user;
        user.setData(this);
        config = new DatabaseConfig()
                .minCacheSize(100_000_000)
                .durabilityMode(DurabilityMode.NO_FLUSH);
    }

    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }

        String dbPath = user.getDatabaseDir();
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
    public synchronized void flush() throws IOException {
        if (!isClosed()) {
            db.checkpoint();
        }
    }

    @Override
    public void remove() throws IOException {
        if (!isClosed()) {
            String dbPath = user.getDatabaseDir();
            dbPath += storageName;
            close();
            new File(dbPath + ".db").delete();
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
                IBase base = new Base(db, context, user);
                bases.put(context, base);
            }
            return bases.get(context);
        } else {
            throw new RuntimeErrorException("Database doesn't opened");
        }
    }

    @Override
    public IBase connect(String context) throws Exception {
        return getBase(context);
    }

    @Override
    public String getDescription() {
        return "Cojen Tupl based data model";
    }

    @Override
    public Collection<String> list() {
        //TODO: Список доступных баз
        return new ArrayList<>();
    }

}
