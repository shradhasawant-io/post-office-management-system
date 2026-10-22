
package com.postoffice.agent.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionReportResponse {

    private String reportType;

    private LocalDate startDate;

    private LocalDate endDate;

    private long totalTransactions;

    private BigDecimal totalCollection;

    private List<CollectionTransactionResponse> transactions;
}
