package com.example.demo.controller.audio;

import com.example.demo.service.audio.AudioTranscriptionService;
import com.example.demo.service.audio.TextToSpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Slf4j
public class AudioController {

    private final TextToSpeechService textToSpeechService;
    private final AudioTranscriptionService transcriptionService;

    @PostMapping(value = "/speak", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> generateSpeech(@RequestBody Map<String, String> payload) {
        // phần text-to-speech của bạn ở đây (nếu đã có)
        return ResponseEntity.ok(new byte[0]);
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribeAudio(@RequestParam("file") MultipartFile audioFile) {
        if (audioFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Audio file is empty."));
        }

        try {
            String transcribedText = transcriptionService.transcribe(audioFile);
            return ResponseEntity.ok(Map.of("text", transcribedText));
        } catch (Exception e) {
            log.error("Error transcribing audio: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to transcribe audio."));
        }
    }
}
