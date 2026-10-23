package learn.java.billingsoftware.controller;

import learn.java.billingsoftware.io.CustomerRequest;
import learn.java.billingsoftware.io.CustomerResponse;
import learn.java.billingsoftware.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @GetMapping
    public Page<CustomerResponse> fetchAll(
            @RequestParam(value = "query", required = false) String query,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.fetchCustomers(query, pageable);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse fetchById(@PathVariable String customerId) {
        return customerService.fetchByCustomerId(customerId);
    }

    @GetMapping("/by-phone/{phoneNumber}")
    public CustomerResponse fetchByPhone(@PathVariable String phoneNumber) {
        return customerService.fetchByPhoneNumber(phoneNumber);
    }

    @PutMapping("/{customerId}")
    public CustomerResponse update(@PathVariable String customerId, @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(customerId, request);
    }

    @DeleteMapping("/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String customerId) {
        customerService.deleteCustomer(customerId);
    }
}
