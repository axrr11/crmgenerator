package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "crm_payments", indexes = {
        @Index(columnList = "subjectType,subjectId")
})
@Getter @Setter
public class CrmPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String subjectType;
    @Column(nullable = false)
    private Long subjectId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    private LocalDate paidAt;
    private LocalDate dueDate;
    private Integer installment;
    private String method;
    private String status = "paid";
    private String reference;
    @Column(length = 2000)
    private String note;
    private Long userId;
    private String userName;
    private Long tenantId;
    private Instant createdAt = Instant.now();
}
