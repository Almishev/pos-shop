package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.PromotionRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface PromotionRuleRepository extends JpaRepository<PromotionRuleEntity, Long> {

    Optional<PromotionRuleEntity> findByRuleId(String ruleId);
    
    List<PromotionRuleEntity> findByIsActiveTrue();
    
    List<PromotionRuleEntity> findByRuleTypeAndIsActiveTrue(PromotionRuleEntity.RuleType ruleType);
    
    List<PromotionRuleEntity> findByTargetItemIdAndIsActiveTrue(String itemId);
    
    List<PromotionRuleEntity> findByTargetCategoryIdAndIsActiveTrue(String categoryId);
    
    @Query("SELECT pr FROM PromotionRuleEntity pr WHERE pr.isActive = true AND " +
           "(pr.validFrom IS NULL OR pr.validFrom <= :currentTime) AND " +
           "(pr.validUntil IS NULL OR pr.validUntil >= :currentTime)")
    List<PromotionRuleEntity> findActiveRulesAtTime(@Param("currentTime") Timestamp currentTime);
    
    @Query("SELECT pr FROM PromotionRuleEntity pr WHERE pr.isActive = true AND " +
           "pr.ruleType = :ruleType AND " +
           "(pr.validFrom IS NULL OR pr.validFrom <= :currentTime) AND " +
           "(pr.validUntil IS NULL OR pr.validUntil >= :currentTime) " +
           "ORDER BY pr.priority DESC")
    List<PromotionRuleEntity> findActiveRulesByType(@Param("ruleType") PromotionRuleEntity.RuleType ruleType, 
                                                   @Param("currentTime") Timestamp currentTime);
    
    @Query("SELECT COUNT(pr) FROM PromotionRuleEntity pr WHERE pr.isActive = true")
    Long countActiveRules();
    
    @Query("SELECT pr FROM PromotionRuleEntity pr ORDER BY pr.currentUsage DESC")
    List<PromotionRuleEntity> findMostUsedPromotions(Integer limit);
}
