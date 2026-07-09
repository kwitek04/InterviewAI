package com.interviewai.cv.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted CV text into fixed-size, overlapping chunks suitable for
 * embedding. Pure domain logic — no framework dependencies.
 */
public final class TextChunker {

    static final int MAX_CHUNK_CHARS = 1500;
    static final int MAX_OVERLAP_CHARS = 300;

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = normalize(text);
        List<String> rawChunks = packParagraphs(splitParagraphs(normalized));
        List<String> overlapped = applyOverlap(rawChunks);
        return toIndexedChunks(overlapped);
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
    }

    private List<String> splitParagraphs(String text) {
        return List.of(text.split("\n\n", -1));
    }

    private List<String> packParagraphs(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            if (trimmed.length() > MAX_CHUNK_CHARS) {
                flushCurrent(current, chunks);
                chunks.addAll(packPieces(splitIntoPieces(trimmed)));
            } else {
                appendWithParagraphSeparator(current, trimmed, chunks);
            }
        }

        flushCurrent(current, chunks);
        return chunks;
    }

    private List<String> packPieces(List<String> pieces) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String piece : pieces) {
            String trimmed = piece.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            if (trimmed.length() > MAX_CHUNK_CHARS) {
                flushCurrent(current, chunks);
                chunks.addAll(hardSplit(trimmed));
            } else {
                appendWithSentenceSeparator(current, trimmed, chunks);
            }
        }

        flushCurrent(current, chunks);
        return chunks;
    }

    private void appendWithParagraphSeparator(StringBuilder current, String piece, List<String> chunks) {
        if (current.isEmpty()) {
            current.append(piece);
            return;
        }
        if (current.length() + piece.length() <= MAX_CHUNK_CHARS) {
            current.append("\n\n").append(piece);
            return;
        }
        flushCurrent(current, chunks);
        current.append(piece);
    }

    private void appendWithSentenceSeparator(StringBuilder current, String piece, List<String> chunks) {
        if (current.isEmpty()) {
            current.append(piece);
            return;
        }
        if (current.length() + piece.length() <= MAX_CHUNK_CHARS) {
            current.append(" ").append(piece);
            return;
        }
        flushCurrent(current, chunks);
        current.append(piece);
    }

    private void flushCurrent(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private List<String> splitIntoPieces(String paragraph) {
        List<String> pieces = new ArrayList<>();
        for (String sentence : splitSentences(paragraph)) {
            if (sentence.length() > MAX_CHUNK_CHARS) {
                pieces.addAll(hardSplit(sentence));
            } else {
                pieces.add(sentence);
            }
        }
        return pieces;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < text.length() - 1; index++) {
            char character = text.charAt(index);
            if (isSentenceBoundary(character, text.charAt(index + 1))) {
                sentences.add(text.substring(start, index + 1));
                start = index + 2;
                index++;
            }
        }
        if (start < text.length()) {
            sentences.add(text.substring(start));
        }
        return sentences;
    }

    private boolean isSentenceBoundary(char punctuation, char next) {
        return (punctuation == '.' || punctuation == '!' || punctuation == '?') && next == ' ';
    }

    private List<String> hardSplit(String text) {
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < text.length(); start += MAX_CHUNK_CHARS) {
            pieces.add(text.substring(start, Math.min(start + MAX_CHUNK_CHARS, text.length())));
        }
        return pieces;
    }

    private List<String> applyOverlap(List<String> rawChunks) {
        if (rawChunks.isEmpty()) {
            return List.of();
        }

        List<String> overlapped = new ArrayList<>();
        overlapped.add(rawChunks.getFirst());

        for (int index = 1; index < rawChunks.size(); index++) {
            String previousChunk = rawChunks.get(index - 1);
            String currentChunk = rawChunks.get(index);
            String overlap = lastFullSentence(previousChunk);

            if (!overlap.isBlank() && overlap.length() <= MAX_OVERLAP_CHARS) {
                overlapped.add(overlap + " " + currentChunk);
            } else {
                overlapped.add(currentChunk);
            }
        }
        return overlapped;
    }

    private String lastFullSentence(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return text.trim();
        }
        return sentences.getLast().trim();
    }

    private List<TextChunk> toIndexedChunks(List<String> chunks) {
        List<TextChunk> result = new ArrayList<>();
        int index = 0;
        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (!trimmed.isBlank()) {
                result.add(new TextChunk(index++, trimmed));
            }
        }
        return result;
    }
}
