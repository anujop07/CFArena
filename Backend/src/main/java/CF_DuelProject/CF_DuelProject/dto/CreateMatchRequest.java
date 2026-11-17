package CF_DuelProject.CF_DuelProject.dto;

import lombok.Data;

@Data
public class CreateMatchRequest {
    public int duration; 
    public int durationMinutes;
    public String difficulty;

    public int getDuration() {
        return durationMinutes > 0 ? durationMinutes : (duration > 0 ? duration : 30);
    }
}