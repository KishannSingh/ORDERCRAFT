package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.Customer;
import com.ordercraft.ordercraft.repository.CustomerRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

@Service
@Transactional
public class CustomerService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{10}$");

    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            SalesOrderRepository salesOrderRepository
    ) {
        this.customerRepository = customerRepository;
        this.salesOrderRepository = salesOrderRepository;
    }

    public Customer saveCustomer(Customer customer) {
        if (customer == null) {
            throw badRequest("Customer data is required");
        }

        String name = customer.getName() == null ? "" : customer.getName().trim();
        String email = customer.getEmail() == null ? "" : customer.getEmail().trim();
        String phone = customer.getPhone() == null ? "" : customer.getPhone().trim();

        if (name.isBlank()) {
            throw badRequest("Customer name is required");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw badRequest("A valid customer email is required");
        }
        if (!PHONE.matcher(phone).matches()) {
            throw badRequest("Phone number must contain exactly 10 digits");
        }

        Long id = customer.getCustomerId();
        if (id != null && (id <= 0 || !customerRepository.existsById(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found with id: " + id);
        }
        boolean duplicateEmail = id == null
                ? customerRepository.existsByEmailIgnoreCase(email)
                : customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(email, id);
        if (duplicateEmail) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this email already exists");
        }

        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        validateId(id);
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found with id: " + id
                ));
    }

    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        if (salesOrderRepository.existsByCustomerId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete this customer because sales orders reference it"
            );
        }
        customerRepository.delete(customer);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("A valid customer ID is required");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
