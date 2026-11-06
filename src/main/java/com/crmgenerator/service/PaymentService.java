package com.crmgenerator.service;

import com.crmgenerator.domain.CrmPayment;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.CrmPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentService {

    private final CrmPaymentRepository payments;
    private final PermissionService permissionService;

    public PaymentService(CrmPaymentRepository payments, PermissionService permissionService) {
        this.payments = payments;
        this.permissionService = permissionService;
    }

    public List<CrmPayment> list(String slug, long recordId) {
        return payments.findBySubjectTypeAndSubjectIdOrderByInstallmentAscIdDesc(subject(slug), recordId);
    }

    public BigDecimal sumPaid(String slug, long recordId) {
        BigDecimal v = payments.sumPaid(subject(slug), recordId);
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumAll(String slug, long recordId) {
        BigDecimal v = payments.sumAll(subject(slug), recordId);
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Genera cuotas con fechas explícitas (una por cuota). Si faltan fechas, completa mes a mes.
     */
    @Transactional
    public List<CrmPayment> generateInstallments(String slug, long recordId, BigDecimal total,
                                                  int installments, List<LocalDate> dueDates) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total inválido para cuotas");
        }
        if (installments < 1 || installments > 60) {
            throw new IllegalArgumentException("Número de cuotas entre 1 y 60");
        }
        String st = subject(slug);

        List<CrmPayment> existing = payments.findBySubjectTypeAndSubjectIdOrderByInstallmentAscIdDesc(st, recordId);
        for (CrmPayment p : existing) {
            if ("pending".equalsIgnoreCase(p.getStatus())) {
                payments.delete(p);
            }
        }

        BigDecimal base = total.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
        BigDecimal assigned = BigDecimal.ZERO;
        List<CrmPayment> created = new ArrayList<>();
        UserAccount user = permissionService.currentUser().orElse(null);
        LocalDate fallback = LocalDate.now().plusMonths(1);

        for (int i = 1; i <= installments; i++) {
            BigDecimal amount = (i == installments) ? total.subtract(assigned) : base;
            assigned = assigned.add(amount);

            LocalDate due = null;
            if (dueDates != null && dueDates.size() >= i && dueDates.get(i - 1) != null) {
                due = dueDates.get(i - 1);
            } else {
                due = fallback.plusMonths(i - 1L);
            }

            CrmPayment p = new CrmPayment();
            p.setSubjectType(st);
            p.setSubjectId(recordId);
            p.setAmount(amount);
            p.setInstallment(i);
            p.setDueDate(due);
            p.setStatus("pending");
            p.setMethod("cuota");
            if (user != null) {
                p.setUserId(user.getId());
                p.setUserName(user.getName());
                p.setTenantId(user.getTenantId());
            }
            created.add(payments.save(p));
        }
        return created;
    }

    @Transactional
    public CrmPayment registerPayment(String slug, long recordId, BigDecimal amount,
                                      LocalDate paidAt, String method, String note, Long paymentId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monto de pago inválido");
        }
        UserAccount user = permissionService.currentUser().orElse(null);
        CrmPayment p;
        if (paymentId != null) {
            p = payments.findById(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada"));
            if (!subject(slug).equals(p.getSubjectType()) || !p.getSubjectId().equals(recordId)) {
                throw new IllegalArgumentException("La cuota no pertenece a este registro");
            }
            p.setStatus("paid");
            p.setPaidAt(paidAt != null ? paidAt : LocalDate.now());
            p.setAmount(amount);
            if (method != null && !method.isBlank()) p.setMethod(method);
            if (note != null) p.setNote(note);
        } else {
            p = new CrmPayment();
            p.setSubjectType(subject(slug));
            p.setSubjectId(recordId);
            p.setAmount(amount);
            p.setPaidAt(paidAt != null ? paidAt : LocalDate.now());
            p.setStatus("paid");
            p.setMethod(method != null ? method : "efectivo");
            p.setNote(note);
        }
        if (user != null) {
            p.setUserId(user.getId());
            p.setUserName(user.getName());
            p.setTenantId(user.getTenantId());
        }
        return payments.save(p);
    }

    private static String subject(String slug) {
        return "module:" + slug;
    }
}
