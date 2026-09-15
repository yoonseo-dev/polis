// CLAUDE.md 패키지 구조에 명세돼 있었으나 그동안 비어 있던 metric 패키지.
// M2-3(매 틱 스냅샷 push)에서 polis-server가 raw opinion 값 대신 보낼 "히스토그램 버킷 +
// 요약 지표"를 만들 곳이 필요해 이제 채운다.
//
// 주의: Main.java/BaselineRunner.java의 기존 계산 로직은 M0 baseline 앵커(plan.md 2절
// "손대지 않는다")라 여기서 그쪽을 이 클래스로 갈아끼우지 않는다. 대신 여기서 동일한 계산을
// 새로 만들고, MetricCollectorTest에서 Main.java의 20-버킷 분할 방식과 결과가 같은지 확인한다.
package com.sys.polis.polis_engine.metric;

import com.sys.polis.polis_engine.agent.Agent;

import java.util.List;

public class MetricCollector {

    // Main.java 콘솔 히스토그램과 동일한 20개 구간 — 이 값을 바꾸면 콘솔 출력과 어긋난다.
    private static final int BUCKET_COUNT = 20;

    // |opinion| > 0.8을 "극단"으로 보는 기준. Main.java/BaselineRunner.java와 동일.
    private static final double EXTREME_THRESHOLD = 0.8;

    private MetricCollector() {
        // 정적 유틸리티라 인스턴스화하지 않는다.
    }

    // opinion을 [-1, 1] 범위에서 20개 구간으로 나눠 각 구간에 몇 명이 있는지 센다.
    public static int[] histogram(List<Agent> agents) {
        int[] buckets = new int[BUCKET_COUNT];
        for (Agent agent : agents) {
            double opinion = agent.getOpinion();
            int index = (int) ((opinion + 1.0) / 0.1);
            index = Math.max(0, Math.min(index, BUCKET_COUNT - 1));
            buckets[index]++;
        }
        return buckets;
    }

    // 전체 행위자 opinion의 분산.
    public static double variance(List<Agent> agents) {
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

    // |opinion| > 0.8인 행위자 비율(0~1).
    public static double extremeRatio(List<Agent> agents) {
        long extremeCount = agents.stream()
                .filter(a -> Math.abs(a.getOpinion()) > EXTREME_THRESHOLD)
                .count();
        return (double) extremeCount / agents.size();
    }

    // 특정 틱 시점의 히스토그램·분산·극단값 비율을 한 번에 묶어서 만든다.
    // raw opinion 배열을 담지 않는다 — 이 레코드 자체가 M2-3의 "집계만 전송" 요구사항이다.
    public static Snapshot snapshot(int tick, List<Agent> agents) {
        return new Snapshot(tick, histogram(agents), variance(agents), extremeRatio(agents));
    }

    // WebSocket으로 그대로 직렬화해 보낼 집계 스냅샷. polis-server가 이 레코드를 재사용한다.
    public record Snapshot(int tick, int[] histogram, double variance, double extremeRatio) {
    }
}
