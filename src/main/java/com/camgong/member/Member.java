package com.camgong.member;

import java.time.Instant;

import com.camgong.auth.Keys;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String displayNameKey;

    private String email;

    private String emailKey;

    private String passwordHash;

    private Instant consentAt;

    private String consentVersion;

    private Instant createdAt;

    /** 학교 키(009). 가입 확인 때 메일 도메인으로 정하고 바뀌지 않는다. */
    private String school;

    protected Member() {
    }

    public Member(String displayName, String email, String passwordHash,
            Instant consentAt, String consentVersion, Instant createdAt, String school) {
        this.name = Keys.strip(displayName);
        this.displayNameKey = Keys.normalize(displayName);
        this.email = email.trim();
        this.emailKey = Keys.normalize(email);
        this.passwordHash = passwordHash;
        this.consentAt = consentAt;
        this.consentVersion = consentVersion;
        this.createdAt = createdAt;
        this.school = school;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSchool() {
        return school;
    }
}
