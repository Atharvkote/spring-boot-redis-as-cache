package net.spring_boot.redis_caching.services;

import net.spring_boot.redis_caching.dto.EmployeeDto;
import net.spring_boot.redis_caching.model.Department;
import net.spring_boot.redis_caching.model.Employee;
import net.spring_boot.redis_caching.repositories.DepartmentRepository;
import net.spring_boot.redis_caching.repositories.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class EmployeeServiceCachingTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @SpyBean
    private EmployeeRepository employeeRepository;

    @Autowired
    private CacheManager cacheManager;

    private Department testDepartment;
    private EmployeeDto testEmployeeDto;

    @BeforeEach
    void setUp() {
        // Clear caches
        Cache cache = cacheManager.getCache("employees");
        if (cache != null) {
            cache.clear();
        }
        Cache listCache = cacheManager.getCache("employees-list");
        if (listCache != null) {
            listCache.clear();
        }

        // Clean database (employees first due to foreign key)
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        // Create department
        testDepartment = departmentRepository.save(
                Department.builder()
                        .name("Test Department")
                        .build()
        );

        // Prepare Employee DTO
        testEmployeeDto = EmployeeDto.builder()
                .name("Alice Cooper")
                .email("alice@example.com")
                .phoneNumber("1234567890")
                .departmentId(testDepartment.getId())
                .joiningDate(LocalDate.now())
                .build();
    }

    @Test
    void testCreateEmployeePopulatesCache() {
        // Create employee
        EmployeeDto savedDto = employeeService.createEmployee(testEmployeeDto);
        assertNotNull(savedDto.getId());

        // Verify it was stored in the 'employees' cache
        Cache cache = cacheManager.getCache("employees");
        assertNotNull(cache);
        EmployeeDto cachedDto = cache.get(savedDto.getId(), EmployeeDto.class);
        assertNotNull(cachedDto);
        assertEquals(savedDto.getName(), cachedDto.getName());
        assertEquals(savedDto.getEmail(), cachedDto.getEmail());
    }

    @Test
    void testGetEmployeeByIdHitsCacheOnSubsequentCalls() {
        // Create and save directly through repository to bypass createEmployee cache population
        Employee employee = employeeRepository.save(
                Employee.builder()
                        .name("Bob Dylan")
                        .email("bob@example.com")
                        .phoneNumber("9876543210")
                        .department(testDepartment)
                        .joiningDate(LocalDate.now())
                        .build()
        );
        UUID empId = employee.getId();

        // Reset spy count
        reset(employeeRepository);

        // First call: Should miss cache, hit database
        EmployeeDto dto1 = employeeService.getEmployeeById(empId);
        assertNotNull(dto1);
        verify(employeeRepository, times(1)).findById(empId);

        // Second call: Should hit cache, NOT hit database
        EmployeeDto dto2 = employeeService.getEmployeeById(empId);
        assertNotNull(dto2);
        verify(employeeRepository, times(1)).findById(empId); // Count remains 1
    }

    @Test
    void testUpdateEmployeeUpdatesCache() {
        // Create employee
        EmployeeDto savedDto = employeeService.createEmployee(testEmployeeDto);
        UUID empId = savedDto.getId();

        // Update name
        savedDto.setName("Alice Updated");
        EmployeeDto updatedDto = employeeService.updateEmployee(empId, savedDto);
        assertEquals("Alice Updated", updatedDto.getName());

        // Verify cache has updated value
        Cache cache = cacheManager.getCache("employees");
        assertNotNull(cache);
        EmployeeDto cachedDto = cache.get(empId, EmployeeDto.class);
        assertNotNull(cachedDto);
        assertEquals("Alice Updated", cachedDto.getName());
    }

    @Test
    void testDeleteEmployeeEvictsCache() {
        // Create employee
        EmployeeDto savedDto = employeeService.createEmployee(testEmployeeDto);
        UUID empId = savedDto.getId();

        // Verify in cache
        Cache cache = cacheManager.getCache("employees");
        assertNotNull(cache);
        assertNotNull(cache.get(empId));

        // Delete employee
        employeeService.deleteEmployee(empId);

        // Verify evicted from cache
        assertNull(cache.get(empId));
    }
}
