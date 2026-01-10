package com.yuu.vnpay.service;

import com.yuu.vnpay.entity.Seat;
import com.yuu.vnpay.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SeatService {

    @Autowired
    private SeatRepository seatRepository;

    public synchronized List<Seat> getSeats() {
        if (seatRepository.count() == 0) {
            String[] ids = {"A1", "A2", "A3", "A4", "D1", "D2", "D3", "D4"};
            for (String id : ids) {
                // Ghế D3 lưu vào DB là TRUE (đã đặt)
                seatRepository.saveAndFlush(new Seat(id, id.equals("D3")));
            }
        }
        return seatRepository.findAll();
    }

    @Transactional
    public boolean confirmBooking(String seatId) {
        return seatRepository.findById(seatId).map(seat -> {
            if (!seat.isBooked()) {
                seat.setBooked(true);
                seatRepository.save(seat);
                return true;
            }
            return false;
        }).orElse(false);
    }
}