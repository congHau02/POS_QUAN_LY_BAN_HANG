package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.UserEntity;
import learn.java.billingsoftware.io.UserRequest;
import learn.java.billingsoftware.io.UserResponse;
import learn.java.billingsoftware.repository.UserRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public UserResponse createUser(UserRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (email != null && userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại: " + email);
        }
        UserEntity newUser = convertToEntity(request);
        newUser = userRepository.save(newUser);
        activityLogService.logActivity("CREATE", "USER", newUser.getUserId(), "Created user: " + newUser.getEmail() + " (" + newUser.getRole() + ")");
        return convertToResponse(newUser);
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
                .email(normalizeEmail(request.getEmail()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(normalizeRole(request.getRole()))
                .name(request.getName())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String getUserRole(String email) {
       UserEntity existingUser = userRepository.findByEmail(normalizeEmail(email))
               .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for the email: " + email));
       return existingUser.getRole();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        UserEntity existingUser = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for the email: " + email));
        return convertToResponse(existingUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> readUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> convertToResponse(user))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUser(String id) {
        UserEntity existingUser = userRepository.findByUserId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank() && !authentication.getName().equals("anonymousUser")) {
            String currentAuthEmail = authentication.getName().trim().toLowerCase();
            if (existingUser.getEmail() != null && existingUser.getEmail().trim().toLowerCase().equals(currentAuthEmail)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tự xóa tài khoản đang đăng nhập: " + existingUser.getEmail());
            }
        }

        userRepository.delete(existingUser);
        activityLogService.logActivity("DELETE", "USER", existingUser.getUserId(), "Deleted user: " + existingUser.getEmail());
    }

    @Override
    @Transactional
    public UserResponse updateUser(String userId, UserRequest request) {
        UserEntity existingUser = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = normalizeEmail(request.getEmail());
            if (!newEmail.equalsIgnoreCase(existingUser.getEmail())) {
                var otherUser = userRepository.findByEmail(newEmail);
                if (otherUser.isPresent() && !otherUser.get().getUserId().equals(existingUser.getUserId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác: " + newEmail);
                }
                existingUser.setEmail(newEmail);
            }
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            existingUser.setName(request.getName().trim());
        }

        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            String role = normalizeRole(request.getRole());
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }
            existingUser.setRole(role);
        }

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }

        existingUser = userRepository.save(existingUser);
        activityLogService.logActivity("UPDATE", "USER", existingUser.getUserId(), "Updated user: " + existingUser.getEmail() + " (" + existingUser.getRole() + ")");
        return convertToResponse(existingUser);
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "ROLE_USER";
        }
        String cleanRole = role.trim().toUpperCase();
        return cleanRole.startsWith("ROLE_") ? cleanRole : "ROLE_" + cleanRole;
    }
}
