package com.stark.wallet.wallet_app.service;
import com.stark.wallet.wallet_app.entity.User;

import com.stark.wallet.wallet_app.entity.Role;
import com.stark.wallet.wallet_app.entity.Wallet;
import com.stark.wallet.wallet_app.entity.WalletType;
import com.stark.wallet.wallet_app.repository.UserRepository;
import com.stark.wallet.wallet_app.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;


    private UUID walletAId;
    private UUID walletBId;


    @BeforeEach
    void setUp() {
        User userA = new User();
        userA.setEmail("concurrencyA-" + UUID.randomUUID() + "@test.com");
        userA.setPasswordHash("dummy");
        userA.setRole(Role.USER);
        userA = userRepository.save(userA);

        User userB = new User();
        userB.setEmail("ConcurrencyB-" + UUID.randomUUID() + "@test.com");
        userB.setPasswordHash("dummy");
        userB.setRole(Role.USER);
        userB = userRepository.save(userB);

        Wallet walletA = new Wallet();
        walletA.setUser(userA);
        walletA.setType(WalletType.PRIMARY);
        walletA.setBalance(new BigDecimal("1000.00"));
        walletA = walletRepository.save(walletA);
        walletAId = walletA.getId();

        Wallet walletB = new Wallet();
        walletB.setUser(userB);
        walletB.setType(WalletType.PRIMARY);
        walletB.setBalance(new BigDecimal("0.00"));
        walletB = walletRepository.save(walletB);
        walletBId = walletB.getId();
    }

    @Test
    void concurrentTransfers_shouldNotLoseUpdatesOrOverdraw() throws InterruptedException{
            int numberOfTransfers=20;
            BigDecimal amountEach =new BigDecimal("10.00");

            ExecutorService executor= Executors.newFixedThreadPool(10);
            CountDownLatch latch=new CountDownLatch(numberOfTransfers);

            for(int i=0;i<numberOfTransfers;i++){
                executor.submit(()-> {
                    try{
                        walletService.transfer(walletAId,walletBId,amountEach,UUID.randomUUID().toString());

                    }
                    catch(Exception e){
                        System.out.println("Transfer attempt failed:"+e.getMessage());

                    }finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed=latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(true,completed,"All transfers should complete within timeout");


            Wallet finalA = walletRepository.findById(walletAId).orElseThrow();
            Wallet finalB = walletRepository.findById(walletBId).orElseThrow();

            BigDecimal expectedMoved = amountEach.multiply(BigDecimal.valueOf(numberOfTransfers));

            assertEquals(new BigDecimal("1000.00").subtract(expectedMoved), finalA.getBalance());
            assertEquals(expectedMoved, finalB.getBalance());








    }

}
