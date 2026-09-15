// M0 고정 시드 결과(분산 추이, 극단값 비율 시계열)를 baseline/m0.json으로 저장하는 일회성 도구.
// M1-a에서 이미 Simulation(틱 병렬 + invokeAll)이 M0(단일 스레드 동기 구현)와 수치적으로 동일함을
// 검증했으므로, 여기서 순차 엔진을 별도로 다시 구현하지 않고 같은 Simulation을 1틱씩 실행하며 값을 기록한다.
// 이 파일은 plan.md M3에서 새 갱신 규칙(EchoChamberRule 등)이 만드는 거시 패턴을 M0와
// 대조(트렌드 일관성 검증)하는 앵커로 쓰인다 — Simulation의 갱신 규칙만 교체해도 재사용 가능.
package com.sys.polis;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.rule.AttractionRepulsionRule;
import com.sys.polis.polis_engine.world.NeighborSelector;
import com.sys.polis.polis_engine.world.Simulation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BaselineRunner {

    // Main.java의 M0 기본값과 동일한 시나리오를 그대로 앵커로 고정한다 — 값이 달라지면 앵커의 의미가 없어진다.
    private static final int AGENT_COUNT = 100;
    private static final int TICK_COUNT = 100;
    private static final double MU = 0.01;
    private static final double THRESHOLD = 1.5;
    private static final long INITIAL_OPINION_SEED = 100;
    private static final long NEIGHBOR_SEED = 42;

    public static void main(String[] args) throws InterruptedException, IOException {
        AttractionRepulsionRule rule = new AttractionRepulsionRule(MU, THRESHOLD);
        Random random = new Random(INITIAL_OPINION_SEED);

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < AGENT_COUNT; i++) {
            double initialOpinion = -1.0 + 2.0 * random.nextDouble();
            agents.add(new Agent(new AgentState(i, initialOpinion)));
        }

        NeighborSelector neighborSelector = new NeighborSelector(AGENT_COUNT, new Random(NEIGHBOR_SEED));
        Simulation simulation = new Simulation(agents, neighborSelector, rule);

        // tick=0(초기 상태)부터 tick=TICK_COUNT까지 매 틱 끝의 분산·극단값 비율을 기록한다.
        List<Double> varianceSeries = new ArrayList<>();
        List<Double> extremeRatioSeries = new ArrayList<>();
        varianceSeries.add(variance(agents));
        extremeRatioSeries.add(extremeRatio(agents));

        for (int tick = 0; tick < TICK_COUNT; tick++) {
            simulation.run(1); // 정확히 한 틱만 진행 — 틱마다 지표를 찍기 위해 잘게 쪼갠다.
            varianceSeries.add(variance(agents));
            extremeRatioSeries.add(extremeRatio(agents));
        }

        Path outputPath = Path.of("baseline", "m0.json");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, toJson(varianceSeries, extremeRatioSeries), StandardCharsets.UTF_8);

        System.out.println("Baseline saved: " + outputPath.toAbsolutePath());
    }

    // |opinion| > 0.8인 행위자 비율(0~1).
    private static double extremeRatio(List<Agent> agents) {
        long extremeCount = agents.stream().filter(a -> Math.abs(a.getOpinion()) > 0.8).count();
        return (double) extremeCount / agents.size();
    }

    // 전체 행위자 opinion의 분산.
    private static double variance(List<Agent> agents) {
        double sum = 0.0;
        double sumSq = 0.0;
        for (Agent agent : agents) {
            double opinion = agent.getOpinion();
            sum += opinion;
            sumSq += opinion * opinion;
        }
        double mean = sum / agents.size();
        return (sumSq / agents.size()) - (mean * mean);
    }

    // 외부 JSON 라이브러리 없이 손으로 직렬화한다(KISS — 이 프로젝트엔 JSON 의존성이 없고, 필요한 구조도 평평한 배열뿐).
    private static String toJson(List<Double> varianceSeries, List<Double> extremeRatioSeries) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"scenario\": \"M0 anchor - AttractionRepulsionRule\",\n");
        json.append("  \"agentCount\": ").append(AGENT_COUNT).append(",\n");
        json.append("  \"tickCount\": ").append(TICK_COUNT).append(",\n");
        json.append("  \"mu\": ").append(MU).append(",\n");
        json.append("  \"threshold\": ").append(THRESHOLD).append(",\n");
        json.append("  \"seeds\": { \"initialOpinion\": ").append(INITIAL_OPINION_SEED)
                .append(", \"neighborSelector\": ").append(NEIGHBOR_SEED).append(" },\n");
        json.append("  \"varianceByTick\": ").append(arrayOf(varianceSeries)).append(",\n");
        json.append("  \"extremeRatioByTick\": ").append(arrayOf(extremeRatioSeries)).append("\n");
        json.append("}\n");
        return json.toString();
    }

    private static String arrayOf(List<Double> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        return sb.append("]").toString();
    }
}
