// M2-4: 제어 API가 실제로 조작하는 대상. M2-3까지는 "누가 /topic/snapshots를 구독하면
// 자동 발사"였는데, 그건 제어 API가 없던 동안의 임시방편이었다(SimulationSnapshotBroadcaster,
// 이제 삭제). 지금부터는 REST(SimulationController)가 명시적으로 start/stop/params를 호출해야
// 움직인다 — 구독은 더 이상 트리거가 아니다.
//
// N(agentCount) 변경과 mu/threshold 변경은 "런타임 변경"이라도 성격이 다르다.
//   - mu/threshold: 돌고 있는 rule 인스턴스의 필드값만 바꾸면 된다. opinion 상태를 유지한 채
//     다음 틱부터 새 값으로 갱신되므로 진짜 "실행 중 변경"이다.
//   - agentCount: Agent 리스트 자체의 크기를 바꾸는 것이라, 살아있는 시뮬레이션에 행위자를
//     추가/제거하는 의미 있는 규칙이 없다(그건 별도 기획이 필요한 "출생/사망" 모델이다).
//     그래서 agentCount 변경은 start()를 다시 불러 처음부터 재시작하는 방식으로만 지원한다.
package com.sys.polis.polis_server.simulation;

import com.sys.polis.polis_engine.agent.Agent;
import com.sys.polis.polis_engine.agent.AgentState;
import com.sys.polis.polis_engine.metric.MetricCollector;
import com.sys.polis.polis_engine.rule.AttractionRepulsionRule;
import com.sys.polis.polis_engine.world.NeighborSelector;
import com.sys.polis.polis_engine.world.Simulation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class SimulationRunner {

    private static final String SNAPSHOT_DESTINATION = "/topic/snapshots";

    private final SimpMessagingTemplate messagingTemplate;

    // 다음 start() 호출의 기본값. application.yml 초기값에서 시작하고, start()/updateParams()가
    // 호출될 때마다 갱신된다 — "런타임에 바꾼 값이 재시작 이후에도 유지된다"는 뜻.
    // 제어 API는 REST 요청 스레드에서 호출되고, 조회는 상태 조회(status)나 runLoop 스레드에서
    // 일어나므로 start/stop/updateParams/status를 synchronized로 묶어 직렬화한다(admin 성격의
    // 저빈도 호출이라 락 경합은 문제되지 않는다).
    private int agentCount;
    private int tickCount;
    private double mu;
    private double threshold;

    // 실행 중인 규칙 인스턴스. mu/threshold 라이브 갱신은 이 인스턴스의 세터를 호출하는 방식이다.
    private volatile AttractionRepulsionRule liveRule;

    // 실행 중인 루프 스레드. null이면 "실행 중 아님". runLoop 종료 시 스스로도 null로 되돌리는데,
    // 이건 단일 관리자(REST 호출)만 이 필드를 건드린다는 전제하의 단순 volatile 필드다 —
    // 아주 짧은 순간 status()가 낡은 값을 보여줄 수 있지만 다음 조회에서 곧바로 정정된다.
    private volatile Thread runningThread;

    public SimulationRunner(
            SimpMessagingTemplate messagingTemplate,
            @Value("${polis.simulation.agent-count}") int agentCount,
            @Value("${polis.simulation.tick-count}") int tickCount,
            @Value("${polis.simulation.mu}") double mu,
            @Value("${polis.simulation.threshold}") double threshold) {
        this.messagingTemplate = messagingTemplate;
        this.agentCount = agentCount;
        this.tickCount = tickCount;
        this.mu = mu;
        this.threshold = threshold;
    }

    // 이미 돌고 있으면 먼저 멈추고 새로 시작한다. null인 파라미터는 이전 값을 그대로 쓴다.
    public synchronized StatusView start(Integer agentCount, Double mu, Double threshold, Integer tickCount) {
        stopInternal();
        if (agentCount != null) this.agentCount = agentCount;
        if (mu != null) this.mu = mu;
        if (threshold != null) this.threshold = threshold;
        if (tickCount != null) this.tickCount = tickCount;

        List<Agent> agents = createAgents(this.agentCount);
        NeighborSelector neighborSelector = new NeighborSelector(this.agentCount, new Random(42));
        AttractionRepulsionRule rule = new AttractionRepulsionRule(this.mu, this.threshold);
        this.liveRule = rule;
        Simulation simulation = new Simulation(agents, neighborSelector, rule);

        this.runningThread = Thread.ofVirtual().name("simulation-runner").start(() -> runLoop(simulation, agents));
        return status();
    }

    public synchronized StatusView stop() {
        stopInternal();
        return status();
    }

    // 실행 중이면 즉시 반영, 아니면 다음 start()의 기본값만 갱신한다.
    public synchronized StatusView updateParams(Double mu, Double threshold) {
        if (mu != null) {
            this.mu = mu;
            AttractionRepulsionRule rule = this.liveRule;
            if (rule != null) rule.setMu(mu);
        }
        if (threshold != null) {
            this.threshold = threshold;
            AttractionRepulsionRule rule = this.liveRule;
            if (rule != null) rule.setThreshold(threshold);
        }
        return status();
    }

    public synchronized StatusView status() {
        return new StatusView(runningThread != null, agentCount, tickCount, mu, threshold);
    }

    // interrupt 후 join까지 해서, 호출부(start/stop)가 돌아왔을 때는 이전 루프가 확실히
    // 끝난 뒤라는 걸 보장한다 — 안 그러면 재시작 직후 두 루프가 동시에 같은 토픽에 publish할 수 있다.
    private void stopInternal() {
        Thread thread = this.runningThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.runningThread = null;
        this.liveRule = null;
    }

    private void runLoop(Simulation simulation, List<Agent> agents) {
        try {
            publish(MetricCollector.snapshot(0, agents));
            for (int tick = 1; tick <= tickCount && !Thread.currentThread().isInterrupted(); tick++) {
                simulation.run(1);
                publish(MetricCollector.snapshot(tick, agents));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 자연 종료(tickCount 소진) 시에도 status()가 "실행 중 아님"을 보고하도록 스스로 정리한다.
            // stopInternal()이 이미 정리한 경우(외부 stop)엔 다른 스레드의 필드라 건드리지 않는다.
            if (runningThread == Thread.currentThread()) {
                runningThread = null;
                liveRule = null;
            }
        }
    }

    private void publish(MetricCollector.Snapshot snapshot) {
        messagingTemplate.convertAndSend(SNAPSHOT_DESTINATION, snapshot);
    }

    private List<Agent> createAgents(int agentCount) {
        Random initialOpinionRandom = new Random(100);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            double initialOpinion = -1.0 + 2.0 * initialOpinionRandom.nextDouble();
            agents.add(new Agent(new AgentState(i, initialOpinion)));
        }
        return agents;
    }

    public record StatusView(boolean running, int agentCount, int tickCount, double mu, double threshold) {
    }
}
