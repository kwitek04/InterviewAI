package com.interviewai.cv.adapter.out.parsing;

import com.interviewai.cv.application.CvParsingException;
import com.interviewai.cv.application.port.out.CvTextExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extracts plain text from PDF (and other Tika-supported) documents using
 * Apache Tika.
 */
@Component
class TikaCvTextExtractor implements CvTextExtractor {

    @Override
    public String extractText(byte[] pdfContent) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        try (ByteArrayInputStream input = new ByteArrayInputStream(pdfContent)) {
            parser.parse(input, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (IOException | SAXException | TikaException exception) {
            throw new CvParsingException("Failed to extract text from the uploaded document", exception);
        }
    }
}
