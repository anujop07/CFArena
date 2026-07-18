package CF_DuelProject.CF_DuelProject.repository;

import CF_DuelProject.CF_DuelProject.model.Tournament;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentRepository extends MongoRepository<Tournament, String> {

    Optional<Tournament> findByInviteCode(String inviteCode);

    List<Tournament> findByStatus(String status);

    List<Tournament> findByStatusIn(List<String> statuses);

    List<Tournament> findByRegisteredPlayersContaining(String cfHandle);
}
