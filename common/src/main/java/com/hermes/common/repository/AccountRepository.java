package com.hermes.common.repository;

import com.hermes.common.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Fetches an account with a pessimistic write lock so concurrent ledger
     * workers serialise on the row while debiting — this is what prevents an
     * account from being overdrawn when charges arrive in a burst.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") String id);

    /** Overdraft invariant: this must always be 0. */
    long countByBalanceCentsLessThan(long threshold);
}
