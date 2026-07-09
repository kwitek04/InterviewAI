package com.interviewai.interview.application;

import com.interviewai.interview.application.port.out.InterviewContext;

/**
 * Builds the system prompt for interviewer question generation.
 */
public class InterviewPromptAssembler {

    private static final String BASE_PROMPT = """
            You are an expert technical interviewer conducting a live interview with a candidate.
            Ask exactly one clear, focused question at a time based on the conversation so far.
            Do not answer on the candidate's behalf and do not repeat a question already asked.
            Respond with the question text only. Do not prefix your response with role labels
            such as "assistant" or "interviewer", and do not add commentary or formatting.""";

    public String assemble(InterviewContext context) {
        StringBuilder prompt = new StringBuilder(BASE_PROMPT);

        if (context.jobOffer() != null && !context.jobOffer().isBlank()) {
            prompt.append("\n\nJob offer:\n")
                    .append(context.jobOffer().trim())
                    .append("\n\nProbe requirements from the job offer when relevant.");
        }

        if (!context.cvExcerpts().isEmpty()) {
            prompt.append("\n\nGround your question in the candidate's CV excerpts below. ")
                    .append("Reference a specific project, technology, employer, or accomplishment from them.\n");
            for (int index = 0; index < context.cvExcerpts().size(); index++) {
                prompt.append(index + 1).append(". ").append(context.cvExcerpts().get(index)).append('\n');
            }
        }

        return prompt.toString().trim();
    }
}
