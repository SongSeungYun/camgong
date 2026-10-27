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

    protected Member() {
    }

    public Member(String displayName, String email, String passwordHash,
            Instant consentAt, String consentVersion, Instant createdAt) {
        this.name = displayName.trim();
        this.displayNameKey = Keys.normalize(displayName);
        this.email = email.trim();
        this.emailKey = Keys.normalize(email);
        this.passwordHash = passwordHash;
        this.consentAt = consentAt;
        this.consentVersion = consentVersion;
        this.createdAt = createdAt;
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
}
