package com.finstack.alert;
import com.finstack.budget.Budget;
import com.finstack.category.TransactionCategory;
import com.finstack.transaction.Transaction;
import com.finstack.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import java.time.*; import java.util.UUID;
@Entity @Table(name="alerts") public class Alert {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Enumerated(EnumType.STRING) @Column(name="alert_type",nullable=false,length=40) private AlertType alertType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AlertSeverity severity;
 @Column(nullable=false,length=180) private String title;
 @Column(nullable=false,length=500) private String message;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="related_budget_id") private Budget relatedBudget;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="related_category_id") private TransactionCategory relatedCategory;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="related_transaction_id") private Transaction relatedTransaction;
 @Column(name="read",nullable=false) private boolean read=false;
 @Column(name="created_at",nullable=false,updatable=false) private OffsetDateTime createdAt;
 protected Alert(){} public Alert(User u,AlertType t,AlertSeverity s,String title,String msg,Budget b){user=u;alertType=t;severity=s;this.title=title;message=msg;relatedBudget=b;}
 public Alert(User u,AlertType t,AlertSeverity s,String title,String msg,Budget b,TransactionCategory c){this(u,t,s,title,msg,b);relatedCategory=c;}
 public static Alert forTransaction(User u,AlertType t,AlertSeverity s,String title,String msg,Transaction transaction){Alert alert=new Alert();alert.user=u;alert.alertType=t;alert.severity=s;alert.title=title;alert.message=msg;alert.relatedTransaction=transaction;return alert;}
 @PrePersist void created(){if(createdAt==null)createdAt=OffsetDateTime.now(ZoneOffset.UTC);} public void markRead(){read=true;}
 public UUID getId(){return id;} public AlertType getAlertType(){return alertType;} public AlertSeverity getSeverity(){return severity;} public String getTitle(){return title;} public String getMessage(){return message;} public boolean isRead(){return read;} public OffsetDateTime getCreatedAt(){return createdAt;}
 @Override public boolean equals(Object o){if(this==o)return true;if(o==null||Hibernate.getClass(this)!=Hibernate.getClass(o))return false;Alert a=(Alert)o;return id!=null&&id.equals(a.id);}@Override public int hashCode(){return Hibernate.getClass(this).hashCode();}
}
