package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.UserRolePeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRolePeriodRepository extends JpaRepository<UserRolePeriodEntity, Long> {

    List<UserRolePeriodEntity> findByUserIdOrderByActiveFromAsc(String userId);

    Optional<UserRolePeriodEntity> findFirstByUserIdAndActiveToIsNullOrderByActiveFromDesc(String userId);

    List<UserRolePeriodEntity> findAllByOrderByActiveFromAsc();
}
