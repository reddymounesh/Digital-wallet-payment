package com.stark.wallet.wallet_app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TransferResponseDto {


    private UUID transactionGroupId;


    private BigDecimal senderBalance;


    private String status;

    private Instant timestamp;



}
