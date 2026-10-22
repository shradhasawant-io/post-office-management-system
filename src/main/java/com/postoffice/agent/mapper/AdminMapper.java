package com.postoffice.agent.mapper;

import com.postoffice.agent.dto.admin.AdminRequest;
import com.postoffice.agent.dto.admin.AdminResponse;
import com.postoffice.agent.entity.Admin;

public class AdminMapper {

    private AdminMapper() {
        // Utility class
    }

    public static Admin toEntity(AdminRequest request) {

        return Admin.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .adminName(request.getAdminName())
                .build();
    }

    public static AdminResponse toResponse(Admin admin) {

        return AdminResponse.builder()
                .adminId(admin.getAdminId())
                .username(admin.getUsername())
                .adminName(admin.getAdminName())
                .adminStatus(admin.getAdminStatus())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }
}