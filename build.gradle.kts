// 루트 빌드 스크립트 — M2-1(폴리스 엔진의 모듈 분리) 이후에는 각 모듈이 자기 플러그인·의존성을
// 각자의 build.gradle.kts에서 선언한다. 루트에는 모든 서브모듈이 공유하는 최소 설정만 둔다.
allprojects {
    group = "me.songyoonseo"
    version = "1.0-SNAPSHOT"
}
