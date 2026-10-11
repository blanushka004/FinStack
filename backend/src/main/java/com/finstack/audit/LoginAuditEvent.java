package com.finstack.audit;
import com.finstack.user.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name = "login_audit_events") public class LoginAuditEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
 @Column(name="email_attempted",nullable=false,length=255) private String emailAttempted;
 @Enumerated(EnumType.STRING) @Column(name="event_type",nullable=false,length=30) private LoginEventType eventType;
 @Column(nullable=false) private boolean successful;
 @Column(name="occurred_at",nullable=false) private OffsetDateTime occurredAt;
 @Column(name="ip_address",length=64) private String ipAddress;
 @Column(name="user_agent",length=500) private String userAgent;
 @Column(name="failure_reason",length=40) private String failureReason;
 protected LoginAuditEvent(){}
 LoginAuditEvent(User user,String email,boolean success,String ip,String agent,String reason){this.user=user;emailAttempted=email;successful=success;eventType=success?LoginEventType.LOGIN_SUCCESS:LoginEventType.LOGIN_FAILURE;ipAddress=ip;userAgent=agent;failureReason=reason;occurredAt=OffsetDateTime.now(java.time.ZoneOffset.UTC);}
 public UUID getId(){return id;} public User getUser(){return user;} public String getEmailAttempted(){return emailAttempted;} public LoginEventType getEventType(){return eventType;} public boolean isSuccessful(){return successful;} public OffsetDateTime getOccurredAt(){return occurredAt;} public String getIpAddress(){return ipAddress;} public String getUserAgent(){return userAgent;} public String getFailureReason(){return failureReason;}
}
