package com.postoffice.agent.entity;

import com.postoffice.agent.enums.CustomerStatus;
import com.postoffice.agent.enums.Gender;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@Getter
@Setter
@Entity
@Table(name = "customers")
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    @Column(name = "customer_code" , unique = true, nullable = false, length = 30)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String name;

    @Column(name = "father_name", nullable = false, length = 100)
    private String fatherName;

    @Column(unique = true)
    @Pattern(regexp = "^[789][0-9]{9}$", message = "Mobile number must be exactly 10 digits")
    private String mobile;

    @Column(nullable = false)
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false, length = 50)
    private String state;

    @Column(nullable = false, length = 6)
    private String pinCode;

    @Email
    private String email;

    @Column(unique = true, length = 12)
    private String aadharNumber;

    @Column(unique = true, length = 10)
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "PAN must be in a valid format")
    private String pan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus customerStatus = CustomerStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Agent agent;
}
