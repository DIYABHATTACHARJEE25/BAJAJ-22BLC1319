package com.example.webhooksolver;

import com.example.webhooksolver.dto.WebhookResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartupRunner implements ApplicationRunner {

    private final RestTemplate restTemplate;

    @Value("${user.name}")
    private String userName;

    @Value("${user.email}")
    private String userEmail;

    @Value("${user.regNo}")
    private String regNo;

    @Value("${api.generate}")
    private String generateUrl;

    @Value("${api.submit}")
    private String submitUrl;

    public StartupRunner(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Step 1: Call generateWebhook
            Map<String, String> req = new HashMap<>();
            req.put("name", userName);
            req.put("regNo", regNo);
            req.put("email", userEmail);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(req, headers);

            System.out.println("Sending generateWebhook request to: " + generateUrl);
            ResponseEntity<WebhookResponse> resp =
                    restTemplate.postForEntity(generateUrl, entity, WebhookResponse.class);

            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                System.err.println("Failed to generate webhook. Status: " + resp.getStatusCode());
                return;
            }

            WebhookResponse body = resp.getBody();
            String webhookUrl = body.getWebhook();
            String accessToken = body.getAccessToken();

            System.out.println("Received webhook: " + webhookUrl);
            System.out.println("Received accessToken: " + (accessToken != null ? "[REDACTED]" : null));

            // Step 2: Pick Question 1 (regNo ends in 19 = odd)
            final String finalQuery = "SELECT p.AMOUNT AS SALARY, CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME, " +
                    "TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE, d.DEPARTMENT_NAME " +
                    "FROM PAYMENTS p " +
                    "JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID " +
                    "JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID " +
                    "WHERE DAY(p.PAYMENT_TIME) <> 1 " +
                    "AND p.AMOUNT = (SELECT MAX(AMOUNT) FROM PAYMENTS WHERE DAY(PAYMENT_TIME) <> 1);";

            // Step 3: Submit finalQuery
            HttpHeaders submitHeaders = new HttpHeaders();
            submitHeaders.setContentType(MediaType.APPLICATION_JSON);
            submitHeaders.set("Authorization", accessToken); // If fails, try "Bearer " + accessToken

            Map<String, String> submitBody = new HashMap<>();
            submitBody.put("finalQuery", finalQuery);

            HttpEntity<Map<String, String>> submitEntity = new HttpEntity<>(submitBody, submitHeaders);

            System.out.println("Submitting final query to: " + submitUrl);
            ResponseEntity<String> submitResp =
                    restTemplate.exchange(submitUrl, HttpMethod.POST, submitEntity, String.class);

            System.out.println("Submit response status: " + submitResp.getStatusCode());
            System.out.println("Submit response body: " + submitResp.getBody());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
