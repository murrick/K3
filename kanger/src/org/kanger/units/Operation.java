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

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Dmitry G. Quznetsov on 27.05.15.
 */
public class Operation implements IUnit<Operation>, IOperation {

    protected final List<String> params = new ArrayList<>();
    protected final List<String> scripts = new ArrayList<>();
    protected LibMode mode = LibMode.UNKNOWN;
    protected String name = "";                   /* predefined name */
    protected IReactor proc = null;              /* called procedure */
    protected int range = 0;
    protected Operation next = null;
    protected transient Mind mind = null;
    //    protected transient boolean deleted = false;
    protected long id = -1;                                       // id домена
    private long mindId = -1;                                   // id транзакции


    public Operation(Mind mind) {

        this.mind = mind;
    }


    public Operation(LibMode mode, String name, int range, IReactor proc) {
        this.mode = mode;
        this.name = name;
        this.proc = proc;
        this.range = range;
    }

    public Operation() {
    }

    public static void showLog(IUnit o, TValue v) {
        if (((Mind) o.getMind()).isLogging() && v != null) {
            o.getMind().getLog().add(LogMode.ANALYZER, "Added: " + v);
            o.getMind().getLog().add(LogMode.ANALYZER, "\tFrom: " + o);
            o.getMind().getLog().add(LogMode.ANALYZER, "-------------------------------------------");
        }
    }

    @Override
    public LibMode getMode() {
        return mode;
    }

    public void setMode(LibMode mode) {
        this.mode = mode;
    }

    @Override
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

    @Override
    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public Operation getNext() {
        return next;
    }

    public void setNext(Operation next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return name + "(" + range + ")";
    }

    @Override
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
    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putInt(mode.ordinal())
                .putString(name)
                .putInt(scripts.size());
        for (String s : scripts) {
            packet.putString(s);
        }
        packet.putInt(range);
        for (int i = 0; i < range; ++i) {
            packet.putString(params.get(i));
        }
        return packet.createMarked();
    }

    @Override
    public Operation apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        mode = LibMode.values()[packet.getInt()];
        name = packet.getString();
        int cnt = packet.getInt();
        while (cnt-- > 0) {
            scripts.add(packet.getString());
        }
        range = packet.getInt();
        for (int i = 0; i < range; ++i) {
            String param = packet.getString();
            params.add(param);
        }
        params.add(name);
        return this;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.SYSOP;
    }

//    @Override
//    public SysOp commit(Mind m) throws Exception {
//        m.getLibrary().add(this);
//        this.setMind(m);
//        return this;
//    }


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
    public boolean equalsTo(Operation to) {
        return toString().equals(to.toString());
    }

    @Override
    public IMind getMind() {
        return mind;
    }

    @Override
    public Operation setMind(Mind mind) {
        this.mind = mind;
        return this;
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    @Override
    public List<String> getScripts() {
        return scripts;
    }

    @Override
    public List<String> getParams() {
        return params;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

}
