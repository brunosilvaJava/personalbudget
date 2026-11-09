package com.bts.personalbudget.core.domain.enumerator;

/**
 * What moved the balance of an account on a line of its statement.
 */
public enum AccountStatementEntryType {
    /** The initial balance, on the initial balance date. It is not income. */
    INITIAL_BALANCE,
    /** An active settlement (payment or receipt) of a movement on this account. */
    SETTLEMENT,
    /** An active transfer from another account. */
    TRANSFER_IN,
    /** An active transfer to another account. */
    TRANSFER_OUT
}
