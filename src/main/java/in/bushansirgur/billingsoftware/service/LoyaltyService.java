package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.CustomerRequest;
import in.bushansirgur.billingsoftware.io.CustomerResponse;
import in.bushansirgur.billingsoftware.io.PromotionRuleRequest;
import in.bushansirgur.billingsoftware.io.PromotionRuleResponse;
import in.bushansirgur.billingsoftware.io.DiscountCalculationRequest;
import in.bushansirgur.billingsoftware.io.DiscountCalculationResponse;

import java.util.List;

public interface LoyaltyService {
    
    // Customer management
    CustomerResponse createCustomer(CustomerRequest request);
    CustomerResponse updateCustomer(String customerId, CustomerRequest request);
    CustomerResponse getCustomerById(String customerId);
    CustomerResponse getCustomerByLoyaltyCard(String loyaltyCardBarcode);
    CustomerResponse getCustomerByPhone(String phoneNumber);
    List<CustomerResponse> searchCustomers(String searchTerm);
    List<CustomerResponse> getAllCustomers();
    void deleteCustomer(String customerId);
    
    // Loyalty card management
    String generateLoyaltyCardBarcode();
    CustomerResponse activateLoyaltyCard(String customerId, String loyaltyCardBarcode);
    CustomerResponse deactivateLoyaltyCard(String customerId);
    
    // Promotion rules management
    PromotionRuleResponse createPromotionRule(PromotionRuleRequest request);
    PromotionRuleResponse updatePromotionRule(String ruleId, PromotionRuleRequest request);
    PromotionRuleResponse getPromotionRuleById(String ruleId);
    List<PromotionRuleResponse> getAllPromotionRules();
    List<PromotionRuleResponse> getActivePromotionRules();
    void deletePromotionRule(String ruleId);
    void activatePromotionRule(String ruleId);
    void deactivatePromotionRule(String ruleId);
    
    // Discount calculation engine
    DiscountCalculationResponse calculateDiscounts(DiscountCalculationRequest request);
    
    // Loyalty points management
    CustomerResponse addLoyaltyPoints(String customerId, Integer points, String orderId);
    CustomerResponse redeemLoyaltyPoints(String customerId, Integer points, String orderId);
    
    // Analytics and reports
    List<CustomerResponse> getTopCustomers(Integer limit);
    List<PromotionRuleResponse> getMostUsedPromotions(Integer limit);
}
