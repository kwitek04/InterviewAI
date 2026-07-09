package com.interviewai.cv.adapter.in.web;

import com.interviewai.cv.application.CvEmbeddingException;
import com.interviewai.cv.application.port.out.CvChunkStore;
import com.interviewai.cv.application.port.out.CvTextExtractor;
import com.interviewai.cv.application.port.out.EmbeddingGenerator;
import com.interviewai.cv.application.port.out.FileStorage;
import com.interviewai.cv.application.port.out.StoredFile;
import com.interviewai.cv.domain.TextChunk;
import com.interviewai.cv.domain.TextChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CvControllerIT {

    private static final byte[] VALID_PDF = "%PDF-1.4 fake cv content".getBytes(StandardCharsets.UTF_8);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private CvTextExtractor cvTextExtractor;

    @MockitoBean
    private TextChunker textChunker;

    @MockitoBean
    private EmbeddingGenerator embeddingGenerator;

    @MockitoBean
    private CvChunkStore cvChunkStore;

    @Test
    @DisplayName("POST /api/v1/cv with a valid PDF and job offer returns 201 with the cv id, character count, and chunk count")
    void uploadCv_withValidPdfAndJobOffer_returns201() throws Exception {
        when(fileStorage.store(anyString(), any(byte[].class), anyString()))
                .thenReturn(new StoredFile("cv/some-id.pdf", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn("Jane Doe, Senior Backend Engineer");
        when(textChunker.chunk("Jane Doe, Senior Backend Engineer"))
                .thenReturn(List.of(new TextChunk(0, "Jane Doe, Senior Backend Engineer")));
        when(embeddingGenerator.embedAll(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", VALID_PDF);

        mockMvc.perform(multipart("/api/v1/cv").file(file).param("jobOffer", "We are hiring a backend engineer."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("cv.pdf"))
                .andExpect(jsonPath("$.characterCount").value("Jane Doe, Senior Backend Engineer".length()))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andExpect(jsonPath("$.cvId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/cv returns 503 when embedding backend is unavailable")
    void uploadCv_whenEmbeddingBackendUnavailable_returns503() throws Exception {
        when(fileStorage.store(anyString(), any(byte[].class), anyString()))
                .thenReturn(new StoredFile("cv/some-id.pdf", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn("Jane Doe, Senior Backend Engineer");
        when(textChunker.chunk("Jane Doe, Senior Backend Engineer"))
                .thenReturn(List.of(new TextChunk(0, "Jane Doe, Senior Backend Engineer")));
        when(embeddingGenerator.embedAll(anyList()))
                .thenThrow(new CvEmbeddingException("Embedding model is unavailable in Ollama", null));

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", VALID_PDF);

        mockMvc.perform(multipart("/api/v1/cv").file(file).param("jobOffer", "We are hiring a backend engineer."))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /api/v1/cv with non-PDF bytes returns 400")
    void uploadCv_withNonPdfBytes_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/cv").file(file).param("jobOffer", "We are hiring a backend engineer."))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/cv with a blank job offer returns 400")
    void uploadCv_withBlankJobOffer_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", VALID_PDF);

        mockMvc.perform(multipart("/api/v1/cv").file(file).param("jobOffer", "   "))
                .andExpect(status().isBadRequest());
    }
}
