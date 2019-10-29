package org.kanger.compiler;

import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.units.TValue;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by Dmitry G. Qusnetsov on 27.05.15.
 */
public class SysOp implements Externalizable, IUnit<SysOp> {

    protected final List<String> scripts = new ArrayList<>();
    protected final List<String> params = new ArrayList<>();
    protected long id = -1;                                       // id домена
    protected LibMode mode = LibMode.UNKNOWN;
    protected String name = "";                   /* predefined name */
    protected IReactor proc = null;              /* called procedure */
    protected int range = 0;
    protected SysOp next = null;
    protected transient IUser user = null;
    protected transient boolean deleted = false;


    public SysOp(final IUser user) {

        this.user = user;
    }


    public SysOp(LibMode mode, String name, int range, IReactor proc) {
        this.mode = mode;
        this.name = name;
        this.proc = proc;
        this.range = range;
    }

    public SysOp() {
    }

    public static void showLog(IUnit o, TValue v) {
        if (o.getUser().getMind().isLogging() && v != null) {
            o.getUser().getMind().getLog().add(LogMode.ANALIZER, "Added: " + v);
            o.getUser().getMind().getLog().add(LogMode.ANALIZER, "\tFrom: " + o);
            o.getUser().getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
    }

    public LibMode getMode() {
        return mode;
    }

    public void setMode(LibMode mode) {
        this.mode = mode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IReactor getProc() {
        return proc;
    }

    public void setProc(IReactor proc) {
        this.proc = proc;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public SysOp getNext() {
        return next;
    }

    public void setNext(SysOp next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return name + "(" + range + ")";
    }

    public String asString() {
        String str = "=" + name + "(";
        if (params.isEmpty()) {
            str += (range > 0 ? range + "" : "") + ")";
        } else {
            String par = "";
            int i = 0;
            for (String n : params) {
                if (i++ < range) {
                    if (!par.isEmpty()) {
                        par += ",";
                    }
                    par += n;
                }
            }
            str += par + ")";
        }
        for (String script : scripts) {
            str += "\n{" + script.replace('\r', '\n') + "}";
        }
        str += ";";
        return str;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(id);
        out.writeInt(mode.ordinal());
        out.writeUTF(name);
        out.writeInt(scripts.size());
        for (String s : scripts) {
            out.writeUTF(s);
        }
        out.writeInt(range);
        for (int i = 0; i < range; ++i) {
            out.writeUTF(params.get(i));
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        id = in.readLong();
        mode = LibMode.values()[in.readInt()];
        name = in.readUTF();
        int cnt = in.readInt();
        while (cnt-- > 0) {
            scripts.add(in.readUTF());
        }
        range = in.readInt();
        for (int i = 0; i < range; ++i) {
            String param = in.readUTF();
            params.add(param);
        }
        params.add(name);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public int getHash() {
        return toString().hashCode();
    }

    @Override
    public boolean equalsTo(SysOp to) {
        return toString().equals(to.toString());
    }

    @Override
    public IUser getUser() {
        return user;
    }

    @Override
    public void setUser(IUser user) {
        this.user = user;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted() {
        deleted = true;
    }

    public List<String> getScripts() {
        return scripts;
    }

    public List<String> getParams() {
        return params;
    }

}
