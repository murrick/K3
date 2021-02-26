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

import org.kanger.enums.LogMode;
import org.kanger.interfaces.ILogEntry;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class LogEntry implements ILogEntry {

    LogMode type = LogMode.ALL;
    Date time = new Date();
    String record = "";

    public LogEntry(LogMode type, String rec) {
        this.type = type;
        this.record = rec;
    }

    @Override
    public LogMode getType() {
        return type;
    }

    @Override
    public Date getTime() {
        return time;
    }

    @Override
    public String getRecord() {
        return record;
    }

    @Override
    public String toString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time) +
                " [" + (type.name() + "        ").substring(0, 8) + "] " +
                record;
    }
}
