package com.finstack.audit;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.UUID;
public interface LoginAuditRepository extends JpaRepository<LoginAuditEvent, UUID>, JpaSpecificationExecutor<LoginAuditEvent> {
 @Query("select count(distinct e.user.id) from LoginAuditEvent e where e.successful = true") long countDistinctSuccessfulUsers();
 long countBySuccessful(boolean successful);
 long countByOccurredAtAfter(OffsetDateTime occurredAt);
}
