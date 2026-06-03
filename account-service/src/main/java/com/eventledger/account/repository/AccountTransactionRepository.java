package com.eventledger.account.repository;

import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    Optional<AccountTransaction> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    boolean existsByAccountId(String accountId);

    /** Transactions for an account in chronological order by the original event time. */
    List<AccountTransaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    long countByAccountId(String accountId);

    /**
     * Net balance computed at the database: sum(CREDIT) - sum(DEBIT). This is
     * order-independent, so out-of-order arrival never affects the result.
     * Returns {@code null} only when the account has no transactions at all
     * (a netting-to-zero account still has rows and returns 0).
     */
    @Query("""
            select sum(case when t.type = :credit then t.amount else -t.amount end)
            from AccountTransaction t
            where t.accountId = :accountId
            """)
    BigDecimal computeBalance(@Param("accountId") String accountId,
                              @Param("credit") TransactionType credit);
}
