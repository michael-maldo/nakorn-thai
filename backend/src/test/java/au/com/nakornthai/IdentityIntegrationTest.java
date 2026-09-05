package au.com.nakornthai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class IdentityIntegrationTest {
    static final String SECRET=Base64.getEncoder().encodeToString("identity-test-key-32-bytes-long!!x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));
        p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
        p.add("JWT_SECRET_BASE64",()->SECRET);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    String admin,foh,boh;
    UUID adminId,fohId;
    String password="test-staff-password";
    @BeforeEach void accounts() {
        adminId=UUID.randomUUID();fohId=UUID.randomUUID();
        admin="admin-"+adminId;foh="foh-"+fohId;boh="boh-"+UUID.randomUUID();
        insert(adminId,admin,"ADMIN");insert(fohId,foh,"FOH");insert(UUID.randomUUID(),boh,"BOH");
    }
    void insert(UUID id,String username,String role) {
        jdbc.update("INSERT INTO staff_user(id,username,password_hash,role,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id,username,new BCryptPasswordEncoder(4).encode(password),role);
    }
    record Login(String access,MockCookie refresh) {}
    Login login(String username) throws Exception {
        var result=mvc.perform(post("/api/identity/login").with(csrf()).contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        var cookie=MockCookie.parse(result.getResponse().getHeader("Set-Cookie"));
        assertTrue(cookie.isHttpOnly());assertEquals("/api/identity",cookie.getPath());assertEquals("Strict",cookie.getSameSite());
        return new Login(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.accessToken"),cookie);
    }
    @Test void loginRolesAndBasicRemoval() throws Exception {
        var front=login(foh);
        mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+front.access())).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("FOH"));
        mvc.perform(get("/api/staff/foh/orders").header("Authorization","Bearer "+front.access())).andExpect(status().isOk());
        mvc.perform(get("/api/staff/menu/items").header("Authorization","Bearer "+front.access())).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/foh/orders").with(httpBasic(foh,password))).andExpect(status().isUnauthorized());
        var kitchen=login(boh);
        mvc.perform(get("/api/staff/kitchen/orders").header("Authorization","Bearer "+kitchen.access())).andExpect(status().isOk());
        mvc.perform(get("/api/staff/foh/orders").header("Authorization","Bearer "+kitchen.access())).andExpect(status().isForbidden());
    }
    @Test void refreshRotatesAndLogoutRevokesAccess() throws Exception {
        var first=login(admin);
        var result=mvc.perform(post("/api/identity/refresh").with(csrf()).cookie(first.refresh())).andExpect(status().isOk()).andReturn();
        var rotated=MockCookie.parse(result.getResponse().getHeader("Set-Cookie"));
        assertNotEquals(first.refresh().getValue(),rotated.getValue());
        String access=com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.accessToken");
        mvc.perform(post("/api/identity/logout").with(csrf()).cookie(rotated)).andExpect(status().isNoContent());
        mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/identity/refresh").with(csrf()).cookie(rotated)).andExpect(status().isUnauthorized());
    }
    @Test void reuseOfRotatedRefreshRevokesSession() throws Exception {
        var first=login(admin);
        mvc.perform(post("/api/identity/refresh").with(csrf()).cookie(first.refresh())).andExpect(status().isOk());
        mvc.perform(post("/api/identity/refresh").with(csrf()).cookie(first.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+first.access())).andExpect(status().isUnauthorized());
    }
    @Test void badPasswordsMissingCsrfExpiredAndForgedTokensFail() throws Exception {
        mvc.perform(post("/api/identity/login").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/identity/login").with(csrf()).contentType("application/json").content("{\"username\":\""+admin+"\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        var logged=login(admin);
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        var claims=io.jsonwebtoken.Jwts.parser().verifyWith(key).build().parseSignedClaims(logged.access()).getPayload();
        String expired=io.jsonwebtoken.Jwts.builder().issuer("nakorn-thai").subject(adminId.toString()).claim("sid",claims.get("sid"))
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(key,io.jsonwebtoken.Jwts.SIG.HS256).compact();
        mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        String forged=io.jsonwebtoken.Jwts.builder().issuer("nakorn-thai").subject(adminId.toString()).claim("sid",claims.get("sid"))
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(io.jsonwebtoken.Jwts.SIG.HS256.key().build()).compact();
        mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+forged)).andExpect(status().isUnauthorized());
    }
    @Test void adminCanCreateDisableAndResetAccountsAndCannotDisableLastAdmin() throws Exception {
        var root=login(admin);var front=login(foh);
        String auth="Bearer "+root.access();
        mvc.perform(post("/api/identity/users").header("Authorization",auth).with(csrf()).contentType("application/json")
                .content("{\"username\":\"new-"+UUID.randomUUID()+"\",\"role\":\"BOH\",\"password\":\"a-long-new-password\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("BOH")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(put("/api/identity/users/"+fohId).header("Authorization",auth).with(csrf()).contentType("application/json")
                .content("{\"version\":0,\"role\":\"FOH\",\"enabled\":false,\"password\":\"updated-staff-password\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+front.access())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/identity/refresh").cookie(front.refresh()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/identity/users/"+adminId).header("Authorization",auth).with(csrf()).contentType("application/json")
                .content("{\"version\":0,\"role\":\"ADMIN\",\"enabled\":false,\"password\":\"\"}"))
                .andExpect(status().isConflict());
    }
}
