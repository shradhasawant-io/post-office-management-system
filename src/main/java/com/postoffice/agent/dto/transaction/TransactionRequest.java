
package com.postoffice.agent.dto.transaction;

import com.postoffice.agent.enums.PaymentMode;
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
public class TransactionRequest {

    /**
     * Customer-facing account number used for new deposit requests.
     */
    private String transactionAccountNumber;

    /**
     * Legacy internal account ID retained for backward compatibility.
     * New frontend requests should send transactionAccountNumber.
     */
    private Long transactionAccountId;

    /**
     * Reuse the same key when retrying the same deposit request.
     */
    private String idempotencyKey;

    private BigDecimal transactionAmount;

    private PaymentMode paymentMode;

    private String remarks;
}