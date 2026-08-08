package net.spring_boot.redis_caching.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.spring_boot.redis_caching.model.Department;
import net.spring_boot.redis_caching.model.Employee;
import net.spring_boot.redis_caching.repositories.DepartmentRepository;
import net.spring_boot.redis_caching.repositories.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public void run(String... args) {
        log.info("Checking database state for sample data seeding...");

        if (departmentRepository.count() == 0) {
            log.info("No departments found. Initializing sample departments...");
            Department engineering = Department.builder().name("Engineering").build();
            Department hr = Department.builder().name("Human Resources").build();
            Department marketing = Department.builder().name("Marketing").build();
            
            departmentRepository.saveAll(List.of(engineering, hr, marketing));
            log.info("Sample departments saved successfully.");

            if (employeeRepository.count() == 0) {
                log.info("No employees found. Initializing sample employees...");
                Employee emp1 = Employee.builder()
                        .name("John Doe")
                        .email("john.doe@example.com")
                        .phoneNumber("+1-555-0199")
                        .department(engineering)
                        .joiningDate(LocalDate.of(2023, 1, 15))
                        .build();

                Employee emp2 = Employee.builder()
                        .name("Jane Smith")
                        .email("jane.smith@example.com")
                        .phoneNumber("+1-555-0120")
                        .department(hr)
                        .joiningDate(LocalDate.of(2022, 6, 1))
                        .build();

                employeeRepository.saveAll(List.of(emp1, emp2));
                log.info("Sample employees saved successfully.");
            }
        } else {
            log.info("Database already contains data, skipping initialization.");
        }
    }
}
