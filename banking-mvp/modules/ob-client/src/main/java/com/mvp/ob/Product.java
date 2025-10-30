package com.mvp.ob;

public record Product(
    String productId,
    String productType,
    String productName,
    String description,
    String interestRate,
    String minAmount,
    String maxAmount,
    Integer termMonths
) {}
