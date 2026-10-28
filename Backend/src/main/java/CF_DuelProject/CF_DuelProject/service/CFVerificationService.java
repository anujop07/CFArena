package CF_DuelProject.CF_DuelProject.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.model.CFVerification;
import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.CFVerificationRepository;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CFVerificationService {

    private final UserRepository userRepository;
    private final CFVerificationRepository cfVerificationRepository;
    private final PrimaryMatchRepository primaryMatchRepository;
    private final SecondaryMatchRepository secondaryMatchRepository;
    private final CodeforcesService codeforcesService;
    private RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom secureRandom = new SecureRandom();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DEFAULT_CONTEST_ID = 4;
    private static final String DEFAULT_PROBLEM_INDEX = "A";
    private static final String DEFAULT_PROBLEM_NAME = "Watermelon";
    private static final String DEFAULT_PROBLEM_URL = "https://codeforces.com/problemset/problem/4/A";

    public boolean isUserInActiveMatch(User user) {
        if (user == null) return false;
        String handle = user.getCodeforcesHandle();
        String email = user.getEmail();

        // If user has no CF handle and is not verified, they cannot be in a match
        if ((handle == null || handle.isBlank()) && !user.isCfVerified()) {
            return false;
        }

        // 1. Check live primary matches
        List<MatchPrimary> primaryMatches = primaryMatchRepository.findAll();
        for (MatchPrimary m : primaryMatches) {
            if ("ONGOING".equalsIgnoreCase(m.getStatus())) {
                if (matchesPlayer(m.getPlayer1CFHandle(), m.getUser1(), handle, email) ||
                    matchesPlayer(m.getPlayer2CFHandle(), m.getUser2(), handle, email)) {
                    return true;
                }
            }
        }

        // 2. Check active secondary lobbies
        List<MatchSecondary> activeSecondary = secondaryMatchRepository.findByStatusIn(List.of("WAITING", "READY", "ONGOING"));
        for (MatchSecondary m : activeSecondary) {
            if (matchesPlayer(m.getPlayer1CFHandle(), m.getUser1(), handle, email) ||
                matchesPlayer(m.getPlayer2CFHandle(), m.getUser2(), handle, email)) {
                return true;
            }
        }

        return false;
    }


    private boolean matchesPlayer(String matchHandle, String matchUser, String userHandle) {
        if (userHandle == null || userHandle.isBlank()) return false;
        if (matchHandle != null && matchHandle.equalsIgnoreCase(userHandle)) return true;
        if (matchUser != null && matchUser.equalsIgnoreCase(userHandle)) return true;
        return false;
    }

    private boolean matchesPlayer(String matchHandle, String matchUser, String userHandle, String userEmail) {
        // Check by CF handle
        if (userHandle != null && !userHandle.isBlank()) {
            if (matchHandle != null && matchHandle.equalsIgnoreCase(userHandle)) return true;
            if (matchUser != null && matchUser.equalsIgnoreCase(userHandle)) return true;
        }
        // Check by email (matches may store email in user1/user2)
        if (userEmail != null && !userEmail.isBlank()) {
            if (matchUser != null && matchUser.equalsIgnoreCase(userEmail)) return true;
        }
        return false;
    }


    private String generateUniqueVerificationCode() {
        StringBuilder sb = new StringBuilder("CF-ARENA-");
        for (int i = 0; i < 5; i++) {
            sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String buildCodeSnippet(String verificationCode) {
        return "// " + verificationCode + "\n" +
               "#include <iostream>\n" +
               "int main() {\n" +
               "    cf_arena_verification_error;\n" +
               "    return 0;\n" +
               "}";
    }

    public Map<String, Object> generateChallenge(User user, String handle) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated.");
        }

        // ✅ Permanent Linking Rule: Verified users CANNOT change their Codeforces account
        if (user.isCfVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codeforces account is already verified and cannot be changed.");
        }

        if (handle == null || handle.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid Codeforces handle.");
        }

        // Active match protection
        if (isUserInActiveMatch(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot verify Codeforces account while participating in an active match.");
        }

        // Step 1: Verify that the account exists on Codeforces via official user.info
        Map<String, Object> cfInfo = codeforcesService.verifyHandle(handle.trim());
        String officialHandle = (String) cfInfo.get("handle");
        if (officialHandle == null || officialHandle.isBlank()) {
            officialHandle = handle.trim();
        }

        // Step 2: Duplicate CF account protection across different users
        List<User> existingUsers = userRepository.findAllByCodeforcesHandleIgnoreCase(officialHandle);
        boolean isLinkedToAnother = existingUsers.stream()
                .anyMatch(u -> !u.getId().equals(user.getId()) && u.isCfVerified());
        if (isLinkedToAnother) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This Codeforces account is already linked to another CF Arena account.");
        }


        // Step 3: Expire any existing pending verification challenges for this user
        List<CFVerification> existingPending = cfVerificationRepository.findByUserIdAndStatus(user.getId(), "PENDING");
        for (CFVerification v : existingPending) {
            v.setStatus("EXPIRED");
            cfVerificationRepository.save(v);
        }

        // Step 4: Generate unique verification code (e.g. CF-ARENA-7K29X) and problem assignment
        String verificationCode = generateUniqueVerificationCode();
        String codeSnippet = buildCodeSnippet(verificationCode);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        CFVerification verification = new CFVerification();
        verification.setUserId(user.getId());
        verification.setUserEmail(user.getEmail());
        verification.setCfHandle(officialHandle);
        verification.setVerificationCode(verificationCode);
        verification.setContestId(DEFAULT_CONTEST_ID);
        verification.setProblemIndex(DEFAULT_PROBLEM_INDEX);
        verification.setProblemName(DEFAULT_PROBLEM_NAME);
        verification.setProblemUrl(DEFAULT_PROBLEM_URL);
        verification.setCodeSnippet(codeSnippet);
        verification.setCreatedAt(now);
        verification.setExpiresAt(expiresAt);
        verification.setStatus("PENDING");

        cfVerificationRepository.save(verification);
        log.info("Generated CF verification challenge '{}' for user {} ({}) on problem {} ({}{})",
                verificationCode, user.getEmail(), officialHandle, DEFAULT_PROBLEM_NAME, DEFAULT_CONTEST_ID, DEFAULT_PROBLEM_INDEX);

        Map<String, Object> result = new HashMap<>();
        result.put("challengeId", verification.getId());
        result.put("cfHandle", officialHandle);
        result.put("verificationCode", verificationCode);
        result.put("contestId", DEFAULT_CONTEST_ID);
        result.put("problemIndex", DEFAULT_PROBLEM_INDEX);
        result.put("problemName", DEFAULT_PROBLEM_NAME);
        result.put("problemUrl", DEFAULT_PROBLEM_URL);
        result.put("codeSnippet", codeSnippet);
        result.put("createdAt", now.toString());
        result.put("expiresAt", expiresAt.toString());
        result.put("rating", cfInfo.get("rating"));
        result.put("rank", cfInfo.get("rank"));
        result.put("avatar", cfInfo.get("avatar"));
        result.put("message", "Please submit the verification code snippet on Codeforces problem " + DEFAULT_CONTEST_ID + DEFAULT_PROBLEM_INDEX + " (" + DEFAULT_PROBLEM_NAME + ") using account '" + officialHandle + "'.");

        return result;
    }

    public Map<String, Object> checkVerification(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated.");
        }

        if (user.isCfVerified()) {
            return Map.of(
                    "verified", true,
                    "handle", user.getCfHandle(),
                    "message", "Codeforces account is already verified."
            );
        }

        CFVerification challenge = cfVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active verification challenge found. Please generate a new verification code."));

        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            challenge.setStatus("EXPIRED");
            cfVerificationRepository.save(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification request expired. Please generate a new verification code.");
        }

        // Duplicate check
        List<User> existingUsers = userRepository.findAllByCodeforcesHandleIgnoreCase(challenge.getCfHandle());
        boolean isLinkedToAnother = existingUsers.stream()
                .anyMatch(u -> !u.getId().equals(user.getId()) && u.isCfVerified());
        if (isLinkedToAnother) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This Codeforces account is already linked to another CF Arena account.");
        }


        String cfHandle = challenge.getCfHandle();

        // 1. Fetch fresh submissions dynamically every time
        String statusUrl = "https://codeforces.com/api/user.status?handle=" + URLEncoder.encode(cfHandle, StandardCharsets.UTF_8) + "&from=1&count=10";

        Map resp;
        try {
            resp = restTemplate.getForObject(statusUrl, Map.class);
        } catch (Exception e) {
            log.error("Failed to query Codeforces user.status API for {}: {}", cfHandle, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to check Codeforces submissions right now. Please try again later.");
        }

        if (resp == null || !"OK".equalsIgnoreCase(String.valueOf(resp.get("status")))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to check Codeforces submissions right now. Please try again later.");
        }

        List<Map<String, Object>> submissions = (List<Map<String, Object>>) resp.get("result");
        if (submissions == null || submissions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No Codeforces submission was found yet. Please submit the verification code on the assigned problem and try again.");
        }

        // 2. Identify the latest submission dynamically based on newest id / creationTimeSeconds
        Map<String, Object> latestSub = submissions.get(0);
        for (Map<String, Object> s : submissions) {
            long sTime = s.containsKey("creationTimeSeconds") ? ((Number) s.get("creationTimeSeconds")).longValue() : 0;
            long latestTime = latestSub.containsKey("creationTimeSeconds") ? ((Number) latestSub.get("creationTimeSeconds")).longValue() : 0;
            long sId = s.containsKey("id") ? ((Number) s.get("id")).longValue() : 0;
            long latestId = latestSub.containsKey("id") ? ((Number) latestSub.get("id")).longValue() : 0;

            if (sTime > latestTime || (sTime == latestTime && sId > latestId)) {
                latestSub = s;
            }
        }

        // 3. Verify handle match
        boolean handleMatches = false;
        Map<String, Object> author = (Map<String, Object>) latestSub.get("author");
        if (author != null && author.containsKey("members")) {
            List<Map<String, Object>> members = (List<Map<String, Object>>) author.get("members");
            if (members != null) {
                for (Map<String, Object> m : members) {
                    String h = (String) m.get("handle");
                    if (h != null && h.equalsIgnoreCase(cfHandle)) {
                        handleMatches = true;
                        break;
                    }
                }
            }
        }

        if (!handleMatches) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The latest submission does not belong to the Codeforces handle '" + cfHandle + "'.");
        }

        // 4. Check problem matches assigned problem
        if (latestSub.containsKey("problem")) {
            Map<String, Object> prob = (Map<String, Object>) latestSub.get("problem");
            if (prob != null) {
                int cId = prob.containsKey("contestId") ? ((Number) prob.get("contestId")).intValue() : 0;
                String pIdx = (String) prob.get("index");
                int expectedContestId = challenge.getContestId() > 0 ? challenge.getContestId() : DEFAULT_CONTEST_ID;
                String expectedIndex = challenge.getProblemIndex() != null ? challenge.getProblemIndex() : DEFAULT_PROBLEM_INDEX;

                if (cId != expectedContestId || (pIdx != null && !expectedIndex.equalsIgnoreCase(pIdx))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The latest Codeforces submission is for problem " + cId + pIdx + ", but the assigned problem is " + expectedContestId + expectedIndex + " (" + (challenge.getProblemName() != null ? challenge.getProblemName() : DEFAULT_PROBLEM_NAME) + ").");
                }
            }
        }

        // 5. Check verdict of the latest submission
        String verdict = (String) latestSub.get("verdict");
        long latestSubId = latestSub.containsKey("id") ? ((Number) latestSub.get("id")).longValue() : 0;

        if ("COMPILATION_ERROR".equalsIgnoreCase(verdict)) {
            // ✅ VERIFICATION SUCCESS!
            challenge.setStatus("VERIFIED");
            challenge.setVerifiedAt(Instant.now());
            challenge.setSubmissionId(latestSubId);
            cfVerificationRepository.save(challenge);

            user.setCfHandle(cfHandle);
            user.setCodeforcesHandle(cfHandle);
            user.setCfVerified(true);
            user.setCodeforcesVerified(true);
            user.setCfVerifiedAt(Instant.now());
            user.setCodeforcesVerifiedAt(Instant.now());
            
            userRepository.save(user);

            log.info("🎉 Codeforces handle '{}' successfully verified via latest CE submission #{} and permanently linked to user {}",
                    cfHandle, latestSubId, user.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("verified", true);
            response.put("handle", cfHandle);
            response.put("codeforcesHandle", cfHandle);
            response.put("submissionId", latestSubId);
            response.put("verifiedAt", user.getCfVerifiedAt().toString());
            response.put("message", "Codeforces account successfully verified and permanently linked!");

            return response;
        }

        // If latest submission is not a Compilation Error
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Your latest Codeforces submission is not a Compilation Error. Please submit the verification code and try again.");
    }

    public Map<String, Object> getActiveChallenge(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated.");
        }

        if (user.isCfVerified()) {
            return Map.of(
                    "active", false,
                    "verified", true,
                    "handle", user.getCfHandle()
            );
        }

        Optional<CFVerification> opt = cfVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING");
        if (opt.isEmpty()) {
            return Map.of("active", false, "verified", false);
        }

        CFVerification challenge = opt.get();
        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            challenge.setStatus("EXPIRED");
            cfVerificationRepository.save(challenge);
            return Map.of("active", false, "verified", false);
        }

        String code = challenge.getVerificationCode();
        String snippet = challenge.getCodeSnippet() != null ? challenge.getCodeSnippet() : buildCodeSnippet(code);

        Map<String, Object> result = new HashMap<>();
        result.put("active", true);
        result.put("challengeId", challenge.getId());
        result.put("cfHandle", challenge.getCfHandle());
        result.put("verificationCode", code);
        result.put("contestId", challenge.getContestId() > 0 ? challenge.getContestId() : DEFAULT_CONTEST_ID);
        result.put("problemIndex", challenge.getProblemIndex() != null ? challenge.getProblemIndex() : DEFAULT_PROBLEM_INDEX);
        result.put("problemName", challenge.getProblemName() != null ? challenge.getProblemName() : DEFAULT_PROBLEM_NAME);
        result.put("problemUrl", challenge.getProblemUrl() != null ? challenge.getProblemUrl() : DEFAULT_PROBLEM_URL);
        result.put("codeSnippet", snippet);
        result.put("createdAt", challenge.getCreatedAt().toString());
        result.put("expiresAt", challenge.getExpiresAt().toString());

        return result;
    }
}

