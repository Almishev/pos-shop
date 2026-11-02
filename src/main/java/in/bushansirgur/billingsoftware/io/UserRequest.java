package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRequest {

    private String name;
    private String firstName;
    private String middleName;
    private String lastName;
    private String position;
    private String email;
    private String password;
    private String role;
    /** Optional 4-digit operator code. Assigned automatically when empty. */
    private String operatorCode;
}
