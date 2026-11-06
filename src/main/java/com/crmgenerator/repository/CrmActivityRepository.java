package com.crmgenerator.repository;

import com.crmgenerator.domain.CrmActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrmActivityRepository extends JpaRepository<CrmActivity, Long> {
    List<CrmActivity> findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(String subjectType, Long subjectId);
    List<CrmActivity> findTop12ByOrderByCreatedAtDesc();
    List<CrmActivity> findByTypeAndStatusOrderByDueAtAsc(String type, String status);
}
