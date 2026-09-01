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

package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Quznetsov on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument implements IArgument {

    private static final long serialVersionUID = -7113328096110690461L; //196402070011L;

    private transient long id = -1;
    private transient ArgumentType type = ArgumentType.EMPTY;
    private IUnit o = null;
    private int varOrder = -1;

    public Argument() {
    }

    public Argument(ITerm d) {
        this((IUnit) d);
    }

    public Argument(IUnit d) {
        o = d;
        if (o != null) {
            id = o.getId();
            type = detectObjectType();
        }
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putInt(type.ordinal())
                .putInt(varOrder);
        return packet.createMarked();
    }

    public Argument apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        type = ArgumentType.values()[packet.getInt()];
        varOrder = packet.getInt();
        return this;
    }

    private void load(Mind mind) throws Exception {
        switch (type) {
            case TERM:
                o = mind.getTerms().get(id);
                break;
            case TVARIABLE:
                o = mind.getTVars().get(id);
                break;
            case TVALUE:
                o = mind.getTValues().get(id);
                break;
            case FUNCTION:
                o = mind.getFunctions().get(id);
                break;
            case FVALUE:
                o = mind.getFValues().get(id);
                break;
            default:
                o = null;
        }
    }

    private ArgumentType detectObjectType() {
        if (o instanceof Term) {
            return ArgumentType.TERM;
        } else if (o instanceof TVariable) {
            return ArgumentType.TVARIABLE;
        } else if (o instanceof TValue) {
            return ArgumentType.TVALUE;
        } else if (o instanceof FValue) {
            return ArgumentType.FVALUE;
        } else if (o instanceof Function) {
            return ArgumentType.FUNCTION;
        } else {
            return ArgumentType.EMPTY;
        }
    }

    @Override
    public ITerm getValue(IMind mind) throws Exception {
        switch (type) {
            case TERM:
                return (Term) getObject(mind);
            case TVARIABLE:
                return ((TVariable) getObject(mind)).getValue();
            case TVALUE:
                return ((TValue) getObject(mind)).getValue((Mind) mind);
            case FVALUE:
                return ((FValue) getObject(mind)).getValue((Mind) mind);
            case FUNCTION:
                return ((Function) getObject(mind)).getValue((Mind) mind);
            default:
                return null;
        }
    }

    public boolean setValue(Mind mind, ITerm t) throws Exception {
        switch (type) {
            case EMPTY:
                o = (IUnit) t;
                if (o != null) {
                    id = o.getId();
                    type = ArgumentType.TERM;
                }
                return true;
            case TERM:
                o = (IUnit) t;
                id = o.getId();
                return true;
            case TVARIABLE:
                TVariable tv = (TVariable) getObject((Mind) mind);
                TValue s = tv.setValue(t);
                return true;
            case FUNCTION:
                Function f = (Function) getObject(mind);
                if (f.isCalculable()) {
                    f.setResult(t);
                    mind.getCalculator().calculate(f, mind.isLogging());
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public IUnit getObject(IMind mind) throws Exception {
        if (o == null && id != -1 && type != ArgumentType.EMPTY) {
            load((Mind) mind);
            if (o == null && mind != null && mind.isStorageUsed()) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                        "Database " + mind.getStorageName()
                                + " is semantically inconsistent: missing "
                                + type.name() + " id=" + id);
            }
        }
        return o;
    }

    public void setObject(IUnit o) {
        this.o = o;
        type = detectObjectType();
        if (o != null) {
            id = o.getId();
        } else {
            id = -1;
        }
    }

    public void clear() {
        o = null;
        type = ArgumentType.EMPTY;
        id = -1;
    }

    @Override
    public boolean isEmpty(Mind mind) {
        try {
            return getValue(mind) == null;
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return true;
        }
    }

    @Override
    public boolean isDeleted(IMind mind) {
        if (o == null) {
            return false;
        } else {
            return o.isDeleted(mind);
        }
    }

    @Override
    public String toString(IMind mind) throws Exception {
        Object val = getValue(mind);
        if (val != null) {
            return val.toString();
        } else {
            return "null";
        }
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public ArgumentType getType() {
        return type;
    }

    public UnitType getUnitType() {
        return UnitType.ARGUMENT;
    }

    public int getVarOrder() {
        return varOrder;
    }

    public void setVarOrder(int varOrder) {
        this.varOrder = varOrder;
    }

    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", type.name());
        map.put("var_order", varOrder);
        map.put("object", o != null ? o.createMap(mind) : "_");
        return map;
    }

    public Argument applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        type = ArgumentType.valueOf((String) map.get("type"));
        varOrder = Integer.parseInt(map.get("var_order") + "");
        switch (type) {
            case TERM:
                o = new Term();
                break;
            case TVARIABLE:
                o = new TVariable();
                break;
            case TVALUE:
                o = new TValue();
                break;
            case FVALUE:
                o = new FValue();
                break;
            case FUNCTION:
                o = new Function();
                break;
            default:
                o = null;
        }
        if (o != null) {
            o.applyMap((Map<String, Object>) map.get("object"));
        }
        return this;
    }
}
