// M0 진입점 — 행위자를 만들고, 시뮬레이션을 틱만큼 돌린 뒤, 콘솔에서 양극화 여부를 확인한다.
package com.sys.polis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.rule.AttractionRepulsionRule;
import com.sys.polis.polis_engine.world.NeighborSelector;
import com.sys.polis.polis_engine.world.Simulation;

//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
public class Main {
    public static void main(String[] args) throws InterruptedException {

        // TODO 1. 파라미터 정의 — 행위자 수, 틱 수, mu(학습률), threshold(임계값)
        // CLAUDE.md 3-4: M0는 전역 상수로 시작 (변수 최소화)
        final int AGENT_COUNT = 2;
        final int TICK_COUNT = 2;
        // mu가 0.1일 경우 동화율이 너무 높아서 틱 중간에 극단적 성향이 매우 높아짐.
        // mu를 0.01로 낮추면 동화율이 낮아져서 극단적 성향이 줄어들었다.
        final double mu = 0.01;// 학습률
        final double threshold = 1.5;// 임계값
        // 두 가상스레드를 돌릴 때 임계값이 높으면 중간으로 수렴(1.5 확인), 낮으면 극단으로 수렴(0.5 확인)

        AttractionRepulsionRule updateRule = new AttractionRepulsionRule(mu, threshold);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // 재현 가능한 실험을 위해 Random에 고정 시드 사용(첫 틱 시작 시 동일한 랜덤값 생성)
        Random random = new Random(100);

        Agent agentA = new Agent(new AgentState(0, 0.2));
        Agent agentB = new Agent(new AgentState(1, -0.8));

        agentA.receiveMessage(0.8);
        agentB.receiveMessage(-0.6);

        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // 조건은 Thread.currentThread().isInterrupted()가 false일 때만 반복하도록 설정
                try {
                    // 내 메일박스에서 상대방이 보낸 값을 꺼냄
                    double receivedOpinion = agentA.takeMessages();
                    // TODO: 받은 메시지를 처리하는 로직을 추가
                    AgentState neighborState = new AgentState(0, receivedOpinion);
                    AgentState updateOpinion = updateRule.update(agentA.getCurrentState(),
                            List.of(neighborState));
                    agentA.applyNextState(updateOpinion);
                    // 내 의견값을 상대방의 메일박스에 넣어줌
                    agentB.receiveMessage(agentA.getOpinion());
                    System.out.println("A: " + agentA.getOpinion() + ", received: " + receivedOpinion);

                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    double receivedOpinion = agentB.takeMessages();
                    AgentState neighborState = new AgentState(1, receivedOpinion);
                    AgentState updateOpinion = updateRule.update(agentB.getCurrentState(),
                            List.of(neighborState));
                    agentB.applyNextState(updateOpinion);
                    agentA.receiveMessage(agentB.getOpinion());
                    System.out.println("B: " + agentB.getOpinion() + ", received: " + receivedOpinion);

                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        Thread.sleep(100); // 1초 대기
        executor.shutdownNow(); // 스레드 종료
        System.out.println("Final A: " + agentA.getOpinion());
        System.out.println("Final B: " + agentB.getOpinion());

        // TODO 2. 초기 행위자 목록 생성(행위자 리스트 생성, 랜덤값 지정)
        // List<Agent> agents = ...
        // opinion 초기값을 어떻게 분포시킬지 결정 (예: [-1, 1] 균등 랜덤)
        // 재현 가능한 실험을 원하면 Random에 고정 시드 사용
        List<Agent> agents = new ArrayList<>();

        for (int i = 0; i < AGENT_COUNT; i++) {
            double initialOpinion = -1.0 + 2.0 * random.nextDouble(); // [-1, 1] 균등 랜덤
            agents.add(new Agent(new AgentState(i, initialOpinion)));
        }

        // TODO 3. NeighborSelector, UpdateRule(AttractionRepulsionRule) 생성
        // NeighborSelector(int agentCount, Random random)
        // 위에서 설정한 값들 넣어주기
        NeighborSelector neighborSelector = new NeighborSelector(AGENT_COUNT, new java.util.Random(42)); // 고정 시드
        // 시뮬레이션 종료 후 종합 통계(동화/반발 횟수)를 출력하기 위해 구체 타입으로 들고 있는다.

        // TODO 4. Simulation 생성 후 run(tickCount) 호출
        // 위에서 생성한 agents, neighborSelector, updateRule 넣어주기
        // 아래줄의 역할은 시뮬레이션을 생성하고, 지정된 틱 수만큼 시뮬레이션을 실행하는 것
        // 시뮬레이션의 역할은 행위자들의 상태를 업데이트하고, neighborSelector를 통해 이웃을 선택하며,
        // updateRule을 적용하여 행위자들의 의견을 변화시키는 것이다.
        Simulation simulation = new Simulation(agents, neighborSelector, updateRule);

        // printDistribution("초기: ", agents);
        // simulation.run(TICK_COUNT / 2);
        // printDistribution("중간: ", agents);
        // simulation.run(TICK_COUNT / 2);
        // printDistribution("최종: ", agents);

        // System.out.println("Assimilation Count: " +
        // updateRule.getAssimilationCount());
        // System.out.println("Repulsion Count: " + updateRule.getRepulsionCount());
        // System.out.println("Total Interaction Count: " +
        // updateRule.getTotalInteractionCount());

    }

    private static void printDistribution(String label, List<Agent> agents) {
        // 분포도 확인.
        int[] bins = new int[20]; // 히스토그램을 위한 20개의 구간

        for (int i = 0; i < agents.size(); i++) {
            double opinion = agents.get(i).getCurrentState().opinion();
            int binIndex = (int) ((opinion + 1.0) / 0.1); // 20개의 구간으로 나누기
            binIndex = Math.max(0, Math.min(binIndex, 19)); // 범위 제한
            bins[binIndex]++;
        }
        System.out.println("Histogram: " + java.util.Arrays.toString(bins));

        // 극단적 성향의 행위자 수와 분산 계산
        int extremeCount = 0;
        double sum = 0.0;
        double sumSq = 0.0;
        for (Agent agent : agents) {
            double opinion = agent.getCurrentState().opinion();
            sum += opinion;
            sumSq += opinion * opinion;
            if (Math.abs(opinion) > 0.8) {
                extremeCount++;
            }
        }
        double mean = sum / agents.size();
        double variance = (sumSq / agents.size()) - (mean * mean);

        // 극단적 성향의 행위자의 수, 비율.
        System.out.println("Extreme opinions: " + extremeCount + " (" + (extremeCount * 100.0 / agents.size()) + "%)");
        // 전체 행위자의 의견 값의 분산 정도.
        System.out.println("Variance: " + variance);

    }
}
