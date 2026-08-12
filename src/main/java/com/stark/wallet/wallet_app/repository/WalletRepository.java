package com.stark.wallet.wallet_app.repository;

import com.stark.wallet.wallet_app.entity.Wallet;
import com.stark.wallet.wallet_app.entity.WalletType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id=:id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
    boolean existsByUserIdAndType(UUID userId, WalletType type);

}


