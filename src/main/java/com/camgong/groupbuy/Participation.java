package com.camgong.groupbuy;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.camgong.member.Member;

/** 쓰기는 Long 컬럼으로 하고, 조회 전용 연관관계(003)를 같은 컬럼에 읽기 전용으로 병행한다. */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", insertable = false, updatable = false)
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    private Member member;

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

    /** 조회 전용. 쓰기 경로에서는 부르지 않는다. */
    public GroupBuy getGroupBuy() {
        return groupBuy;
    }

    public Member getMember() {
        return member;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
