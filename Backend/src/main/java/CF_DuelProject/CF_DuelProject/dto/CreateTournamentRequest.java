package CF_DuelProject.CF_DuelProject.dto;

import java.util.Map;

public class CreateTournamentRequest {
    public String name;
    public int maxPlayers;           // 8, 16, 32
    public String difficulty;        // "EASY", "MEDIUM", or "HARD"
    public int matchDurationMinutes;
}
