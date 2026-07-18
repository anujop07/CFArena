package CF_DuelProject.CF_DuelProject.scheduler;

import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class TournamentScheduler {

    private final TournamentRepository tournamentRepository;
    private final TournamentService tournamentService;
    private final MatchService matchService;

    @Scheduled(fixedRate = 30000) // every 30 seconds
    public void checkRoundStartTimes() {

        // Rounds are now started manually by the tournament admin
        // No automatic time-based scheduling is needed.
    }
}
