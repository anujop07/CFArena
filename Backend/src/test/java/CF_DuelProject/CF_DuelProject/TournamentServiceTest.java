package CF_DuelProject.CF_DuelProject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.CodeforcesService;
import CF_DuelProject.CF_DuelProject.service.TournamentService;

/** Mock-based tests of tournament advancement rules (no database). */
class TournamentServiceTest {

    private TournamentRepository tournaments;
    private SecondaryMatchRepository matches;
    private CodeforcesService codeforcesService;
    private TournamentService service;

    @BeforeEach
    void setUp() {
        tournaments = mock(TournamentRepository.class);
        matches = mock(SecondaryMatchRepository.class);
        codeforcesService = mock(CodeforcesService.class);
        service = new TournamentService(tournaments, mock(PrimaryMatchRepository.class), matches,
                mock(UserRepository.class), mock(SimpMessagingTemplate.class), codeforcesService);
        when(tournaments.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matches.save(any(MatchSecondary.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Tournament fourPlayerRound1() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("ONGOING");
        t.setMaxPlayers(8);
        t.setMatchDurationMinutes(30);
        t.setDifficulty("EASY");
        t.setCurrentRound(1);
        t.setTotalRounds(2);
        t.setBracket(new HashMap<>());
        t.getBracket().put(1, new ArrayList<>(List.of("alice", "bob", "carol", "dave")));
        t.setMatchCodes(new HashMap<>());
        t.getMatchCodes().put(1, new ArrayList<>(List.of("M1", "M2")));
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));
        return t;
    }

    private void result(String code, String status, String winner) {
        MatchSecondary m = new MatchSecondary();
        m.setInviteCode(code);
        m.setStatus(status);
        m.setWinnerId(winner);
        when(matches.findByInviteCode(code)).thenReturn(Optional.of(m));
    }

    @Test
    void advance_usesOnlyFinishedResultsAndPutsWinnersInTheNextRound() {
        Tournament t = fourPlayerRound1();
        result("M1", "FINISHED", "bob");
        result("M2", "FINISHED", "CAROL"); // stored in a different case: normalised to the bracket spelling

        Tournament out = service.advanceRoundManual("t1", "alice");

        assertEquals(2, out.getCurrentRound());
        assertEquals(List.of("bob", "carol"), out.getBracket().get(2));
    }

    @Test
    void advance_onlyTheOrganizerMayAdvance() {
        fourPlayerRound1();
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.advanceRoundManual("t1", "bob")).getStatusCode());
    }

    @Test
    void advance_refusesWhileAnyMatchIsNotFinal() {
        fourPlayerRound1();
        result("M1", "FINISHED", "bob");
        result("M2", "READY", null);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.advanceRoundManual("t1", "alice")).getStatusCode());
        verify(tournaments, never()).save(any());
    }

    @Test
    void advance_cancelledMatchNeverPromotesANonPlayer() {
        Tournament t = fourPlayerRound1();
        result("M1", "FINISHED", "CANCELLED");
        result("M2", "FINISHED", "dave");

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.advanceRoundManual("t1", "alice")).getStatusCode());
        assertEquals(1, t.getCurrentRound());
        verify(tournaments, never()).save(any());
    }

    @Test
    void advance_winnerWhoWasNotInThatMatchIsRejected() {
        fourPlayerRound1();
        result("M1", "FINISHED", "carol"); // carol played M2, not M1
        result("M2", "FINISHED", "dave");

        assertThrows(ResponseStatusException.class, () -> service.advanceRoundManual("t1", "alice"));
        verify(tournaments, never()).save(any());
    }

    @Test
    void advance_drawStillAdvancesExactlyOneOfThePlayersAndFinalCrownsAChampion() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("ONGOING");
        t.setMatchDurationMinutes(30);
        t.setCurrentRound(1);
        t.setBracket(new HashMap<>());
        t.getBracket().put(1, new ArrayList<>(List.of("alice", "bob")));
        t.setMatchCodes(new HashMap<>());
        t.getMatchCodes().put(1, new ArrayList<>(List.of("M1")));
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));
        result("M1", "FINISHED", "DRAW");

        Tournament out = service.advanceRoundManual("t1", "alice");

        assertEquals("FINISHED", out.getStatus());
        assertTrue(List.of("alice", "bob").contains(out.getWinnerId()));
    }

    @Test
    void advance_drawAdvancesPlayerWithHigherRating_player1() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("ONGOING");
        t.setMatchDurationMinutes(30);
        t.setCurrentRound(1);
        t.setBracket(new HashMap<>());
        t.getBracket().put(1, new ArrayList<>(List.of("alice", "bob")));
        t.setMatchCodes(new HashMap<>());
        t.getMatchCodes().put(1, new ArrayList<>(List.of("M1")));
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));
        result("M1", "FINISHED", "DRAW");

        // Alice (1800) vs Bob (1400) -> Alice has higher rating, so Alice advances
        when(codeforcesService.getUserRating("alice")).thenReturn(1800);
        when(codeforcesService.getUserRating("bob")).thenReturn(1400);

        Tournament out = service.advanceRoundManual("t1", "alice");

        assertEquals("FINISHED", out.getStatus());
        assertEquals("alice", out.getWinnerId());
    }

    @Test
    void advance_drawAdvancesPlayerWithHigherRating_player2() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("ONGOING");
        t.setMatchDurationMinutes(30);
        t.setCurrentRound(1);
        t.setBracket(new HashMap<>());
        t.getBracket().put(1, new ArrayList<>(List.of("alice", "bob")));
        t.setMatchCodes(new HashMap<>());
        t.getMatchCodes().put(1, new ArrayList<>(List.of("M1")));
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));
        result("M1", "FINISHED", "DRAW");

        // Alice (1200) vs Bob (1750) -> Bob has higher rating, so Bob advances
        when(codeforcesService.getUserRating("alice")).thenReturn(1200);
        when(codeforcesService.getUserRating("bob")).thenReturn(1750);

        Tournament out = service.advanceRoundManual("t1", "alice");

        assertEquals("FINISHED", out.getStatus());
        assertEquals("bob", out.getWinnerId());
    }

    @Test
    void advance_drawWithSameRatingPerformsCoinFlip() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("ONGOING");
        t.setMatchDurationMinutes(30);
        t.setCurrentRound(1);
        t.setBracket(new HashMap<>());
        t.getBracket().put(1, new ArrayList<>(List.of("alice", "bob")));
        t.setMatchCodes(new HashMap<>());
        t.getMatchCodes().put(1, new ArrayList<>(List.of("M1")));
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));
        result("M1", "FINISHED", "DRAW");

        // Both players have identical rating: 1500 == 1500
        when(codeforcesService.getUserRating("alice")).thenReturn(1500);
        when(codeforcesService.getUserRating("bob")).thenReturn(1500);

        Tournament out = service.advanceRoundManual("t1", "alice");

        assertEquals("FINISHED", out.getStatus());
        assertTrue(List.of("alice", "bob").contains(out.getWinnerId()));
    }

    @Test
    void advance_drawWithUnratedPlayer_ratedAdvances() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("ONGOING");
        t.setMatchDurationMinutes(30);
        t.setCurrentRound(1);
        t.setBracket(new HashMap<>());
        t.getBracket().put(1, new ArrayList<>(List.of("alice", "bob")));
        t.setMatchCodes(new HashMap<>());
        t.getMatchCodes().put(1, new ArrayList<>(List.of("M1")));
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));
        result("M1", "FINISHED", "DRAW");

        // Alice has 1300, Bob is unrated (returns 0 or null)
        when(codeforcesService.getUserRating("alice")).thenReturn(1300);
        when(codeforcesService.getUserRating("bob")).thenReturn(0);

        Tournament out = service.advanceRoundManual("t1", "alice");

        assertEquals("FINISHED", out.getStatus());
        assertEquals("alice", out.getWinnerId());
    }

    @Test
    void advance_concurrentSecondAdvanceIsAConflictNotAServerError() {
        fourPlayerRound1();
        result("M1", "FINISHED", "bob");
        result("M2", "FINISHED", "carol");
        when(tournaments.save(any(Tournament.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> service.advanceRoundManual("t1", "alice")).getStatusCode());
    }

    @Test
    void refereeCannotRegisterAsPlayer() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("REGISTRATION");
        t.setRegisteredPlayers(new ArrayList<>());
        t.setMaxPlayers(8);
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registerPlayer("t1", "alice"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("referee"));
    }

    @Test
    void requestToJoinAndAcceptDeclineFlow() {
        Tournament t = new Tournament();
        t.setId("t1");
        t.setCreatorId("alice");
        t.setStatus("REGISTRATION");
        t.setRegisteredPlayers(new ArrayList<>());
        t.setJoinRequests(new ArrayList<>());
        t.setMaxPlayers(8);
        when(tournaments.findById("t1")).thenReturn(Optional.of(t));

        // Creator cannot request to join
        assertThrows(ResponseStatusException.class, () -> service.requestToJoin("t1", "alice"));

        // Bob requests to join
        service.requestToJoin("t1", "bob");
        assertTrue(t.getJoinRequests().contains("bob"));

        // Non-organizer cannot accept
        assertThrows(ResponseStatusException.class, () -> service.acceptJoinRequest("t1", "bob", "charlie"));

        // Alice (organizer/referee) accepts Bob
        service.acceptJoinRequest("t1", "alice", "bob");
        assertTrue(t.getRegisteredPlayers().contains("bob"));
        assertTrue(!t.getJoinRequests().contains("bob"));
    }
}
