package org.kanger.primitives;

import org.kanger.enums.LogMode;

import java.util.Date;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class LogEntry {

    LogMode type = LogMode.ALL;
    Date time = new Date();
    String record = "";

    public LogEntry(LogMode type, String rec) {
        this.type = type;
        this.record = rec;
    }

    public LogMode getType() {
        return type;
    }

    public Date getTime() {
        return time;
    }

    public String getRecord() {
        return record;
    }


}
