package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 009 plan 결정 1: 학교 키 판정과 추가 목록 설정 형식. */
class SchoolDomainsTest {

    private final SchoolDomains acKrOnly = new SchoolDomains("");
    private final SchoolDomains withExtra = new SchoolDomains("skku.edu=skku.edu,g.skku.edu;sogang.ac.kr=sogang.ac.kr,sg.ac.kr");

    @ParameterizedTest
    @CsvSource({
            "kim@snu.ac.kr, snu.ac.kr",
            "lee@cs.snu.ac.kr, snu.ac.kr",
            "a@x.y.korea.ac.kr, korea.ac.kr",
            "'  Kim@SNU.AC.KR ', snu.ac.kr",
            "a@my-univ.ac.kr, my-univ.ac.kr"})
    void acKrDomainsAreSchoolsByTheirLabelBeforeAcKr(String email, String school) {
        assertThat(acKrOnly.schoolOf(email)).contains(school);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a@ac.kr", "a@fakeac.kr", "a@stanford.edu", "a@gmail.com", "a@snu.ac.kr.evil.com",
            "a@.ac.kr", "a@snu..ac.kr", "a@b@snu.ac.kr", "@snu.ac.kr", "snu.ac.kr", "a@snu.ac.kr.", "a@sn_u.ac.kr"})
    void otherDomainsAreNotSchools(String email) {
        assertThat(acKrOnly.schoolOf(email)).isEmpty();
        assertThat(withExtra.schoolOf(email)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "a@skku.edu, skku.edu",
            "a@g.skku.edu, skku.edu",
            "a@mail.g.skku.edu, skku.edu",
            "a@G.SKKU.EDU, skku.edu"})
    void groupedDomainsAndTheirSubdomainsShareTheGroupsKey(String email, String school) {
        assertThat(withExtra.schoolOf(email)).contains(school);
        assertThat(acKrOnly.schoolOf(email)).isEmpty();
    }

    @Test
    void groupsWinOverTheAcKrRule() {
        // sg.ac.kr 은 ac.kr 규칙이면 sg.ac.kr 이지만, 묶음이 먼저라 sogang.ac.kr 이다
        assertThat(withExtra.schoolOf("a@sg.ac.kr")).contains("sogang.ac.kr");
        assertThat(withExtra.schoolOf("a@cs.sg.ac.kr")).contains("sogang.ac.kr");
        assertThat(acKrOnly.schoolOf("a@sg.ac.kr")).contains("sg.ac.kr");
    }

    @Test
    void groupMatchingIsOnDotBoundaries() {
        assertThat(withExtra.schoolOf("a@xskku.edu")).isEmpty();
        assertThat(withExtra.schoolOf("a@skku.edu.evil.com")).isEmpty();
    }

    @Test
    void theKeyIsWhatTheOperatorWroteWhateverTheOrder() {
        SchoolDomains reordered = new SchoolDomains("skku.edu=g.skku.edu,skku.edu;sogang.ac.kr=sg.ac.kr,sogang.ac.kr");
        assertThat(reordered.schoolOf("a@skku.edu")).contains("skku.edu");
        assertThat(reordered.schoolOf("a@g.skku.edu")).contains("skku.edu");
        // 키가 묶음의 도메인 가운데 하나일 필요는 없다
        assertThat(new SchoolDomains("hanyang.ac.kr=hanyang.ac.kr,hy.edu").schoolOf("a@hy.edu")).contains("hanyang.ac.kr");
        // 같은 묶음 안에서는 하위 도메인을 함께 적어도 된다
        assertThat(new SchoolDomains("u.edu=u.edu,mail.u.edu").schoolOf("a@x.mail.u.edu")).contains("u.edu");
    }

    @Test
    void blankOrMissingConfigMeansAcKrOnlyAndSpacesAroundEntriesAreIgnored() {
        assertThat(new SchoolDomains((String) null).schoolOf("a@skku.edu")).isEmpty();
        assertThat(new SchoolDomains("   ").schoolOf("a@snu.ac.kr")).contains("snu.ac.kr");
        assertThat(new SchoolDomains(" SKKU.edu = SKKU.edu , g.skku.edu ; b.edu=b.edu ").schoolOf("a@g.skku.edu"))
                .contains("skku.edu");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "skku.edu=skku.edu,,g.skku.edu",          // 빈 도메인
            "skku.edu=skku.edu, ",
            "skku.edu=",
            "=skku.edu",                              // 빈 키
            "skku.edu=skku.edu;",                     // 빈 묶음
            ";skku.edu=skku.edu",
            "skku.edu,g.skku.edu",                    // 키 없음(옛 형식)
            "a.edu=a.edu;a.edu=b.edu",                // 같은 키가 두 묶음
            "a.edu=a.edu,x.edu;b.edu=X.EDU",          // 다른 묶음에 같은 도메인
            "skku.edu=skku.edu;g.edu=g.skku.edu",     // 다른 묶음의 하위 도메인
            "g.edu=mail.g.skku.edu;skku.edu=skku.edu",
            "a@skku.edu=a@skku.edu",                  // 도메인 형식이 아님
            "skku=skku.edu",
            "skku.edu=skku",
            "skku.edu=skku.edu.",
            "skku.edu=sk ku.edu",
            "x.edu=ac.kr",                            // 모든 ac.kr 학교가 한 묶음이 된다
            "x.edu=AC.KR",
            "a.edu=x.edu;x.edu=y.edu"})               // 키가 다른 묶음의 도메인
    void malformedConfigFailsAtStartup(String spec) {
        assertThatThrownBy(() -> new SchoolDomains(spec)).isInstanceOf(IllegalArgumentException.class);
    }
}
