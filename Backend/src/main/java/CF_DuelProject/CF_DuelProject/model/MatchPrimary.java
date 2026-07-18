package CF_DuelProject.CF_DuelProject.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "primary_matches")
public class MatchPrimary {
  
    @Id
    private String id;
    private String difficulty;
    private String user1;
    private String user2;
    private int score1;
    private int score2;
    private List<String> problems;
    private int curIdx;
    private String status; 
    private String winnerId;
    private Date startTime;
    private Date endTime;
    @Indexed(unique = true)
    private String inviteCode;
    private Map<Integer, String> player1Results = new HashMap<>();
    private Map<Integer, String> player2Results = new HashMap<>();

    private boolean skipVoteUser1;
    private boolean skipVoteUser2;

    private String tournamentId;
    private Integer bracketRound;
}
