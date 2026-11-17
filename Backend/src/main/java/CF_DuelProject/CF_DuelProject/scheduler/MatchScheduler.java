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

    // fixedDelay: the next tick starts 5 s after the previous one FINISHED. Codeforces calls are throttled to
    // one per ~2 s, so a tick with many live matches can take a while and must not overlap itself.
    @Scheduled(fixedDelay = 5000)
    public void runScheduler() {

        List<MatchPrimary> matches = matchRepository.findAll();
        log.debug("===== Scheduler Tick =====");
        for (MatchPrimary match : matches) {
            try {
                matchService.processMatch(match);
            } catch (Exception e) {
                // one broken match must not stop the others from being scored
                log.error("Processing match {} failed: {}", match.getInviteCode(), e.toString());
            }
        }
    }
}