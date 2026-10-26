package com.camgong.groupbuy;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 시간은 DB 기본값·@CreatedDate 없이 서비스가 Instant.now(clock)으로 넣는다. 상태 전이 메서드는 US3에서 추가. */
@Entity
@Table(name = "group_buy")
public class GroupBuy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id")
    private Long organizerId;

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

    public Long getId() {
        return id;
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
