package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.CustomerEntity;
import learn.java.billingsoftware.io.CustomerRequest;
import learn.java.billingsoftware.io.CustomerResponse;
import learn.java.billingsoftware.repository.CustomerRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer name is required");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer phone number is required");
        }

        String phone = request.getPhoneNumber().trim();
        if (customerRepository.existsByPhoneNumber(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer with phone number " + phone + " already exists");
        }

        CustomerEntity entity = CustomerEntity.builder()
                .customerId(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .phoneNumber(phone)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .totalSpent(0.0)
                .orderCount(0)
                .build();

        CustomerEntity saved = customerRepository.save(entity);
        activityLogService.logActivity("CREATE", "CUSTOMER", saved.getCustomerId(), "Created customer: " + saved.getName() + " (" + saved.getPhoneNumber() + ")");
        return convertToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> fetchCustomers(String query) {
        List<CustomerEntity> list;
        if (query != null && !query.trim().isEmpty()) {
            list = customerRepository.searchByNameOrPhoneNumber(query.trim());
        } else {
            list = customerRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> fetchCustomers(String query, Pageable pageable) {
        Page<CustomerEntity> page;
        if (query != null && !query.trim().isEmpty()) {
            page = customerRepository.searchByNameOrPhoneNumber(query.trim(), pageable);
        } else {
            page = customerRepository.findAll(pageable);
        }
        return page.map(this::convertToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse fetchByCustomerId(String customerId) {
        CustomerEntity entity = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId));
        return convertToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse fetchByPhoneNumber(String phoneNumber) {
        CustomerEntity entity = customerRepository.findByPhoneNumber(phoneNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found with phone: " + phoneNumber));
        return convertToResponse(entity);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(String customerId, CustomerRequest request) {
        CustomerEntity entity = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            entity.setName(request.getName().trim());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            String newPhone = request.getPhoneNumber().trim();
            if (!newPhone.equals(entity.getPhoneNumber()) && customerRepository.existsByPhoneNumber(newPhone)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already used by another customer: " + newPhone);
            }
            entity.setPhoneNumber(newPhone);
        }
        if (request.getEmail() != null) {
            entity.setEmail(request.getEmail().trim().isEmpty() ? null : request.getEmail().trim());
        }

        CustomerEntity saved = customerRepository.save(entity);
        activityLogService.logActivity("UPDATE", "CUSTOMER", saved.getCustomerId(), "Updated customer: " + saved.getName());
        return convertToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCustomer(String customerId) {
        CustomerEntity entity = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId));
        customerRepository.delete(entity);
        activityLogService.logActivity("DELETE", "CUSTOMER", entity.getCustomerId(), "Deleted customer: " + entity.getName());
    }

    public CustomerResponse convertToResponse(CustomerEntity entity) {
        return CustomerResponse.builder()
                .customerId(entity.getCustomerId())
                .name(entity.getName())
                .phoneNumber(entity.getPhoneNumber())
                .email(entity.getEmail())
                .totalSpent(entity.getTotalSpent())
                .orderCount(entity.getOrderCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
