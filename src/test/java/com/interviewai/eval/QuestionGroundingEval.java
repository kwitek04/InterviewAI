package com.interviewai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.cv.application.CvApplicationService;
import com.interviewai.cv.application.CvUploadCommand;
import com.interviewai.cv.application.CvUploadResult;
import com.interviewai.session.application.AcceptedGeneration;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.CvId;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("eval")
@EnabledIfSystemProperty(named = "eval", matches = "true")
@SpringBootTest
@Testcontainers
class QuestionGroundingEval {

    private static final String CANNED_ANSWER =
            "I worked on that in my last role, mainly focusing on reliability.";
    private static final Path REPORT_PATH = Path.of("target", "eval-report.md");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4")).withServices("s3");

    @Container
    static final OllamaContainer OLLAMA = new OllamaContainer("ollama/ollama:0.6.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("interviewai.storage.s3.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("interviewai.storage.s3.region", LOCALSTACK::getRegion);
        registry.add("interviewai.storage.s3.access-key", LOCALSTACK::getAccessKey);
        registry.add("interviewai.storage.s3.secret-key", LOCALSTACK::getSecretKey);

        registry.add("interviewai.llm.ollama.base-url", OLLAMA::getEndpoint);
        registry.add("interviewai.llm.ollama.model-name", () -> "llama3.2:3b");
        registry.add("interviewai.llm.ollama.embedding-model-name", () -> "nomic-embed-text");
    }

    @Autowired
    private CvApplicationService cvApplicationService;

    @Autowired
    private SessionApplicationService sessionApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void pullModels() throws IOException, InterruptedException {
        OLLAMA.execInContainer("ollama", "pull", "llama3.2:3b");
        OLLAMA.execInContainer("ollama", "pull", "nomic-embed-text");
    }

    @Test
    @DisplayName("measures question grounding across synthetic CV fixtures")
    @Timeout(value = 55, unit = TimeUnit.MINUTES)
    void evaluateQuestionGrounding() throws Exception {
        List<EvalFixture> fixtures = loadFixtures();
        assertThat(fixtures).hasSize(25);

        List<EvalRow> rows = new ArrayList<>();
        int q1Grounded = 0;
        int q2Grounded = 0;

        for (EvalFixture fixture : fixtures) {
            CvUploadResult upload = cvApplicationService.uploadCv(
                    CvUploadCommand.fromPlainText(fixture.id() + ".txt", fixture.cvText(), fixture.jobOffer()));
            CvId cvId = upload.document().id();

            AcceptedGeneration firstAccepted =
                    sessionApplicationService.startInterview(Optional.of(cvId));
            InterviewSession afterFirstQuestion = awaitAwaitingAnswer(firstAccepted.sessionId());
            String question1 = latestInterviewerMessage(afterFirstQuestion);
            String q1Fact = GroundingMetric.findMatchedFact(question1, fixture.facts());
            boolean q1GroundedFlag = q1Fact != null;
            if (q1GroundedFlag) {
                q1Grounded++;
            }

            AcceptedGeneration followUpAccepted = sessionApplicationService.submitAnswer(
                    afterFirstQuestion.id(), CANNED_ANSWER);
            InterviewSession afterAnswer = awaitAwaitingAnswer(followUpAccepted.sessionId());
            String question2 = latestInterviewerMessage(afterAnswer);
            String q2Fact = GroundingMetric.findMatchedFact(question2, fixture.facts());
            boolean q2GroundedFlag = q2Fact != null;
            if (q2GroundedFlag) {
                q2Grounded++;
            }

            rows.add(new EvalRow(
                    fixture.id(),
                    fixture.role(),
                    q1GroundedFlag,
                    q1Fact,
                    q2GroundedFlag,
                    q2Fact));
        }

        int overallGrounded = q1Grounded + q2Grounded;
        int totalQuestions = fixtures.size() * 2;
        String report = buildReport(rows, q1Grounded, q2Grounded, overallGrounded, totalQuestions, fixtures.size());
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report);

        System.out.println();
        System.out.println("Q1 grounded rate: " + q1Grounded + "/" + fixtures.size() + " ("
                + percentage(q1Grounded, fixtures.size()) + "%)");
        System.out.println("Q2 grounded rate: " + q2Grounded + "/" + fixtures.size() + " ("
                + percentage(q2Grounded, fixtures.size()) + "%)");
        System.out.println("Overall grounded rate: " + overallGrounded + "/" + totalQuestions + " ("
                + percentage(overallGrounded, totalQuestions) + "%)");
        System.out.println("Report written to " + REPORT_PATH.toAbsolutePath());

        assertThat(REPORT_PATH).exists();
    }

    private List<EvalFixture> loadFixtures() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:eval/cv-*.json");
        return Arrays.stream(resources)
                .sorted(Comparator.comparing(resource -> resource.getFilename()))
                .map(this::readFixture)
                .toList();
    }

    private EvalFixture readFixture(Resource resource) {
        try {
            return objectMapper.readValue(resource.getInputStream(), EvalFixture.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read fixture " + resource.getFilename(), exception);
        }
    }

    private InterviewSession awaitAwaitingAnswer(SessionId sessionId) throws InterruptedException {
        for (int attempt = 0; attempt < 180; attempt++) {
            InterviewSession session = sessionApplicationService.getSession(sessionId);
            if (session.state() instanceof SessionState.AwaitingAnswer) {
                return session;
            }
            Thread.sleep(1_000);
        }
        throw new AssertionError("Timed out waiting for generated question for session " + sessionId.value());
    }

    private static String latestInterviewerMessage(InterviewSession session) {
        return session.transcript().messages().stream()
                .filter(message -> message.role() == MessageRole.INTERVIEWER)
                .reduce((first, second) -> second)
                .orElseThrow()
                .content();
    }

    private static String buildReport(
            List<EvalRow> rows,
            int q1Grounded,
            int q2Grounded,
            int overallGrounded,
            int totalQuestions,
            int fixtureCount) {
        StringBuilder report = new StringBuilder();
        report.append("| CV | Role | Q1 grounded | Q1 matched fact | Q2 grounded | Q2 matched fact |\n");
        report.append("| --- | --- | --- | --- | --- | --- |\n");
        for (EvalRow row : rows) {
            report.append("| ")
                    .append(row.cvId())
                    .append(" | ")
                    .append(row.role())
                    .append(" | ")
                    .append(row.q1Grounded())
                    .append(" | ")
                    .append(valueOrDash(row.q1MatchedFact()))
                    .append(" | ")
                    .append(row.q2Grounded())
                    .append(" | ")
                    .append(valueOrDash(row.q2MatchedFact()))
                    .append(" |\n");
        }
        report.append('\n');
        report.append("Q1 grounded rate: ")
                .append(q1Grounded)
                .append('/')
                .append(fixtureCount)
                .append(" (")
                .append(percentage(q1Grounded, fixtureCount))
                .append("%)\n");
        report.append("Q2 grounded rate: ")
                .append(q2Grounded)
                .append('/')
                .append(fixtureCount)
                .append(" (")
                .append(percentage(q2Grounded, fixtureCount))
                .append("%)\n");
        report.append("Overall grounded rate: ")
                .append(overallGrounded)
                .append('/')
                .append(totalQuestions)
                .append(" (")
                .append(percentage(overallGrounded, totalQuestions))
                .append("%)\n");
        return report.toString();
    }

    private static String valueOrDash(String value) {
        return value == null ? "-" : value;
    }

    private static int percentage(int grounded, int total) {
        return total == 0 ? 0 : Math.round(grounded * 100f / total);
    }

    private record EvalFixture(String id, String role, String cvText, String jobOffer, List<String> facts) {
    }

    private record EvalRow(
            String cvId,
            String role,
            boolean q1Grounded,
            String q1MatchedFact,
            boolean q2Grounded,
            String q2MatchedFact) {
    }
}
