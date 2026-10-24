package com.postoffice.agent.controller;

import com.postoffice.agent.dto.admin.AdminRequest;
import com.postoffice.agent.dto.admin.AdminResponse;
import com.postoffice.agent.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admins")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // Create Admin
    @PostMapping
    public ResponseEntity<AdminResponse> createAdmin(@RequestBody AdminRequest request) {
        AdminResponse response = adminService.createAdmin(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // Get Admin by ID
    @GetMapping("/{adminId}")
    public ResponseEntity<AdminResponse> getAdminById(@PathVariable Long adminId) {
        AdminResponse response = adminService.getAdminById(adminId);
        return ResponseEntity.ok(response);
    }


    // Get all Admins
    @GetMapping
    public ResponseEntity<List<AdminResponse>> getAllAdmins() {
        List<AdminResponse> response = adminService.getAllAdmins();
        return ResponseEntity.ok(response);
    }


    // Update Admin
    @PutMapping("/{adminId}")
    public ResponseEntity<AdminResponse> updateAdmin(@PathVariable Long adminId, @RequestBody AdminRequest request) {
        AdminResponse response = adminService.updateAdmin(adminId, request);
        return ResponseEntity.ok(response);
    }


    // Deactivate Admin
    @PatchMapping("/{adminId}/deactivate")
    public ResponseEntity<AdminResponse> deactivateAdmin(@PathVariable Long adminId) {

        AdminResponse response = adminService.deactivateAdmin(adminId);

        return ResponseEntity.ok(response);
    }
}