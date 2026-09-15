package com.sys.polis.polis_engine.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Agent {

    // 실제 구동 경로(world.Simulation, 틱 병렬 + invokeAll 배리어)에서는 배리어 자체가 가시성을 보장하므로
    // volatile이 없어도 안전하다. 다만 배리어가 없는 experimental.PersistentThreadSimulation(M4 프로토타입)에서는
    // 한 행위자의 스레드가 쓰는 동안 다른 행위자의 스레드가 getOpinion()으로 동시에 읽을 수 있어 진짜 가시성 문제가 된다.
    // 쓰는 주체가 자기 자신 하나뿐이라 CAS/락까지는 필요 없고, volatile만으로 두 모델 모두에서 안전하게 공유된다.
    private volatile AgentState currentState;
    // final은 "이 메일박스 참조는 안 바뀐다"는 뜻(메일박스 객체 자체는 한 번 정해지면 교체 안 됨).
    // Double이 아니라 Message를 담는 이유: opinion 값만 넣으면 받는 쪽이 "누가 보냈는지" 잃어버린다.
    // 한 틱에 여러 발신자가 같은 수신자를 고를 수 있는데(동일 시드에서도 발생), 그 여러 메시지를 어떤 순서로
    // 적용하느냐에 따라 결과가 달라진다 — 발신자 id가 있어야 Simulation이 그 순서를 결정론적으로 고정할 수 있다.
    private final BlockingQueue<Message> mailbox = new LinkedBlockingQueue<>();

    // 초기 의견값을 가진 AgentState를 생성자에서 받아서 currentState에 할당
    public Agent(AgentState initialState) {
        this.currentState = initialState;
    }

    // 현재 상태를 반환하는 메서드. 다음 상태를 계산할 때 사용.
    public AgentState getCurrentState() {
        return currentState;
    }

    // 계산된 다음 상태를 현재 상태로 교체. updateRule이 새 AgentState를 계산해서 넘겨줄때
    // 해당 메서드에서 교체됨.
    public void applyNextState(AgentState next) {
        this.currentState = next;
    }

    // opinion을 업데이트하는 메서드, 내부 상태를 변경. 현재 의견값을 바로 꺼낼 때 사용.
    public double getOpinion() {
        return currentState.opinion();
    }

    // id를 반환하는 메서드, 행위자 ID를 바로 꺼낼 때 사용
    public int getId() {
        return currentState.id();
    }

    // 받은 메시지 put — 보낸 사람의 id를 함께 담아 넣는다(수신자가 "누가 보냈는지" 알아야 하므로).
    public void receiveMessage(int senderId, double opinion) throws InterruptedException {
        mailbox.put(new Message(senderId, opinion));
    }

    // 받은 메시지 take
    public Message takeMessages() throws InterruptedException {
        return mailbox.take();
    }

    // 메일박스가 비어 있으면 즉시 null을 반환하는 논블로킹 조회.
    // take()와 달리 상대가 안 보냈을 수도 있는 상황(직접 참조 라우팅에서 이번 틱에 아무도 안 골랐을 때)에 블로킹 없이 확인할 때 쓴다.
    public Message pollMessage() {
        return mailbox.poll();
    }

    // 지정한 시간(ms) 동안만 기다렸다가, 그래도 메시지가 없으면 null을 반환하는 타임아웃 조회.
    // 실제 구동 경로(world.Simulation)는 쓰지 않고, experimental.PersistentThreadSimulation의 영속 루프가 쓴다 —
    // take()로 무한정 기다리면 아무도 이 행위자를 이웃으로 안 고를 경우 영원히 멈춰서
    // "보내는" 역할조차 못 하게 된다(영속 루프의 교착 위험) — 그래서 짧은 타임아웃으로 끊어준다.
    // 이 타임아웃 대기는 인터럽트에 즉시 반응하므로, shutdownNow() 종료 신호를 받는 통로이기도 하다.
    public Message pollMessage(long timeoutMillis) throws InterruptedException {
        return mailbox.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    // opinion을 업데이트하는 메서드, 내부 상태를 변경
    @Override
    public String toString() {
        return String.format("Agent{id=%d, opinion=%.4f}", getId(), getOpinion());
    }
}
