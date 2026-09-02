plugins {
    id("java")
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

group = "me.songyoonseo"
version = "1.0-SNAPSHOT"

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