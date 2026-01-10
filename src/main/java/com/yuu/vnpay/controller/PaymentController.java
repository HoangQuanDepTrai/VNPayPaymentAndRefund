package com.yuu.vnpay.controller;

import com.yuu.vnpay.config.VNPayConfig;
import com.yuu.vnpay.service.SeatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class PaymentController {
    @Autowired private SeatService seatService;
    @Autowired private VNPayConfig vnpayConfig;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("seats", seatService.getSeats());
        return "index";
    }

    @PostMapping("/pay")
    public String createPayment(@RequestParam String seatId, @RequestParam long amount) throws Exception {
        String vnp_TxnRef = seatId + "-" + System.currentTimeMillis();
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpayConfig.vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan ghe " + seatId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpayConfig.vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", "127.0.0.1");
        vnp_Params.put("vnp_CreateDate", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append("=")
                    .append(URLEncoder.encode(vnp_Params.get(fieldName), StandardCharsets.US_ASCII)).append("&");
        }
        String vnp_SecureHash = vnpayConfig.hashAllFields(vnp_Params);
        // Chị Lunar lưu ý: Thêm dấu & trước vnp_SecureHash nhen
        return "redirect:" + vnpayConfig.vnp_PayUrl + "?" + query.toString() + "vnp_SecureHash=" + vnp_SecureHash;
    }

    @GetMapping("/vnpay-callback")
    public String callback(HttpServletRequest request, Model model) {
        String responseCode = request.getParameter("vnp_ResponseCode");
        String txnRef = request.getParameter("vnp_TxnRef");
        String seatId = (txnRef != null) ? txnRef.split("-")[0] : "Unknown";

        if ("00".equals(responseCode)) {
            if (seatService.checkAndBook(seatId)) {
                model.addAttribute("msg", "🎉 Thành công rồi chị Lunar ơi! Ghế " + seatId + " đã là của chị.");
            } else {
                model.addAttribute("msg", "⚠️ Ối! Ghế " + seatId + " đã bị đặt mất rồi. Hệ thống sẽ Refund cho chị nhé.");
                System.out.println(">>> Đang gọi Refund cho giao dịch: " + txnRef);
            }
        } else {
            model.addAttribute("msg", "❌ Thanh toán thất bại rồi chị Lunar ạ.");
        }
        return "result";
    }
}