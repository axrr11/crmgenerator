package com.crmgenerator.repository;

import com.crmgenerator.domain.CrmPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface CrmPaymentRepository extends JpaRepository<CrmPayment, Long> {
    List<CrmPayment> findBySubjectTypeAndSubjectIdOrderByInstallmentAscIdDesc(String subjectType, Long subjectId);

    @Query("select coalesce(sum(p.amount),0) from CrmPayment p where p.subjectType=?1 and p.subjectId=?2 and p.status='paid'")
    BigDecimal sumPaid(String subjectType, Long subjectId);

    @Query("select coalesce(sum(p.amount),0) from CrmPayment p where p.subjectType=?1 and p.subjectId=?2")
    BigDecimal sumAll(String subjectType, Long subjectId);
}
