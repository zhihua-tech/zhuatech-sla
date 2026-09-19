/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.sla;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static cn.zhuatech.sla.Model.*;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Component public class SlaMonitor {
 final Engine e;final Domain domain;
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public SlaMonitor(Engine e,Domain domain){this.e=e;this.domain=domain;}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Scheduled(fixedDelay=60000,initialDelay=60000)
 @Transactional public void scan(){
  for(String tenant:e.jdbc().queryForList("SELECT tenant FROM tenant_guard",String.class)){
   User system=new User("system-sla-monitor",tenant,"sla-monitor","ADMIN");e.lock(system);
   for(Row ticket:e.all(system,"tickets")){
    if(Set.of("CLOSED","RESOLVED").contains(ticket.state()))continue;
    Map<String,Object> next=new LinkedHashMap<>(ticket.data());Instant now=Instant.now();
    if(!next.containsKey("respondedAt")&&now.isAfter(Domain.instant(next,"responseDue")))domain.breach(e,system,ticket,next,"RESPONSE");
    if(now.isAfter(Domain.instant(next,"resolutionDue")))domain.breach(e,system,ticket,next,"RESOLUTION");
    if(!next.equals(ticket.data()))e.save(system,ticket,ticket.state(),next,"SLA_SCAN","自动检测服务时限");
   }
  }
 }
}
