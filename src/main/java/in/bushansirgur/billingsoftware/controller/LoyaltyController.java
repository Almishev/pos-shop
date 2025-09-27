package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.*;
import in.bushansirgur.billingsoftware.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/loyalty")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    // Customer management endpoints
    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@RequestBody CustomerRequest request) {
        CustomerResponse response = loyaltyService.createCustomer(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/customers/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable String customerId, @RequestBody CustomerRequest request) {
        CustomerResponse response = loyaltyService.updateCustomer(customerId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable String customerId) {
        CustomerResponse response = loyaltyService.getCustomerById(customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers/loyalty-card/{loyaltyCardBarcode}")
    public ResponseEntity<CustomerResponse> getCustomerByLoyaltyCard(@PathVariable String loyaltyCardBarcode) {
        CustomerResponse response = loyaltyService.getCustomerByLoyaltyCard(loyaltyCardBarcode);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers/phone/{phoneNumber}")
    public ResponseEntity<CustomerResponse> getCustomerByPhone(@PathVariable String phoneNumber) {
        CustomerResponse response = loyaltyService.getCustomerByPhone(phoneNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers/search")
    public ResponseEntity<List<CustomerResponse>> searchCustomers(@RequestParam String searchTerm) {
        List<CustomerResponse> response = loyaltyService.searchCustomers(searchTerm);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        List<CustomerResponse> response = loyaltyService.getAllCustomers();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/customers/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable String customerId) {
        loyaltyService.deleteCustomer(customerId);
        return ResponseEntity.ok().build();
    }

    // Loyalty card management endpoints
    @GetMapping("/loyalty-cards/generate")
    public ResponseEntity<Map<String, String>> generateLoyaltyCardBarcode() {
        String barcode = loyaltyService.generateLoyaltyCardBarcode();
        return ResponseEntity.ok(Map.of("barcode", barcode));
    }

    @PostMapping("/loyalty-cards/activate")
    public ResponseEntity<CustomerResponse> activateLoyaltyCard(@RequestBody Map<String, String> request) {
        String customerId = request.get("customerId");
        String loyaltyCardBarcode = request.get("loyaltyCardBarcode");
        CustomerResponse response = loyaltyService.activateLoyaltyCard(customerId, loyaltyCardBarcode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/loyalty-cards/deactivate")
    public ResponseEntity<CustomerResponse> deactivateLoyaltyCard(@RequestBody Map<String, String> request) {
        String customerId = request.get("customerId");
        CustomerResponse response = loyaltyService.deactivateLoyaltyCard(customerId);
        return ResponseEntity.ok(response);
    }

    // Promotion rules management endpoints
    @PostMapping("/promotion-rules")
    public ResponseEntity<PromotionRuleResponse> createPromotionRule(@RequestBody PromotionRuleRequest request) {
        PromotionRuleResponse response = loyaltyService.createPromotionRule(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/promotion-rules/{ruleId}")
    public ResponseEntity<PromotionRuleResponse> updatePromotionRule(@PathVariable String ruleId, @RequestBody PromotionRuleRequest request) {
        PromotionRuleResponse response = loyaltyService.updatePromotionRule(ruleId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/promotion-rules/{ruleId}")
    public ResponseEntity<PromotionRuleResponse> getPromotionRuleById(@PathVariable String ruleId) {
        PromotionRuleResponse response = loyaltyService.getPromotionRuleById(ruleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/promotion-rules")
    public ResponseEntity<List<PromotionRuleResponse>> getAllPromotionRules() {
        List<PromotionRuleResponse> response = loyaltyService.getAllPromotionRules();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/promotion-rules/active")
    public ResponseEntity<List<PromotionRuleResponse>> getActivePromotionRules() {
        List<PromotionRuleResponse> response = loyaltyService.getActivePromotionRules();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/promotion-rules/{ruleId}")
    public ResponseEntity<Void> deletePromotionRule(@PathVariable String ruleId) {
        loyaltyService.deletePromotionRule(ruleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/promotion-rules/{ruleId}/activate")
    public ResponseEntity<Void> activatePromotionRule(@PathVariable String ruleId) {
        loyaltyService.activatePromotionRule(ruleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/promotion-rules/{ruleId}/deactivate")
    public ResponseEntity<Void> deactivatePromotionRule(@PathVariable String ruleId) {
        loyaltyService.deactivatePromotionRule(ruleId);
        return ResponseEntity.ok().build();
    }

    // Discount calculation endpoint
    @PostMapping("/calculate-discounts")
    public ResponseEntity<DiscountCalculationResponse> calculateDiscounts(@RequestBody DiscountCalculationRequest request) {
        DiscountCalculationResponse response = loyaltyService.calculateDiscounts(request);
        return ResponseEntity.ok(response);
    }

    // Loyalty points management endpoints
    @PostMapping("/customers/{customerId}/points/add")
    public ResponseEntity<CustomerResponse> addLoyaltyPoints(@PathVariable String customerId, 
                                                           @RequestBody Map<String, Object> request) {
        Integer points = (Integer) request.get("points");
        String orderId = (String) request.get("orderId");
        CustomerResponse response = loyaltyService.addLoyaltyPoints(customerId, points, orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/customers/{customerId}/points/redeem")
    public ResponseEntity<CustomerResponse> redeemLoyaltyPoints(@PathVariable String customerId, 
                                                              @RequestBody Map<String, Object> request) {
        Integer points = (Integer) request.get("points");
        String orderId = (String) request.get("orderId");
        CustomerResponse response = loyaltyService.redeemLoyaltyPoints(customerId, points, orderId);
        return ResponseEntity.ok(response);
    }

    // Analytics and reports endpoints
    @GetMapping("/analytics/top-customers")
    public ResponseEntity<List<CustomerResponse>> getTopCustomers(@RequestParam(defaultValue = "10") Integer limit) {
        List<CustomerResponse> response = loyaltyService.getTopCustomers(limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/most-used-promotions")
    public ResponseEntity<List<PromotionRuleResponse>> getMostUsedPromotions(@RequestParam(defaultValue = "10") Integer limit) {
        List<PromotionRuleResponse> response = loyaltyService.getMostUsedPromotions(limit);
        return ResponseEntity.ok(response);
    }
}
