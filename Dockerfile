# 서비스 이미지 하나를 만든다. 8개 서비스가 이 파일 하나를 같이 쓰고 MODULE로 고른다
# docker build --build-arg MODULE=content -t recu-content .
# MODULE: gateway, discovery, config, user, content, collector, guide, bookmark
# 이미지 이름은 recu-<모듈> (docs/design.md 10장). 바탕은 원본 루트 Dockerfile(Railway 배포용)

# 1단계: 빌드. 서비스가 common 모듈을 쓰므로 저장소 전체를 넣는다(.dockerignore로 거름)
FROM eclipse-temurin:17-jdk AS build
ARG MODULE
RUN test -n "$MODULE" || (echo "MODULE을 지정하세요. 예) --build-arg MODULE=content" && exit 1)
WORKDIR /app
COPY . .
# 테스트는 CI가 돌린다. Gradle 캐시는 빌드 사이에 재사용한다
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :${MODULE}:bootJar -x test \
    && cp ${MODULE}/build/libs/${MODULE}-*.jar app.jar

# 2단계: 실행. 빌드 도구 없이 jar 하나만 담는다
# 기본 이미지에 curl이 있어 compose 헬스체크에서 /actuator/health를 부를 수 있다
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# 한글 깨짐 방지, 한국 시간, 힙 상한(design.md 10장: 256m~384m). compose에서 JAVA_TOOL_OPTIONS로 덮어쓴다
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul -Xmx384m"
# 포트는 서비스마다 달라서(application.yml) 여기 적지 않고 compose에서 연다
ENTRYPOINT ["java", "-jar", "app.jar"]
