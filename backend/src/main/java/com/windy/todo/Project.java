package com.windy.todo;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") AppUser owner;
    @Column(nullable = false, length = 100) String name;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected Project() {}
    public Long getId() { return id; }
    Project(AppUser owner, String name) { this.owner = owner; this.name = name; this.createdAt = Instant.now(); }
}
