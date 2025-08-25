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
    private String serialNumber; // Сериен номер на ФУ
    
    private String manufacturer; // Производител
    
    private String model; // Модел
    
    private String fiscalMemoryNumber; // Номер на фискалната памет
    
    private LocalDateTime registrationDate; // Дата на регистрация
    
    private LocalDateTime lastMaintenanceDate; // Последно обслужване
    
    private String apiEndpoint; // API endpoint за комуникация
    
    private String apiKey; // API ключ за достъп
    
    @Enumerated(EnumType.STRING)
    private DeviceStatus status; // Статус на устройството
    
    private String location; // Локация на устройството
    
    private String notes; // Бележки
    
    @PrePersist
    protected void onCreate() {
        this.registrationDate = LocalDateTime.now();
        if (this.status == null) {
            this.status = DeviceStatus.ACTIVE;
        }
    }
    
    public enum DeviceStatus {
        ACTIVE,     // Активно
        INACTIVE,   // Неактивно
        MAINTENANCE, // В обслужване
        ERROR,      // Грешка
        DISCONNECTED // Изключено
    }
}
