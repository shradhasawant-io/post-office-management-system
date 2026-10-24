package com.postoffice.agent.dto.customer;

import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.enums.CustomerStatus;
import com.postoffice.agent.enums.Gender;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@Getter
@Setter
public class CustomerResponse {

    private Long id;

    private String customerCode;

    private String name;

    private String fatherName;

    private String mobile;

    private LocalDate dob;

    private Gender gender;

    private String address;

    private String city;

    private String state;

    private String pinCode;

    private String email;

    private String aadhaar;

    private String pan;

    private CustomerStatus status;

    private LocalDateTime createdAt;

    private Long agentId;

    private String agentName;
}