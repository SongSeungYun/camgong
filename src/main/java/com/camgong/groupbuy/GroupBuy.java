package com.camgong.groupbuy;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.camgong.member.Member;

/** 시간은 DB 기본값·@CreatedDate 없이 서비스가 Instant.now(clock)으로 넣는다. 상태 전이 메서드는 US3에서 추가. */
@Entity
@Table(name = "group_buy")
public class GroupBuy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id")
    private Long organizerId;

    /** 조회 전용(003). 같은 컬럼의 읽기 전용 연관관계라 쓰기 경로에서는 접근하지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", insertable = false, updatable = false)
    private Member organizer;

    private String title;

    private String description;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "unit_price")
    private int unitPrice;

    @Column(name = "total_quantity")
    private int totalQuantity;

    @Column(name = "secured_quantity")
    private int securedQuantity;

    private Instant deadline;

    @Enumerated(EnumType.STRING)
    private GroupBuyCategory category;

    @Column(name = "item_url")
    private String itemUrl;

    @Column(name = "open_chat_url")
    private String openChatUrl;

    @Enumerated(EnumType.STRING)
    private GroupBuyStatus status;

    @Version
    private long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected GroupBuy() {
    }

    /** 새 공구는 항상 OPEN. 확보 수량은 개설자 수량으로 시작한다. */
    public GroupBuy(Long organizerId, String title, String description, String itemName, int unitPrice,
            int totalQuantity, int securedQuantity, Instant deadline, GroupBuyCategory category,
            String itemUrl, String openChatUrl, Instant now) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.totalQuantity = totalQuantity;
        this.securedQuantity = securedQuantity;
        this.deadline = deadline;
        this.category = category;
        this.itemUrl = itemUrl;
        this.openChatUrl = openChatUrl;
        this.status = GroupBuyStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 수정·취소 허용 조건: OPEN 이고 마감 전(FR-015). */
    public boolean isModifiable(Instant now) {
        return status == GroupBuyStatus.OPEN && deadline.isAfter(now);
    }

    /**
     * 확보 수량이 총 수량에 닿으면 COMPLETED 로 바꾼다. 모집 완료 판정은 이 메서드 하나만 한다.
     * 이미 COMPLETED 이거나 아직 덜 찼으면 변화 없음.
     *
     * @return 이번 호출로 COMPLETED 가 되었으면 true
     */
    public boolean completeIfFull() {
        if (status == GroupBuyStatus.OPEN && securedQuantity == totalQuantity) {
            status = GroupBuyStatus.COMPLETED;
            return true;
        }
        return false;
    }

    /** OPEN 에서만 CANCELLED 로 간다. 행은 지우지 않는다. */
    public void cancel(Instant now) {
        if (status != GroupBuyStatus.OPEN) {
            throw new IllegalStateException("OPEN 이 아닌 공구는 취소할 수 없다: " + status);
        }
        status = GroupBuyStatus.CANCELLED;
        updatedAt = now;
    }

    /** 개설자·생성 시각·상태는 바꾸지 않는다. 호출자가 락 안에서 검증한 값만 넘긴다. */
    public void update(String title, String description, String itemName, int unitPrice, int totalQuantity,
            int securedQuantity, Instant deadline, GroupBuyCategory category, String itemUrl, String openChatUrl,
            Instant now) {
        this.title = title;
        this.description = description;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.totalQuantity = totalQuantity;
        this.securedQuantity = securedQuantity;
        this.deadline = deadline;
        this.category = category;
        this.itemUrl = itemUrl;
        this.openChatUrl = openChatUrl;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    /** 조회 전용(003). 쓰기 경로에서는 부르지 않는다. */
    public Member getOrganizer() {
        return organizer;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getItemName() {
        return itemName;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getSecuredQuantity() {
        return securedQuantity;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public GroupBuyCategory getCategory() {
        return category;
    }

    public String getItemUrl() {
        return itemUrl;
    }

    public String getOpenChatUrl() {
        return openChatUrl;
    }

    public GroupBuyStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
