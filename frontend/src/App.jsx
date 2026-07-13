import { useCallback, useState } from 'react';
import StartScreen from './components/StartScreen.jsx';
import ChatScreen from './components/ChatScreen.jsx';
import { uploadCv, startInterview, submitAnswer, ApiError } from './api/interviewApi.js';
import './App.css';

let messageIdSeq = 0;
function nextMessageId() {
  messageIdSeq += 1;
  return messageIdSeq;
}

function toErrorMessage(error) {
  return error instanceof ApiError
    ? error.message
    : 'Could not connect to the server. Make sure the backend is running on port 8080.';
}

function matchesResponseId(entryResponseId, activeResponseId) {
  return String(entryResponseId) === String(activeResponseId);
}

function App() {
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [isStarting, setIsStarting] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isReconnecting, setIsReconnecting] = useState(false);
  const [streamTarget, setStreamTarget] = useState(null);
  const [error, setError] = useState(null);

  const handleToken = useCallback((responseId, text) => {
    setIsReconnecting(false);
    setMessages((prev) =>
      prev.map((entry) =>
        matchesResponseId(entry.responseId, responseId)
          ? { ...entry, content: entry.content + text }
          : entry,
      ),
    );
  }, []);

  const handleCompleted = useCallback((responseId, question) => {
    setStreamTarget(null);
    setIsStreaming(false);
    setIsReconnecting(false);
    setMessages((prev) =>
      prev.map((entry) =>
        matchesResponseId(entry.responseId, responseId)
          ? { ...entry, content: question, isStreaming: false }
          : entry,
      ),
    );
  }, []);

  const handleServerError = useCallback((_responseId, message) => {
    setStreamTarget(null);
    setIsStreaming(false);
    setIsReconnecting(false);
    setMessages((prev) =>
      prev.map((entry) => (entry.isStreaming ? { ...entry, isStreaming: false } : entry)),
    );
    setError(message);
  }, []);

  const handleReconnecting = useCallback(() => {
    setIsReconnecting(true);
  }, []);

  const handleStart = useCallback(async ({ file, jobOffer }) => {
    setIsStarting(true);
    setError(null);
    try {
      const cvResponse = await uploadCv(file, jobOffer);
      const sessionResponse = await startInterview(cvResponse.cvId);
      setSessionId(sessionResponse.sessionId);
      setIsStreaming(true);
      setIsReconnecting(false);
      setMessages([
        {
          id: sessionResponse.responseId,
          responseId: sessionResponse.responseId,
          role: 'INTERVIEWER',
          content: '',
          isStreaming: true,
        },
      ]);
      setStreamTarget({
        responseId: sessionResponse.responseId,
        eventsUrl: sessionResponse.eventsUrl,
      });
    } catch (err) {
      setStreamTarget(null);
      setIsStreaming(false);
      setError(toErrorMessage(err));
    } finally {
      setIsStarting(false);
    }
  }, []);

  const handleSendAnswer = useCallback(
    async (answerText) => {
      if (!sessionId || isStreaming) return;

      setMessages((prev) => [...prev, { id: nextMessageId(), role: 'CANDIDATE', content: answerText }]);
      setIsStreaming(true);
      setIsReconnecting(false);
      setError(null);

      try {
        const response = await submitAnswer(sessionId, answerText);
        setMessages((prev) => [
          ...prev,
          {
            id: response.responseId,
            responseId: response.responseId,
            role: 'INTERVIEWER',
            content: '',
            isStreaming: true,
          },
        ]);
        setStreamTarget({
          responseId: response.responseId,
          eventsUrl: response.eventsUrl,
        });
      } catch (err) {
        setStreamTarget(null);
        setIsStreaming(false);
        setIsReconnecting(false);
        setError(toErrorMessage(err));
      }
    },
    [sessionId, isStreaming],
  );

  const handleRestart = useCallback(() => {
    setStreamTarget(null);
    setSessionId(null);
    setMessages([]);
    setError(null);
    setIsStarting(false);
    setIsStreaming(false);
    setIsReconnecting(false);
  }, []);

  if (!sessionId) {
    return <StartScreen onStart={handleStart} isLoading={isStarting} error={error} />;
  }

  return (
    <ChatScreen
      messages={messages}
      streamTarget={streamTarget}
      isStreaming={isStreaming}
      isReconnecting={isReconnecting}
      error={error}
      onSendAnswer={handleSendAnswer}
      onRestart={handleRestart}
      onToken={handleToken}
      onCompleted={handleCompleted}
      onServerError={handleServerError}
      onReconnecting={handleReconnecting}
    />
  );
}

export default App;
