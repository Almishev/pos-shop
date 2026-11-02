package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_users")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String userId;
    private String email;
    private String password;
    private String role;
    private String name;
    /** 4-digit operator code used in the unique sale number (УНП). */
    @Column(unique = true, length = 4)
    private String operatorCode;
    private String firstName;
    private String middleName;
    private String lastName;
    private String position;
    /** Start of the current role assignment. */
    private LocalDateTime roleActiveFrom;
    /** End of the current role assignment. Empty while the role is active. */
    private LocalDateTime roleActiveTo;
    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;
    @UpdateTimestamp
    private Timestamp updatedAt;
}
