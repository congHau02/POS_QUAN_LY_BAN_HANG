package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.UserEntity;
import learn.java.billingsoftware.io.UserRequest;
import learn.java.billingsoftware.io.UserResponse;
import learn.java.billingsoftware.repository.UserRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
    public UserResponse createUser(UserRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
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
                .email(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole().toUpperCase())
                .name(request.getName())
                .build();
    }

    @Override
    public String getUserRole(String email) {
       UserEntity existingUser = userRepository.findByEmail(email != null ? email.trim().toLowerCase() : null)
               .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for the email: " + email));
       return existingUser.getRole();
    }

    @Override
    public UserResponse findByEmail(String email) {
        UserEntity existingUser = userRepository.findByEmail(email != null ? email.trim().toLowerCase() : null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for the email: " + email));
        return convertToResponse(existingUser);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id));
        userRepository.delete(existingUser);
        activityLogService.logActivity("DELETE", "USER", existingUser.getUserId(), "Deleted user: " + existingUser.getEmail());
    }

    @Override
    public UserResponse updateUser(String userId, UserRequest request) {
        UserEntity existingUser = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = request.getEmail().trim().toLowerCase();
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
            String role = request.getRole().trim().toUpperCase();
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
}
