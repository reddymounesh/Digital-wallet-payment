package com.stark.wallet.wallet_app.repository;

import com.stark.wallet.wallet_app.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);


}
