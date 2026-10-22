package com.postoffice.agent.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private long totalCustomers;

    private long totalAccounts;

    private BigDecimal todayDeposits;

    private BigDecimal monthlyDeposits;
}