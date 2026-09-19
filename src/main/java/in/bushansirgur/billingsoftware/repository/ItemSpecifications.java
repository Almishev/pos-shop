package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ItemSpecifications {

    private ItemSpecifications() {}

    public static Specification<ItemEntity> withFilters(
            String search,
            String searchBy,
            String categoryName,
            String stockStatus
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                String by = searchBy == null ? "name" : searchBy.trim().toLowerCase();
                if ("barcode".equals(by)) {
                    predicates.add(cb.like(cb.lower(cb.coalesce(root.get("barcode"), "")), like));
                } else {
                    predicates.add(cb.like(cb.lower(root.get("name")), like));
                }
            }

            if (categoryName != null && !categoryName.isBlank()) {
                Join<Object, Object> category = root.join("category", JoinType.LEFT);
                predicates.add(cb.equal(category.get("name"), categoryName.trim()));
            }

            if (stockStatus != null && !stockStatus.isBlank()) {
                String status = stockStatus.trim().toUpperCase();
                switch (status) {
                    case "OUT_OF_STOCK" -> predicates.add(cb.or(
                            cb.isNull(root.get("stockQuantity")),
                            cb.lessThanOrEqualTo(root.get("stockQuantity"), 0)
                    ));
                    case "LOW_STOCK" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("stockQuantity")),
                            cb.greaterThan(root.get("stockQuantity"), 0),
                            cb.isNotNull(root.get("reorderPoint")),
                            cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("reorderPoint"))
                    ));
                    case "OVERSTOCK" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("stockQuantity")),
                            cb.isNotNull(root.get("maxStockLevel")),
                            cb.greaterThan(root.get("stockQuantity"), root.get("maxStockLevel"))
                    ));
                    case "NORMAL" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("stockQuantity")),
                            cb.greaterThan(root.get("stockQuantity"), 0),
                            cb.or(
                                    cb.isNull(root.get("reorderPoint")),
                                    cb.greaterThan(root.get("stockQuantity"), root.get("reorderPoint"))
                            ),
                            cb.or(
                                    cb.isNull(root.get("maxStockLevel")),
                                    cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("maxStockLevel"))
                            )
                    ));
                    default -> { /* ignore unknown */ }
                }
            }

            if (query != null && ItemEntity.class.equals(query.getResultType())) {
                root.fetch("category", JoinType.LEFT);
                query.distinct(true);
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
