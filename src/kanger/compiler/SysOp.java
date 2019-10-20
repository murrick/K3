package kanger.compiler;

import kanger.Mind;
import kanger.User;
import kanger.enums.LibMode;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.IReactor;
import kanger.interfaces.IUnit;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.units.Domain;
import kanger.units.Function;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

//import javax.script.*;

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


    //TODO: Очень странные результаты ?$x $y x=plus(y,4), y: 1..8;
    //TODO: И еще более странные ?$x $y x=y+4, y: 1..8;  ?$x $y y: 1..8, x=y+4;
    public SysOp(final Mind mind) {

        proc = new IReactor() {
            @Override
            public Object run(Object o) throws Exception {

                ArgList arg = (o instanceof Domain) ? ((Domain) o).getArguments() : ((Function) o).getArguments();
                ScriptEngine scryptEngine = new ScriptEngineManager().getEngineByName("js");

                int result = 1;
                String script = "";
//                Term rval = null;
//                rval = arg.createCVar(arg.size() - 1).getC();
                int i = 0;
                int undefined = 0;
                for (Argument a : arg) {
                    String var = params.get(i);
                    if (arg.get(i).getValue() != null) {
                        scryptEngine.put(var, arg.get(i).getValue().getValue());
                    } else {
                        ++undefined;
                        if (i + 1 == arg.size() && !scripts.isEmpty()) {
                            script = scripts.get(0);
                        } else if (i + 1 < scripts.size()) {
                            script = scripts.get(i + 1);
                        } else {
                            script = "";
                        }
                    }
                    ++i;
                }
                if (undefined > 1) {
                    result = 0;
                } else {
                    try {

                        scryptEngine.put("kanger", mind);
                        scryptEngine.eval(script);

                        i = 0;
                        for (String var : params) {
                            Object val = scryptEngine.get(var);
                            if (val == null) {
                                result = 0;
                                arg.get(i++).setValue(null);
                            } else {
                                arg.get(i++).setValue(mind.getTerms().add(val));
                            }
                        }
//                        if (result != 0 && rval != null) {
//                            if (rval.compareTo(arg.createCVar(arg.size() - 1).getC()) != 0) {
//                                result = 0;
//                            }
//                        }
                    } catch (ScriptException ex) {
                        throw new RuntimeErrorException(SysOp.this, ex.getMessage());
                    }
                }
                return result;
            }
        };

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

    //TODO: name ---->>>>> Term !!!!!!!!!!
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
}
