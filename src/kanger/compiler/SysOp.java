package kanger.compiler;

import kanger.Mind;
import kanger.enums.LibMode;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.IRunnable;
import kanger.primitives.Argument;
import kanger.primitives.Domain;
import kanger.primitives.Function;

import javax.script.ScriptException;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by murray on 27.05.15.
 */
public class SysOp {

    private final List<String> scripts = new ArrayList<>();
    private final List<String> params = new ArrayList<>();
    private LibMode mode = LibMode.UNKNOWN;
    private String name = "";                   /* predefined name */
    private IRunnable proc = null;              /* called procedure */
    private int range = 0;
    private SysOp next = null;
//    private boolean registered = false;

    public SysOp(final Mind mind) {
        proc = new IRunnable() {
            @Override
            public Object run(Object o) throws RuntimeErrorException {

                List<Argument> arg = (o instanceof Domain) ? ((Domain) o).getArguments() : ((Function) o).getArguments();

                int result = 1;
                String script = "";
//                Term rval = null;
//                rval = arg.get(arg.size() - 1).getC();
                int i = 0;
                int undefined = 0;
                for (Argument a : arg) {
                    String var = params.get(i);
                    if (arg.get(i).getValue() != null) {
                        mind.getScryptEngine().put(var, arg.get(i).getValue().getValue());
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

                        mind.getScryptEngine().put("kanger", mind);
                        mind.getScryptEngine().eval(script);
                        i = 0;
                        for (String var : params) {
                            Object val = mind.getScryptEngine().get(var);
                            if (val == null) {
                                result = 0;
                                arg.get(i++).delValue();
                            } else {
                                arg.get(i++).setValue(mind.getTerms().add(val));
                            }
                        }
//                        if (result != 0 && rval != null) {
//                            if (rval.compareTo(arg.get(arg.size() - 1).getC()) != 0) {
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

    public SysOp(DataInputStream dis, Mind mind) throws IOException {
        this(mind);
        mode = LibMode.values()[dis.readInt()];
        name = dis.readUTF();
        int cnt = dis.readInt();
        while (cnt-- > 0) {
            scripts.add(dis.readUTF());
        }
        range = dis.readInt();
        for (int i = 0; i < range; ++i) {
            String param = dis.readUTF();
            params.add(param);
        }
    }


    public SysOp(LibMode mode, String name, int range, IRunnable proc) {
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

    public IRunnable getProc() {
        return proc;
    }

    public void setProc(IRunnable proc) {
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
}
