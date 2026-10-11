package com.finstack.audit.dto;
import com.finstack.audit.LoginAuditEvent;
import java.time.OffsetDateTime;
import java.util.UUID;
public record LoginAuditResponse(UUID id,UUID userId,String email,boolean successful,OffsetDateTime occurredAt,String ipAddress,String userAgent){public static LoginAuditResponse from(LoginAuditEvent e){return new LoginAuditResponse(e.getId(),e.getUser()==null?null:e.getUser().getId(),e.getEmailAttempted(),e.isSuccessful(),e.getOccurredAt(),e.getIpAddress(),e.getUserAgent());}}
