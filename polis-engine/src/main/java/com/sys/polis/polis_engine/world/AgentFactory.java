package com.sys.polis.polis_engine.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;

// 초기 행위자 목록 생성을 한곳에 모은다. 시드 정책(고정/랜덤)은 호출하는 쪽이 Random을 주입해 정한다.
// 진입점(Main, 서버, BaselineRunner 등)마다 초기화 코드를 복사해 쓰던 것을 대체한다.
public final class AgentFactory {

    private AgentFactory() {
    }

    // opinion 초기값은 [-1, 1] 균등 랜덤 분포. id는 0부터 agentCount-1까지 순서대로 부여한다.
    // 같은 시드의 Random을 넘기면 항상 같은 초기 분포가 나온다(호출당 행위자마다 nextDouble() 1회 소비).
    public static List<Agent> createAgents(int agentCount, Random random) {
        List<Agent> agents = new ArrayList<>(agentCount);
        for (int i = 0; i < agentCount; i++) {
            double initialOpinion = -1.0 + 2.0 * random.nextDouble();
            agents.add(new Agent(new AgentState(i, initialOpinion)));
        }
        return agents;
    }
}
