package com.finstack.alert.dto;
import com.finstack.alert.*; import java.time.*; import java.util.*;
public record AlertResponse(UUID id,AlertType alertType,AlertSeverity severity,String title,String message,boolean read,OffsetDateTime createdAt){public static AlertResponse from(Alert a){return new AlertResponse(a.getId(),a.getAlertType(),a.getSeverity(),a.getTitle(),a.getMessage(),a.isRead(),a.getCreatedAt());}}
