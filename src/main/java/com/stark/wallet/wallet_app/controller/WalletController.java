package com.stark.wallet.wallet_app.controller;

import com.stark.wallet.wallet_app.dto.*;
import com.stark.wallet.wallet_app.entity.Wallet;
import com.stark.wallet.wallet_app.security.CustomUserDetails;
import com.stark.wallet.wallet_app.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService){
        this.walletService=walletService;

    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponseDto> transfer(@Valid @RequestBody TransferRequestDto request){

        TransferResponseDto response = walletService.transfer(
                request.getSenderWalletId(),
                request.getReceiverWalletId(),
                request.getAmount(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<List<TransactionHistoryDto>> getTransactionHistory(@PathVariable UUID walletId) {
        List<TransactionHistoryDto> history = walletService.getTransactionHistory(walletId);
        return ResponseEntity.ok(history);
    }

    @PostMapping
    public ResponseEntity<WalletResponseDto> createWallet(@Valid @RequestBody WalletCreateRequestDto request, @AuthenticationPrincipal CustomUserDetails userDetails) {

        Wallet wallet = walletService.createWallet(userDetails.getUser(), request.getType());
        WalletResponseDto response=new WalletResponseDto(wallet.getId(),wallet.getType().name(),wallet.getBalance());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);


    }



}
