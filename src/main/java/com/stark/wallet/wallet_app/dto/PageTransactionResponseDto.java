package com.stark.wallet.wallet_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageTransactionResponseDto {
    private List<TransactionHistoryDto> transactions;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private boolean hasNext;

}


