package com.hermes.worker.service;

import com.hermes.common.domain.Account;
import com.hermes.common.domain.Payment;
import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.repository.AccountRepository;
import com.hermes.common.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional heart of the ledger. Each call runs in one DB transaction:
 * the account row is locked, the balance is checked and debited, and the payment
 * is settled atomically. If anything throws, the whole unit of work rolls back
 * and Kafka redelivers — combined with the idempotency guard below, that makes
 * settlement exactly-once in effect.
 */
@Service
public class LedgerService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;

    public LedgerService(PaymentRepository paymentRepository, AccountRepository accountRepository) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public LedgerResult apply(PaymentRequestedEvent event) {
        Payment payment = paymentRepository.findById(event.paymentId()).orElse(null);
        if (payment == null) {
            // Producer commits its own transaction; a fast worker can arrive first.
            // Retry after a back-off, then DLT if it truly never appears.
            throw new PaymentNotYetVisibleException(event.paymentId());
        }

        // Idempotency: redelivery of an already-settled payment is a no-op, so a
        // charge is never applied twice even under at-least-once delivery.
        if (!payment.isPending()) {
            return LedgerResult.SKIPPED_DUPLICATE;
        }

        Account account = accountRepository.findByIdForUpdate(event.accountId()).orElse(null);
        if (account == null) {
            payment.markRejected("UNKNOWN_ACCOUNT");
            return LedgerResult.REJECTED_UNKNOWN_ACCOUNT;
        }

        if (!account.canDebit(event.amountCents())) {
            payment.markRejected("INSUFFICIENT_FUNDS");
            return LedgerResult.REJECTED_INSUFFICIENT_FUNDS;
        }

        account.debit(event.amountCents());
        payment.markApplied();
        // both entities are managed → flushed on commit
        return LedgerResult.APPLIED;
    }
}
