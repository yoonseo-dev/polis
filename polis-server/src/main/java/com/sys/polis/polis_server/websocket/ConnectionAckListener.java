// M2-2 검증 대상: "프론트 연결 후 더미 메시지 1회 push 도착". 실제 시뮬레이션 스냅샷(M2-3)과는
// 별개로, 순수히 "WebSocket/STOMP 배관이 연결돼 있다"만 확인하는 최소 신호다.
//
// SessionConnectedEvent(STOMP CONNECT 시점)가 아니라 /topic/handshake 구독 시점에 보낸다.
// Simple Broker는 구독 이전에 보낸 메시지를 버퍼링하지 않으므로, CONNECT 직후 곧바로 쏘면
// 클라이언트가 아직 구독을 마치기 전에 메시지가 날아가 버려 유실된다(SimulationSnapshotBroadcaster와
// 동일한 이유로 구독 트리거를 쓴다).
package com.sys.polis.polis_server.websocket;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;

@Component
public class ConnectionAckListener {

    private static final String DESTINATION = "/topic/handshake";

    private final SimpMessagingTemplate messagingTemplate;

    public ConnectionAckListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @org.springframework.context.event.EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        String destination = SimpMessageHeaderAccessor.getDestination(event.getMessage().getHeaders());
        if (DESTINATION.equals(destination)) {
            messagingTemplate.convertAndSend(DESTINATION, Map.of("status", "connected"));
        }
    }
}
