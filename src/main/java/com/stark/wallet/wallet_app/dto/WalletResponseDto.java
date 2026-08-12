package com.stark.wallet.wallet_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class WalletResponseDto {
    private UUID id;
    private String type;
    private BigDecimal balance;

}

