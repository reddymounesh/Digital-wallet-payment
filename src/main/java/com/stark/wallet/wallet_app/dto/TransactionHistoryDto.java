package com.stark.wallet.wallet_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TransactionHistoryDto {
    private UUID transactionGroupId;
    private String direction;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
}