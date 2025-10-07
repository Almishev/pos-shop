package in.bushansirgur.billingsoftware.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "card.terminal")
@Data
public class CardTerminalProperties {
    private boolean simulate = true;
    private Integer timeout = 30000;
    private String defaultId = "TERMINAL_001";
    private String merchantId = "DEMO_MERCHANT";
    private String apiEndpoint = "https://api.terminal-provider.com";
    private String apiKey = "your-api-key-here";
    private String apiSecret = "your-api-secret-here";
}


