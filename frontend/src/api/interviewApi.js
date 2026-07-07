const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function extractErrorMessage(response) {
  try {
    // The backend returns RFC 7807 ProblemDetail bodies (400/404/409).
    const problem = await response.json();
    return problem.detail || problem.title || `Request failed (status ${response.status}).`;
  } catch {
    return `Request failed (status ${response.status}).`;
  }
}

async function handleResponse(response) {
  if (!response.ok) {
    throw new ApiError(await extractErrorMessage(response), response.status);
  }
  return response.json();
}

/**
 * Starts a new interview session and returns { sessionId, question }.
 */
export function startInterview() {
  return fetch(`${BASE_URL}/api/v1/sessions`, { method: 'POST' }).then(handleResponse);
}

/**
 * Submits the candidate's answer and returns the next { sessionId, question }.
 */
export function submitAnswer(sessionId, answer) {
  return fetch(`${BASE_URL}/api/v1/sessions/${sessionId}/answers`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer }),
  }).then(handleResponse);
}

/**
 * Fetches the full session state and transcript.
 */
export function fetchSession(sessionId) {
  return fetch(`${BASE_URL}/api/v1/sessions/${sessionId}`).then(handleResponse);
}
