package com.postoffice.agent.service;

import com.postoffice.agent.dto.admin.AdminRequest;
import com.postoffice.agent.dto.admin.AdminResponse;

import java.util.List;

public interface AdminService {

    AdminResponse createFirstAdmin(AdminRequest request);

    AdminResponse createAdmin(AdminRequest request);

    AdminResponse getAdminById(Long adminId);

    List<AdminResponse> getAllAdmins();

    AdminResponse updateAdmin(Long adminId, AdminRequest request);

    AdminResponse deactivateAdmin(Long adminId);
}