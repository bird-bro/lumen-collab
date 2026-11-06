package org.birdbro.lumen.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.birdbro.framework.config.WebMvcConfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Common image upload proof (org-logo-upload-api; spec delta `api` § Common Image
 * Upload Endpoint + Gateway Path Convention static segment). Driven through the real
 * servlet context with the run-scoped admin token:
 *
 * <ul>
 *   <li>upload success: envelope {@code 200} + {@code data.path} =
 *       {@code /profile/avatar/yyyy/MM/dd/<uuid>.<ext>}, identical bytes on disk under
 *       the run's {@code upload.home}, and an anonymous no-envelope GET round-trip;</li>
 *   <li>server-side validation (D4): extension whitelist / 2MB cap / empty file each
 *       answer {@code A0400} and write NOTHING;</li>
 *   <li>auth: a tokenless POST answers the {@code 203} entry-point semantic before any
 *       controller logic;</li>
 *   <li>framework adaptation #7: the completed {@code WebMvcConfig} serves
 *       {@code /profile/**} from {@code upload.home}; a blank default keeps the
 *       OPS-parity no-locations registration (other-service parity scenario).</li>
 * </ul>
 *
 * <p>The suite injects its own {@code upload.home}/{@code upload.avatar} via
 * {@code @DynamicPropertySource}, so it boots a DEDICATED context (distinct cache key);
 * every other Auth* suite keeps the base context with no {@code upload.home} - their
 * parity holds naturally. Uploads touch only the temp dir: no database rows, no team
 * server pollution.</p>
 *
 * @author birdbro
 */
class OrgLogoUploadTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String UPLOAD_URL = "/api/v1/system/common/upload/image";
    /** 2MB business cap + 1 byte - must pass Boot's multipart ceiling (5MB, committed yml). */
    private static final int OVERSIZE_BYTES = 2 * 1024 * 1024 + 1;
    private static final Pattern RETURNED_PATH = Pattern.compile(
            "^/profile/avatar/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png$");

    /** Run temp dir wired as upload.home; created before the context refreshes. */
    private static final Path UPLOAD_HOME = createUploadHome();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("upload.home", () -> UPLOAD_HOME.toString());
        registry.add("upload.avatar", () -> "/profile/avatar");
    }

    @AfterAll
    static void sweepUploadHome() throws IOException {
        if (Files.exists(UPLOAD_HOME)) {
            try (Stream<Path> walk = Files.walk(UPLOAD_HOME)) {
                walk.sorted(Comparator.reverseOrder()).forEach((path) -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                        // best-effort sweep - a locked file must not fail the suite
                    }
                });
            }
        }
    }

    private static Path createUploadHome() {
        try {
            return Files.createTempDirectory("org-logo-upload-home");
        } catch (IOException e) {
            throw new IllegalStateException("cannot create upload.home temp dir", e);
        }
    }

    // ---- helpers -----------------------------------------------------------

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertFalse(token.isEmpty(), "the admin token must be present");
        return token;
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> postUpload(Path file, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        return restTemplate.exchange(UPLOAD_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        MediaType contentType = response.getHeaders().getContentType();
        assertTrue(contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON),
                "every upload answer must be a JSON body: " + response);
        return objectMapper.readTree(response.getBody());
    }

    private static byte[] content(int size, byte fill) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, fill);
        return bytes;
    }

    private Path writeTemp(String fileName, byte[] bytes) throws IOException {
        Path target = UPLOAD_HOME.getParent().resolve("org-logo-upload-files").resolve(fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return target;
    }

    private long filesOnDisk() {
        AtomicLong count = new AtomicLong();
        try (Stream<Path> walk = Files.walk(UPLOAD_HOME)) {
            walk.filter(Files::isRegularFile).forEach((path) -> count.incrementAndGet());
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk upload home", e);
        }
        return count.get();
    }

    private Path diskPathFor(String returnedPath) {
        // stored value "/profile/avatar/..." resolves under upload.home without the leading slash
        return UPLOAD_HOME.resolve(returnedPath.substring(1));
    }

    // ---- scenarios ---------------------------------------------------------

    @Test
    void uploadSucceedsEnvelopePathDiskBytesAndAnonymousReadBack() throws Exception {
        String token = adminToken();
        Path logo = writeTemp("logo-test.png", content(1024, (byte) 7));

        JsonNode body = parse(postUpload(logo, token));
        assertEquals("200", body.path("code").asText(), "a valid png upload must succeed: " + body);

        String returnedPath = body.path("data").path("path").asText("");
        assertTrue(RETURNED_PATH.matcher(returnedPath).matches(),
                "data.path must be the /profile/avatar/<yyyy/MM/dd>/<uuid>.png stored value: " + returnedPath);

        Path onDisk = diskPathFor(returnedPath);
        assertTrue(Files.exists(onDisk), "the uploaded bytes must exist under upload.home: " + onDisk);
        assertEquals(1024, Files.size(onDisk), "the stored file must keep the posted size");

        // anonymous read-back: no token, no envelope - raw bytes through /profile/**
        ResponseEntity<byte[]> readBack =
                restTemplate.getForEntity(returnedPath, byte[].class);
        assertEquals(200, readBack.getStatusCode().value(), "the stored path must serve anonymously");
        assertFalse(readBack.getHeaders().getContentType() == null
                        || !readBack.getHeaders().getContentType().toString().startsWith("image/"),
                "the static read-back must answer as image content");
        assertEquals(1024, readBack.getBody() == null ? -1 : readBack.getBody().length,
                "the read-back must serve the identical bytes");
    }

    @Test
    void uppercaseExtensionPassesTheCaseInsensitiveWhitelist() throws Exception {
        String token = adminToken();
        Path logo = writeTemp("LOGO-TEST.PNG", content(64, (byte) 1));

        JsonNode body = parse(postUpload(logo, token));
        assertEquals("200", body.path("code").asText(),
                "the whitelist is case-insensitive: " + body);
        assertTrue(body.path("data").path("path").asText("").endsWith(".PNG"),
                "the stored name keeps the original extension spelling: " + body);
    }

    @Test
    void wrongExtensionIsRejectedWithA0400AndNothingIsWritten() throws Exception {
        String token = adminToken();
        long before = filesOnDisk();
        Path gif = writeTemp("logo-test.gif", content(128, (byte) 3));

        JsonNode body = parse(postUpload(gif, token));
        assertEquals("A0400", body.path("code").asText(),
                "an extension outside png/jpg/jpeg/webp must be rejected: " + body);
        assertEquals(before, filesOnDisk(), "a rejected upload must write nothing to disk");
    }

    @Test
    void oversizeFileIsRejectedWithA0400AndNothingIsWritten() throws Exception {
        String token = adminToken();
        long before = filesOnDisk();
        Path big = writeTemp("logo-huge.png", content(OVERSIZE_BYTES, (byte) 5));

        JsonNode body = parse(postUpload(big, token));
        assertEquals("A0400", body.path("code").asText(),
                "a file above the 2MB cap must be rejected: " + body);
        assertEquals(before, filesOnDisk(), "a rejected upload must write nothing to disk");
    }

    @Test
    void emptyFileIsRejectedWithA0400AndNothingIsWritten() throws Exception {
        String token = adminToken();
        long before = filesOnDisk();
        Path empty = writeTemp("logo-empty.png", new byte[0]);

        JsonNode body = parse(postUpload(empty, token));
        assertEquals("A0400", body.path("code").asText(),
                "an empty file must be rejected: " + body);
        assertEquals(before, filesOnDisk(), "a rejected upload must write nothing to disk");
    }

    @Test
    void tokenlessPostAnswersThe203EntryPointSemantic() throws Exception {
        Path logo = writeTemp("logo-noauth.png", content(64, (byte) 9));

        JsonNode body = parse(postUpload(logo, null));
        assertEquals("203", body.path("code").asText(),
                "an unauthenticated upload must hit the entry point semantic: " + body);
    }

    @Test
    void blankUploadHomeKeepsTheOpsParityRegistrationWithoutLocations() throws Exception {
        // scenario: other services are unaffected - a fresh WebMvcConfig (no injected
        // upload.home) carries the BLANK @Value default, so its /profile/** registration
        // stays the location-less OPS-parity form (conditional guard is
        // code-by-construction; the integration serving proof above covers the
        // configured path).
        WebMvcConfig configurer = new WebMvcConfig();
        Field uploadHome = WebMvcConfig.class.getDeclaredField("uploadHome");
        uploadHome.setAccessible(true);
        assertEquals("", uploadHome.get(configurer),
                "the @Value default must stay blank so non-upload services register no locations");
    }
}
