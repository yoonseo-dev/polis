// 두 통합 테스트(핸드셰이크 M2-2, 스냅샷 M2-3)가 STOMP 연결하는 방식이 같아서 헬퍼로 뽑았다.
package com.sys.polis.polis_server.websocket;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.TimeUnit;

public final class StompTestSupport {

    private StompTestSupport() {
    }

    // 프론트가 없으니 SockJS 폴백 없이(WebSocketConfig와 대칭) 순수 WebSocket으로 붙는다.
    public static StompSession connect(int port) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // 서버가 보내는 스냅샷/핸드셰이크 페이로드가 JSON이라, 받는 쪽도 Jackson으로 디코딩해야 한다.
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);
    }

    // M2-3까지는 "구독 이벤트 자체가 서버 쪽 발행 트리거"라 구독이 끝나야만 발행이 시작될 수 있었다.
    // M2-4부터 발행 트리거가 REST 호출(SimulationRunner.start)로 분리되면서, 테스트가 구독
    // 프레임을 보낸 직후 곧바로 REST를 호출하면 브로커가 구독을 등록하기 전에 첫 스냅샷이 지나가
    // 버리는 경합이 생긴다. STOMP RECEIPT로 정확히 대기하는 방법도 시도했으나, 이 클라이언트/브로커
    // 조합에서는 RECEIPT 프레임이 돌아오지 않아(5초 타임아웃으로 관측) 실용적인 대안으로 짧은 유예를
    // 둔다 — 로컬 인메모리 브로커의 구독 등록은 밀리초 이내로 끝나므로 이 정도로 충분하다.
    public static void subscribeAndSettle(StompSession session, String destination, StompFrameHandler handler)
            throws InterruptedException {
        session.subscribe(destination, handler);
        Thread.sleep(300);
    }
}
