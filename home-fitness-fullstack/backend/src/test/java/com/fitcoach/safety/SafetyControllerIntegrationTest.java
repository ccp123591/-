package com.fitcoach.safety;

import com.fitcoach.infra.notify.MailSender;
import com.fitcoach.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "management.health.redis.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:fitcoach-safety;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class SafetyControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean MailSender mailSender;

    private String bearer;

    @BeforeEach
    void setup() {
        bearer = "Bearer " + jwtUtil.generateAccessToken(88L, "safety-test", "USER");
        when(mailSender.name()).thenReturn("smtp-mail");
    }

    @Test
    void fallAlert_requires_authentication() throws Exception {
        mvc.perform(post("/api/safety/fall-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"family@example.com\"}"))
                .andExpect(status().isUnauthorized());

        verify(mailSender, never()).send(eq("family@example.com"), contains("安全告警"), contains("疑似跌倒"));
    }

    @Test
    void fallAlert_rejects_invalid_email() throws Exception {
        mvc.perform(post("/api/safety/fall-alert")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verify(mailSender, never()).send(eq("not-an-email"), contains("安全告警"), contains("疑似跌倒"));
    }

    @Test
    void fallAlert_sends_email_for_authenticated_user() throws Exception {
        mvc.perform(post("/api/safety/fall-alert")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"family@example.com\",\"note\":\"动作：深蹲\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.notified").value(true));

        verify(mailSender).send(eq("family@example.com"), contains("安全告警"), contains("动作：深蹲"));
    }
}
