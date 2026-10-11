package com.finstack.audit.dto;
public record LoginAuditSummaryResponse(long totalLoginAttempts,long successfulLogins,long failedLogins,long uniqueUsersWhoLoggedIn,long loginsToday,long loginsThisWeek) { }
