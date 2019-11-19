package org.kanger.udf;

import org.kanger.Mind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.units.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;


public class UDF extends SysOp implements IReactor {

    private static Context scriptContext = null;

    public UDF() {
        if (scriptContext == null) {
            scriptContext = Context.enter();
            scriptContext.setLanguageVersion(Context.VERSION_1_7);
        }
        this.proc = this;
    }

    @Override
    public Object run(Object o) throws Exception {
        Mind mind = ((IUnit) o).getUser().getMind();
        ArgList arg = (o instanceof Domain) ? ((Domain) o).getArguments() : ((Function) o).getArguments();
//                    ScriptEngine scryptEngine = new ScriptEngineManager().getEngineByName("js");
        Scriptable scope = scriptContext.initStandardObjects();

        int ret = 1;
        String script = "";
        int undefined = 0;
        int index = -1;
        for (int i = 0; i < arg.size(); ++i) {
            String var = params.get(i);
            if (arg.get(i).isDefined(mind)) {
                scope.put(var, scope, arg.get(i).getValue(mind).getValue());
            } else if (arg.get(i).isEmpty(mind)) {
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
                fres = arg.get(arg.size() - 1).getValue(mind);
            } else if (index + 1 == arg.size() && !scripts.isEmpty()) {
                script = scripts.get(0);
            } else if (index + 1 < scripts.size()) {
                script = scripts.get(index + 1);
            } else {
                script = "";
            }

            scope.put("org/kanger", scope, user.getMind());

            scriptContext.evaluateString(scope, script, "script", 1, null);

            if (index != -1) {
                Object val = scope.get(params.get(index), scope);
                if (val == null) {
                    ret = 0;
                    arg.get(index).setValue(mind, null);
                } else {
                    if (!arg.get(index).setValue(mind, user.getMind().getTerms().add(val))) {
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
                            scriptContext.evaluateString(scope, script, "script", 1, null);
                            Object calc = scope.get(var, scope);
                            scope.put(var, scope, tmp);
                            TValue v = arg.get(i).addValue(mind, user.getMind().getTerms().add(calc));
                            showLog((IUnit) o, v);
                        }
                    }
                    ret = 0;
                }
            }
        }
        return ret;
    }

    @Override
    public IReactor getProc() {
        proc = this;
        return proc;
    }
}
