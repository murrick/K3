/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.udf;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.ArgumentType;
import org.kanger.factory.DictionaryFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Function;
import org.kanger.units.Operation;
import org.kanger.units.TValue;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class UDF extends Operation implements IReactor {

    private static Context scriptContext = null;

    public UDF() {
        if (scriptContext == null) {
            scriptContext = Context.enter();
            scriptContext.setLanguageVersion(Context.VERSION_1_7);
        }
        this.proc = this;
    }

    public void init(IUser user) {
        ((User) user).setUdf(this.getClass());
    }

    @Override
    public Object run(Object o) throws Exception {
        IMind mind = ((IUnit) o).getMind();
        ArgumentsList arg = ((Function) o).getArguments();
        Scriptable scope = scriptContext.initStandardObjects();

        int ret = 1;
        String script = "";
        int undefined = 0;
        int index = -1;
        for (int i = 0; i < arg.size(); ++i) {
            String var = params.get(i);
            if (((Mind) mind).getCalculator().getFunctions().isDefined(arg.get(i))) {
                scope.put(var, scope, arg.get(i).getValue(mind).getValue());
            } else if (arg.get(i).isEmpty((Mind) mind)) {
                index = i;
                ++undefined;
            } else {
                undefined = 3;
            }
        }
        if (undefined > 1) {
            ret = 0;
        } else {
            ITerm fres = null;
            if (index == -1) {
                script = scripts.get(0);
                fres = arg.get(arg.size() - 1).getValue(mind);
            } else if (index + 1 == arg.size() && !scripts.isEmpty()) {
                script = scripts.get(0);
            } else if (index + 1 < scripts.size()) {
                script = scripts.get(index + 1);
            } else {
                script = "";
                ret = 0;
            }

            if (!script.isEmpty()) {
                scope.put("mind", scope, mind);

                scriptContext.evaluateString(scope, script, "script", 1, null);

                if (index != -1) {
                    Object val = scope.get(params.get(index), scope);
                    if (val == null) {
                        ret = 0;
                        ((Argument) arg.get(index)).setValue((Mind) mind, null);
                    } else {
                        if (val instanceof NativeJavaObject) {
                            val = ((NativeJavaObject) val).unwrap();
                        } else if (val instanceof NativeArray) {
                            val = ((NativeArray) val).toArray();
                        }
                        if (!(val instanceof ITerm)) {
                            val = ((DictionaryFactory) mind.getTerms()).add(val);
                        }
                        if (!((Argument) arg.get(index)).setValue((Mind) mind, (ITerm) val)) {
                            ret = 0;
                        }
                    }
                } else if (fres != null) {
                    Object val = scope.get(params.get(params.size() - 1), scope);
                    ITerm cres = ((DictionaryFactory) mind.getTerms()).add(val);
                    if (cres.getId() == fres.getId()) {
                        ret = 2;
                    } else {
                        scope.put(params.get(params.size() - 1), scope, fres.getValue());
                        for (int i = 0; i < arg.size() - 1; ++i) {
                            if (arg.get(i).getType() == ArgumentType.TVARIABLE) {
                                String var = params.get(i);
                                script = scripts.get(i + 1);
                                Object tmp = scope.get(var, scope);
                                scriptContext.evaluateString(scope, script, "script", 1, null);
                                Object calc = scope.get(var, scope);
                                scope.put(var, scope, tmp);
                                TValue v = ((Mind) mind).getCalculator().getFunctions().addTValue(arg.get(i), ((DictionaryFactory) mind.getTerms()).add(calc));
                                showLog((IUnit) o, v);
                            }
                        }
                        ret = 0;
                    }
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
