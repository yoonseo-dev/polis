// polis-server 진입점. Spring Boot는 이 모듈에만 존재한다 — polis-engine은 순수 Java로 유지된다(M2-1).
package com.sys.polis.polis_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PolisServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolisServerApplication.class, args);
    }
}
