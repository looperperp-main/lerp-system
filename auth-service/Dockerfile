# Usa a imagem oficial do Eclipse Temurin para o Java 25
FROM eclipse-temurin:25-jre-alpine

# Cria um usuário não-root para segurança (boas práticas em produção)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Define o diretório de trabalho dentro do container
WORKDIR /app

# O TeamCity (via Maven) já vai ter gerado o .jar na etapa de CI.
# Copiamos o .jar da pasta target do auth-service para dentro da imagem.
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Expõe a porta que o Spring Boot utiliza
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "/app/app.jar"]