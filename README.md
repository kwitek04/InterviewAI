# InterviewAI

InterviewAI is a technical interview simulator that generates interview questions based on a candidate's CV and a job offer. It uses RAG to retrieve relevant CV fragments, allowing the interviewer to ask questions about the candidate's actual experience instead of relying on generic prompts.

The backend is implemented as a modular monolith using hexagonal architecture and Spring Modulith.

## Tech Stack

- **Java 21** · **Spring Boot 4** · **Spring Modulith**
- **LangChain4j** (Ollama chat + embedding models)
- **PostgreSQL** · **pgvector**
- **Apache Tika** (PDF text extraction)
- **AWS S3** via **LocalStack**
- **Ollama** `llama3.2:3b`, `nomic-embed-text`
- **Testcontainers** (integration tests + eval harness)
- **React 19** · **Vite**

## Architecture & Features

The application is split into feature modules (`session`, `cv`, `interview`). Modules communicate through public application services and never access each other's persistence layer directly.

**Implemented capabilities:**

- **CV upload** — PDF ingestion, storage in S3, text extraction with Tika
- **Chunking & embeddings** — text split into overlapping chunks, embedded via Ollama, stored in pgvector
- **RAG-powered interviews** — session start accepts an optional cvId; for every interview question, the system retrieves the most relevant CV fragments together with the job offer
- **Session state machine** — sealed interfaces, exhaustive transition tests, REST API for the full interview flow
- **LLM eval harness** — automated grounding evaluation on 25 synthetic CVs with the results published as a CI artifact

## Quick Start

**Prerequisites:** Docker, JDK 21, Maven 3.9+

1. **Start infrastructure:**

   ```bash
   docker compose up -d
   ```

2. **Run the backend:**

   ```bash
   mvn spring-boot:run
   ```

   API: `http://localhost:8080` · Swagger UI: `http://localhost:8080/swagger-ui.html`


3. **Run the frontend:**

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

4. **Run tests:**

   ```bash
   mvn verify
   ```

## Eval Results

The eval harness measures whether generated interview questions are grounded in the uploaded CV. A question is considered grounded if it contains at least one literal fact from the CV (case-insensitive substring match).

**Latest evaluation** (25 synthetic CVs, 2 questions each, Ollama `llama3.2:3b`):

- **Q1 grounded:** 76%
- **Q2 grounded:** 40%
- **Overall:** 58%

To reproduce locally:

```bash
mvn test -Deval=true -Dtest=QuestionGroundingEval
```

The full per-CV report is written to `target/eval-report.md`. CI uploads the same artifact as `eval-report`.
