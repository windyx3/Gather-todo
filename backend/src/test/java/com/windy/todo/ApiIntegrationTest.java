package com.windy.todo;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
public class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtEncoder encoder;
    @Test void consoleIsNotPublicOutsideLocalProfile() throws Exception {
        mvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
        mvc.perform(post("/h2-console/login.do")).andExpect(status().isForbidden());
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor csrf() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.token");
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return request -> {
            var cookies = new ArrayList<Cookie>();
            if (request.getCookies() != null) cookies.addAll(Arrays.asList(request.getCookies()));
            cookies.add(cookie); request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token); return request;
        };
    }
    record Account(String token, Cookie refresh, String email) {}
    Account register() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var response = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"CorrectHorse123!\",\"displayName\":\"Windy\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn().getResponse();
        assertThat(response.getCookie("refresh_token").isHttpOnly()).isTrue();
        return new Account(JsonPath.read(response.getContentAsString(), "$.accessToken"), response.getCookie("refresh_token"), email);
    }
    long project(Account a) throws Exception {
        var response = mvc.perform(post("/api/projects").with(csrf()).header("Authorization", "Bearer " + a.token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Interview prep\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse();
        return ((Number)JsonPath.read(response.getContentAsString(), "$.id")).longValue();
    }
    String body(String title, boolean completed) {
        return "{\"title\":\"" + title + "\",\"description\":\"Learn Spring\",\"priority\":\"HIGH\",\"dueDate\":\"2026-12-01\",\"completed\":" + completed + "}";
    }
    long task(Account a, long project, String title) throws Exception {
        var response = mvc.perform(post("/api/projects/" + project + "/tasks").with(csrf()).header("Authorization", "Bearer " + a.token)
                .contentType(MediaType.APPLICATION_JSON).content(body(title, false)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return ((Number)JsonPath.read(response.getContentAsString(), "$.id")).longValue();
    }
    @Test void authenticationAndProfile() throws Exception {
        var a = register();
        assertThat(users.findByEmail(a.email).orElseThrow().passwordHash).startsWith("$2").doesNotContain("CorrectHorse");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email.toUpperCase(Locale.ROOT) + "\",\"password\":\"CorrectHorse123!\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email + "\",\"password\":\"WrongPassword\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me").with(csrf()).header("Authorization", "Bearer " + a.token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("New name"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + a.token)).andExpect(jsonPath("$.displayName").value("New name"));
    }
    @Test void duplicateRegistrationAndValidation() throws Exception {
        var a = register();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email + "\",\"password\":\"CorrectHorse123!\",\"displayName\":\"Again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
    }
    @Test void crudFiltersAndProjectConflict() throws Exception {
        var a = register(); long p = project(a); long t = task(a, p, "Learn JWT"); task(a, p, "Learn SQL");
        mvc.perform(get("/api/projects/" + p + "/tasks?q=jwt&priority=HIGH&completed=false&size=1").header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].title").value("Learn JWT"));
        mvc.perform(get("/api/tasks?size=1&page=1").header("Authorization", "Bearer " + a.token))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(put("/api/tasks/" + t).with(csrf()).header("Authorization", "Bearer " + a.token)
                .contentType(MediaType.APPLICATION_JSON).content(body("JWT done", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        mvc.perform(delete("/api/projects/" + p).with(csrf()).header("Authorization", "Bearer " + a.token)).andExpect(status().isConflict());
        mvc.perform(delete("/api/tasks/" + t).with(csrf()).header("Authorization", "Bearer " + a.token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/" + t).header("Authorization", "Bearer " + a.token)).andExpect(status().isNotFound());
        mvc.perform(put("/api/projects/" + p).with(csrf()).header("Authorization", "Bearer " + a.token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}")).andExpect(jsonPath("$.name").value("Renamed"));
        long empty = project(a);
        mvc.perform(delete("/api/projects/" + empty).with(csrf()).header("Authorization", "Bearer " + a.token)).andExpect(status().isNoContent());
    }
    @Test void usersCannotAccessEachOthersResources() throws Exception {
        var a = register(); var b = register(); long p = project(a); long t = task(a, p, "Private");
        for (String path : List.of("/api/tasks/" + t, "/api/projects/" + p + "/tasks"))
            mvc.perform(get(path).header("Authorization", "Bearer " + b.token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + b.token)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + b.token)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/tasks/" + t).with(csrf()).header("Authorization", "Bearer " + b.token)
                .contentType(MediaType.APPLICATION_JSON).content(body("Hacked", false))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/" + t).with(csrf()).header("Authorization", "Bearer " + b.token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects/" + p + "/tasks").with(csrf()).header("Authorization", "Bearer " + b.token)
                .contentType(MediaType.APPLICATION_JSON).content(body("Hacked", false))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/projects/" + p).with(csrf()).header("Authorization", "Bearer " + b.token)).andExpect(status().isNotFound());
    }
    @Test void validationAndSortAllowlist() throws Exception {
        var a = register(); long p = project(a);
        mvc.perform(post("/api/projects/" + p + "/tasks").with(csrf()).header("Authorization", "Bearer " + a.token)
                .contentType(MediaType.APPLICATION_JSON).content(body(" ", false))).andExpect(status().isBadRequest());
        for (String path : List.of("/api/tasks?size=101", "/api/tasks?page=-1", "/api/tasks?sort=passwordHash", "/api/tasks?direction=oops", "/api/tasks/abc"))
            mvc.perform(get(path).header("Authorization", "Bearer " + a.token)).andExpect(status().isBadRequest());
        task(a, p, "100% done");
        mvc.perform(get("/api/tasks").param("q", "%").header("Authorization", "Bearer " + a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void refreshRotatesAndReplayRevokesFamily() throws Exception {
        var a = register();
        var rotated = mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(a.refresh))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
        assertThat(rotated.getValue()).isNotEqualTo(a.refresh.getValue());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(a.refresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(rotated)).andExpect(status().isUnauthorized());
    }
    @Test void logoutRevokesRefreshButAccessHasBoundedLifetime() throws Exception {
        var a = register();
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(a.refresh)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(a.refresh)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + a.token)).andExpect(status().isOk());
    }
    @Test void realCsrfCookieRequired() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String token = JsonPath.read(result.getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/logout").cookie(result.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").cookie(result.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
    }
    @Test void invalidExpiredIssuerAndAudienceTokensRejected() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer broken")).andExpect(status().isUnauthorized());
        for (int variant=0; variant<3; variant++) {
            var now = Instant.now();
            var claims = JwtClaimsSet.builder().subject("1").issuedAt(now.minusSeconds(1000))
                    .expiresAt(variant==0 ? now.minusSeconds(120) : now.plusSeconds(600))
                    .issuer(variant==1 ? "wrong" : "todo-api").audience(List.of(variant==2 ? "wrong" : "todo-web")).build();
            String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        }
    }
}
