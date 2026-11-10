package com.mediassistrover.backend.service;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
@Service @RequiredArgsConstructor @Slf4j
public class StaffNoticeStream {
 private final AlertService alerts;private final StaffMeService me;
 private record Client(AccountPrincipal actor,SseEmitter emitter) {}
 private final List<Client> clients=new CopyOnWriteArrayList<>();
 public SseEmitter subscribe(AccountPrincipal actor){me.requireActiveProfile(actor);
  if(clients.stream().filter(c->c.actor().accountId().equals(actor.accountId())).count()>=8)throw new com.mediassistrover.backend.exception.BusinessRuleException("Too many notification streams");
  var emitter=new SseEmitter(0L);var client=new Client(actor,emitter);clients.add(client);
  emitter.onCompletion(()->clients.remove(client));emitter.onTimeout(()->clients.remove(client));emitter.onError(e->clients.remove(client));
  try{send(client);}catch(Exception e){disconnect(client,e);throw new com.mediassistrover.backend.exception.BusinessRuleException("Unable to open notification stream");}
  return emitter;
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=2000) public void refresh(){for(var c:clients)try{
  send(c);
 }catch(Exception e){disconnect(c,e);}}
 private void send(Client c)throws IOException{
  if(c.actor().tokenExpiresAt()!=null&&!c.actor().tokenExpiresAt().isAfter(java.time.Instant.now()))throw new IllegalStateException("Token expired");
  me.requireActiveProfile(c.actor());var rows=alerts.deliveryInbox(c.actor());
  c.emitter().send(SseEmitter.event().name("alerts").data(rows));
 }
 private void disconnect(Client c,Exception e){
  clients.remove(c);log.warn("Staff notification stream closed for account {}: {}",c.actor().accountId(),e.getClass().getSimpleName());
  c.emitter().completeWithError(e);
 }
 @jakarta.annotation.PreDestroy public void close(){clients.forEach(c->c.emitter().complete());clients.clear();}
}
