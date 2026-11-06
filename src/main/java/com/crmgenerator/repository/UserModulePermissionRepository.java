package com.crmgenerator.repository;

import com.crmgenerator.domain.UserModulePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserModulePermissionRepository extends JpaRepository<UserModulePermission, Long> {
    List<UserModulePermission> findByUserId(Long userId);

    Optional<UserModulePermission> findByUserIdAndModuleSlug(Long userId, String moduleSlug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from UserModulePermission p where p.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
