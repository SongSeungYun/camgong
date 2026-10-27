package com.camgong.groupbuy;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 연관관계 매핑 없이 Long 컬럼만 둔다(N+1 비교용 매핑은 조회 기능에서 결정). */
@Entity
@Table(name = "participation")
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id")
    private Long groupBuyId;

    @Column(name = "member_id")
    private Long memberId;

    private int quantity;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Participation() {
    }

    public Participation(Long groupBuyId, Long memberId, int quantity, Instant createdAt) {
        this.groupBuyId = groupBuyId;
        this.memberId = memberId;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
