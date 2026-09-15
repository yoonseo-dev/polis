// STOMP 엔드포인트·브로커 설정(M2-2). 프론트(React)는 아직 없으므로 SockJS 폴백 없이
// 순수 WebSocket 엔드포인트만 연다 — 필요해지면 M2-5에서 withSockJS()를 얹는다(KISS).
package com.sys.polis.polis_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버가 클라이언트에게 push하는 목적지 prefix. 클라이언트는 /topic/** 를 구독한다.
        registry.enableSimpleBroker("/topic");
    }

    // Spring 기본값은 clientOutboundChannel을 스레드 풀(코어 스레드 여러 개)로 돌린다.
    // SimulationSnapshotBroadcaster는 틱을 하나의(가상) 스레드에서 순서대로 publish하지만,
    // 그 뒤 브로커가 여러 워커 스레드로 나눠 클라이언트에 전달하면 제출 순서와 도착 순서가
    // 어긋날 수 있다(테스트에서 실제로 재현됨 — 부하가 걸리면 tick 29/30/31이 뒤섞여 도착).
    // 스냅샷은 반드시 틱 순서로 도착해야 하므로 아웃바운드 채널을 단일 스레드로 고정해
    // "발행 순서 = 전달 순서"를 보장한다. 이 서버가 클라이언트에 보내는 트래픽은 한 스트림뿐이라
    // 단일 스레드로 병목이 생기지 않는다.
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(1).maxPoolSize(1);
    }
}
