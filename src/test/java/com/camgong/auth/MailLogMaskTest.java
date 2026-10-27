package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailLogMaskTest {

    @Test
    void keepsOnlyTheFirstLetterAndTheDomain() {
        assertThat(VerificationMailListener.mask("student@school.ac.kr")).isEqualTo("s***@school.ac.kr");
        assertThat(VerificationMailListener.mask("a@b.c")).isEqualTo("a***@b.c");
        assertThat(VerificationMailListener.mask("weird")).isEqualTo("***");
    }
}
