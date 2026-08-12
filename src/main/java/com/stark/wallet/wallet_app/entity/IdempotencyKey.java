package com.stark.wallet.wallet_app.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="idempotency_keys")
@Getter @Setter
public class IdempotencyKey {

    @Id
    private String idempotencykey;

    @Column(name = "transaction_group_id",nullable = false)
    private UUID transactionGroupId;

    @Column(name="sender_new_balance",nullable = false,precision = 19,scale = 4)
    private BigDecimal senderNewBalance;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at",nullable = false,updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist(){
        this.createdAt=Instant.now();
    }



}
