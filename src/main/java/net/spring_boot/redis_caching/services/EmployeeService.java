package net.spring_boot.redis_caching.services;

import net.spring_boot.redis_caching.dto.EmployeeDto;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {
    EmployeeDto createEmployee(EmployeeDto employeeDto);
    
    EmployeeDto getEmployeeById(UUID id);
    
    List<EmployeeDto> getAllEmployees();
    
    EmployeeDto updateEmployee(UUID id, EmployeeDto employeeDto);
    
    void deleteEmployee(UUID id);
}
