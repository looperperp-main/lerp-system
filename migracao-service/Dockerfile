FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=builder --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/application/ ./
# Diretório de exports criado ANTES do USER: um volume nomeado herda dono/permissão daqui no
# primeiro mount; sem o chown o processo (spring) não consegue gravar (AccessDeniedException).
RUN mkdir -p /var/syax/exports && chown spring:spring /var/syax/exports
USER spring:spring
EXPOSE 8094
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
