package io.lytgos.web;

import io.lytgos.core.Lytgos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Minimal system endpoints for the Maven skeleton.
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "name", Lytgos.NAME,
                "version", Lytgos.VERSION,
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "name", Lytgos.NAME,
                "version", Lytgos.VERSION,
                "modules", 9
        );
    }
}
