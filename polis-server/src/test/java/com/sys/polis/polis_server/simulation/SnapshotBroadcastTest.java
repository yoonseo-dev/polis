// M2-3 검증: 매 틱 스냅샷이 순서대로·빠짐없이 도착하는지, 그리고 raw opinion이 아니라
// 집계(히스토그램 합 == agentCount)만 오는지 확인한다. 덧붙여 M0에서 이미 확인된
// "threshold=1.5 → 분산 수렴" 거시 패턴이 서버 경로에서도 재현되는지까지 본다(트렌드 일관성).
//
// M2-4부터는 구독이 더 이상 자동 트리거가 아니라서(SimulationRunner 참조), 구독 후
// POST /api/simulation/start를 명시적으로 호출해야 스냅샷이 흐르기 시작한다.
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SnapshotBroadcastTest {

    // application.yml 기본값(agent-count=100, tick-count=200)과 맞춘다 — 틀리면 개수 단언이 깨진다.
    private static final int AGENT_COUNT = 100;
    private static final int TICK_COUNT = 200;

    @LocalServerPort
    private int port;

    @Test
    void 매_틱_집계_스냅샷이_순서대로_빠짐없이_도착하고_수렴_트렌드를_재현한다() throws Exception {
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
            MetricCollector.Snapshot snapshot = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(snapshot, "tick " + tick + " 스냅샷이 5초 내 도착하지 않음 — 유실 의심");
            snapshots.add(snapshot);
        }

        // 순서 보존(드랍·재정렬 없음) 확인.
        for (int tick = 0; tick <= TICK_COUNT; tick++) {
            assertEquals(tick, snapshots.get(tick).tick());
        }

        // raw opinion이 아니라 집계만 왔는지: 히스토그램 총합이 매 틱 agentCount와 같아야 한다.
        for (MetricCollector.Snapshot snapshot : snapshots) {
            assertEquals(AGENT_COUNT, Arrays.stream(snapshot.histogram()).sum());
        }

        // M0에서 확인된 threshold=1.5 수렴 패턴(baseline/m0.json과 같은 방향)이 서버 경로에서도 나오는지.
        double initialVariance = snapshots.get(0).variance();
        double finalVariance = snapshots.get(snapshots.size() - 1).variance();
        assertTrue(finalVariance < initialVariance,
                "threshold=1.5에서는 분산이 줄어드는 수렴 패턴이 나와야 한다 (초기=" + initialVariance
                        + ", 최종=" + finalVariance + ")");

        session.disconnect();
    }
}
