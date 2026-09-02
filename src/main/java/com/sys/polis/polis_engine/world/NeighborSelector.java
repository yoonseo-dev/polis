package com.sys.polis.polis_engine.world;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class NeighborSelector {

    // 한 틱에 만나는 이웃 수. M0 단계는 1명으로 단순화(전역 상수 — 변수 최소화 원칙).
    private static final int NEIGHBORS_PER_TICK = 1;

    // 전체 행위자 수. 인덱스 뽑기는 [0, agentCount) 범위만 알면 되는 순수 정수 연산이다.
    private final int agentCount;

    // 난수 생성기. 시드를 고정한 Random을 주입받으면 실험을 재현할 수 있다.
    private final Random random;

    // 예외처리
    public NeighborSelector(int agentCount, Random random) {
        // 만나는 이웃 수가 전체 행위자 수보다 많은 상황을 방지
        if (agentCount <= NEIGHBORS_PER_TICK) {
            throw new IllegalArgumentException(
                    "agentCount는 NEIGHBORS_PER_TICK(" + NEIGHBORS_PER_TICK + ")보다 커야 합니다: " + agentCount);
        }
        this.agentCount = agentCount;
        this.random = random;
    }

    // 지정된 행위자(self)와 만나는 이웃의 인덱스를 반환. 중복 없이 랜덤하게 선택.
    public List<Integer> selectNeighbors(int self) {
        Set<Integer> neighbors = new HashSet<>();

        while (neighbors.size() < NEIGHBORS_PER_TICK) {
            int candidate = random.nextInt(agentCount);
            if (candidate != self) {
                neighbors.add(candidate);
            }
        }

        return List.copyOf(neighbors);
    }
}
