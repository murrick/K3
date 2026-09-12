/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.units.Term;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for deterministic PERIOD semantic identity. */
public class PeriodIdentityTest {

    @Test
    void equivalentFixedPeriodsShareIdentity() throws Exception {
        Term minute = new Term("1 minute", null);
        Term seconds = new Term("60 seconds", null);
        Term week = new Term("1 week", null);
        Term days = new Term("7 days", null);

        assertTrue(minute.equalsTo(seconds));
        assertEquals(minute.getHash(), seconds.getHash());
        assertTrue(week.equalsTo(days));
        assertEquals(week.getHash(), days.getHash());
    }

    @Test
    void calendarPeriodsRemainCalendarPeriods() throws Exception {
        Term year = new Term("1 year", null);
        Term months = new Term("12 months", null);
        Term month = new Term("1 month", null);
        Term thirtyDays = new Term("30 days", null);
        Term thirtyOneDays = new Term("31 days", null);

        assertTrue(year.equalsTo(months));
        assertEquals(year.getHash(), months.getHash());
        assertFalse(month.equalsTo(thirtyDays));
        assertFalse(month.equalsTo(thirtyOneDays));
    }

    @Test
    void defaultTimeZoneCannotChangePeriodIdentity() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Term utc = new Term("6 months 1 day", null);

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            Term auckland = new Term("6 months 1 day", null);

            assertTrue(utc.equalsTo(auckland));
            assertEquals(utc.getHash(), auckland.getHash());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
