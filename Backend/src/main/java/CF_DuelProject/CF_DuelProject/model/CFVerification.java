package CF_DuelProject.CF_DuelProject.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "cf_verifications")
public class CFVerification {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;

    private String cfHandle;

    private String verificationCode; // e.g. CF-ARENA-7K29X

    private int contestId = 4;

    private String problemIndex = "A";

    private String problemName = "Watermelon";

    private String problemUrl = "https://codeforces.com/problemset/problem/4/A";

    private String codeSnippet;

    private Long submissionId;

    private int blogEntryId = 1;

    private String blogEntryUrl = "https://codeforces.com/blog/entry/1";

    private Long commentId;

    private Instant createdAt;

    private Instant expiresAt;

    private String status; // PENDING, VERIFIED, EXPIRED

    private Instant verifiedAt;
}

