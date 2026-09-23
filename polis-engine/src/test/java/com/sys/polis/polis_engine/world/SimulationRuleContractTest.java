package com.sys.polis.polis_engine.world;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.rule.UpdateRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

// M3-1: 엔진이 UpdateRule 인터페이스 하나만 알고 그 계약대로 호출하는지 확인한다.
// AttractionRepulsionRule이 아닌 임의 구현체(람다)로 돌려 보므로, 엔진이 구체 규칙에 묶여 있으면 실패한다.
class SimulationRuleContractTest {

    private static final int AGENT_COUNT = 50;

    private static Simulation newSimulation(UpdateRule rule) {
        List<Agent> agents = AgentFactory.createAgents(AGENT_COUNT, new Random(1));
        return new Simulation(agents, new NeighborSelector(AGENT_COUNT, new Random(2)), rule);
    }

    @Test
    void 임의_구현체의_반환값이_그대로_상태에_반영됨() throws InterruptedException {
        // 이웃이 오면 항상 0.5로 만드는 규칙 — 동화/반발과 무관한 전혀 다른 규칙.
        Simulation simulation = newSimulation((self, neighbors) -> self.withOpinion(0.5));

        simulation.run(1);

        long moved = simulation.getAgents().stream().filter(a -> a.getOpinion() == 0.5).count();
        assertTrue(moved > 0, "규칙이 한 번도 적용되지 않았다");
    }

    @Test
    void 이웃은_발신자_id_오름차순이고_빈_리스트로는_호출되지_않음() throws InterruptedException {
        Queue<List<Integer>> calls = new ConcurrentLinkedQueue<>();
        Simulation simulation = newSimulation((self, neighbors) -> {
            calls.add(neighbors.stream().map(AgentState::id).toList());
            return self;
        });

        simulation.run(20);

        assertFalse(calls.isEmpty());
        for (List<Integer> ids : calls) {
            assertFalse(ids.isEmpty(), "이웃이 없는 행위자에 대해 update()가 호출됐다");
            assertEquals(ids.stream().sorted().toList(), ids, "발신자 id 오름차순이 아니다: " + ids);
        }
    }
}
