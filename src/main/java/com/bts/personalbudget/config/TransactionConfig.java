package com.bts.personalbudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionConfig {

    /**
     * Used by RecurrenceBillService to run each account's catch-up (FixedBillService.
     * postDueOccurrences / InstallmentBillService.postDueInstallments) in its own transaction, so
     * one account failing can't roll back the accounts already posted earlier in the same run,
     * and the job can keep going after logging the failure.
     */
    @Bean
    public TransactionTemplate requiresNewTransactionTemplate(final PlatformTransactionManager transactionManager) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }
}
