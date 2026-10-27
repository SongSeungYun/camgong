package com.camgong.groupbuy.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

import com.camgong.groupbuy.GroupBuy;
import com.camgong.groupbuy.GroupBuyCategory;
import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 목록의 동적 조건(상태, 카테고리, 검색 단어, 키셋)을 Criteria API 로 조립한다(research 결정 6).
 * 카운트 쿼리는 하지 않고 size + 1 건을 읽어 다음 페이지 유무를 판정한다.
 */
@Repository
public class GroupBuySearchRepository {

    /** 카드에 필요한 컬럼만 읽는 프로젝션. 표시 상태는 서비스가 마감과 시계로 계산한다. */
    public record Row(Long id, String title, String itemName, GroupBuyCategory category, int unitPrice,
            int securedQuantity, int totalQuantity, Instant deadline, GroupBuyStatus status) {
    }

    @PersistenceContext
    private EntityManager em;

    public List<Row> search(GroupBuyListQuery q, Instant now) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Row> cq = cb.createQuery(Row.class);
        Root<GroupBuy> g = cq.from(GroupBuy.class);
        cq.select(cb.construct(Row.class, g.get("id"), g.get("title"), g.get("itemName"), g.get("category"),
                g.get("unitPrice"), g.get("securedQuantity"), g.get("totalQuantity"), g.get("deadline"), g.get("status")));

        List<Predicate> where = new ArrayList<>();
        where.add(cb.notEqual(g.get("status"), GroupBuyStatus.CANCELLED));
        where.add(q.status().toPredicate(cb, g, now));
        if (q.category() != null) {
            where.add(cb.equal(g.get("category"), q.category()));
        }
        for (String word : q.words()) {
            String pattern = "%" + escapeLike(word) + "%";
            where.add(cb.or(cb.like(g.get("title"), pattern, '\\'), cb.like(g.get("itemName"), pattern, '\\')));
        }
        PageCursor c = q.cursor();
        if (c != null) {
            where.add(switch (q.sort()) {
                case LATEST -> cb.lessThan(g.<Long>get("id"), c.id());
                case DEADLINE -> cb.or(cb.greaterThan(g.<Instant>get("deadline"), c.deadline()),
                        cb.and(cb.equal(g.get("deadline"), c.deadline()), cb.greaterThan(g.<Long>get("id"), c.id())));
            });
        }
        cq.where(where.toArray(Predicate[]::new));
        cq.orderBy(switch (q.sort()) {
            case LATEST -> List.of(cb.desc(g.get("id")));
            case DEADLINE -> List.of(cb.asc(g.get("deadline")), cb.asc(g.get("id")));
        });
        return em.createQuery(cq).setMaxResults(q.size() + 1).getResultList();
    }

    /** LIKE 의 와일드카드(%, _)와 이스케이프 문자(\)를 문자 그대로 찾도록 막는다. */
    static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
