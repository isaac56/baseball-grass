package team9.baseball.domain.aggregate.game;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import team9.baseball.domain.aggregate.team.Team;
import team9.baseball.domain.enums.GameStatus;
import team9.baseball.domain.enums.Halves;
import team9.baseball.domain.enums.PitchResult;
import team9.baseball.exception.BadStatusException;
import team9.baseball.exception.NotFoundException;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {
    @Id
    private Long id;

    private Integer awayTeamId;

    private Integer homeTeamId;

    private Integer currentInning;

    private Halves currentHalves;

    private Integer pitcherUniformNumber;

    private Integer batterUniformNumber;

    private Integer base1UniformNumber;

    private Integer base2UniformNumber;

    private Integer base3UniformNumber;

    private int strikeCount;

    private int ballCount;

    private int outCount;

    private GameStatus status;

    @MappedCollection(idColumn = "game_id", keyColumn = "key_in_game")
    private Map<String, BattingHistory> battingHistoryMap = new HashMap<>();

    @MappedCollection(idColumn = "game_id", keyColumn = "key_in_game")
    private Map<String, Inning> inningMap = new HashMap<>();

    private final int STRIKE_OUT_COUNT = 3;
    private final int INNING_OUT_COUNT = 3;

    public Game(Team awayTeam, Team homeTeam) {
        this.awayTeamId = awayTeam.getId();
        this.homeTeamId = homeTeam.getId();
        initializeBattingHistory(awayTeam);
        initializeBattingHistory(homeTeam);

        this.pitcherUniformNumber = homeTeam.getFirstPlayerUniformNumber();
        sendBatterOnPlate(awayTeamId, awayTeam.getFirstPlayerUniformNumber());

        this.currentInning = 1;
        this.currentHalves = Halves.TOP;
        this.inningMap.put(Inning.acquireKeyInGame(currentInning, currentHalves), new Inning(currentInning, currentHalves));

        this.status = GameStatus.WAITING;
    }

    private void initializeBattingHistory(Team team) {
        for (Integer uniform_number : team.getPlayerMap().keySet()) {
            String key = BattingHistory.acquireKeyInGame(team.getId(), uniform_number);
            BattingHistory battingHistory = new BattingHistory(team.getId(), uniform_number);
            this.battingHistoryMap.put(key, battingHistory);
        }
    }

    public void checkWaiting() {
        if (this.status != GameStatus.WAITING) {
            throw new BadStatusException("???????????? ????????? ????????????.");
        }
    }

    public void checkPlaying() {
        if (this.status != GameStatus.PLAYING) {
            throw new BadStatusException("???????????? ????????? ????????????.");
        }
    }

    public void proceedStrike(Team awayTeam, Team homeTeam) {
        //????????? ??????
        this.strikeCount++;
        //????????? pitch history ??????
        PitchHistory pitchHistory = new PitchHistory(acquireDefenseTeamId(), pitcherUniformNumber,
                acquireAttackTeamId(), batterUniformNumber, PitchResult.STRIKE, this.strikeCount, this.ballCount);
        //?????? ????????? pitch history ??????
        acquireCurrentInning().pitchHistoryList.add(pitchHistory);

        //?????? ?????? ??????
        if (strikeCount == STRIKE_OUT_COUNT) {
            proceedOut(awayTeam, homeTeam);
        }
    }

    public void proceedBall(Team awayTeam, Team homeTeam) {
        //????????? ??????
        this.ballCount++;
        //????????? pitch history ??????
        PitchHistory pitchHistory = new PitchHistory(acquireDefenseTeamId(), pitcherUniformNumber,
                acquireAttackTeamId(), batterUniformNumber, PitchResult.BALL, this.strikeCount, this.ballCount);
        //?????? ????????? pitch history ??????
        acquireCurrentInning().pitchHistoryList.add(pitchHistory);

        //????????? ?????? ???????????? ?????? ?????? ??????
        if (ballCount == 4) {
            sendBatterOnBase();

            Team attackTeam = acquireAttackTeam(awayTeam, homeTeam);
            sendBatterOnPlate(attackTeam.getId(), attackTeam.getNextPlayerUniformNumber(batterUniformNumber));
        }
    }

    public void proceedHit(Team awayTeam, Team homeTeam) {
        //????????? pitch history ??????
        PitchHistory pitchHistory = new PitchHistory(acquireDefenseTeamId(), pitcherUniformNumber,
                acquireAttackTeamId(), batterUniformNumber, PitchResult.HIT, this.strikeCount, this.ballCount);
        //?????? ????????? pitch history ??????
        acquireCurrentInning().pitchHistoryList.add(pitchHistory);

        //????????? battingHistory ??? ?????? ????????? ??????
        Team attackTeam = acquireAttackTeam(awayTeam, homeTeam);
        BattingHistory battingHistory = acquireBattingHistory(attackTeam.getId(), batterUniformNumber);
        battingHistory.plusHits();

        //?????? ??????
        sendBatterOnBase();

        //????????? ?????? ?????? ??????
        sendBatterOnPlate(attackTeam.getId(), attackTeam.getNextPlayerUniformNumber(batterUniformNumber));
    }

    public int getTotalScore(Halves halves) {
        return inningMap.values().stream().filter(x -> x.getHalves() == halves).mapToInt(x -> x.getScore()).sum();
    }

    public Team acquireAttackTeam(Team awayTeam, Team homeTeam) {
        if (currentHalves == Halves.TOP) {
            return awayTeam;
        }
        return homeTeam;
    }

    public Team acquireDefenseTeam(Team awayTeam, Team homeTeam) {
        if (currentHalves == Halves.TOP) {
            return homeTeam;
        }
        return awayTeam;
    }

    public Inning acquireCurrentInning() {
        String currentInningKey = Inning.acquireKeyInGame(currentInning, currentHalves);
        return inningMap.get(currentInningKey);
    }

    private BattingHistory acquireBattingHistory(int batterTeamId, int batterUniformNumber) {
        String key = BattingHistory.acquireKeyInGame(batterTeamId, batterUniformNumber);
        if (!battingHistoryMap.containsKey(key)) {
            throw new NotFoundException(String.format("%d??? ???????????? %d??? %d ?????? ????????? ?????? ????????? ????????????.",
                    this.id, batterTeamId, batterUniformNumber));
        }
        return battingHistoryMap.get(key);
    }

    public String acquireBatterStatus() {
        int attackTeamId = acquireAttackTeamId();
        BattingHistory batterHistory = acquireBattingHistory(attackTeamId, this.batterUniformNumber);
        return batterHistory.getStatus();
    }

    public String acquirePitcherStatus() {
        int defenseTeamId = acquireDefenseTeamId();
        long pitcherCount = inningMap.values().stream()
                .flatMap(inning -> inning.getPitchHistoryList().stream())
                .filter(pitchHistory -> pitchHistory.hasMatchedPitcher(defenseTeamId, this.pitcherUniformNumber))
                .count();

        return "#" + pitcherCount;
    }

    public Integer getAwayPlayingUniformNumber() {
        return this.currentHalves == Halves.TOP ? batterUniformNumber : pitcherUniformNumber;
    }

    public Integer getHomePlayingUniformNumber() {
        return this.currentHalves == Halves.BOTTOM ? batterUniformNumber : pitcherUniformNumber;
    }

    private void proceedOut(Team awayTeam, Team homeTeam) {
        //?????? ????????? ??????
        this.outCount++;

        //????????? battingHistory ??? ?????? ????????? ??????
        String battingHistoryKey = BattingHistory.acquireKeyInGame(acquireAttackTeamId(), batterUniformNumber);
        BattingHistory battingHistory = battingHistoryMap.get(battingHistoryKey);
        battingHistory.plusOut();

        //3??? ???????????? ?????????????????? ??????
        if (outCount == INNING_OUT_COUNT) {
            goToNextInning(awayTeam, homeTeam);
            return;
        }

        //????????? ?????? ?????? ??????
        Team attackTeam = acquireAttackTeam(awayTeam, homeTeam);
        sendBatterOnPlate(attackTeam.getId(), attackTeam.getNextPlayerUniformNumber(batterUniformNumber));
    }

    private void sendBatterOnBase() {
        //3?????? ????????? ???????????? ??????
        if (this.base3UniformNumber != null) {
            acquireCurrentInning().plusScore();
        }

        //????????? 1?????? ??????
        this.base3UniformNumber = this.base2UniformNumber;
        this.base2UniformNumber = this.base1UniformNumber;
        this.base1UniformNumber = this.batterUniformNumber;
    }

    private void goToNextInning(Team awayTeam, Team homeTeam) {
        //?????? ?????? ???????????? ?????????????????? ???????????? ?????? ????????????
        if (isExited()) {
            this.status = GameStatus.EXITED;
            return;
        }

        //????????? ?????????
        this.strikeCount = 0;
        this.ballCount = 0;
        this.outCount = 0;
        this.base1UniformNumber = null;
        this.base2UniformNumber = null;
        this.base3UniformNumber = null;

        //?????? ???????????? ??????
        if (this.currentHalves == Halves.BOTTOM) {
            this.currentHalves = Halves.TOP;
            this.currentInning += 1;
        } else {
            this.currentHalves = Halves.BOTTOM;
        }
        this.inningMap.put(Inning.acquireKeyInGame(currentInning, currentHalves), new Inning(currentInning, currentHalves));

        //?????? ????????? ????????? ????????? ??????
        Team attackTeam = acquireAttackTeam(awayTeam, homeTeam);
        Team defenseTeam = acquireDefenseTeam(awayTeam, homeTeam);

        //???????????? ?????? ?????? (?????? ????????? ?????? ????????? ????????? ???)
        int nextPitcherUniformNumber = defenseTeam.getNextPlayerUniformNumber(batterUniformNumber);
        //????????? ?????? ?????? (?????? ????????? ?????? ???????????? ????????? ???)
        int nextBatterUniformNumber = attackTeam.getNextPlayerUniformNumber(pitcherUniformNumber);
        this.pitcherUniformNumber = nextPitcherUniformNumber;
        sendBatterOnPlate(attackTeam.getId(), nextBatterUniformNumber);
    }

    private boolean isScoreDifferent() {
        return getTotalScore(Halves.TOP) != getTotalScore(Halves.BOTTOM);
    }

    private boolean isExited() {
        if ((currentInning == 9 && currentHalves == Halves.BOTTOM && isScoreDifferent())
                || (currentInning == 12 && currentHalves == Halves.BOTTOM)) {
            return true;
        }

        return false;
    }

    private void sendBatterOnPlate(int batterTeamId, int nextBatterUniformNumber) {
        //????????? ?????????
        this.strikeCount = 0;
        this.ballCount = 0;

        //????????? ?????? ?????? ??????
        this.batterUniformNumber = nextBatterUniformNumber;

        //????????? BatterHistory ??? ?????? ????????? ??????
        String battingHistoryKey = BattingHistory.acquireKeyInGame(batterTeamId, batterUniformNumber);
        BattingHistory battingHistory = battingHistoryMap.get(battingHistoryKey);
        battingHistory.plusAppear();
    }

    private int acquireAttackTeamId() {
        if (currentHalves == Halves.TOP) {
            return awayTeamId;
        }
        return homeTeamId;
    }

    private int acquireDefenseTeamId() {
        if (currentHalves == Halves.TOP) {
            return homeTeamId;
        }
        return awayTeamId;
    }
}
