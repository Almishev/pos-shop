package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ExcelImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ExcelImportService {
    ExcelImportResponse importProductsFromExcel(MultipartFile file) throws IOException;
}
