package com.yuu.vnpay.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeatService {
    private final Map<String, Boolean> seatMap = new ConcurrentHashMap<>();

    public SeatService() {
        seatMap.put("A1", false);
        seatMap.put("A2", false);
        seatMap.put("A3", false);
        seatMap.put("A4", false);
    }

    public synchronized boolean checkAndBook(String seatId) {
        if (seatMap.containsKey(seatId) && !seatMap.get(seatId)) {
            seatMap.put(seatId, true);
            return true;
        }
        return false;
    }

    public Map<String, Boolean> getSeats() { return seatMap; }
}