// [실험용 프로토타입 — 실제 구동 경로 아님]
// M4에서 정식으로 다룰 "행위자 1명 = 영속 가상 스레드 1개" 모델의 초안.
// plan.md v2: M1-b는 world.Simulation(틱 병렬 + invokeAll)으로 재정의되었고,
// 이 클래스는 그 대안 모델을 미리 만들어보고 수동으로 테스트해보기 위한 자리다.
// Main.java는 이 클래스를 쓰지 않는다 — 필요할 때 이 파일의 main()으로 직접 돌려본다.
//
// world.Simulation과의 핵심 차이: 틱 경계(invokeAll 배리어)가 없다.
// 각 행위자가 무한 루프로 자기 페이스에 맞춰 "보내기 → 받기 → 갱신"을 반복하므로
// 실행마다 인터리빙이 달라지는 비결정적 시스템이다(트렌드 일관성으로만 검증 가능).
package com.sys.polis.polis_engine.experimental;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.agent.Message;
import com.sys.polis.polis_engine.rule.UpdateRule;
import com.sys.polis.polis_engine.world.NeighborSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sys.polis.polis_engine.rule.AttractionRepulsionRule;

public class PersistentThreadSimulation {

    // 자기 메일박스에 아무것도 없을 때 한 번에 최대 이만큼(ms)만 기다렸다가 다시 루프를 돈다.
    // take()로 무한 대기하면 아무도 이웃으로 안 고른 행위자는 영원히 멈춰 "보내는" 역할도 못 하게 된다(교착 위험).
    // 반대로 poll(0)이면 메시지가 없을 때도 계속 CPU를 태우는 바쁜 대기(busy-wait)가 된다.
    // 짧은 타임아웃 poll이 그 중간 지점 — 응답성(인터럽트도 이 대기 중에 즉시 전달됨)과 CPU 낭비 방지를 함께 챙긴다.
    private static final long MAILBOX_POLL_TIMEOUT_MS = 10;

    private final List<Agent> agents;
    private final NeighborSelector neighborSelector;
    private final UpdateRule rule;

    public PersistentThreadSimulation(List<Agent> agents, NeighborSelector neighborSelector, UpdateRule rule) {
        this.agents = agents;
        this.neighborSelector = neighborSelector;
        this.rule = rule;
    }

    // durationMillis 동안 모든 행위자를 영속 가상 스레드로 자유 실행시킨 뒤 전원 종료한다.
    // 틱이 없으므로 파라미터도 "몇 틱"이 아니라 "몇 ms 동안 돌릴지"다.
    // newVirtualThreadPerTaskExecutor(): 태스크(=행위자의 무한 루프)마다 OS 스레드가 아니라
    // 가상 스레드를 붙여준다 — "영속" 루프를 수만 개 동시에 띄워도 OS 스레드 수 제약에 걸리지 않는다.
    public void run(long durationMillis) throws InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < agents.size(); i++) {
                final int selfIndex = i; // 람다에 캡처되는 변수는 effectively final이어야 함
                executor.submit(() -> agentLoop(selfIndex));
            }

            Thread.sleep(durationMillis);

            // shutdownNow()는 제출된 모든 태스크(=agentLoop들)에 인터럽트를 건다.
            // agentLoop()는 블로킹 지점(mailbox.poll(timeout))에서 InterruptedException으로 깨어나
            // catch에서 루프를 벗어나도록 만들어져 있다 — 시간 기반 종료 패턴.
            executor.shutdownNow();
        }
        // try-with-resources의 close()가 여기서 남은 태스크 종료를 기다린다.
        // shutdownNow()로 이미 인터럽트를 걸어뒀으므로 곧 끝난다(무한정 블록되지 않음).
    }

    // 행위자 한 명의 영속 루프 본체. 인터럽트(=시간 종료 신호)가 올 때까지
    // "이웃에게 보내기 → 내 메일박스 확인 → 도착했으면 갱신"을 반복한다.
    private void agentLoop(int selfIndex) {
        Agent self = agents.get(selfIndex);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<Integer> neighborIndexes = neighborSelector.selectNeighbors(selfIndex);
                for (int neighborIndex : neighborIndexes) {
                    Agent neighbor = agents.get(neighborIndex);
                    neighbor.receiveMessage(self.getId(), self.getOpinion());
                }

                Message message = self.pollMessage(MAILBOX_POLL_TIMEOUT_MS);
                if (message != null) {
                    List<AgentState> received = new ArrayList<>();
                    received.add(new AgentState(message.senderId(), message.opinion()));
                    Message more;
                    while ((more = self.pollMessage()) != null) { // 남은 건 논블로킹으로 마저 비운다
                        received.add(new AgentState(more.senderId(), more.opinion()));
                    }
                    // 영속 스레드 모델은 배리어가 없어 애초에 도착 순서가 비결정적이므로(설계 의도),
                    // world.Simulation과 달리 여기서는 정렬하지 않는다 — 트렌드 일관성으로만 검증한다.
                    AgentState next = rule.update(self.getCurrentState(), received);
                    self.applyNextState(next);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<Agent> getAgents() {
        return agents;
    }

    // 수동 테스트 진입점 — 프로젝트의 실제 Main이 아니라 이 프로토타입만 단독으로 돌려볼 때 쓴다.
    // build.gradle.kts의 application.mainClass가 com.sys.polis.Main으로 고정돼 있어 `gradle run`으로는 못 돌리고,
    // 컴파일된 클래스를 classpath에 놓고 직접 실행해야 한다:
    //   ./gradlew compileJava
    //   java -cp build/classes/java/main com.sys.polis.polis_engine.experimental.PersistentThreadSimulation 100 2000
    public static void main(String[] args) throws InterruptedException {
        int agentCount = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        long durationMillis = args.length > 1 ? Long.parseLong(args[1]) : 2000;

        Random random = new Random(100);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            agents.add(new Agent(new AgentState(i, -1.0 + 2.0 * random.nextDouble())));
        }

        NeighborSelector neighborSelector = new NeighborSelector(agentCount, new Random(42));
        UpdateRule rule = new AttractionRepulsionRule(0.01, 1.5);
        PersistentThreadSimulation simulation = new PersistentThreadSimulation(agents, neighborSelector, rule);

        System.out.println("[experimental] AGENT_COUNT=" + agentCount + ", DURATION_MILLIS=" + durationMillis);
        simulation.run(durationMillis);
        System.out.println("[experimental] done — M4에서 world.Simulation과 정식 비교 예정.");
    }
}
