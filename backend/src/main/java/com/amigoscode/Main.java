package com.amigoscode;

import com.amigoscode.customer.*;
import com.amigoscode.jwt.JWTUtil;
import com.amigoscode.s3.S3Buckets;
import com.amigoscode.s3.S3Config;
import com.amigoscode.s3.S3Service;
import com.github.javafaker.Faker;
import com.github.javafaker.Name;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Random;
import java.util.UUID;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    CommandLineRunner runner(
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder
            ){
        return args -> {
            createRandomCustomer(customerRepository, passwordEncoder);
            //testBucketUploadAndDownload(s3Service, s3Buckets);
        };
    }

    private static void testBucketUploadAndDownload(S3Service s3Service, S3Buckets s3Buckets) {

        s3Service.putObject(
                s3Buckets.getCustomer(),
                "foo",
                "Hello World".getBytes()
        );
        byte[] object = s3Service.getObject(s3Buckets.getCustomer(), "foo");
        System.out.println("Hooray: " + new String(object));
    }

    private static void createRandomCustomer(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        var faker = new Faker();
        Random random = new Random();
        Name name = faker.name();
        String firstName = name.firstName();
        String lastName = name.lastName();
        int age = random.nextInt(16, 99);
        Gender gender = age % 2 == 0 ? Gender.MALE : Gender.FEMALE;
        String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@gmail.com";
        Customer customer = new Customer(
                firstName +  " " + lastName,
                email,
                passwordEncoder.encode("password"),
                age,
                gender);
        // customerRepository.save(customer);
        var s3Config = new S3Config();
        var s3Service = new S3Service(s3Config.s3Client());
        var s3Bucket = new S3Buckets();
        var customerDao = new CustomerJPADataAccessService(customerRepository);
        var customerDTOMapper = new CustomerDTOMapper();
        var customerService = new CustomerService(customerDao, customerDTOMapper, passwordEncoder, s3Service, s3Bucket);
        var jwtUtil = new JWTUtil();
        var customerController = new CustomerController(customerService, jwtUtil);
        System.out.println(email);
        var request = new CustomerRegistrationRequest(
                customer.getName(),
                customer.getEmail(),
                customer.getPassword(),
                customer.getAge(),
                customer.getGender()
        );
        System.out.println(customerController.registerCustomer(request));

        var allCustomers = customerController.getCustomers();
        for (var currentCustomer : allCustomers) {
            if (customer.getEmail().equals(currentCustomer.email())) {
                customer.setId(currentCustomer.id());
                s3Bucket.setCustomer(customer.getId().toString());

                byte[] bytes = "Hello World".getBytes();
                MultipartFile multipartFile = new MockMultipartFile("file", bytes);

                customerController.uploadCustomerProfileImage(customer.getId(), multipartFile);
            }
        }
    }
}
