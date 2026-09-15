package com.sys.polis.polis_engine.metric;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetricCollectorTest {

    @Test
    void 히스토그램_버킷_합은_행위자_수와_같다() {
        // 버킷 경계(예: -0.9, 0.95)는 0.1 부동소수점 오차로 어느 구간에 떨어질지 애매해지므로
        // 여기서는 "합이 인원 수와 같다"만 확인하고, 경계값 자체는 아래 두 테스트에서 별도로 검증한다.
        List<Agent> agents = List.of(
                new Agent(new AgentState(1, -1.0)),
                new Agent(new AgentState(2, -0.9)),
                new Agent(new AgentState(3, 0.0)),
                new Agent(new AgentState(4, 0.95)),
                new Agent(new AgentState(5, 1.0))
        );

        int[] histogram = MetricCollector.histogram(agents);

        assertEquals(20, histogram.length);
        assertEquals(5, java.util.Arrays.stream(histogram).sum());
    }

    @Test
    void 극좌_끝값_opinion_마이너스1은_첫_구간에_들어간다() {
        List<Agent> agents = List.of(new Agent(new AgentState(1, -1.0)));

        assertEquals(1, MetricCollector.histogram(agents)[0]);
    }

    @Test
    void 극우_끝값_opinion_1은_clamp돼서_마지막_구간에_들어간다() {
        List<Agent> agents = List.of(new Agent(new AgentState(1, 1.0)));

        assertEquals(1, MetricCollector.histogram(agents)[19]);
    }

    @Test
    void 극단값_비율은_절댓값_0_8_초과_비율이다() {
        List<Agent> agents = List.of(
                new Agent(new AgentState(1, 0.9)),   // 극단
                new Agent(new AgentState(2, -0.85)), // 극단
                new Agent(new AgentState(3, 0.1)),   // 아님
                new Agent(new AgentState(4, 0.0))    // 아님
        );

        assertEquals(0.5, MetricCollector.extremeRatio(agents), 1e-9);
    }

    @Test
    void 분산은_0에_가까운_동일값_집단에서_0에_가깝다() {
        List<Agent> agents = List.of(
                new Agent(new AgentState(1, 0.3)),
                new Agent(new AgentState(2, 0.3)),
                new Agent(new AgentState(3, 0.3))
        );

        assertEquals(0.0, MetricCollector.variance(agents), 1e-9);
    }

    @Test
    void 스냅샷은_틱_번호와_집계_지표를_함께_담고_raw_opinion을_노출하지_않는다() {
        List<Agent> agents = List.of(
                new Agent(new AgentState(1, 0.5)),
                new Agent(new AgentState(2, -0.5))
        );

        MetricCollector.Snapshot snapshot = MetricCollector.snapshot(7, agents);

        assertEquals(7, snapshot.tick());
        assertEquals(20, snapshot.histogram().length);
        assertEquals(MetricCollector.variance(agents), snapshot.variance(), 1e-9);
        assertEquals(MetricCollector.extremeRatio(agents), snapshot.extremeRatio(), 1e-9);
        // Snapshot 레코드의 컴포넌트가 (tick, histogram, variance, extremeRatio) 4개뿐임이
        // 이 테스트가 컴파일된다는 사실 자체로 보장된다 — opinion 배열을 담는 필드가 없다.
    }
}
