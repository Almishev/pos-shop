package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseStatusResponse {
    private boolean enabled;
    private boolean active;
    private String status;
    private String shopId;
    private String shopName;
    private String validUntil;
    private String reason;
    /** remote | cache | disabled */
    private String source;
    private String message;
}
