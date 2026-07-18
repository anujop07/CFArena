package CF_DuelProject.CF_DuelProject.scheduler;

import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchScheduler {

    private final PrimaryMatchRepository matchRepository;
    private final MatchService matchService;

    @Scheduled(fixedRate = 5000)
    public void runScheduler() {

        List<MatchPrimary> matches = matchRepository.findAll();
        log.debug("===== Scheduler Tick =====");
            for (MatchPrimary match : matches) {
            matchService.processMatch(match);
        }
    }
}