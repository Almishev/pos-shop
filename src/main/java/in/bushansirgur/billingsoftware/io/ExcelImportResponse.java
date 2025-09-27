package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExcelImportResponse {
    private int totalRows;
    private int successfulImports;
    private int failedImports;
    private List<String> errors;
    private List<String> warnings;
    private String message;
}
