package com.postoffice.agent.dto.customer;

import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerRequest {

    private String customerCode;

    @NotBlank(message = "Customer name is required")
    private String name;

    @NotBlank(message = "Father name is required")
    private String fatherName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[789][0-9]{9}$", message = "Mobile number must be exactly 10 digits and start with 7, 8, or 9")
    private String mobile;

    private LocalDate dob;

    private Gender gender;

    private String address;

    private String city;

    private String state;

    private String pinCode;

    private String email;

    private String aadhaar; //getAadhaar

    private String pan;

    private Agent agentId;
}