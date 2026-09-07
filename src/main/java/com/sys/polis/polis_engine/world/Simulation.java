// 틱 루프와 가상 스레드 실행기를 관리하는 엔진 핵심 클래스.
// 행위자들은 배열을 공유해서 읽는 대신, agents 리스트에서 얻은 이웃 Agent 참조를 직접 들고
// 그 mailbox에 opinion을 넣는 "직접 참조 라우팅" 방식으로 통신한다.
package com.sys.polis.polis_engine.world;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.rule.UpdateRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Simulation {

    // 시뮬레이션을 구성하는 행위자, 이웃 선택기, 업데이트 규칙
    private final List<Agent> agents;

    private final NeighborSelector neighborSelector;

    private final UpdateRule rule;

    // 생성자에서 시뮬레이션 구성 요소를 초기화한다. 상태는 각 Agent 객체가 직접 들고 있으므로
    // (이전처럼 별도 current/next 배열로 복사해두지 않는다) 여기서 할 일은 참조 저장뿐이다.
    public Simulation(List<Agent> agents, NeighborSelector neighborSelector, UpdateRule rule) {
        this.agents = agents;
        this.neighborSelector = neighborSelector;
        this.rule = rule;
    }

    // 시뮬레이션을 지정된 틱 수만큼 실행하는 메서드.
    // ExecutorService를 run() 호출 전체에서 재사용한다(틱마다 새로 만들면 스레드 풀 생성 비용이 반복됨).
    // try-with-resources: ExecutorService는 AutoCloseable이라 블록을 벗어나면 close()가 자동 호출되어
    // 그 시점까지 제출된 작업이 모두 끝날 때까지 기다린 뒤 종료한다.
    public void run(int tickCount) throws InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int tick = 0; tick < tickCount; tick++) {
                runOneTick(executor);
            }
        }
    }

    // 한 틱을 "보내기"와 "받아서 갱신하기" 2단계로 나눠 실행한다.
    // 두 단계 사이, 그리고 각 단계 끝에서 invokeAll()로 전원 완료를 기다려야
    // 이번 틱에 보낸 메시지와 다음 틱에 보낼 메시지가 섞이지 않는다
    // (가상 스레드로 병렬 처리하면서도 틱 경계는 동기화 유지 — CLAUDE.md M0 결정론 요구사항).
    private void runOneTick(ExecutorService executor) throws InterruptedException {

        // Phase 1(보내기): 각 행위자가 이번 틱에 뽑힌 이웃의 mailbox에 자기 opinion을 직접 넣는다.
        List<Callable<Void>> sendTasks = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            Agent self = agents.get(i);
            List<Integer> neighborIndexes = neighborSelector.selectNeighbors(i);
            sendTasks.add(() -> {
                for (int neighborIndex : neighborIndexes) {
                    Agent neighbor = agents.get(neighborIndex); // 리스트에서 얻은 참조를 그대로 사용 — 직접 참조 라우팅
                    neighbor.receiveMessage(self.getOpinion());
                }
                return null;
            });
        }
        // invokeAll()은 제출한 작업이 전부 끝날 때까지 블록된다 — 이 틱의 발신이 모두 끝난 뒤에야 다음 단계로 넘어간다.
        executor.invokeAll(sendTasks);

        // Phase 2(받아서 갱신하기): 각 행위자가 이번 틱에 도착한 메시지를 모두 꺼내 갱신 규칙을 적용한다.
        List<Callable<Void>> receiveTasks = new ArrayList<>();
        for (Agent self : agents) {
            receiveTasks.add(() -> {
                List<AgentState> received = new ArrayList<>();
                Double opinion;
                // pollMessage()는 논블로킹 — 아무도 이 행위자를 이웃으로 고르지 않았으면 즉시 null.
                while ((opinion = self.pollMessage()) != null) {
                    received.add(new AgentState(self.getId(), opinion));
                }
                if (!received.isEmpty()) {
                    AgentState next = rule.update(self.getCurrentState(), received);
                    self.applyNextState(next);
                }
                return null;
            });
        }
        executor.invokeAll(receiveTasks);
    }

    /** 가장 최근 틱 종료 시점의 전체 행위자 목록. 콘솔 출력·지표 수집이 여기서 상태를 읽는다. */
    public List<Agent> getAgents() {
        return agents;
    }
}
