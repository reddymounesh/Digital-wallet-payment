package com.stark.wallet.wallet_app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Setter
@Getter
@Entity
@Table(name = "wallets",uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id","type"})})
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id",nullable = false)
    private User user;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletType type;

    @Column(nullable = false,precision = 19,scale = 4)
    private BigDecimal balance;

    @Version
    private Long version;

    @Column(name="createdAt",nullable = false,updatable = false)
    private LocalDateTime createdAt;


    @PrePersist
    private void prePersist(){
        createdAt=LocalDateTime.now();
    }








}
