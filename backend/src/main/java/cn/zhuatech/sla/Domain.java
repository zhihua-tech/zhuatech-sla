/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.sla;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
import static cn.zhuatech.sla.Model.*;
import static cn.zhuatech.sla.Engine.*;
@Component public class Domain {
 static String text(Row r,String k){return txt(r.data(),k);}
 static Instant instant(Map<String,Object>d,String key){try{return Instant.parse(txt(d,key));}catch(Exception ex){throw new Failure(400,"时间须为 UTC ISO-8601 格式: "+key);}}
 static List<Row> linked(Engine e,User u,String module,String field,String id){return e.all(u,module).stream().filter(x->text(x,field).equals(id)).toList();}
 void validateAgreement(Map<String,Object>d){require(num(d,"responseHours").compareTo(num(d,"resolutionHours"))<=0,"响应时限不能大于解决时限");}
 public void create(Engine e,User u,String module,Map<String,Object>d){
  if(module.equals("agreements")){validateAgreement(d);require(e.all(u,module).stream().noneMatch(x->text(x,"name").equalsIgnoreCase(txt(d,"name"))),"协议名称重复");}
  if(module.equals("tickets")){
   Row agreement=e.ref(u,d,"agreement","agreements");Instant opened=instant(d,"openedAt");
   require(!opened.isAfter(Instant.now())&&!opened.isBefore(Instant.now().minus(Duration.ofDays(366))),"受理时间必须在最近一年内");
   d.put("responseDue",opened.plus(Duration.ofHours(num(agreement.data(),"responseHours").longValueExact())).toString());
   d.put("resolutionDue",opened.plus(Duration.ofHours(num(agreement.data(),"resolutionHours").longValueExact())).toString());
   d.put("responseBreached",false);d.put("resolutionBreached",false);
  }
 }
 public void edit(Engine e,User u,Row r,Map<String,Object>d){
  if(r.module().equals("agreements")){require(linked(e,u,"tickets","agreement",r.id()).isEmpty(),"协议已有工单，不能改写服务承诺");validateAgreement(d);}
  if(r.module().equals("tickets")){require(txt(d,"agreement").equals(text(r,"agreement"))&&txt(d,"openedAt").equals(text(r,"openedAt")),"不能修改已受理工单的协议或计时起点");r.data().forEach((k,v)->{if(!d.containsKey(k))d.put(k,v);});}
 }
 void breach(Engine e,User u,Row r,Map<String,Object>d,String kind){
  String flag=kind.equals("RESPONSE")?"responseBreached":"resolutionBreached";if(Boolean.TRUE.equals(d.get(flag)))return;
  d.put(flag,true);e.ledger(u,"breaches","OPEN",Map.of("ticket",r.id(),"kind",kind,"occurredAt",Instant.now().toString()));
  e.ledger(u,"escalations","OPEN",Map.of("ticket",r.id(),"reason",kind+" 时限超时","occurredAt",Instant.now().toString()));
 }
 public String action(Engine e,User u,Row r,String action,Map<String,Object>i,Map<String,Object>d){
  Instant now=Instant.now();
  switch(r.module()+"."+action){
   case "tickets.acknowledge" -> {d.put("respondedAt",now.toString());if(now.isAfter(instant(d,"responseDue")))breach(e,u,r,d,"RESPONSE");}
   case "tickets.escalate" -> {require(d.containsKey("respondedAt"),"请先确认响应再升级");e.ledger(u,"escalations","OPEN",Map.of("ticket",r.id(),"reason",txt(i,"reason"),"occurredAt",now.toString()));d.put("escalationReason",txt(i,"reason"));}
   case "tickets.resolve" -> {require(d.containsKey("respondedAt"),"尚未响应");d.put("resolvedAt",now.toString());if(now.isAfter(instant(d,"resolutionDue")))breach(e,u,r,d,"RESOLUTION");}
   case "tickets.reopen" -> {Row agreement=e.ref(u,d,"agreement","agreements");d.put("openedAt",now.toString());d.put("responseDue",now.plus(Duration.ofHours(num(agreement.data(),"responseHours").longValueExact())).toString());d.put("resolutionDue",now.plus(Duration.ofHours(num(agreement.data(),"resolutionHours").longValueExact())).toString());d.remove("respondedAt");d.remove("resolvedAt");d.put("responseBreached",false);d.put("resolutionBreached",false);d.put("reopenCount",((Number)d.getOrDefault("reopenCount",0)).intValue()+1);}
   case "tickets.close" -> {require(d.containsKey("resolvedAt"),"未解决不能关闭");d.put("closedAt",now.toString());}
  }
  return null;
 }
 public Map<String,Object> metrics(Engine e,User u){return Map.of("待响应工单",e.all(u,"tickets").stream().filter(r->r.state().equals("OPEN")).count(),"超时事件",e.all(u,"breaches").size(),"已关闭工单",e.all(u,"tickets").stream().filter(r->r.state().equals("CLOSED")).count());}
}
