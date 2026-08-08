/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

/**
 * Canonical operator intents produced by the shared KANGER command parser.
 *
 * <p>This enum belongs to the command-language boundary only. It deliberately
 * carries no Core, Console, Server, transport or persistence behavior.</p>
 */
public enum CommandIntent {
    RULE_STATUS,
    RULE_SHOW,
    RULE_ALL,
    RULE_PRODUCED,
    RULE_LEVEL,
    RULE_TREE,
    RULE_COMMENT_GET,
    RULE_COMMENT_SET,

    FUNCTIONS,
    FUNCTION_SHOW,
    FUNCTION_SOURCE,

    BASE_STATUS,
    BASE_PREDICATES,
    BASE_PREDICATE,
    BASE_TREE,

    VALUES,
    VALUES_ORDER,

    SOLUTIONS,
    SOLUTION_SHOW,
    SOLUTION_TREE,

    WHEN_STATUS,
    WHEN_ACCEPT,

    TX_STATUS,
    TX_START,
    TX_COMMIT,
    TX_ROLLBACK,

    SOURCE_GET,
    SOURCE_PUT,
    SOURCE_DELETE,

    STORAGE_STATUS,
    STORAGE_USE,
    STORAGE_CLOSE,
    STORAGE_DROP,
    STORAGE_REINDEX,

    ERASE,
    HELP,
    QUIT
}
