package com.sys.polis.polis_engine.rule;

import com.sys.polis.polis_engine.agent.AgentState;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public class AttractionRepulsionRule implements UpdateRule {

    // M2-4: 실행 중인 시뮬레이션의 파라미터를 재시작 없이 바꿀 수 있어야 해서(제어 API가 세터를
    // 호출) final을 걷어내고 volatile로 바꿨다 — 세터를 호출하는 스레드(REST 요청 처리)와
    // update()를 호출하는 스레드(시뮬레이션 루프)가 다르므로 가시성 보장이 필요하다.
    private volatile double mu;// 학습률, 몇 퍼센트 동화될지.

    private volatile double threshold;// 임계값(경계선)

    // 아래 세 카운터는 이 규칙 인스턴스 하나를 전체 행위자가 공유해서 호출하므로,
    // 특정 틱만의 값이 아니라 시뮬레이션 시작부터 누적된 "전체 인구 기준" 횟수다.
    // 틱별 값이 필요하면 호출부에서 이전 틱 끝의 값을 빼서(델타) 구해야 한다.
    private LongAdder assimilationCount = new LongAdder(); // 동화(d <= threshold) 누적 횟수
    private LongAdder repulsionCount = new LongAdder(); // 반발(d > threshold) 누적 횟수
    private LongAdder totalInteractionCount = new LongAdder(); // 동화 + 반발 누적 횟수

    // 예외처리
    public AttractionRepulsionRule(double mu, double threshold) {
        setMu(mu);
        setThreshold(threshold);
    }

    // 실행 중에도 호출 가능 — 다음 update()부터 새 값이 적용된다(재시작 불필요).
    public void setMu(double mu) {
        if (mu <= 0 || mu > 1) {
            throw new IllegalArgumentException("mu는 (0, 1] 범위여야 합니다: " + mu);
        }
        this.mu = mu;
    }

    // 실행 중에도 호출 가능 — 다음 update()부터 새 값이 적용된다(재시작 불필요).
    public void setThreshold(double threshold) {
        if (threshold <= 0 || threshold > 2) {
            throw new IllegalArgumentException("threshold는 (0, 2] 범위여야 합니다: " + threshold);
        }
        this.threshold = threshold;
    }

    // UpdateRule 인터페이스 구현
    @Override
    public AgentState update(AgentState self, List<AgentState> neighbors) {

        // 이웃이 없으면 자기 자신을 그대로 반환.
        if (neighbors.isEmpty()) {
            return self;
        }

        // 이웃을 순차적으로 만나며 의견을 누적 갱신한다.
        // double 변수로 꺼내 연산하고, 마지막에 한 번만 AgentState를 생성한다.
        double opinion = self.opinion();

        for (AgentState neighbor : neighbors) {
            double d = Math.abs(opinion - neighbor.opinion());
            // abs -> 절댓값 구하기.(거리값 계산)

            if (d <= threshold) {
                // 동화: 상대 의견 쪽으로 이동
                opinion += mu * (neighbor.opinion() - opinion);
                assimilationCount.increment();
            } else {
                // 반발: 상대 의견 반대 방향으로 이동
                opinion -= mu * (neighbor.opinion() - opinion);
                repulsionCount.increment();
            }

            totalInteractionCount.increment();
        }

        // withOpinion 내부에서 [-1, +1] clamp + 새 AgentState 생성
        return self.withOpinion(opinion);
    }

    // 시뮬레이션 시작부터 지금까지 누적된 동화 횟수. 틱별 값은 호출부에서 델타로 계산.
    public Long getAssimilationCount() {
        return assimilationCount.sum();
    }

    // 시뮬레이션 시작부터 지금까지 누적된 반발 횟수. 틱별 값은 호출부에서 델타로 계산.
    public Long getRepulsionCount() {
        return repulsionCount.sum();
    }

    // 시뮬레이션 시작부터 지금까지 누적된 전체 상호작용 횟수(동화+반발).
    public Long getTotalInteractionCount() {
        return totalInteractionCount.sum();
    }

}
