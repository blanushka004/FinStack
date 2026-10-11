package com.finstack.audit;
import com.finstack.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Service public class LoginAuditService { private final LoginAuditRepository repository; public LoginAuditService(LoginAuditRepository repository){this.repository=repository;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void record(User user,String email,boolean success,String ip,String agent,String reason){try{repository.save(new LoginAuditEvent(user,email,success,trim(ip,64),trim(agent,500),reason));}catch(Exception ignored){}} private String trim(String value,int max){return value==null?null:value.length()<=max?value:value.substring(0,max);} }
