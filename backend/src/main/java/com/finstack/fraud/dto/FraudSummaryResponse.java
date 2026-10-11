package com.finstack.fraud.dto;

public record FraudSummaryResponse(long lowRisk, long mediumRisk, long highRisk, long suspicious) {}
