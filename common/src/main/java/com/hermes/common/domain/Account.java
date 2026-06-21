package com.hermes.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A wallet/account with a balance held in integer cents — money is never a
 * floating-point value. The {@link Version} column gives optimistic locking;
 * the worker additionally takes a pessimistic row lock when debiting so two
 * concurrent debits on the same account can never overdraw it.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "holder", nullable = false)
    private String holder;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    @Version
    @Column(name = "version")
    private long version;

    protected Account() {
        // for JPA
    }

    public Account(String id, String holder, long balanceCents) {
        this.id = id;
        this.holder = holder;
        this.balanceCents = balanceCents;
    }

    public boolean canDebit(long amountCents) {
        return amountCents > 0 && balanceCents >= amountCents;
    }

    public void debit(long amountCents) {
        if (!canDebit(amountCents)) {
            throw new IllegalStateException("Insufficient funds on " + id);
        }
        this.balanceCents -= amountCents;
    }

    public String getId() {
        return id;
    }

    public String getHolder() {
        return holder;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public long getVersion() {
        return version;
    }
}
