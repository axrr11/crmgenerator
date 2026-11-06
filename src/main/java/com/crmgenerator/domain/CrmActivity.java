package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "crm_activities", indexes = {
        @Index(columnList = "subjectType,subjectId")
})
@Getter @Setter
public class CrmActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String subjectType;
    @Column(nullable = false)
    private Long subjectId;
    private String type = "note";
    @Column(nullable = false)
    private String title;
    @Column(length = 5000)
    private String body;
    private String status = "done";
    private LocalDateTime dueAt;
    private Long userId;
    private String userName;
    private Instant createdAt = Instant.now();
}
