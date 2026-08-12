package com.stark.wallet.wallet_app.dto;

import com.stark.wallet.wallet_app.entity.WalletType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WalletCreateRequestDto {

    @NotNull
    private WalletType type;
}
