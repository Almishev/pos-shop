package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.io.BackupFileInfo;
import in.bushansirgur.billingsoftware.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> createBackup(
            @RequestParam(defaultValue = "local") String destination
    ) {
        ArchiveDestination dest = ArchiveDestination.from(destination);
        String location = backupService.createBackup(dest);
        Map<String, String> body = new HashMap<>();
        body.put("message", "Database backup created");
        body.put("location", location);
        return body;
    }

    @GetMapping
    public List<BackupFileInfo> listBackups() {
        return backupService.listLocalBackups();
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        Path file = backupService.resolveLocalBackup(filename);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
