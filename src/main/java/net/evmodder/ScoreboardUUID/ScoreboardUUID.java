package net.evmodder.ScoreboardUUID;

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

    @Override
    public void onEnable() {
        saveDefaultConfig();//fails if it exists
        super.reloadConfig();//shouldn't matter but sometimes...
        scoresToUpdate = getConfig().getStringList("uuid-based-scores");
        resetOldScores = getConfig().getBoolean("reset-old-scores");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
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
            String scoreName = entry.getKey();
            Objective obj = sb.getObjective(scoreName);
            if (obj == null) {
                getLogger().warning("Scoreboard Objective " + scoreName + " doesn't exist!");
                continue;
            }
            Score newScore = obj.getScore(newName);
            newScore.setScore(entry.getValue());
        }
        
        //remove scores for old username
        if(resetOldScores) sb.resetScores(oldName);
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
            try{
                updateScores(prevName, currName);
            }catch(IllegalStateException ex){
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
