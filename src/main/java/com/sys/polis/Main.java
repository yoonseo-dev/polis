// M0 진입점 — 행위자 목록을 생성하고, 매 틱마다 무작위 이웃과 상호작용시킨 뒤, 콘솔에서 양극화 여부를 확인한다.
package com.sys.polis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.rule.AttractionRepulsionRule;
import com.sys.polis.polis_engine.world.NeighborSelector;
import com.sys.polis.polis_engine.world.Simulation;

//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
public class Main {
    public static void main(String[] args) throws InterruptedException {

        // 파라미터 정의 — 행위자 수, 틱 수, mu(학습률), threshold(임계값)
        // CLAUDE.md 3-4: M0는 전역 상수로 시작 (변수 최소화)
        final int AGENT_COUNT = 100;
        final int TICK_COUNT = 100;
        // mu가 0.1일 경우 동화율이 너무 높아서 틱 중간에 극단적 성향이 매우 높아짐.
        // mu를 0.01로 낮추면 동화율이 낮아져서 극단적 성향이 줄어들었다.
        final double mu = 0.01; // 학습률
        final double threshold = 1.5; // 임계값
        // 임계값이 높으면 중간으로 수렴(1.5 확인), 낮으면 극단으로 수렴(0.5 확인)

        AttractionRepulsionRule updateRule = new AttractionRepulsionRule(mu, threshold);

        // 재현 가능한 실험을 위해 Random에 고정 시드 사용(초기 분포 생성 시 동일한 랜덤값 생성)
        Random random = new Random(100);

        // 초기 행위자 목록 생성 — opinion 초기값은 [-1, 1] 균등 랜덤 분포
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < AGENT_COUNT; i++) {
            double initialOpinion = -1.0 + 2.0 * random.nextDouble(); // [-1, 1] 균등 랜덤
            agents.add(new Agent(new AgentState(i, initialOpinion)));
        }

        // 어떤 이웃을 만날지는 NeighborSelector로 분리한다.
        // 이 Random(42)는 매 틱 selectNeighbors()를 호출할 때마다 소비되므로,
        // 같은 시드라도 틱이 진행될수록 다른 이웃이 무작위로 뽑힌다.
        NeighborSelector neighborSelector = new NeighborSelector(AGENT_COUNT, new Random(42));

        // 시뮬레이션 생성 — agents, neighborSelector, updateRule을 받아 틱 루프(직접 참조 라우팅)를 관리한다.
        Simulation simulation = new Simulation(agents, neighborSelector, updateRule);

        printDistribution("초기: ", agents);
        simulation.run(TICK_COUNT / 2);
        printDistribution("중간: ", agents);
        simulation.run(TICK_COUNT / 2);
        printDistribution("최종: ", agents);

        System.out.println("Assimilation Count: " + updateRule.getAssimilationCount());
        System.out.println("Repulsion Count: " + updateRule.getRepulsionCount());
        System.out.println("Total Interaction Count: " + updateRule.getTotalInteractionCount());
    }

    // 분포도 출력
    private static void printDistribution(String label, List<Agent> agents) {
        int[] bins = new int[20]; // 히스토그램을 위한 20개의 구간

        for (int i = 0; i < agents.size(); i++) {
            double opinion = agents.get(i).getCurrentState().opinion();
            int binIndex = (int) ((opinion + 1.0) / 0.1); // 20개의 구간으로 나누기
            binIndex = Math.max(0, Math.min(binIndex, 19)); // 범위 제한
            bins[binIndex]++;
        }
        System.out.println(label + "Histogram: " + java.util.Arrays.toString(bins));

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
        System.out.println(label + "Extreme opinions: " + extremeCount + " (" + (extremeCount * 100.0 / agents.size()) + "%)");
        // 전체 행위자의 의견 값의 분산 정도.
        System.out.println(label + "Variance: " + variance);
    }
}
