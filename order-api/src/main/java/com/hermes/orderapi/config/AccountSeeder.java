package com.hermes.orderapi.config;

import com.hermes.common.domain.Account;
import com.hermes.common.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds wallet accounts on first start so the ledger demo runs out of the box.
 * Balances are deliberately modest so a load burst produces a mix of APPLIED and
 * REJECTED (insufficient funds) — the interesting case to watch on the dashboard.
 */
@Component
@Order(2)
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);
    private static final int ACCOUNT_COUNT = 50;

    private final AccountRepository accountRepository;

    public AccountSeeder(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            log.info("Accounts already present ({}), skipping seed", accountRepository.count());
            return;
        }
        Random rnd = new Random(7);
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= ACCOUNT_COUNT; i++) {
            String id = String.format("ACC-%04d", i);
            long balanceCents = (50 + rnd.nextInt(951)) * 100L; // $50 .. $1000
            accounts.add(new Account(id, "Holder " + i, balanceCents));
        }
        accountRepository.saveAll(accounts);
        log.info("Seeded {} accounts into the ledger", accounts.size());
    }
}
