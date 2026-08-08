package net.spring_boot.redis_caching.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.spring_boot.redis_caching.dto.EmployeeDto;
import net.spring_boot.redis_caching.exceptions.ResourceNotFoundException;
import net.spring_boot.redis_caching.services.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.context.annotation.Import;
import net.spring_boot.redis_caching.config.AppConfiguraation;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import(AppConfiguraation.class)
public class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetEmployeeNotFoundReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(employeeService.getEmployeeById(nonExistentId))
                .thenThrow(new ResourceNotFoundException("Employee not found with ID: " + nonExistentId));

        mockMvc.perform(get("/api/employees/{id}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Employee not found with ID: " + nonExistentId));
    }

    @Test
    void testCreateEmployeeValidationFailure() throws Exception {
        EmployeeDto invalidDto = EmployeeDto.builder()
                .name("") // blank
                .email("invalid-email") // invalid email pattern
                .phoneNumber("") // blank
                .departmentId(null) // null
                .joiningDate(null) // null
                .build();

        mockMvc.perform(post("/api/employees")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.departmentId").exists());
    }

    @Test
    void testCreateEmployeeSuccess() throws Exception {
        UUID deptId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        
        EmployeeDto inputDto = EmployeeDto.builder()
                .name("Charlie Brown")
                .email("charlie@example.com")
                .phoneNumber("555-4321")
                .departmentId(deptId)
                .joiningDate(LocalDate.of(2023, 5, 20))
                .build();

        EmployeeDto outputDto = EmployeeDto.builder()
                .id(empId)
                .name("Charlie Brown")
                .email("charlie@example.com")
                .phoneNumber("555-4321")
                .departmentId(deptId)
                .departmentName("Engineering")
                .joiningDate(LocalDate.of(2023, 5, 20))
                .build();

        when(employeeService.createEmployee(any(EmployeeDto.class))).thenReturn(outputDto);

        mockMvc.perform(post("/api/employees")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(empId.toString()))
                .andExpect(jsonPath("$.name").value("Charlie Brown"))
                .andExpect(jsonPath("$.email").value("charlie@example.com"));
    }
}
