package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CustomerEntity;
import in.bushansirgur.billingsoftware.entity.PromotionRuleEntity;
import in.bushansirgur.billingsoftware.entity.LoyaltyTransactionEntity;
import in.bushansirgur.billingsoftware.io.*;
import in.bushansirgur.billingsoftware.repository.CustomerRepository;
import in.bushansirgur.billingsoftware.repository.PromotionRuleRepository;
import in.bushansirgur.billingsoftware.repository.LoyaltyTransactionRepository;
import in.bushansirgur.billingsoftware.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyServiceImpl implements LoyaltyService {

    private final CustomerRepository customerRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        // Check if phone number already exists
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            customerRepository.findByPhoneNumber(request.getPhoneNumber())
                    .ifPresent(customer -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, 
                                "Customer with phone number " + request.getPhoneNumber() + " already exists");
                    });
        }

        // Check if email already exists
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            customerRepository.findByEmail(request.getEmail())
                    .ifPresent(customer -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, 
                                "Customer with email " + request.getEmail() + " already exists");
                    });
        }

        CustomerEntity customer = CustomerEntity.builder()
                .customerId(UUID.randomUUID().toString())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .loyaltyCardBarcode(request.getLoyaltyCardBarcode())
                .isLoyaltyActive(request.getIsLoyaltyActive() != null ? request.getIsLoyaltyActive() : false)
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .notes(request.getNotes())
                .build();

        customer = customerRepository.save(customer);
        return convertToCustomerResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(String customerId, CustomerRequest request) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);

        // Update fields
        if (request.getFirstName() != null) customer.setFirstName(request.getFirstName());
        if (request.getLastName() != null) customer.setLastName(request.getLastName());
        if (request.getEmail() != null) customer.setEmail(request.getEmail());
        if (request.getPhoneNumber() != null) customer.setPhoneNumber(request.getPhoneNumber());
        if (request.getLoyaltyCardBarcode() != null) customer.setLoyaltyCardBarcode(request.getLoyaltyCardBarcode());
        if (request.getIsLoyaltyActive() != null) customer.setIsLoyaltyActive(request.getIsLoyaltyActive());
        if (request.getStatus() != null) customer.setStatus(request.getStatus());
        if (request.getNotes() != null) customer.setNotes(request.getNotes());

        customer = customerRepository.save(customer);
        return convertToCustomerResponse(customer);
    }

    @Override
    public CustomerResponse getCustomerById(String customerId) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);
        return convertToCustomerResponse(customer);
    }

    @Override
    public CustomerResponse getCustomerByLoyaltyCard(String loyaltyCardBarcode) {
        CustomerEntity customer = customerRepository.findByLoyaltyCardBarcode(loyaltyCardBarcode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return convertToCustomerResponse(customer);
    }

    @Override
    public CustomerResponse getCustomerByPhone(String phoneNumber) {
        CustomerEntity customer = customerRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return convertToCustomerResponse(customer);
    }

    @Override
    public List<CustomerResponse> searchCustomers(String searchTerm) {
        List<CustomerEntity> customers = customerRepository.searchCustomers(searchTerm);
        return customers.stream()
                .map(this::convertToCustomerResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {
        List<CustomerEntity> customers = customerRepository.findAll();
        return customers.stream()
                .map(this::convertToCustomerResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCustomer(String customerId) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);
        customerRepository.delete(customer);
    }

    @Override
    public String generateLoyaltyCardBarcode() {
        // Generate a unique EAN-13 barcode for loyalty card
        String base12 = String.valueOf(System.currentTimeMillis());
        if (base12.length() > 12) {
            base12 = base12.substring(base12.length() - 12);
        } else if (base12.length() < 12) {
            base12 = ("000000000000" + base12).substring(base12.length());
        }
        int checksum = computeEan13Checksum(base12);
        return base12 + checksum;
    }

    @Override
    @Transactional
    public CustomerResponse activateLoyaltyCard(String customerId, String loyaltyCardBarcode) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);

        // Check if barcode is already used
        customerRepository.findByLoyaltyCardBarcode(loyaltyCardBarcode)
                .ifPresent(existingCustomer -> {
                    if (!existingCustomer.getCustomerId().equals(customerId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, 
                                "Loyalty card barcode already in use");
                    }
                });

        customer.setLoyaltyCardBarcode(loyaltyCardBarcode);
        customer.setIsLoyaltyActive(true);
        customer = customerRepository.save(customer);

        // Create transaction record
        createLoyaltyTransaction(customerId, null, LoyaltyTransactionEntity.TransactionType.CARD_ACTIVATED, 
                0, 0, BigDecimal.ZERO, "Loyalty card activated", null);

        return convertToCustomerResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse deactivateLoyaltyCard(String customerId) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);

        customer.setIsLoyaltyActive(false);
        customer = customerRepository.save(customer);

        // Create transaction record
        createLoyaltyTransaction(customerId, null, LoyaltyTransactionEntity.TransactionType.CARD_DEACTIVATED, 
                0, 0, BigDecimal.ZERO, "Loyalty card deactivated", null);

        return convertToCustomerResponse(customer);
    }

    @Override
    @Transactional
    public PromotionRuleResponse createPromotionRule(PromotionRuleRequest request) {
        PromotionRuleEntity rule = PromotionRuleEntity.builder()
                .ruleId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .ruleType(PromotionRuleEntity.RuleType.valueOf(request.getRuleType()))
                .discountType(PromotionRuleEntity.DiscountType.valueOf(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .minimumQuantity(request.getMinimumQuantity())
                .minimumAmount(request.getMinimumAmount())
                .targetItemId(request.getTargetItemId())
                .targetCategoryId(request.getTargetCategoryId())
                .buyQuantity(request.getBuyQuantity())
                .getQuantity(request.getGetQuantity())
                .getDiscountPercentage(request.getGetDiscountPercentage())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .maxUsagePerCustomer(request.getMaxUsagePerCustomer())
                .maxTotalUsage(request.getMaxTotalUsage())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .requiresLoyaltyCard(request.getRequiresLoyaltyCard() != null ? request.getRequiresLoyaltyCard() : false)
                .minimumLoyaltyPoints(request.getMinimumLoyaltyPoints() != null ? request.getMinimumLoyaltyPoints() : 0)
                .build();

        rule = promotionRuleRepository.save(rule);
        return convertToPromotionRuleResponse(rule);
    }

    @Override
    public List<PromotionRuleResponse> getAllPromotionRules() {
        List<PromotionRuleEntity> rules = promotionRuleRepository.findAll();
        return rules.stream()
                .map(this::convertToPromotionRuleResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromotionRuleResponse> getActivePromotionRules() {
        List<PromotionRuleEntity> rules = promotionRuleRepository.findActiveRulesAtTime(Timestamp.valueOf(LocalDateTime.now()));
        return rules.stream()
                .map(this::convertToPromotionRuleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DiscountCalculationResponse calculateDiscounts(DiscountCalculationRequest request) {
        CustomerEntity customer = null;
        
        // Find customer by loyalty card or phone
        if (request.getLoyaltyCardBarcode() != null && !request.getLoyaltyCardBarcode().trim().isEmpty()) {
            customer = customerRepository.findByLoyaltyCardBarcode(request.getLoyaltyCardBarcode()).orElse(null);
        } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            customer = customerRepository.findByPhoneNumber(request.getPhoneNumber()).orElse(null);
        } else if (request.getCustomerId() != null && !request.getCustomerId().trim().isEmpty()) {
            customer = customerRepository.findByCustomerId(request.getCustomerId()).orElse(null);
        }

        // Get active promotion rules
        List<PromotionRuleEntity> activeRules = promotionRuleRepository.findActiveRulesAtTime(Timestamp.valueOf(LocalDateTime.now()));
        
        // Filter rules that require loyalty card if customer doesn't have one
        final CustomerEntity finalCustomer = customer;
        if (customer == null || !customer.getIsLoyaltyActive()) {
            activeRules = activeRules.stream()
                    .filter(rule -> !rule.getRequiresLoyaltyCard())
                    .collect(Collectors.toList());
        } else {
            // Filter rules by minimum loyalty points
            activeRules = activeRules.stream()
                    .filter(rule -> rule.getMinimumLoyaltyPoints() == null || 
                                   rule.getMinimumLoyaltyPoints() <= finalCustomer.getLoyaltyPoints())
                    .collect(Collectors.toList());
        }

        // Calculate discounts
        List<DiscountCalculationResponse.AppliedDiscount> appliedDiscounts = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<String> applicablePromotions = new ArrayList<>();

        for (DiscountCalculationRequest.CartItem cartItem : request.getCartItems()) {
            for (PromotionRuleEntity rule : activeRules) {
                DiscountCalculationResponse.AppliedDiscount discount = calculateItemDiscount(cartItem, rule, customer);
                if (discount != null && discount.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                    appliedDiscounts.add(discount);
                    totalDiscount = totalDiscount.add(discount.getDiscountAmount());
                    applicablePromotions.add(rule.getName());
                }
            }
        }

        BigDecimal finalAmount = request.getSubtotal().subtract(totalDiscount);

        return DiscountCalculationResponse.builder()
                .customerId(customer != null ? customer.getCustomerId() : null)
                .customerName(customer != null ? customer.getFirstName() + " " + customer.getLastName() : null)
                .customerLoyaltyPoints(customer != null ? customer.getLoyaltyPoints() : 0)
                .originalSubtotal(request.getSubtotal())
                .totalDiscount(totalDiscount)
                .finalAmount(finalAmount)
                .appliedDiscounts(appliedDiscounts)
                .applicablePromotions(applicablePromotions)
                .build();
    }

    private DiscountCalculationResponse.AppliedDiscount calculateItemDiscount(
            DiscountCalculationRequest.CartItem cartItem, 
            PromotionRuleEntity rule, 
            CustomerEntity customer) {
        
        // Check if rule applies to this item
        if (!isRuleApplicableToItem(cartItem, rule)) {
            return null;
        }

        BigDecimal originalPrice = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        BigDecimal discountAmount = BigDecimal.ZERO;

        switch (rule.getRuleType()) {
            case PRODUCT:
                discountAmount = calculateProductDiscount(cartItem, rule);
                break;
            case CATEGORY:
                discountAmount = calculateCategoryDiscount(cartItem, rule);
                break;
            case QUANTITY:
                discountAmount = calculateQuantityDiscount(cartItem, rule);
                break;
            case AMOUNT:
                // Amount-based discounts are calculated at cart level, not item level
                return null;
        }

        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal finalPrice = originalPrice.subtract(discountAmount);
            
            return DiscountCalculationResponse.AppliedDiscount.builder()
                    .ruleId(rule.getRuleId())
                    .ruleName(rule.getName())
                    .ruleType(rule.getRuleType().toString())
                    .itemId(cartItem.getItemId())
                    .itemName(cartItem.getItemName())
                    .quantity(cartItem.getQuantity())
                    .originalPrice(originalPrice)
                    .discountAmount(discountAmount)
                    .finalPrice(finalPrice)
                    .description(generateDiscountDescription(rule))
                    .build();
        }

        return null;
    }

    private boolean isRuleApplicableToItem(DiscountCalculationRequest.CartItem cartItem, PromotionRuleEntity rule) {
        switch (rule.getRuleType()) {
            case PRODUCT:
                return cartItem.getItemId().equals(rule.getTargetItemId()) || 
                       cartItem.getBarcode().equals(rule.getTargetItemId());
            case CATEGORY:
                return cartItem.getCategoryId().equals(rule.getTargetCategoryId());
            case QUANTITY:
                return cartItem.getQuantity() >= rule.getMinimumQuantity();
            default:
                return false;
        }
    }

    private BigDecimal calculateProductDiscount(DiscountCalculationRequest.CartItem cartItem, PromotionRuleEntity rule) {
        BigDecimal itemTotal = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        
        switch (rule.getDiscountType()) {
            case PERCENTAGE:
                return itemTotal.multiply(rule.getDiscountValue()).setScale(2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT:
                return rule.getDiscountValue().min(itemTotal);
            default:
                return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateCategoryDiscount(DiscountCalculationRequest.CartItem cartItem, PromotionRuleEntity rule) {
        return calculateProductDiscount(cartItem, rule);
    }

    private BigDecimal calculateQuantityDiscount(DiscountCalculationRequest.CartItem cartItem, PromotionRuleEntity rule) {
        if (rule.getDiscountType() == PromotionRuleEntity.DiscountType.BUY_X_GET_Y) {
            int buyQuantity = rule.getBuyQuantity();
            int getQuantity = rule.getGetQuantity();
            int itemQuantity = cartItem.getQuantity();
            
            int freeItems = (itemQuantity / buyQuantity) * getQuantity;
            BigDecimal freeAmount = cartItem.getPrice().multiply(BigDecimal.valueOf(freeItems));
            
            return freeAmount;
        }
        return BigDecimal.ZERO;
    }

    private String generateDiscountDescription(PromotionRuleEntity rule) {
        switch (rule.getDiscountType()) {
            case PERCENTAGE:
                return rule.getDiscountValue().multiply(BigDecimal.valueOf(100)) + "% отстъпка";
            case FIXED_AMOUNT:
                return rule.getDiscountValue() + " лв. отстъпка";
            case BUY_X_GET_Y:
                return "Купи " + rule.getBuyQuantity() + " вземи " + rule.getGetQuantity() + " безплатно";
            default:
                return "Промоция";
        }
    }

    // Helper methods for conversion
    private CustomerResponse convertToCustomerResponse(CustomerEntity customer) {
        return CustomerResponse.builder()
                .customerId(customer.getCustomerId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .loyaltyCardBarcode(customer.getLoyaltyCardBarcode())
                .isLoyaltyActive(customer.getIsLoyaltyActive())
                .loyaltyPoints(customer.getLoyaltyPoints())
                .totalSpent(customer.getTotalSpent())
                .totalOrders(customer.getTotalOrders())
                .status(customer.getStatus())
                .notes(customer.getNotes())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .lastPurchaseDate(customer.getLastPurchaseDate())
                .build();
    }

    private PromotionRuleResponse convertToPromotionRuleResponse(PromotionRuleEntity rule) {
        return PromotionRuleResponse.builder()
                .ruleId(rule.getRuleId())
                .name(rule.getName())
                .description(rule.getDescription())
                .ruleType(rule.getRuleType().toString())
                .discountType(rule.getDiscountType().toString())
                .discountValue(rule.getDiscountValue())
                .minimumQuantity(rule.getMinimumQuantity())
                .minimumAmount(rule.getMinimumAmount())
                .targetItemId(rule.getTargetItemId())
                .targetCategoryId(rule.getTargetCategoryId())
                .buyQuantity(rule.getBuyQuantity())
                .getQuantity(rule.getGetQuantity())
                .getDiscountPercentage(rule.getGetDiscountPercentage())
                .validFrom(rule.getValidFrom())
                .validUntil(rule.getValidUntil())
                .isActive(rule.getIsActive())
                .maxUsagePerCustomer(rule.getMaxUsagePerCustomer())
                .maxTotalUsage(rule.getMaxTotalUsage())
                .currentUsage(rule.getCurrentUsage())
                .priority(rule.getPriority())
                .requiresLoyaltyCard(rule.getRequiresLoyaltyCard())
                .minimumLoyaltyPoints(rule.getMinimumLoyaltyPoints())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private void createLoyaltyTransaction(String customerId, String orderId, 
                                        LoyaltyTransactionEntity.TransactionType transactionType,
                                        Integer pointsEarned, Integer pointsRedeemed, 
                                        BigDecimal discountAmount, String description, String notes) {
        LoyaltyTransactionEntity transaction = LoyaltyTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .orderId(orderId)
                .transactionType(transactionType)
                .pointsEarned(pointsEarned)
                .pointsRedeemed(pointsRedeemed)
                .discountAmount(discountAmount)
                .description(description)
                .notes(notes)
                .build();
        
        loyaltyTransactionRepository.save(transaction);
    }

    private int computeEan13Checksum(String base12Digits) {
        int sum = 0;
        for (int i = 0; i < base12Digits.length(); i++) {
            int digit = base12Digits.charAt(i) - '0';
            sum += digit * ((i % 2 == 0) ? 1 : 3);
        }
        return (10 - (sum % 10)) % 10;
    }

    // Placeholder implementations for remaining methods
    @Override
    @Transactional
    public PromotionRuleResponse updatePromotionRule(String ruleId, PromotionRuleRequest request) {
        PromotionRuleEntity rule = findPromotionRuleByRuleId(ruleId);

        // Update fields
        if (request.getName() != null) rule.setName(request.getName());
        if (request.getDescription() != null) rule.setDescription(request.getDescription());
        if (request.getRuleType() != null) rule.setRuleType(PromotionRuleEntity.RuleType.valueOf(request.getRuleType()));
        if (request.getDiscountType() != null) rule.setDiscountType(PromotionRuleEntity.DiscountType.valueOf(request.getDiscountType()));
        if (request.getDiscountValue() != null) rule.setDiscountValue(request.getDiscountValue());
        if (request.getMinimumQuantity() != null) rule.setMinimumQuantity(request.getMinimumQuantity());
        if (request.getMinimumAmount() != null) rule.setMinimumAmount(request.getMinimumAmount());
        if (request.getTargetItemId() != null) rule.setTargetItemId(request.getTargetItemId());
        if (request.getTargetCategoryId() != null) rule.setTargetCategoryId(request.getTargetCategoryId());
        if (request.getBuyQuantity() != null) rule.setBuyQuantity(request.getBuyQuantity());
        if (request.getGetQuantity() != null) rule.setGetQuantity(request.getGetQuantity());
        if (request.getGetDiscountPercentage() != null) rule.setGetDiscountPercentage(request.getGetDiscountPercentage());
        if (request.getValidFrom() != null) rule.setValidFrom(request.getValidFrom());
        if (request.getValidUntil() != null) rule.setValidUntil(request.getValidUntil());
        if (request.getIsActive() != null) rule.setIsActive(request.getIsActive());
        if (request.getMaxUsagePerCustomer() != null) rule.setMaxUsagePerCustomer(request.getMaxUsagePerCustomer());
        if (request.getMaxTotalUsage() != null) rule.setMaxTotalUsage(request.getMaxTotalUsage());
        if (request.getPriority() != null) rule.setPriority(request.getPriority());
        if (request.getRequiresLoyaltyCard() != null) rule.setRequiresLoyaltyCard(request.getRequiresLoyaltyCard());
        if (request.getMinimumLoyaltyPoints() != null) rule.setMinimumLoyaltyPoints(request.getMinimumLoyaltyPoints());

        rule = promotionRuleRepository.save(rule);
        return convertToPromotionRuleResponse(rule);
    }

    @Override
    public PromotionRuleResponse getPromotionRuleById(String ruleId) {
        PromotionRuleEntity rule = findPromotionRuleByRuleId(ruleId);
        return convertToPromotionRuleResponse(rule);
    }

    @Override
    @Transactional
    public void deletePromotionRule(String ruleId) {
        PromotionRuleEntity rule = findPromotionRuleByRuleId(ruleId);
        promotionRuleRepository.delete(rule);
    }

    @Override
    @Transactional
    public void activatePromotionRule(String ruleId) {
        PromotionRuleEntity rule = findPromotionRuleByRuleId(ruleId);
        rule.setIsActive(true);
        promotionRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public void deactivatePromotionRule(String ruleId) {
        PromotionRuleEntity rule = findPromotionRuleByRuleId(ruleId);
        rule.setIsActive(false);
        promotionRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public CustomerResponse addLoyaltyPoints(String customerId, Integer points, String orderId) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);

        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        customer = customerRepository.save(customer);

        // Create transaction record
        createLoyaltyTransaction(customerId, orderId, LoyaltyTransactionEntity.TransactionType.POINTS_EARNED, 
                points, 0, BigDecimal.ZERO, "Points earned from purchase", null);

        return convertToCustomerResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse redeemLoyaltyPoints(String customerId, Integer points, String orderId) {
        CustomerEntity customer = findCustomerByCustomerId(customerId);

        if (customer.getLoyaltyPoints() < points) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient loyalty points");
        }

        customer.setLoyaltyPoints(customer.getLoyaltyPoints() - points);
        customer = customerRepository.save(customer);

        // Create transaction record
        createLoyaltyTransaction(customerId, orderId, LoyaltyTransactionEntity.TransactionType.POINTS_REDEEMED, 
                0, points, BigDecimal.ZERO, "Points redeemed for discount", null);

        return convertToCustomerResponse(customer);
    }

    @Override
    public List<CustomerResponse> getTopCustomers(Integer limit) {
        List<CustomerEntity> customers = customerRepository.findTopCustomersByTotalSpent(limit);
        return customers.stream()
                .map(this::convertToCustomerResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromotionRuleResponse> getMostUsedPromotions(Integer limit) {
        List<PromotionRuleEntity> rules = promotionRuleRepository.findMostUsedPromotions(limit);
        return rules.stream()
                .map(this::convertToPromotionRuleResponse)
                .collect(Collectors.toList());
    }
    
    // Helper method to find promotion rule by ruleId
    private PromotionRuleEntity findPromotionRuleByRuleId(String ruleId) {
        return promotionRuleRepository.findByRuleId(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion rule not found"));
    }
    
    // Helper method to find customer by customerId
    private CustomerEntity findCustomerByCustomerId(String customerId) {
        return customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }
}
