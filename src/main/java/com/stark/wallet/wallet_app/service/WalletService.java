package com.stark.wallet.wallet_app.service;

import com.stark.wallet.wallet_app.dto.TransactionHistoryDto;
import com.stark.wallet.wallet_app.dto.TransferResponseDto;
import com.stark.wallet.wallet_app.entity.*;
import com.stark.wallet.wallet_app.exception.InsufficientBalanceException;
import com.stark.wallet.wallet_app.exception.InvalidTransferException;
import com.stark.wallet.wallet_app.exception.WalletNotFoundException;
import com.stark.wallet.wallet_app.repository.IdempotencyKeyRepository;
import com.stark.wallet.wallet_app.repository.TransactionRepository;
import com.stark.wallet.wallet_app.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository = null;


    public WalletService(WalletRepository walletRepository, TransactionRepository transactionRepository, IdempotencyKeyRepository idempotencyKeyRepository){
        this.walletRepository=walletRepository;
        this.transactionRepository=transactionRepository;
    }
    public List<TransactionHistoryDto> getTransactionHistory(UUID walletId) {
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId);

        return transactions.stream()
                .map(t -> new TransactionHistoryDto(
                        t.getTransactionGroupId(),
                        t.getDirection().name(),
                        t.getAmount(),
                        t.getStatus().name(),
                        t.getCreatedAt()
                )).toList();
    }

    public Wallet createWallet(User user,WalletType type){

        boolean alreadyExists = walletRepository.existsByUserIdAndType(user.getId(),type);

        if(alreadyExists){
            throw new InvalidTransferException("User already has a "+type+"wallet");

        }

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setType(type);
        wallet.setBalance(BigDecimal.ZERO);

        return walletRepository.save(wallet);

    }


    @Transactional
    public TransferResponseDto transfer(UUID senderWalletId, UUID receiverWalletId, BigDecimal amount,String idempotencyKey){
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(idempotencyKey);
        if(existing.isPresent()){
            IdempotencyKey saved =existing.get();
            return new TransferResponseDto(
              saved.getTransactionGroupId(),
              saved.getSenderNewBalance(),
              saved.getStatus(),
              saved.getCreatedAt()
            );
        }

        if(senderWalletId.equals(receiverWalletId)){
            throw new InvalidTransferException("sender and receiver should not be same");
        }

        UUID firstLockId;
        UUID secondLockId;





        if(senderWalletId.compareTo(receiverWalletId)<0){
            firstLockId = senderWalletId;
            secondLockId = receiverWalletId;
        }
        else{
            firstLockId = receiverWalletId;
            secondLockId = senderWalletId;

        }

        Wallet firstWallet = walletRepository.findByIdForUpdate(firstLockId).orElseThrow(()->new WalletNotFoundException("wallet not found "+firstLockId));
        Wallet secondWallet = walletRepository.findByIdForUpdate(secondLockId).orElseThrow(()->new WalletNotFoundException("wallet not found "+secondLockId));

        Wallet sender = firstWallet.getId().equals(senderWalletId) ? firstWallet:secondWallet;
        Wallet reciever = firstWallet.getId().equals(receiverWalletId) ? firstWallet:secondWallet;

        if(amount.compareTo(BigDecimal.ZERO)<=0){
            throw new InvalidTransferException("Transfer amount must be positive");
        }

        if(sender.getBalance().compareTo(amount)<0){
            throw new InsufficientBalanceException("Insufficient balance in sender wallet.");
        }



        sender.setBalance(sender.getBalance().subtract(amount));
        reciever.setBalance(reciever.getBalance().add(amount));

        UUID transactionGroupId = UUID.randomUUID();

        walletRepository.save(sender);
        walletRepository.save(reciever);

        Transaction debitTransaction = new Transaction();
        debitTransaction.setWallet(sender);
        debitTransaction.setTransactionGroupId(transactionGroupId);
        debitTransaction.setDirection(TransactionDirection.DEBIT);
        debitTransaction.setAmount(amount);
        debitTransaction.setStatus(TransactionStatus.SUCCESS);
        debitTransaction.setOriginaTransaction(null);

        Transaction creditTransaction = new Transaction();
        creditTransaction.setWallet(reciever);
        creditTransaction.setTransactionGroupId(transactionGroupId);
        creditTransaction.setDirection(TransactionDirection.CREDIT);
        creditTransaction.setAmount(amount);
        creditTransaction.setStatus(TransactionStatus.SUCCESS);
        creditTransaction.setOriginaTransaction(null);



        transactionRepository.save(debitTransaction);
        transactionRepository.save(creditTransaction);

        IdempotencyKey record = new IdempotencyKey();
        record.setIdempotencykey(idempotencyKey);
        record.setTransactionGroupId(transactionGroupId);
        record.setSenderNewBalance(sender.getBalance());
        record.setStatus("Success");
        idempotencyKeyRepository.save(record);




        return new TransferResponseDto(
                transactionGroupId,
                sender.getBalance(),
                "Success",
                Instant.now()
        );
        





    }
}
