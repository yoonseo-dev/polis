// M2-4 제어 API. React(M2-5)가 아직 없어 curl/Postman/테스트 코드로 검증한다.
// 필드는 전부 선택값이다 — 안 보낸 값은 이전 값을 그대로 쓴다(SimulationRunner 참조).
package com.sys.polis.polis_server.simulation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationRunner runner;

    public SimulationController(SimulationRunner runner) {
        this.runner = runner;
    }

    // agentCount를 보내면 항상 처음부터 재시작한다(SimulationRunner 클래스 주석 참조 — N 변경은
    // 살아있는 population을 고치는 게 아니라 다시 만드는 것). 이미 돌고 있던 실행도 먼저 멈춘다.
    @PostMapping("/start")
    public SimulationRunner.StatusView start(@RequestBody(required = false) StartRequest request) {
        StartRequest body = request != null ? request : new StartRequest(null, null, null, null);
        return runner.start(body.agentCount(), body.mu(), body.threshold(), body.tickCount());
    }

    @PostMapping("/stop")
    public SimulationRunner.StatusView stop() {
        return runner.stop();
    }

    // mu/threshold만 받는다 — agentCount는 재시작이 필요해 여기서 다루지 않는다(/start 사용).
    // 의미상으로는 PATCH가 더 맞지만, JDK 기본 HttpURLConnection이 PATCH 메서드 자체를 지원하지
    // 않아(java.net.ProtocolException: Invalid HTTP method: PATCH) 테스트/향후 프론트 클라이언트가
    // 겪을 마찰을 피하려고 start/stop과 통일해 POST로 둔다.
    @PostMapping("/params")
    public SimulationRunner.StatusView updateParams(@RequestBody ParamsRequest request) {
        return runner.updateParams(request.mu(), request.threshold());
    }

    @GetMapping("/status")
    public SimulationRunner.StatusView status() {
        return runner.status();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String onInvalidParams(IllegalArgumentException e) {
        return e.getMessage();
    }

    public record StartRequest(Integer agentCount, Double mu, Double threshold, Integer tickCount) {
    }

    public record ParamsRequest(Double mu, Double threshold) {
    }
}
