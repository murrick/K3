package org.kanger.compiler;

import org.kanger.User;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.units.Domain;
import org.kanger.units.Function;
import org.kanger.units.TValue;
import org.kanger.units.Term;
import org.mozilla.javascript.Scriptable;

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

    private long id = -1;                                       // id домена
    private final List<String> scripts = new ArrayList<>();
    private final List<String> params = new ArrayList<>();
    private LibMode mode = LibMode.UNKNOWN;
    private String name = "";                   /* predefined name */
    private IReactor proc = null;              /* called procedure */
    private int range = 0;
    private SysOp next = null;
//    private boolean registered = false;

    private transient User user = null;

    private transient boolean deleted = false;


    public SysOp(final User user) {

        this.user = user;
        getProc();
    }

//    public SysOp(DataInputStream dis, Mind mind) throws IOException {
//        this(mind);
//        mode = LibMode.values()[dis.readInt()];
//        name = dis.readUTF();
//        int cnt = dis.readInt();
//        while (cnt-- > 0) {
//            scripts.add(dis.readUTF());
//        }
//        range = dis.readInt();
//        for (int i = 0; i < range; ++i) {
//            String param = dis.readUTF();
//            params.add(param);
//        }
//    }


    public SysOp(LibMode mode, String name, int range, IReactor proc) {
        this.mode = mode;
        this.name = name;
        this.proc = proc;
        this.range = range;
    }

    public SysOp() {
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
        if (proc == null) {
            proc = new IReactor() {
                @Override
                public Object run(Object o) throws Exception {

                    ArgList arg = (o instanceof Domain) ? ((Domain) o).getArguments() : ((Function) o).getArguments();
//                    ScriptEngine scryptEngine = new ScriptEngineManager().getEngineByName("js");
                    Scriptable scope = user.getScriptContext().initStandardObjects();

                    int ret = 1;
                    String script = "";
                    int undefined = 0;
                    int index = -1;
                    for (int i = 0; i < arg.size(); ++i) {
                        String var = params.get(i);
                        if (arg.get(i).isDefined()) {
                            scope.put(var, scope, arg.get(i).getValue().getValue());
                        } else if (arg.get(i).isEmpty()) {
                            index = i;
                            ++undefined;
                        } else {
                            undefined = 3;
                        }
                    }
                    if (undefined > 1) {
                        ret = 0;
                    } else {
                        Term fres = null;
                        if (index == -1) {
                            script = scripts.get(0);
                            fres = arg.get(arg.size() - 1).getValue();
                        } else if (index + 1 == arg.size() && !scripts.isEmpty()) {
                            script = scripts.get(0);
                        } else if (index + 1 < scripts.size()) {
                            script = scripts.get(index + 1);
                        } else {
                            script = "";
                        }

                        scope.put("org/kanger", scope, user.getMind());

                        user.getScriptContext().evaluateString(scope, script, "script", 1, null);

                        if (index != -1) {
                            Object val = scope.get(params.get(index), scope);
                            if (val == null) {
                                ret = 0;
                                arg.get(index).setValue(null);
                            } else {
                                if (!arg.get(index).setValue(user.getMind().getTerms().add(val))) {
                                    ret = 0;
                                }
                            }
                        } else if (fres != null) {
                            Object val = scope.get(params.get(params.size() - 1), scope);
                            Term cres = user.getMind().getTerms().add(val);
                            if (cres.getId() == fres.getId()) {
                                ret = 2;
                            } else {
                                scope.put(params.get(params.size() - 1), scope, fres.getValue());
                                for (int i = 0; i < arg.size() - 1; ++i) {
                                    if (arg.get(i).isTSet()) {
                                        String var = params.get(i);
                                        script = scripts.get(i + 1);
                                        Object tmp = scope.get(var, scope);
                                        user.getScriptContext().evaluateString(scope, script, "script", 1, null);
                                        Object calc = scope.get(var, scope);
                                        scope.put(var, scope, tmp);
                                        TValue v = arg.get(i).addValue(user.getMind().getTerms().add(calc));
                                        showLog((IUnit) o, v);
                                    }
                                }
                                ret = 0;
                            }
                        }
                    }
                    return ret;
                }
            };
        }
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

    public List<String> getScripts() {
        return scripts;
    }

    public List<String> getParams() {
        return params;
    }


//    public boolean isRegistered() {
//        return registered;
//    }
//
//    public void setRegistered(boolean registered) {
//        this.registered = registered;
//    }

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
    public User getUser() {
        return user;
    }

    @Override
    public void setUser(User user) {
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

    public static void showLog(IUnit o, TValue v) {
        if (o.getUser().getMind().isLogging() && v != null) {
            o.getUser().getMind().getLog().add(LogMode.ANALIZER, "Added: " + v);
            o.getUser().getMind().getLog().add(LogMode.ANALIZER, "\tFrom: " + o);
            o.getUser().getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
    }

}
