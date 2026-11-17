package CF_DuelProject.CF_DuelProject.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import CF_DuelProject.CF_DuelProject.model.CFVerification;

public interface CFVerificationRepository extends MongoRepository<CFVerification, String> {

    Optional<CFVerification> findTopByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

    Optional<CFVerification> findTopByUserIdOrderByCreatedAtDesc(String userId);

    List<CFVerification> findByUserId(String userId);

    List<CFVerification> findByUserIdAndStatus(String userId, String status);
}
