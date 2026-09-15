// M2-2 검증: "프론트 연결 후 더미 메시지 1회 push 도착"을 자동화한 것.
// 아직 React 프론트(M2-5)가 없으니 Spring의 STOMP 테스트 클라이언트로 그 역할을 대신한다.
package com.sys.polis.polis_server.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandshakeWebSocketTest {

    @LocalServerPort
    private int port;

    @Test
    void 연결_후_핸드셰이크_더미_메시지가_한_번_도착한다() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        StompSession session = StompTestSupport.connect(port);
        session.subscribe("/topic/handshake", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);

        assertNotNull(message, "구독 후 5초 내에 핸드셰이크 더미 메시지가 도착해야 한다");
        assertEquals("connected", message.get("status"));

        session.disconnect();
    }
}
