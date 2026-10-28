package CF_DuelProject.CF_DuelProject.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

import CF_DuelProject.CF_DuelProject.model.User;   

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findFirstByCodeforcesHandleIgnoreCase(String codeforcesHandle);

    Optional<User> findFirstByCfHandleIgnoreCase(String cfHandle);

    List<User> findAllByCodeforcesHandleIgnoreCase(String codeforcesHandle);

    List<User> findAllByCfHandleIgnoreCase(String cfHandle);

    default Optional<User> findByCodeforcesHandleIgnoreCase(String codeforcesHandle) {
        return findFirstByCodeforcesHandleIgnoreCase(codeforcesHandle);
    }

    default Optional<User> findByCfHandleIgnoreCase(String cfHandle) {
        return findFirstByCfHandleIgnoreCase(cfHandle);
    }
}


