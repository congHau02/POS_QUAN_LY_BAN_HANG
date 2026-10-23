package learn.java.billingsoftware.service;

import learn.java.billingsoftware.io.CustomerRequest;
import learn.java.billingsoftware.io.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    List<CustomerResponse> fetchCustomers(String query);

    Page<CustomerResponse> fetchCustomers(String query, Pageable pageable);

    CustomerResponse fetchByCustomerId(String customerId);

    CustomerResponse fetchByPhoneNumber(String phoneNumber);

    CustomerResponse updateCustomer(String customerId, CustomerRequest request);

    void deleteCustomer(String customerId);
}
