package org.kanger;

public final class SemanticOperationReport {
    private final LinkerStatistics statistics;

    private SemanticOperationReport(LinkerStatistics statistics) {
        this.statistics = statistics == null ? new LinkerStatistics() : statistics;
    }

    public static SemanticOperationReport from(LinkerStatistics statistics) {
        return new SemanticOperationReport(statistics);
    }

    public long getInitialDurableExecutions() {
        return statistics.getFirstPassDurableUnifications();
    }

    public long getInitialQueryExecutions() {
        return statistics.getFirstPassQueryUnifications();
    }

    public long getInitialGeneratedExecutions() {
        return statistics.getFirstPassGeneratedUnifications();
    }

    public long getLaterDurableExecutions() {
        return statistics.getLaterPassDurableUnifications();
    }

    public long getLaterQueryExecutions() {
        return statistics.getLaterPassQueryUnifications();
    }

    public long getLaterGeneratedExecutions() {
        return statistics.getLaterPassGeneratedUnifications();
    }

    public long getExecutedOperations() {
        return statistics.getUnificationAttempts();
    }

    public long getProducedTValues() {
        return statistics.getNewTValues();
    }

    public double getTValueProductivity() {
        long executed = getExecutedOperations();
        return executed == 0L ? 0.0 : ((double) getProducedTValues()) / executed;
    }

    public String toCsvRow(int size, String operation, int rows) {
        return String.format(java.util.Locale.ROOT,
                "%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.9f",
                size, operation, rows, statistics.getPasses(),
                statistics.getCandidateRuleVisits(), statistics.getDomainPairs(),
                getExecutedOperations(), getInitialDurableExecutions(),
                getInitialQueryExecutions(), getInitialGeneratedExecutions(),
                getLaterDurableExecutions(), getLaterQueryExecutions(),
                getLaterGeneratedExecutions(), getProducedTValues(),
                getTValueProductivity());
    }

    public static String csvHeader() {
        return "size,operation,rows,passes,candidate_rule_visits,domain_pairs,"
                + "executed_operations,initial_durable,initial_query,"
                + "initial_generated,later_durable,later_query,later_generated,"
                + "produced_tvalues,tvalue_productivity";
    }
}
