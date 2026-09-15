// polis-engine — 순수 Java 시뮬레이션 엔진. M2-1 검증 대상: 이 모듈에 Spring 의존성이 0개여야 한다.
plugins {
    id("java-library")
    application
}

application {
    mainClass = "com.sys.polis.Main"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 소스 파일은 UTF-8로 저장되는데, Windows 기본 플랫폼 인코딩(x-windows-949)으로
// 읽으면 한글 주석이 깨져 컴파일이 실패한다. 인코딩을 명시적으로 고정한다.
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// BaselineRunner는 plan.md가 지정한 "baseline/m0.json"(루트 기준 경로)에 앵커를 저장한다.
// 모듈을 쪼개면서 Gradle의 기본 작업 디렉터리가 polis-engine/ 하위로 바뀌면 경로가 깨지므로
// 루트 프로젝트 디렉터리로 명시해 기존 앵커 위치를 그대로 유지한다.
tasks.register<JavaExec>("runBaseline") {
    group = "application"
    description = "M0 baseline 시나리오를 실행해 baseline/m0.json(레포 루트)을 갱신한다."
    mainClass.set("com.sys.polis.BaselineRunner")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    // 커스텀 JavaExec은 application 플러그인의 run과 달리 toolchain을 자동으로 안 물려받는다 —
    // 명시하지 않으면 시스템 기본 JAVA_HOME(여기선 17)으로 실행되어 21로 컴파일된 클래스와 버전이 안 맞는다.
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
