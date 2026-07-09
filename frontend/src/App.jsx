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

function App() {
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleStart = useCallback(async ({ file, jobOffer }) => {
    setIsLoading(true);
    setError(null);
    try {
      const cvResponse = await uploadCv(file, jobOffer);
      const sessionResponse = await startInterview(cvResponse.cvId);
      setSessionId(sessionResponse.sessionId);
      setMessages([
        { id: nextMessageId(), role: 'INTERVIEWER', content: sessionResponse.question },
      ]);
    } catch (err) {
      setError(toErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const handleSendAnswer = useCallback(
    async (answerText) => {
      if (!sessionId || isLoading) return;

      setMessages((prev) => [...prev, { id: nextMessageId(), role: 'CANDIDATE', content: answerText }]);
      setIsLoading(true);
      setError(null);

      try {
        const response = await submitAnswer(sessionId, answerText);
        setMessages((prev) => [
          ...prev,
          { id: nextMessageId(), role: 'INTERVIEWER', content: response.question },
        ]);
      } catch (err) {
        setError(toErrorMessage(err));
      } finally {
        setIsLoading(false);
      }
    },
    [sessionId, isLoading],
  );

  const handleRestart = useCallback(() => {
    setSessionId(null);
    setMessages([]);
    setError(null);
    setIsLoading(false);
  }, []);

  if (!sessionId) {
    return <StartScreen onStart={handleStart} isLoading={isLoading} error={error} />;
  }

  return (
    <ChatScreen
      messages={messages}
      isLoading={isLoading}
      error={error}
      onSendAnswer={handleSendAnswer}
      onRestart={handleRestart}
    />
  );
}

export default App;
