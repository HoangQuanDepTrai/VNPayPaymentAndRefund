package com.yuu.vnpay.controller;

import com.yuu.vnpay.config.VNPayConfig;
import com.yuu.vnpay.service.SeatService;
import com.yuu.vnpay.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class PaymentController {
    @Autowired private VNPayConfig vnpConfig;
    @Autowired private SeatService seatService;
    @Autowired private RestTemplate restTemplate;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("seats", seatService.getSeats());
        return "index";
    }

    // 🚩 ĐÂY NÈ! Hàm xử lý khi khách bấm nút thanh toán
    @PostMapping("/pay")
    public String createPayment(@RequestParam long amount, @RequestParam String seatId, HttpServletRequest request) throws Exception {
        Map<String, String> vnp_Params = new TreeMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpConfig.getVnp_TmnCode());
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // VNPay tính theo đơn vị xu (VND * 100)
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", VNPayUtil.getRandomNumber(8)); // Mã giao dịch ngẫu nhiên
        vnp_Params.put("vnp_OrderInfo", "Ghe:" + seatId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpConfig.getVnp_ReturnUrl());
        vnp_Params.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));
        vnp_Params.put("vnp_CreateDate", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

        // Tạo chuỗi Hash để bảo mật giao dịch
        String paymentUrl = vnpConfig.getVnp_PayUrl() + "?" + VNPayUtil.hashAllFields(vnp_Params, vnpConfig.getVnp_HashSecret());

        // Nhớ thêm tham số vnp_SecureHash vào cuối URL (Hàm hashAllFields tôi viết ở VNPayUtil đã lo việc này)
        // Nhưng nếu ông giáo dùng hàm tự viết thì chú ý đoạn này
        String queryUrl = "";
        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
            }
        }
        hashData.setLength(hashData.length() - 1);
        query.setLength(query.length() - 1);
        String vnp_SecureHash = VNPayUtil.hmacSHA512(vnpConfig.getVnp_HashSecret(), hashData.toString());
        queryUrl = query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;

        return "redirect:" + vnpConfig.getVnp_PayUrl() + "?" + queryUrl;
    }

    @GetMapping("/vnpay-callback")
    public String callback(HttpServletRequest request, Model model) {
        String finalMsg = "❌ Chữ ký không hợp lệ!";
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String k = params.nextElement();
            String v = request.getParameter(k);
            if (v != null && v.length() > 0) fields.put(k, v);
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        fields.remove("vnp_SecureHash");

        // Kiểm tra chữ ký trả về
        String checkSum = VNPayUtil.hashAllFields(fields, vnpConfig.getVnp_HashSecret());

        if (checkSum.equalsIgnoreCase(vnp_SecureHash)) {
            String responseCode = request.getParameter("vnp_ResponseCode");
            String seatId = request.getParameter("vnp_OrderInfo").replace("Ghe:", "");
            String txnRef = request.getParameter("vnp_TxnRef");
            String transDate = request.getParameter("vnp_PayDate");
            long amount = Long.parseLong(request.getParameter("vnp_Amount")) / 100;

            if ("00".equals(responseCode)) {
                if (seatService.confirmBooking(seatId)) {
                    finalMsg = "✅ Đặt thành công ghế " + seatId;
                } else {
                    // 💸 THỰC HIỆN REFUND THẬT SANG VNPAY
                    String refundStatus = executeRefund(txnRef, amount, transDate, request);
                    finalMsg = "🧨 Ghế " + seatId + " đã có chủ! VNPay đã tiếp nhận lệnh Refund. Mã: " + refundStatus;
                }
            } else {
                finalMsg = "❌ Thanh toán thất bại hoặc đã bị hủy.";
            }
        }

        model.addAttribute("msg", finalMsg);
        return "result";
    }

    private String executeRefund(String txnRef, long amount, String transDate, HttpServletRequest request) {
        try {
            String vnp_RequestId = VNPayUtil.getRandomNumber(8);
            String vnp_Version = "2.1.0";
            String vnp_Command = "refund";
            String vnp_TmnCode = vnpConfig.getVnp_TmnCode();
            String vnp_TransactionType = "02";
            String vnp_TxnRef = txnRef;
            String vnp_Amount = String.valueOf(amount * 100);
            String vnp_OrderInfo = "Hoan tien ghe D3: " + txnRef;
            String vnp_TransactionNo = "0";
            String vnp_TransactionDate = transDate;
            String vnp_CreateBy = "Admin_Yuu";

            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String vnp_CreateDate = formatter.format(new Date());
            String vnp_IpAddr = VNPayUtil.getIpAddress(request);

            // Hash theo format riêng của WebAPI Refund
            String hashData = vnp_RequestId + "|" + vnp_Version + "|" + vnp_Command + "|" + vnp_TmnCode + "|" +
                    vnp_TransactionType + "|" + vnp_TxnRef + "|" + vnp_Amount + "|" + vnp_TransactionNo + "|" +
                    vnp_TransactionDate + "|" + vnp_CreateBy + "|" + vnp_CreateDate + "|" + vnp_IpAddr + "|" + vnp_OrderInfo;

            String vnp_SecureHash = VNPayUtil.hmacSHA512(vnpConfig.getVnp_HashSecret(), hashData);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vnp_RequestId", vnp_RequestId);
            body.put("vnp_Version", vnp_Version);
            body.put("vnp_Command", vnp_Command);
            body.put("vnp_TmnCode", vnp_TmnCode);
            body.put("vnp_TransactionType", vnp_TransactionType);
            body.put("vnp_TxnRef", vnp_TxnRef);
            body.put("vnp_Amount", vnp_Amount);
            body.put("vnp_TransactionNo", vnp_TransactionNo);
            body.put("vnp_TransactionDate", vnp_TransactionDate);
            body.put("vnp_CreateBy", vnp_CreateBy);
            body.put("vnp_CreateDate", vnp_CreateDate);
            body.put("vnp_IpAddr", vnp_IpAddr);
            body.put("vnp_OrderInfo", vnp_OrderInfo);
            body.put("vnp_SecureHash", vnp_SecureHash);

            String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            return response.getBody().get("vnp_ResponseCode").toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}