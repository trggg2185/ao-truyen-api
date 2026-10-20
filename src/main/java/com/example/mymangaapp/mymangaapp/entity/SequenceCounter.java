package com.example.mymangaapp.mymangaapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Entity
public class SequenceCounter extends BaseEntity {

    // Entity này lưu giá trị tiếp theo truyện hoặc nhóm dịch

    @Id
    String name;

    @Column(nullable = false)
    Long nextValue;
}
