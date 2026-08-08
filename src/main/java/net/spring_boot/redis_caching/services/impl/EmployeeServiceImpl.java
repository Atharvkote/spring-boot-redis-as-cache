package net.spring_boot.redis_caching.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.spring_boot.redis_caching.dto.EmployeeDto;
import net.spring_boot.redis_caching.exceptions.ResourceNotFoundException;
import net.spring_boot.redis_caching.model.Department;
import net.spring_boot.redis_caching.model.Employee;
import net.spring_boot.redis_caching.repositories.DepartmentRepository;
import net.spring_boot.redis_caching.repositories.EmployeeRepository;
import net.spring_boot.redis_caching.services.EmployeeService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Creates a new employee in the database and populates the cache.
     * 
     * CACHE BEHAVIOR:
     * - @CachePut is used here because we always want the method to execute (to save to the DB), 
     *   and then store the result (EmployeeDto) in the 'employees' cache.
     * - We also evict the 'employees-list' cache since a new employee has been created, 
     *   which makes the cached list of all employees stale.
     * 
     * KEY DETERMINATION:
     * - key = "#result.id" accesses the ID of the returned EmployeeDto object.
     */
    @Override
    @Transactional
    @Caching(
        put = { @CachePut(value = "employees", key = "#result.id") },
        evict = { @CacheEvict(value = "employees-list", allEntries = true) }
    )
    public EmployeeDto createEmployee(EmployeeDto employeeDto) {
        log.info("Creating employee in database with email: {}", employeeDto.getEmail());
        
        // Ensure email uniqueness
        if (employeeRepository.findByEmail(employeeDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + employeeDto.getEmail());
        }

        Department department = departmentRepository.findById(employeeDto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + employeeDto.getDepartmentId()));

        Employee employee = mapToEntity(employeeDto, department);
        Employee savedEmployee = employeeRepository.save(employee);
        
        return mapToDto(savedEmployee);
    }

    /**
     * Retrieves an employee by ID.
     * 
     * CACHE BEHAVIOR:
     * - @Cacheable is used here. It checks the 'employees' cache with the specified ID first.
     * - If found (cache hit), the cached value is returned directly, bypassing database query.
     * - If not found (cache miss), the method body executes (queries the DB), and then the result is cached.
     * 
     * KEY DETERMINATION:
     * - key = "#id" refers to the ID argument of the method.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "employees", key = "#id")
    public EmployeeDto getEmployeeById(UUID id) {
        log.info("Fetching employee from database for ID: {}", id);
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));
        return mapToDto(employee);
    }

    /**
     * Retrieves a list of all employees.
     * 
     * CACHE BEHAVIOR:
     * - @Cacheable with cache name 'employees-list' and a static key 'all'.
     * - On a hit, returns the list from the cache. On a miss, queries the database and caches the list.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "employees-list", key = "'all'")
    public List<EmployeeDto> getAllEmployees() {
        log.info("Fetching all employees from database");
        return employeeRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing employee.
     * 
     * CACHE BEHAVIOR:
     * - @CachePut is used here. The method body is always executed (updating the database), 
     *   and the returned EmployeeDto is updated/overwritten in the 'employees' cache.
     * - We also evict the 'employees-list' cache to prevent returning outdated lists of employees.
     * 
     * KEY DETERMINATION:
     * - key = "#id" refers to the ID argument of the method.
     */
    @Override
    @Transactional
    @Caching(
        put = { @CachePut(value = "employees", key = "#id") },
        evict = { @CacheEvict(value = "employees-list", allEntries = true) }
    )
    public EmployeeDto updateEmployee(UUID id, EmployeeDto employeeDto) {
        log.info("Updating employee in database for ID: {}", id);
        
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));

        // Ensure email uniqueness if email has changed
        if (!employee.getEmail().equalsIgnoreCase(employeeDto.getEmail()) &&
                employeeRepository.findByEmail(employeeDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + employeeDto.getEmail());
        }

        Department department = departmentRepository.findById(employeeDto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + employeeDto.getDepartmentId()));

        employee.setName(employeeDto.getName());
        employee.setEmail(employeeDto.getEmail());
        employee.setPhoneNumber(employeeDto.getPhoneNumber());
        employee.setDepartment(department);
        employee.setJoiningDate(employeeDto.getJoiningDate());

        Employee updatedEmployee = employeeRepository.save(employee);
        return mapToDto(updatedEmployee);
    }

    /**
     * Deletes an employee from database and evicts the cached item.
     * 
     * CACHE BEHAVIOR:
     * - @CacheEvict with key = "#id" is used here to remove the employee from the 'employees' cache.
     * - We also evict the 'employees-list' cache because the list has changed due to deletion.
     */
    @Override
    @Transactional
    @Caching(
        evict = {
            @CacheEvict(value = "employees", key = "#id"),
            @CacheEvict(value = "employees-list", allEntries = true)
        }
    )
    public void deleteEmployee(UUID id) {
        log.info("Deleting employee from database for ID: {}", id);
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));
        employeeRepository.delete(employee);
    }

    private EmployeeDto mapToDto(Employee employee) {
        return EmployeeDto.builder()
                .id(employee.getId())
                .name(employee.getName())
                .email(employee.getEmail())
                .phoneNumber(employee.getPhoneNumber())
                .departmentId(employee.getDepartment().getId())
                .departmentName(employee.getDepartment().getName())
                .joiningDate(employee.getJoiningDate())
                .build();
    }

    private Employee mapToEntity(EmployeeDto dto, Department department) {
        return Employee.builder()
                .id(dto.getId())
                .name(dto.getName())
                .email(dto.getEmail())
                .phoneNumber(dto.getPhoneNumber())
                .department(department)
                .joiningDate(dto.getJoiningDate())
                .build();
    }
}
