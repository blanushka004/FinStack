package com.finstack.audit;
import com.finstack.audit.dto.*;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.web.PageableDefault;
import java.time.*;
@RestController @RequestMapping("/api/v1/admin/login-audit") @PreAuthorize("hasRole('ADMIN')") public class AdminLoginAuditController {
 private final LoginAuditRepository repository; public AdminLoginAuditController(LoginAuditRepository repository){this.repository=repository;}
 @GetMapping public Page<LoginAuditResponse> history(@RequestParam(required=false) Boolean successful,@RequestParam(required=false) String email,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@PageableDefault(size=20,sort="occurredAt",direction=Sort.Direction.DESC) Pageable pageable){org.springframework.data.jpa.domain.Specification<LoginAuditEvent> spec=(root,q,b)->b.conjunction();if(successful!=null)spec=spec.and((r,q,b)->b.equal(r.get("successful"),successful));if(email!=null&&!email.isBlank()){String p="%"+email.trim().toLowerCase()+"%";spec=spec.and((r,q,b)->b.like(b.lower(r.get("emailAttempted")),p));}if(from!=null)spec=spec.and((r,q,b)->b.greaterThanOrEqualTo(r.get("occurredAt"),from));if(to!=null)spec=spec.and((r,q,b)->b.lessThanOrEqualTo(r.get("occurredAt"),to));return repository.findAll(spec,pageable).map(LoginAuditResponse::from);}
 @GetMapping("/summary") public LoginAuditSummaryResponse summary(){OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);long success=repository.countBySuccessful(true);long failure=repository.countBySuccessful(false);return new LoginAuditSummaryResponse(success+failure,success,failure,repository.countDistinctSuccessfulUsers(),repository.countByOccurredAtAfter(now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)),repository.countByOccurredAtAfter(now.minusDays(7)));}
}
