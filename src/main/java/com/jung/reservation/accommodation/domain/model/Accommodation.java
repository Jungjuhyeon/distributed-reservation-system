package com.jung.reservation.accommodation.domain.model;

import com.jung.reservation.common.entity.BaseEntity;
import com.jung.reservation.user.domain.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accommodation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accommodation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private String description;

    private Accommodation(User host, String name, String address, String description) {
        this.host = host;
        this.name = name;
        this.address = address;
        this.description = description;
    }

    public static Accommodation create(User host, String name, String address) {
        return new Accommodation(host, name, address, null);
    }

    public static Accommodation create(User host, String name, String address, String description) {
        return new Accommodation(host, name, address, description);
    }
}
