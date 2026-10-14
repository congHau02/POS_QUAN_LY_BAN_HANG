package learn.java.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_modifier_groups")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModifierGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String groupId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Integer minSelections = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxSelections = 1;

    @OneToMany(mappedBy = "modifierGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ModifierEntity> modifiers = new ArrayList<>();

    @ManyToMany(mappedBy = "modifierGroups")
    @Builder.Default
    private List<ItemEntity> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;
}
