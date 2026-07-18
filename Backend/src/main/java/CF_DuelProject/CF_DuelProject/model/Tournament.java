package CF_DuelProject.CF_DuelProject.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "tournaments")
public class Tournament {

    @Id
    private String id;

    @Version
    private Long version;

    private String name;                         // "Weekend Blitz Cup"
    private String creatorId;                    // cfHandle of creator
    private String difficulty;                   // "EASY", "MEDIUM", or "HARD"
    private int maxPlayers;                      // 8, 16, 32
    private int matchDurationMinutes;            // per match

    private List<String> registeredPlayers = new ArrayList<>();

    private String status;                       // REGISTRATION, ONGOING, FINISHED, CANCELLED

    // THE BRACKET — flat array per round
    // Key: round number (1-based), Value: player list for that round
    private Map<Integer, List<String>> bracket = new HashMap<>();

    // Match invite codes for each round
    // Key: round number, Value: list of invite codes for matches in that round
    private Map<Integer, List<String>> matchCodes = new HashMap<>();

    private int currentRound;
    private int totalRounds;                     // log2(maxPlayers)

    @Indexed(unique = true)
    private String inviteCode;                   // tournament sharing code
    private Date createdAt;
    private String winnerId;
}
