package com.postoffice.agent.service;

import com.postoffice.agent.dto.auth.ForgotPasswordRequest;
import com.postoffice.agent.dto.auth.ResetPasswordRequest;

public interface PasswordResetService {
    void requestReset(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
