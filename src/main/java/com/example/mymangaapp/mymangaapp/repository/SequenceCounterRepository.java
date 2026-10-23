package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.SequenceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SequenceCounterRepository extends JpaRepository<SequenceCounter, String> {

    // lock row trong transaction đảm bảo 2 request ko đồng thời lấy cùng số
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SequenceCounter s WHERE s.name = :name")
    Optional<SequenceCounter> findForUpdate(@Param("name") String name);

}