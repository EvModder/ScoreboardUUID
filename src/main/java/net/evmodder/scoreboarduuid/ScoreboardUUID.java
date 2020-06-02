package net.evmodder.scoreboarduuid;

import com.github.crashdemons.scoreboarduuid.ScoreboardUpdateBehavior;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class ScoreboardUUID extends JavaPlugin implements Listener {

    List<String> scoresToUpdate;
    boolean resetOldScores;
    ScoreboardUpdateBehavior updateBehavior;

    @Override
    public void onEnable() {
        saveDefaultConfig();//fails if it exists
        super.reloadConfig();//shouldn't matter but sometimes...
        scoresToUpdate = getConfig().getStringList("uuid-based-scores");
        resetOldScores = getConfig().getBoolean("reset-old-scores");

        String strUpdateBehavior = getConfig().getString("scoreboard-update-behavior");
        try {
            updateBehavior = ScoreboardUpdateBehavior.valueOf(strUpdateBehavior);
        } catch (IllegalArgumentException e) {
            updateBehavior = ScoreboardUpdateBehavior.OVERWRITE;
            getLogger().warning("Invalid behavior type " + strUpdateBehavior + ". using " + updateBehavior.name());
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
    }

    boolean updateScore(Scoreboard sb, String scoreName, Integer scoreValue, Score newScoreObject) {
        switch (updateBehavior) {
            case OVERWRITE:
                newScoreObject.setScore(scoreValue);
                return true;
            case ADD:
                int newScore = scoreValue;
                if (newScoreObject.isScoreSet()) {
                    newScore += newScoreObject.getScore();
                }
                newScoreObject.setScore(newScore);
                return true;
            default:
                throw new IllegalArgumentException("Unsupported behavior type - this is a bug!");
        }
    }

    boolean updateScore(Scoreboard sb, String scoreName, Integer scoreValue, String newUsername) {
        Objective obj = sb.getObjective(scoreName);
        if (obj == null) {
            getLogger().warning("Scoreboard Objective " + scoreName + " doesn't exist!");
            return false;
        }
        Score newScore = obj.getScore(newUsername);
        return updateScore(sb, scoreName, scoreValue, newScore);
    }

    void updateScores(String oldName, String newName) {
        getLogger().info("Updating scoreboard of '" + oldName + "' to '" + newName + "'");

        final ScoreboardManager sm = getServer().getScoreboardManager();
        if (sm == null) {
            throw new IllegalStateException("World has not loaded yet - this is a bug!");
        }

        final Scoreboard sb = sm.getMainScoreboard();
        final HashMap<String, Integer> scores = new HashMap<>();

        //collect scores for old username
        for (String scoreName : scoresToUpdate) {
            Objective obj = sb.getObjective(scoreName);
            if (obj == null) {
                getLogger().warning("Scoreboard Objective " + scoreName + " doesn't exist!");
                continue;
            }
            Score score = obj.getScore(oldName);
            if (!score.isScoreSet()) {
                continue;
            }
            scores.put(scoreName, score.getScore());
        }

        //transfer collected scores to new user
        for (Entry<String, Integer> entry : scores.entrySet()) {
            updateScore(sb, entry.getKey(), entry.getValue(), newName);
        }

        //remove scores for old username
        if (resetOldScores) {
            sb.resetScores(oldName);
        }
    }

    String getPreviousName(Player player) {
        for (String tag : player.getScoreboardTags()) {
            if (tag.startsWith("prev_name_")) {
                return tag.substring(10);
            }
        }
        return player.getName();
    }

    private void onPlayerJoinSync(PlayerJoinEvent evt) {
        final String currName = evt.getPlayer().getName();
        final String prevName = getPreviousName(evt.getPlayer());
        if (!prevName.equals(currName)) {// name changed
            try {
                updateScores(prevName, currName);
            } catch (IllegalStateException ex) {
                getLogger().warning("Error updating scores - was the objective deleted?");
                return;//do not reset scoreboard tags if score update failed - attempt again.
            }
        }
        //reset tags for user.
        evt.getPlayer().removeScoreboardTag("prev_name_" + prevName);
        evt.getPlayer().addScoreboardTag("prev_name_" + currName);
    }

    @EventHandler
    public void onPlayerJoinAsync(PlayerJoinEvent evt) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, () -> onPlayerJoinSync(evt), 1L);
    }
}
