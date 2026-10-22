package com.postoffice.agent.dto.customer;

import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.enums.Gender;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerRequest {

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

    private String aadhaar; //getAadhaar

    private String pan;

    private Agent agentId;
}