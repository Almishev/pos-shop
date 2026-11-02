package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.UserEntity;
import in.bushansirgur.billingsoftware.entity.UserRolePeriodEntity;
import in.bushansirgur.billingsoftware.io.AuditAction;
import in.bushansirgur.billingsoftware.io.UserRequest;
import in.bushansirgur.billingsoftware.io.UserResponse;
import in.bushansirgur.billingsoftware.repository.UserRepository;
import in.bushansirgur.billingsoftware.repository.UserRolePeriodRepository;
import in.bushansirgur.billingsoftware.service.AuditService;
import in.bushansirgur.billingsoftware.service.UserService;
import in.bushansirgur.billingsoftware.util.OperatorNames;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ROLES = Set.of("ROLE_USER", "ROLE_ADMIN", "ROLE_AUDITOR");

    private final UserRepository userRepository;
    private final UserRolePeriodRepository userRolePeriodRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional
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
        applyIdentity(newUser, request, true);
        newUser.setRole(normalizeRole(request.getRole()));
        newUser.setOperatorCode(resolveOperatorCode(request.getOperatorCode(), null));
        LocalDateTime now = LocalDateTime.now();
        newUser.setRoleActiveFrom(now);
        newUser.setRoleActiveTo(null);
        newUser = userRepository.save(newUser);
        userRolePeriodRepository.save(UserRolePeriodEntity.builder()
                .userId(newUser.getUserId())
                .role(newUser.getRole())
                .activeFrom(now)
                .build());
        auditService.record(AuditAction.USER_CREATE, "Създаден оператор: " + describe(newUser), null);
        return convertToResponse(newUser);
    }

    @Override
    @Transactional
    public UserResponse updateUser(String userId, UserRequest request) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String before = describe(user);
        applyIdentity(user, request, true);
        String nextRole = normalizeRole(request.getRole() == null || request.getRole().isBlank() ? user.getRole() : request.getRole());
        if (request.getOperatorCode() != null && !request.getOperatorCode().isBlank()) {
            user.setOperatorCode(resolveOperatorCode(request.getOperatorCode(), user.getUserId()));
        }
        if (user.getRoleActiveFrom() == null && nextRole.equals(user.getRole())) {
            LocalDateTime now = LocalDateTime.now();
            user.setRoleActiveFrom(now);
            user.setRoleActiveTo(null);
            userRolePeriodRepository.save(UserRolePeriodEntity.builder()
                    .userId(user.getUserId())
                    .role(nextRole)
                    .activeFrom(now)
                    .build());
        }
        if (!nextRole.equals(user.getRole())) {
            LocalDateTime now = LocalDateTime.now();
            userRolePeriodRepository.findFirstByUserIdAndActiveToIsNullOrderByActiveFromDesc(user.getUserId())
                    .ifPresent(open -> {
                        open.setActiveTo(now);
                        userRolePeriodRepository.save(open);
                    });
            user.setRole(nextRole);
            user.setRoleActiveFrom(now);
            user.setRoleActiveTo(null);
            userRolePeriodRepository.save(UserRolePeriodEntity.builder()
                    .userId(user.getUserId())
                    .role(nextRole)
                    .activeFrom(now)
                    .build());
        }
        user = userRepository.save(user);
        auditService.record(AuditAction.USER_CHANGE, "Промяна на оператор: " + before + " → " + describe(user), null);
        return convertToResponse(user);
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
                .operatorCode(newUser.getOperatorCode())
                .firstName(newUser.getFirstName())
                .middleName(newUser.getMiddleName())
                .lastName(newUser.getLastName())
                .position(newUser.getPosition())
                .roleActiveFrom(newUser.getRoleActiveFrom())
                .roleActiveTo(newUser.getRoleActiveTo())
                .build();
    }

    private UserEntity convertToEntity(UserRequest request) {
        return UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(normalizeRole(request.getRole()))
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
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUser(String id) {
        UserEntity existingUser = userRepository.findByUserId(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        auditService.record(AuditAction.USER_CHANGE, "Изтрит оператор: " + describe(existingUser), null);
        userRolePeriodRepository.findByUserIdOrderByActiveFromAsc(existingUser.getUserId())
                .forEach(userRolePeriodRepository::delete);
        userRepository.delete(existingUser);
    }

    @Override
    public String ensureOperatorCode(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for the email: " + email));
        if (user.getOperatorCode() != null && user.getOperatorCode().matches("\\d{4}")) {
            return user.getOperatorCode();
        }
        user.setOperatorCode(nextOperatorCode());
        return userRepository.save(user).getOperatorCode();
    }

    private void applyIdentity(UserEntity user, UserRequest request, boolean required) {
        String first = trim(request.getFirstName());
        String middle = trim(request.getMiddleName());
        String last = trim(request.getLastName());
        String position = trim(request.getPosition());
        if (required && (first.isEmpty() || middle.isEmpty() || last.isEmpty() || position.isEmpty())) {
            throw new IllegalArgumentException("Трите имена и длъжността са задължителни");
        }
        if (!first.isEmpty()) {
            user.setFirstName(first);
        }
        if (!middle.isEmpty()) {
            user.setMiddleName(middle);
        }
        if (!last.isEmpty()) {
            user.setLastName(last);
        }
        if (!position.isEmpty()) {
            user.setPosition(position);
        }
        user.setName(OperatorNames.display(user.getFirstName(), user.getMiddleName(), user.getLastName(), user.getName()));
    }

    private String describe(UserEntity user) {
        return OperatorNames.display(user)
                + ", длъжност " + empty(user.getPosition())
                + ", роля " + empty(user.getRole())
                + ", код " + empty(user.getOperatorCode())
                + ", период " + user.getRoleActiveFrom() + "–" + (user.getRoleActiveTo() == null ? "активен" : user.getRoleActiveTo());
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_USER";
        }
        String normalized = role.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        if (!ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Непозната роля");
        }
        return normalized;
    }

    private String resolveOperatorCode(String requested, String currentOwnerUserId) {
        if (requested == null || requested.isBlank()) {
            return nextOperatorCode();
        }
        String code = requested.trim();
        if (!code.matches("\\d{4}")) {
            throw new IllegalArgumentException("Кодът на оператора трябва да е 4 цифри");
        }
        userRepository.findByOperatorCode(code).ifPresent(existing -> {
            if (currentOwnerUserId == null || !currentOwnerUserId.equals(existing.getUserId())) {
                throw new IllegalArgumentException("Кодът на оператора вече се използва");
            }
        });
        return code;
    }

    private String nextOperatorCode() {
        Set<String> used = userRepository.findAll().stream()
                .map(UserEntity::getOperatorCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        for (int i = 1; i <= 9999; i++) {
            String code = String.format("%04d", i);
            if (!used.contains(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Няма свободен 4-цифрен код на оператор");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
