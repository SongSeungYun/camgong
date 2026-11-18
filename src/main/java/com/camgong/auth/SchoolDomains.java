package com.camgong.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 메일 주소 → 학교 키(009 plan 결정 1). 운영자 추가 목록(묶음)을 먼저 보고, 아니면 ac.kr 규칙을 쓴다. 둘 다 아니면 대학 메일이 아니다.
 * 추가 목록 형식: {@code 키=도메인,도메인;키=도메인}. 키는 운영자가 정한 고정 학교 키라 설정 순서와 무관하다.
 * 형식이 틀리거나, 키가 겹치거나, 서로 다른 묶음의 도메인이 같거나 한쪽이 다른 쪽의 하위 도메인이면 기동이 실패한다.
 */
@Component
public class SchoolDomains {

    /** member.school / group_buy.school 컬럼 길이. 키가 이보다 길면 가입 확인에서 저장하지 못한다. */
    private static final int MAX_LENGTH = 253;
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private static final Pattern AC_KR = Pattern.compile("(?:[a-z0-9-]+\\.)*([a-z0-9-]+)\\.ac\\.kr");

    /** 추가 목록의 도메인 → 그 묶음의 학교 키. */
    private final Map<String, String> grouped = new HashMap<>();

    @Autowired
    public SchoolDomains(AuthProperties props) {
        this(props.extraSchoolDomains());
    }

    SchoolDomains(String spec) {
        if (spec == null || spec.isBlank()) {
            return;
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String group : spec.split(";", -1)) {
            int eq = group.indexOf('=');
            if (eq < 0) {
                throw invalid("'키=도메인,…' 형식이 아니다: '" + group + "'");
            }
            String school = domain(group.substring(0, eq));
            if (!keys.add(school)) {
                throw invalid("같은 키가 두 묶음에 있다: " + school);
            }
            for (String raw : group.substring(eq + 1).split(",", -1)) {
                String domain = domain(raw);
                if (domain.equals("ac.kr")) {
                    throw invalid("ac.kr 자체는 묶을 수 없다(모든 ac.kr 학교가 한 학교가 된다)");
                }
                if (grouped.putIfAbsent(domain, school) != null && !grouped.get(domain).equals(school)) {
                    throw invalid("도메인이 두 묶음에 있다: " + domain);
                }
            }
        }
        // 키가 다른 묶음의 도메인이면 그 도메인 학생과 이 묶음 학생이 같은 학교 키를 받는다.
        for (String key : keys) {
            String owner = grouped.get(key);
            if (owner != null && !owner.equals(key)) {
                throw invalid("키 " + key + " 가 다른 묶음(" + owner + ")의 도메인이다");
            }
        }
        // 다른 묶음의 도메인이 점 경계로 겹치면(skku.edu 와 g.skku.edu) 어느 학교인지가 설정 순서에 달라진다.
        grouped.forEach((domain, school) -> {
            for (String d = parent(domain); d != null; d = parent(d)) {
                String other = grouped.get(d);
                if (other != null && !other.equals(school)) {
                    throw invalid(domain + " 는 다른 묶음의 " + d + " 의 하위 도메인이다");
                }
            }
        });
    }

    private static String domain(String raw) {
        String d = Keys.normalize(raw);
        if (!DOMAIN.matcher(d).matches() || d.length() > MAX_LENGTH) {
            throw invalid("도메인 형식이 아니다: '" + raw + "'");
        }
        return d;
    }

    /** 왼쪽 라벨을 뗀 상위 도메인. 점이 없는 마지막 라벨(edu, kr)이면 null. */
    private static String parent(String domain) {
        String p = domain.substring(domain.indexOf('.') + 1);
        return p.indexOf('.') > 0 ? p : null;
    }

    private static IllegalArgumentException invalid(String why) {
        return new IllegalArgumentException("camgong.auth.extra-school-domains: " + why);
    }

    /** 대학 메일이면 학교 키, 아니면 empty. 앞뒤 공백·대소문자는 무시한다. @ 가 정확히 하나여야 한다. */
    public Optional<String> schoolOf(String email) {
        String key = Keys.normalize(email);
        int at = key.indexOf('@');
        if (at <= 0 || key.lastIndexOf('@') != at) {
            return Optional.empty();
        }
        String domain = key.substring(at + 1);
        if (!DOMAIN.matcher(domain).matches()) {
            return Optional.empty();
        }
        // 묶음: 도메인 자신부터 왼쪽 라벨을 하나씩 떼며 찾는다(점 경계). 묶음끼리는 겹치지 않으므로 맞는 묶음은 하나뿐이다.
        for (String d = domain; d != null; d = parent(d)) {
            String school = grouped.get(d);
            if (school != null) {
                return Optional.of(school);
            }
        }
        Matcher m = AC_KR.matcher(domain);
        return m.matches() ? Optional.of(m.group(1) + ".ac.kr") : Optional.empty();
    }
}
