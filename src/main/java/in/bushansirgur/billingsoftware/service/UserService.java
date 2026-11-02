package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.UserRequest;
import in.bushansirgur.billingsoftware.io.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse createUser(UserRequest request);

    UserResponse updateUser(String userId, UserRequest request);

    String getUserRole(String email);

    /** Resolve login email from digit PIN (unique match required). */
    String findEmailByPin(String pin);

    List<UserResponse> readUsers();

    void deleteUser(String id);

    /** Returns the 4-digit operator code, assigning the next free one when missing. */
    String ensureOperatorCode(String email);
}
