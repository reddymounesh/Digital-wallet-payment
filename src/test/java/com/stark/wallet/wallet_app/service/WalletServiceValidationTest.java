package com.stark.wallet.wallet_app.service;

import com.stark.wallet.wallet_app.exception.InvalidTransferException;
import com.stark.wallet.wallet_app.repository.TransactionRepository;
import com.stark.wallet.wallet_app.repository.WalletRepository;
import com.stark.wallet.wallet_app.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class WalletServiceValidationTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void transfer_shouldRejectNegativeAmount() {
        WalletService service = new WalletService(walletRepository, transactionRepository, idempotencyKeyRepository);
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        assertThrows(InvalidTransferException.class, () ->
                service.transfer(senderId, receiverId, new BigDecimal("-50"), "key-1")
        );
    }

    @Test
    void transfer_shouldRejectSameSenderAndReceiver() {
        WalletService service = new WalletService(walletRepository, transactionRepository, idempotencyKeyRepository);
        UUID sameWalletId = UUID.randomUUID();

        assertThrows(InvalidTransferException.class, () ->
                service.transfer(sameWalletId, sameWalletId, new BigDecimal("100"), "key-2")
        );
    }
}