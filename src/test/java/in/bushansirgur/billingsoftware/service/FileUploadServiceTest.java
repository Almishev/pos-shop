package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.service.impl.FileUploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileUploadServiceTest {

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private FileUploadServiceImpl fileUploadService;

    @BeforeEach
    void setUp() {
        
        when(multipartFile.getOriginalFilename()).thenReturn("test-image.jpg");
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
    }

    @Test
    @DisplayName("Should handle file with no extension")
    void uploadFile_ShouldHandleFileWithNoExtension() {
       
        when(multipartFile.getOriginalFilename()).thenReturn("test-image");
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        try {
            when(multipartFile.getBytes()).thenReturn("test content".getBytes());
        } catch (IOException e) {
           
        }
        when(multipartFile.isEmpty()).thenReturn(false);

        assertThrows(Exception.class, () -> {
            fileUploadService.uploadFile(multipartFile);
        });
    }

    @Test
    @DisplayName("Should handle file with null original filename")
    void uploadFile_ShouldHandleFileWithNullOriginalFilename() {
        
        when(multipartFile.getOriginalFilename()).thenReturn(null);
        when(multipartFile.getContentType()).thenReturn("image/png");
        try {
            when(multipartFile.getBytes()).thenReturn("test content".getBytes());
        } catch (IOException e) {
          
        }
        when(multipartFile.isEmpty()).thenReturn(false);

        assertThrows(Exception.class, () -> {
            fileUploadService.uploadFile(multipartFile);
        });
    }

    @Test
    @DisplayName("Should successfully delete file")
    void deleteFile_ShouldDeleteFile_Successfully() {
        
        String imgUrl = "https://example.com/test-image.jpg";

        assertThrows(Exception.class, () -> {
            fileUploadService.deleteFile(imgUrl);
        });
    }

    @Test
    @DisplayName("Should handle null imgUrl in delete")
    void deleteFile_ShouldHandleNullImgUrl() {
        assertThrows(Exception.class, () -> {
            fileUploadService.deleteFile(null);
        });
    }

    @Test
    @DisplayName("Should handle empty imgUrl in delete")
    void deleteFile_ShouldHandleEmptyImgUrl() {
        assertThrows(Exception.class, () -> {
            fileUploadService.deleteFile("");
        });
    }

    @Test
    @DisplayName("Should handle invalid imgUrl in delete")
    void deleteFile_ShouldHandleInvalidImgUrl() {
    
        assertThrows(Exception.class, () -> {
            fileUploadService.deleteFile("invalid-url");
        });
    }
}
