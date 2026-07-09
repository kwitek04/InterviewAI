package com.interviewai.interview.application;

import com.interviewai.interview.application.port.out.InterviewContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPromptAssemblerTest {

    private final InterviewPromptAssembler assembler = new InterviewPromptAssembler();

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("assembles prompt sections based on available context")
    void assemble_variousContexts_buildsExpectedPrompt(
            String name,
            InterviewContext context,
            boolean containsJobOffer,
            boolean containsExcerpts) {
        String prompt = assembler.assemble(context);

        assertThat(prompt).contains("Ask exactly one clear, focused question at a time");
        assertThat(prompt).doesNotContain("assistant:");
        assertThat(prompt).doesNotContain("interviewer:");

        if (containsJobOffer) {
            assertThat(prompt).contains("Job offer:");
        } else {
            assertThat(prompt).doesNotContain("Job offer:");
        }

        if (containsExcerpts) {
            assertThat(prompt).contains("Ground your question in the candidate's CV excerpts below.");
            assertThat(prompt).contains("1. ");
        } else {
            assertThat(prompt).doesNotContain("candidate's CV excerpts");
        }
    }

    @ParameterizedTest
    @MethodSource("numberingCase")
    @DisplayName("numbers excerpts in order")
    void assemble_numbersExcerpts(InterviewContext context) {
        String prompt = assembler.assemble(context);
        assertThat(prompt).contains("1. First excerpt");
        assertThat(prompt).contains("2. Second excerpt");
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("empty context", InterviewContext.empty(), false, false),
                Arguments.of("job offer only", new InterviewContext("Spring Boot role", List.of()), true, false),
                Arguments.of("excerpts only", new InterviewContext(null, List.of("Used Kafka in Allegro")), false, true),
                Arguments.of(
                        "job offer and excerpts",
                        new InterviewContext("Backend role", List.of("Led team of 6")),
                        true,
                        true));
    }

    static Stream<Arguments> numberingCase() {
        return Stream.of(Arguments.of(new InterviewContext(null, List.of("First excerpt", "Second excerpt"))));
    }
}
