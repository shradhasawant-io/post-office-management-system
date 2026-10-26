package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.admin.AdminRequest;
import com.postoffice.agent.dto.admin.AdminResponse;
import com.postoffice.agent.entity.Admin;
import com.postoffice.agent.enums.AdminStatus;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.exception.ConflictException;
import com.postoffice.agent.exception.ResourceNotFoundException;
import com.postoffice.agent.mapper.AdminMapper;
import com.postoffice.agent.repository.AdminRepository;
import com.postoffice.agent.service.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminServiceImpl(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AdminResponse createFirstAdmin(AdminRequest request) {

        // Allow public creation only for the first admin
        if (adminRepository.count() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An admin already exists. Only an existing admin can create another admin."
            );
        }

        log.info("Creating the first admin with username: {}", request.getUsername());

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Admin username already exists");
        }

        Admin admin = AdminMapper.toEntity(request);

        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setAdminStatus(AdminStatus.ACTIVE);

        Admin savedAdmin = adminRepository.save(admin);

        log.info("First admin created successfully. Admin ID: {}", savedAdmin.getAdminId());

        return AdminMapper.toResponse(savedAdmin);
    }

    //Create Admins
    @Override
    @Transactional
    public AdminResponse createAdmin(AdminRequest request) {

        log.info("Attempting to create admin with username: {}", request.getUsername());

        if (adminRepository.existsByUsername(request.getUsername())) {
            log.warn("Admin creation failed. Username already exists: {}", request.getUsername());
            throw new ConflictException("Admin username already exists");
        }

        Admin admin = AdminMapper.toEntity(request);

        admin.setPassword(passwordEncoder.encode(request.getPassword()));

        admin.setAdminStatus(AdminStatus.ACTIVE);

        Admin savedAdmin = adminRepository.save(admin);

        log.info("Admin created successfully. Admin ID: {}", savedAdmin.getAdminId());

        return AdminMapper.toResponse(savedAdmin);
    }

    //Get Admins By ID
    @Override
    @Transactional(readOnly = true)
    public AdminResponse getAdminById(Long adminId) {

        log.debug("Fetching admin with ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> {
                    log.warn("Admin not found with ID: {}", adminId);
                    return new RuntimeException("Admin not found with id: " + adminId);
                });
        return AdminMapper.toResponse(admin);
    }

    //Get All Admins
    @Override
    @Transactional(readOnly = true)
    public List<AdminResponse> getAllAdmins() {

        log.debug("Fetching all admins");

        List<Admin> admins = adminRepository.findAll();
        return admins.stream()
                .map(AdminMapper::toResponse)
                .toList();
    }

    //Update Admin
    @Override
    @Transactional
    public AdminResponse updateAdmin(Long adminId, AdminRequest request) {

        log.info("Updating admin with ID: {}", adminId);

        Admin existingAdmin = adminRepository.findById(adminId)
                .orElseThrow(() -> {
                    log.warn("Admin update failed. Admin not found with ID: {}", adminId);
                    return new RuntimeException("Admin not found with id: " + adminId);
                });

        // Update admin username
        if (request.getUsername() != null
                && !request.getUsername().isBlank()
                && !request.getUsername().equals(existingAdmin.getUsername())) {

            if (adminRepository.existsByUsername(request.getUsername())) {
                throw new ResourceNotFoundException("Admin not found with id: " + adminId);
            }

            existingAdmin.setUsername(request.getUsername());
        }

        // Update admin name
        if (request.getAdminName() != null
                && !request.getAdminName().isBlank()) {
            existingAdmin.setAdminName(request.getAdminName());
        }

        //Password is optional during update.If no password is supplied, keep the existing BCrypt hash unchanged
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existingAdmin.setPassword(passwordEncoder.encode(request.getPassword())
            );

            log.info("Password updated for admin ID: {}", adminId);
        }

        Admin updatedAdmin = adminRepository.save(existingAdmin);

        log.info("Admin updated successfully. Admin ID: {}", adminId);

        return AdminMapper.toResponse(updatedAdmin);
    }

    @Override
    @Transactional
    public AdminResponse deactivateAdmin(Long adminId) {

        log.info("Deactivating admin with ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> {

                    log.warn("Admin deactivation failed. Admin not found with ID: {}", adminId);
                    return new RuntimeException("Admin not found with id: " + adminId);
                });


        if (admin.getAdminStatus() == AdminStatus.INACTIVE) {

            log.warn("Admin is already inactive. Admin ID: {}", adminId);
            throw new BusinessException("Admin is already inactive");
        }


        admin.setAdminStatus(AdminStatus.INACTIVE);
        Admin deactivatedAdmin = adminRepository.save(admin);

        log.info("Admin deactivated successfully. Admin ID: {}", adminId);

        return AdminMapper.toResponse(deactivatedAdmin);
    }
}