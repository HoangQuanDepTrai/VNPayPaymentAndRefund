package com.yuu.vnpay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VNPayConfig {
    @Value("${vnpay.tmn-code}")
    private String vnp_TmnCode;

    @Value("${vnpay.hash-secret}")
    private String vnp_HashSecret;

    @Value("${vnpay.pay-url}")
    private String vnp_PayUrl;

    @Value("${vnpay.return-url}")
    private String vnp_ReturnUrl;

    // Viết tay Getter cho chắc ăn nè ông giáo!
    public String getVnp_TmnCode() { return vnp_TmnCode; }
    public String getVnp_HashSecret() { return vnp_HashSecret; }
    public String getVnp_PayUrl() { return vnp_PayUrl; }
    public String getVnp_ReturnUrl() { return vnp_ReturnUrl; }
}