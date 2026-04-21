package com.fitcoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;

/**
 * FitCoach 后端启动类
 */
@SpringBootApplication
@EnableJpaAuditing
@OpenAPIDefinition(
    info = @Info(
        title = "FitCoach API",
        version = "3.0.0",
        description = "AI 居家健身陪练 · REST API 文档",
        contact = @Contact(name = "FitCoach Team"),
        license = @License(name = "Educational Use Only")
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "开发环境")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class FitCoachApplication {
    public static void main(String[] args) {
        SpringApplication.run(FitCoachApplication.class, args);
        System.out.println("""

            ╔═══════════════════════════════════════════════════════╗
            ║  FitCoach Backend v3.0 启动成功！                      ║
            ║                                                        ║
            ║  API 文档:    http://localhost:8080/swagger-ui.html   ║
            ║  H2 控制台:   http://localhost:8080/h2-console        ║
            ║  前端代理:    http://localhost:5173 → /api            ║
            ╚═══════════════════════════════════════════════════════╝
            """);
    }
}
