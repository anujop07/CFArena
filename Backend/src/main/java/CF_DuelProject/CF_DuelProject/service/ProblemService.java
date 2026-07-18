package CF_DuelProject.CF_DuelProject.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

import CF_DuelProject.CF_DuelProject.model.Problem;

@Service
public class ProblemService {
    private FetchProblemService fetchProblemService;
    public ProblemService(FetchProblemService fetchProblemService) {
        this.fetchProblemService = fetchProblemService;
    }


    public List<String> getMatchProblems(String u1, String u2,String difficulty) {
        Set<String> solved1 = fetchProblemService.fetchSolved(u1);
        Set<String> solved2 = fetchProblemService.fetchSolved(u2);

        Set<String> common = new HashSet<>(solved1);
        common.addAll(solved2);

        List<Integer> ratings;
        if (difficulty == null) difficulty = "EASY";
        switch (difficulty.toUpperCase()) {
            case "MEDIUM":
                ratings = List.of(800, 1400, 1500, 1500, 1600);
                break;
            case "HARD":
                ratings = List.of(1300, 1500, 1600, 1700, 1800);
                break;
            case "EASY":
            default:
                ratings = List.of(800, 800, 1000, 1200, 1600);
                break;
        }
        List<String> result = new ArrayList<>();

        for (int r : ratings) {
            Problem p = pickProblem(r, common);
            if (p != null) {
                String id = p.getContestId() + "-" + p.getIndex();
                common.add(id);
                result.add("https://codeforces.com/contest/"+p.getContestId()+"/problem/"+p.getIndex());
            }
        }

        return result;
    }

    private Problem pickProblem(int rating, Set<String> solved) {
        List<Problem> list = fetchProblemService.getShuffledProblems(rating);
        for (Problem p : list) {
            String id = p.getContestId() + "-" + p.getIndex();
            
            // here is the main ...
            // i want t
            if (!solved.contains(id)) {
                return p;
            }
        }
        return null;
    }
}