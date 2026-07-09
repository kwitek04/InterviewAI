package com.interviewai.cv.adapter.in.web;

import com.interviewai.cv.application.CvApplicationService;
import com.interviewai.cv.application.CvUploadResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * REST API for uploading a candidate's CV together with the job offer it is
 * being submitted against.
 */
@RestController
@RequestMapping("/api/v1/cv")
class CvController {

    private final CvApplicationService cvApplicationService;

    CvController(CvApplicationService cvApplicationService) {
        this.cvApplicationService = cvApplicationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CvUploadResponse> uploadCv(
            @RequestParam("file") MultipartFile file, @RequestParam("jobOffer") String jobOffer) {
        CvUploadResult result = cvApplicationService.uploadCv(file.getOriginalFilename(), readBytes(file), jobOffer);
        return ResponseEntity.status(HttpStatus.CREATED).body(CvUploadResponse.from(result));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read the uploaded file", exception);
        }
    }
}
