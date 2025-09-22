package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_fiscal_devices")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FiscalDeviceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String serialNumber;
    
    private String manufacturer;
    
    private String model;
    
    private String fiscalMemoryNumber; 
    
    private LocalDateTime registrationDate; 
    
    private LocalDateTime lastMaintenanceDate; 
    
    private String apiEndpoint; 
    
    private String apiKey; 
    
    @Enumerated(EnumType.STRING)
    private DeviceStatus status; 
    
    private String location; 
    
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        this.registrationDate = LocalDateTime.now();
        if (this.status == null) {
            this.status = DeviceStatus.ACTIVE;
        }
    }
    
    public enum DeviceStatus {
        ACTIVE,     
        INACTIVE,   
        MAINTENANCE, 
        ERROR,      
        DISCONNECTED
    }
}
