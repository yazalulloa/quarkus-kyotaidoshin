package com.yaz.kyotaidoshin.core.bean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ServerSideEventHelper {

  private final ConcurrentHashMap<String, List<WeakReference<SseEventSink>>> sinks = new ConcurrentHashMap<>();

  private final Sse sse;

  public void addSink(String key) {
    log.debug("ADDED_SINK {}", key);
    sinks.putIfAbsent(key, new ArrayList<>());
  }

  public synchronized void checkSink(String key, SseEventSink sseEventSink) {
    final var sseEventSinks = sinks.get(key);

    if (sseEventSinks == null) {
      sseEventSink.close();
      return;
    }

    sseEventSinks.add(new WeakReference<>(sseEventSink));
    log.debug("ADDED_SINK {}", key);
    printSinks();
  }

  public void printSinks() {
    sinks.forEach((key, value) -> {
      log.debug("KEY: {} {}", key, value.size());
    });
  }

  public synchronized void removeClosed() {

    final var enumeration = sinks.keys();
    while (enumeration.hasMoreElements()) {
      final var key = enumeration.nextElement();
      final var eventSinks = sinks.get(key);
      if (eventSinks != null && !eventSinks.isEmpty()) {
        eventSinks.removeIf(weakReference -> {

          final var sseEventSink = weakReference.get();
          return sseEventSink == null || sseEventSink.isClosed();
        });
      }
    }
  }

  public void close(String key) {
    final var eventSinks = sinks.get(key);
    if (eventSinks != null && !eventSinks.isEmpty()) {
      eventSinks.forEach(weakReference -> {
        final var sseEventSink = weakReference.get();
        if (sseEventSink != null && !sseEventSink.isClosed()) {
          sseEventSink.close();
        }
      });
    }

    sinks.remove(key);
  }

  public boolean sendSseEvent(String key, OutboundSseEvent event) {
    final var eventSinks = sinks.get(key);
    if (eventSinks != null && !eventSinks.isEmpty()) {
      eventSinks.forEach(weakReference -> {
        final var sseEventSink = weakReference.get();
        if (sseEventSink != null && !sseEventSink.isClosed()) {
          //log.debug("SENDING_EVENT {}", key);
          sseEventSink.send(event);
        }
      });
      return true;
    }

    log.debug("NO_SINKS {}", key);
    return false;
  }

  public void sendNotification(String value) {

   /*
   OutboundSseEvent sseEvent = sse.newEventBuilder()
        .id(message.getKey())
        .mediaType(MediaType.TEXT_PLAIN_TYPE)
        .name("message")
        .data(data)
        .reconnectDelay(3000)
        .comment(message.getPayload())
        .build();
    */

    // log.debug("SENDING {}", value);
    final var outboundSseEvent = sse.newEvent(value, value);

    sendSseEvent(value, outboundSseEvent);

  }

//  @ConsumeEvent(EventConstants.NEW_RATE)
//  public void consumeNewRate(Object obj) {
//    sendNotification(EventConstants.NEW_RATE);
//  }

  public void sendEvent(String sinkKey, String eventName, String data) {
    final var outboundSseEvent = sse.newEvent(eventName, data);
    sendSseEvent(sinkKey, outboundSseEvent);
  }
}
