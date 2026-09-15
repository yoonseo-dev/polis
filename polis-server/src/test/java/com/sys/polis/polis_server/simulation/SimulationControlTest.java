// M2-4 검증: start로 실행이 시작되고 스냅샷이 흐르는지, stop으로 멈추면 더 이상 새 틱이
// 계산되지 않는지, 그리고 파라미터 변경이 재시작 없이(=opinion 상태를 유지한 채) 돌고 있는
// 규칙에 즉시 반영되는지. tickCount를 충분히 크게 잡아 자연 종료 전에 stop이 개입하도록 한다.
//
// @DirtiesContext: SimulationRunner는 싱글턴 빈이라 상태(agentCount/mu/threshold/실행 여부)가
// 테스트 메서드 사이에 그대로 남는다. 처음엔 없이 작성했다가 실제로 겪은 문제: 이전 테스트가
// 남겨둔 실행 중인 시뮬레이션이 같은 /topic/snapshots로 계속 방송되고 있어서, 다음 테스트가
// 구독하자마자 "자기가 막 시작한" 스냅샷이 아니라 "이전 테스트가 흘리던" 스냅샷을 집어 agentCount
// 단언이 엉뚱하게 깨졌다. 메서드마다 컨텍스트를 새로 띄워 완전히 격리한다.
package com.sys.polis.polis_server.simulation;

import com.sys.polis.polis_engine.metric.MetricCollector;
import com.sys.polis.polis_server.websocket.StompTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SimulationControlTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private StompFrameHandler forward(BlockingQueue<MetricCollector.Snapshot> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MetricCollector.Snapshot.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((MetricCollector.Snapshot) payload);
            }
        };
    }

    @Test
    void start로_시작하고_stop으로_멈추면_더_이상_새_틱이_계산되지_않는다() throws Exception {
        BlockingQueue<MetricCollector.Snapshot> received = new LinkedBlockingQueue<>();
        StompSession session = StompTestSupport.connect(port);
        StompTestSupport.subscribeAndSettle(session, "/topic/snapshots", forward(received));

        SimulationRunner.StatusView started = rest.postForObject(url("/api/simulation/start"),
                Map.of("agentCount", 50, "mu", 0.05, "threshold", 1.5, "tickCount", 1_000_000),
                SimulationRunner.StatusView.class);
        assertTrue(started.running());
        assertNotNull(received.poll(5, TimeUnit.SECONDS), "시작 직후 스냅샷(tick 0)이 도착해야 한다");

        SimulationRunner.StatusView stopped = rest.postForObject(url("/api/simulation/stop"), null,
                SimulationRunner.StatusView.class);
        assertFalse(stopped.running());

        // stop()이 계산 루프를 멈춰도, outbound 채널(단일 스레드)이 그 전까지 이미 쌓인 스냅샷을
        // 뒤늦게 마저 배출할 수 있다 — 계산이 WebSocket 전송보다 훨씬 빠르기 때문에 stop 시점엔
        // 이미 여러 틱 분량이 큐에 있을 수 있다. 그래서 "멈췄다"의 판정 기준은 "즉시 조용해짐"이
        // 아니라 "한동안 조용하고 나면 더 이상 새 틱이 없음"이다: 백로그가 다 빠질 때까지 기다린
        // 뒤(2초 무응답), 그 뒤로 또 2초를 더 기다려도 새 스냅샷이 없어야 진짜로 멈춘 것이다.
        int lastTickSeen = -1;
        MetricCollector.Snapshot snapshot;
        while ((snapshot = received.poll(2, TimeUnit.SECONDS)) != null) {
            lastTickSeen = snapshot.tick();
        }
        assertTrue(lastTickSeen >= 0, "stop 전에 최소 한 틱은 흘렀어야 한다");

        assertNull(received.poll(2, TimeUnit.SECONDS),
                "백로그가 다 빠진 뒤에도(tick " + lastTickSeen + "까지 봄) 새 스냅샷이 오면 안 된다");

        session.disconnect();
    }

    @Test
    void params_변경이_재시작_없이_실행_중인_규칙에_즉시_반영된다() {
        rest.postForObject(url("/api/simulation/start"),
                Map.of("agentCount", 50, "mu", 0.05, "threshold", 1.5, "tickCount", 1_000_000),
                SimulationRunner.StatusView.class);

        rest.postForObject(url("/api/simulation/params"), Map.of("threshold", 0.5),
                SimulationRunner.StatusView.class);

        SimulationRunner.StatusView status = rest.getForObject(url("/api/simulation/status"),
                SimulationRunner.StatusView.class);
        assertTrue(status.running(), "params 변경이 실행 중인 시뮬레이션을 재시작시키면 안 된다");
        assertEquals(0.5, status.threshold(), 1e-9);
        assertEquals(50, status.agentCount(), "agentCount는 params 호출로 바뀌지 않아야 한다");

        rest.postForObject(url("/api/simulation/stop"), null, SimulationRunner.StatusView.class);
    }

    @Test
    void agentCount를_바꿔서_start하면_재시작되고_새_인원수로_스냅샷이_온다() throws Exception {
        BlockingQueue<MetricCollector.Snapshot> received = new LinkedBlockingQueue<>();
        StompSession session = StompTestSupport.connect(port);
        StompTestSupport.subscribeAndSettle(session, "/topic/snapshots", forward(received));

        rest.postForObject(url("/api/simulation/start"),
                Map.of("agentCount", 30, "mu", 0.05, "threshold", 1.5, "tickCount", 1_000_000),
                SimulationRunner.StatusView.class);
        MetricCollector.Snapshot first = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals(30, Arrays.stream(first.histogram()).sum());

        received.clear();
        SimulationRunner.StatusView restarted = rest.postForObject(url("/api/simulation/start"),
                Map.of("agentCount", 80), SimulationRunner.StatusView.class);
        assertEquals(80, restarted.agentCount());

        // start()는 이전 실행(agentCount=30)의 계산을 확실히 멈추지만, 재시작 전에 이미 outbound
        // 큐에 들어간 이전 실행의 스냅샷까지 취소하진 못한다(start_stop 테스트에서 본 것과 같은
        // 배압 지연). 그래서 "재시작 후 첫 스냅샷"이 아니라 "새 인원수(80)로 된 첫 스냅샷"을 찾는다.
        MetricCollector.Snapshot afterRestart;
        do {
            afterRestart = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(afterRestart, "재시작 후 새 인원수(80) 스냅샷이 도착해야 한다");
        } while (Arrays.stream(afterRestart.histogram()).sum() != 80);

        assertEquals(0, afterRestart.tick(), "새 인원수로 재시작한 뒤 첫 스냅샷은 tick 0이어야 한다");

        rest.postForObject(url("/api/simulation/stop"), null, SimulationRunner.StatusView.class);
        session.disconnect();
    }
}
