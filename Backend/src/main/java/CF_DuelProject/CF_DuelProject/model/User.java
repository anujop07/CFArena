package CF_DuelProject.CF_DuelProject.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "users")
public class User {
  
    @Id
    private String id;

    private String name;
    // unique: two simultaneous registrations (or Google + local) can never create two accounts for one e-mail
    @org.springframework.data.mongodb.core.index.Indexed(unique = true)
    private String email;
    private String password;

    private String provider; 

    private String cfHandle;
    private Integer rating;
    private boolean cfVerified = false;

    // Codeforces verification fields
    private String codeforcesHandle;
    private boolean codeforcesVerified = false;

    private Instant codeforcesVerifiedAt;
    private Instant cfVerifiedAt;
    private String codeforcesSubject;

    // ✅ Role-based access control — set manually in DB for admin users
    private boolean admin = false;

    public String getCfHandle() {
        if (this.codeforcesHandle != null && !this.codeforcesHandle.isBlank()) {
            return this.codeforcesHandle;
        }
        return this.cfHandle;
    }

    public void setCfHandle(String cfHandle) {
        this.cfHandle = cfHandle;
        this.codeforcesHandle = cfHandle;
    }

    public String getCodeforcesHandle() {
        if (this.codeforcesHandle != null && !this.codeforcesHandle.isBlank()) {
            return this.codeforcesHandle;
        }
        return this.cfHandle;
    }

    public void setCodeforcesHandle(String codeforcesHandle) {
        this.codeforcesHandle = codeforcesHandle;
        this.cfHandle = codeforcesHandle;
    }

    public boolean isCfVerified() {
        return this.codeforcesVerified || this.cfVerified;
    }

    public void setCfVerified(boolean cfVerified) {
        this.cfVerified = cfVerified;
        this.codeforcesVerified = cfVerified;
    }

    public boolean isCodeforcesVerified() {
        return this.codeforcesVerified || this.cfVerified;
    }

    public void setCodeforcesVerified(boolean codeforcesVerified) {
        this.codeforcesVerified = codeforcesVerified;
        this.cfVerified = codeforcesVerified;
    }
}

