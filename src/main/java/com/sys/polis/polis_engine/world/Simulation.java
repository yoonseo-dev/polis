package com.sys.polis.polis_engine.world;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.rule.UpdateRule;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class Simulation {

    // 시뮬레이션을 구성하는 행위자, 이웃 선택기, 업데이트 규칙
    private final List<Agent> agents;

    private final NeighborSelector neighborSelector;

    private final UpdateRule rule;

    // 현재 틱에서의 행위자 상태 배열과 다음 틱에서의 상태 배열
    private AgentState[] current;

    private AgentState[] next;

    // 생성자에서 시뮬레이션 구성 요소를 초기화하고, 현재 상태 배열을 설정
    public Simulation(List<Agent> agents, NeighborSelector neighborSelector, UpdateRule rule) {
        this.agents = agents;
        this.neighborSelector = neighborSelector;
        this.rule = rule;

        // 현재 상태 배열과 다음 상태 배열을 초기화
        int n = agents.size();
        this.current = new AgentState[n];
        this.next = new AgentState[n];
        for (int i = 0; i < n; i++) {
            current[i] = agents.get(i).getCurrentState();
        }
    }

    // 시뮬레이션을 지정된 틱 수만큼 실행하는 메서드. 각 틱마다 runOneTick() 호출
    public void run(int tickCount) throws InterruptedException {
        for (int tick = 0; tick < tickCount; tick++) {
            runOneTick();
        }
    }

    // 한 틱 동안 모든 행위자의 상태를 업데이트하는 메서드.
    // 내부적으로 current와 next 배열을 스왑하고, Agent 객체에 확정된 상태를 반영
    // InvokeAll()를 사용하여 병렬로 업데이트 수행
    // throws InterruptedException 은 runOneTick() 메서드에 선언되어 있으므로,
    // 여기서 예외를 처리하지 않고 상위로 전달한다.
    // 또한 연관된 모든 메서드에 throws InterruptedException 를 선언해야 한다.
    private void runOneTick() throws InterruptedException {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        // invokeAll() 메서드가 Callable을 요구하므로, Runnable 대신 Callable<Void>를 사용한다.
        List<Callable<Void>> tasks = new java.util.ArrayList<>();

        for (int i = 0; i < current.length; i++) {

            List<AgentState> neighborStates = neighborSelector.selectNeighbors(i).stream()
                    .map(neighborIndex -> current[neighborIndex])
                    .toList();

            // 람다 안에서 i를 직접 참조하면, for 루프가 끝난 후 i는 current.length가 되어버리므로,
            // final 변수에 복사하여 사용한다.
            final int index = i;

            tasks.add(() -> {
                next[index] = rule.update(current[index], neighborStates);
                return null;
            });

            // throws InterruptedException 은 runOneTick() 메서드에 선언되어 있으므로,
            // 여기서 예외를 처리하지 않고 상위로 전달한다.
        }

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            // 알아서 close() 호출되므로 executor.shutdown()은 필요 없다.
        }

        // 한꺼번에 모아서 한 번만 invokeAll() 호출.
        // invokeAll()은 모든 작업이 완료될 때까지 블록된다.

        // 스왑: 배열 내용을 복사하지 않고 변수(참조, 화살표)만 맞바꾼다 — 배열 두 개는 재사용된다.
        AgentState[] tmp = current;
        current = next;
        next = tmp;

        // Agent 객체에 확정된 상태를 반영한다 (Agent.java의 "버퍼 순회 후 applyNextState" 계약).
        for (int i = 0; i < agents.size(); i++) {
            agents.get(i).applyNextState(current[i]);
        }
    }

    /** 가장 최근 틱 종료 시점의 전체 행위자 목록. 콘솔 출력·지표 수집이 여기서 상태를 읽는다. */
    public List<Agent> getAgents() {
        return agents;
    }
}
