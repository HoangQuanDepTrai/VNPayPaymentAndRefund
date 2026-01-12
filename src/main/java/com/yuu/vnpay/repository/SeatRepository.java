package com.yuu.vnpay.repository;

import com.yuu.vnpay.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, String> {
    // Nếu sau này cần tìm ghế trống thì thêm ở đây:
    // List<Seat> findByBookedFalse();
}