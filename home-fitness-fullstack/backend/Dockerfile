# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# 阿里云镜像加速（中国大陆）
RUN mkdir -p /root/.m2 && cat > /root/.m2/settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>*,!central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
    <mirror>
      <id>aliyun-central</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
  </mirrors>
</settings>
EOF

# 先拉依赖，命中缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再编译
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
