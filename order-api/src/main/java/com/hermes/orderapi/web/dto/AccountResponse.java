package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.Account;

public record AccountResponse(
        String id,
        String holder,
        long balanceCents
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getHolder(), a.getBalanceCents());
    }
}
