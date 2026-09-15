// M2-3 체크리스트가 명시한 검증 문구 그대로: "N=10000에서 프레임 드랍 없이 흐르는가."
// 아직 Canvas 프론트(M2-5)가 없어 실제 렌더링 프레임 드랍은 볼 수 없다 — 여기서는
// WebSocket 계층에서 스냅샷이 하나도 유실되지 않는지를 그 근사치로 확인한다.
//
// M2-4부터는 구독이 자동 트리거가 아니므로 구독 후 POST /api/simulation/start로 명시적으로 시작한다.
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "polis.simulation.agent-count=10000",
                "polis.simulation.tick-count=50"
        }
)
class LargeScaleSnapshotBroadcastTest {

    private static final int AGENT_COUNT = 10_000;
    private static final int TICK_COUNT = 50;

    @LocalServerPort
    private int port;

    @Test
    void N_10000에서도_스냅샷이_한_틱도_유실되지_않고_전부_도착한다() throws Exception {
        BlockingQueue<MetricCollector.Snapshot> received = new LinkedBlockingQueue<>();

        StompSession session = StompTestSupport.connect(port);
        StompTestSupport.subscribeAndSettle(session, "/topic/snapshots", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MetricCollector.Snapshot.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((MetricCollector.Snapshot) payload);
            }
        });

        // null을 그대로 넘기면 RestTemplate이 빈 폼(application/x-www-form-urlencoded)으로 POST해서
        // 서버가 415로 거부한다(컨트롤러는 JSON @RequestBody를 기대). 빈 JSON 객체로 보낸다.
        new TestRestTemplate().postForObject(
                "http://localhost:" + port + "/api/simulation/start", Map.of(), SimulationRunner.StatusView.class);

        List<MetricCollector.Snapshot> snapshots = new ArrayList<>();
        for (int tick = 0; tick <= TICK_COUNT; tick++) {
            // N=10,000이라 틱당 연산이 늘어나므로 소규모 테스트보다 넉넉한 타임아웃을 준다.
            MetricCollector.Snapshot snapshot = received.poll(30, TimeUnit.SECONDS);
            assertNotNull(snapshot, "tick " + tick + " 스냅샷 유실 — N=10000 프레임 드랍 발생");
            snapshots.add(snapshot);
        }

        for (int tick = 0; tick <= TICK_COUNT; tick++) {
            assertEquals(tick, snapshots.get(tick).tick());
        }
        for (MetricCollector.Snapshot snapshot : snapshots) {
            assertEquals(AGENT_COUNT, Arrays.stream(snapshot.histogram()).sum());
        }

        session.disconnect();
    }
}
