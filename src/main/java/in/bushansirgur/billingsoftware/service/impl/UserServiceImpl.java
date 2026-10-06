package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.UserEntity;
import in.bushansirgur.billingsoftware.io.UserRequest;
import in.bushansirgur.billingsoftware.io.UserResponse;
import in.bushansirgur.billingsoftware.repository.UserRepository;
import in.bushansirgur.billingsoftware.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(UserRequest request) {
        String rawPassword = request.getPassword() == null ? "" : request.getPassword().trim();
        if (!rawPassword.matches("\\d{4,12}")) {
            throw new IllegalArgumentException("Password must be 4–12 digits only");
        }
        boolean pinTaken = userRepository.findAll().stream()
                .anyMatch(u -> passwordEncoder.matches(rawPassword, u.getPassword()));
        if (pinTaken) {
            throw new IllegalArgumentException("This PIN is already used by another user");
        }
        UserEntity newUser = convertToEntity(request);
        newUser = userRepository.save(newUser);
        return convertToResponse(newUser);
    }

    @Override
    public String findEmailByPin(String pin) {
        String rawPin = pin == null ? "" : pin.trim();
        if (!rawPin.matches("\\d{4,12}")) {
            throw new IllegalArgumentException("PIN must be 4–12 digits");
        }
        List<UserEntity> matches = userRepository.findAll().stream()
                .filter(u -> passwordEncoder.matches(rawPin, u.getPassword()))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            throw new UsernameNotFoundException("No user for PIN");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("PIN matches multiple users");
        }
        return matches.get(0).getEmail();
    }

    private UserResponse convertToResponse(UserEntity newUser) {
        return UserResponse.builder()
                .name(newUser.getName())
                .email(newUser.getEmail())
                .userId(newUser.getUserId())
                .createdAt(newUser.getCreatedAt())
                .updatedAt(newUser.getUpdatedAt())
                .role(newUser.getRole())
                .build();
    }

    private UserEntity convertToEntity(UserRequest request) {
        return UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole().toUpperCase())
                .name(request.getName())
                .build();
    }

    @Override
    public String getUserRole(String email) {
        UserEntity existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for the email: "+email));
        return existingUser.getRole();
    }

    @Override
    public List<UserResponse> readUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> convertToResponse(user))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteUser(String id) {
        UserEntity existingUser = userRepository.findByUserId(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        userRepository.delete(existingUser);
    }
}
