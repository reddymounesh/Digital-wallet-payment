package com.stark.wallet.wallet_app.repository;

import com.stark.wallet.wallet_app.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey,String> {


}
