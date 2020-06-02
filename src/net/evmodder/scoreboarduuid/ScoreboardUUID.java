package net.evmodder.scoreboarduuid;

import com.github.crashdemons.scoreboarduuid.ScoreboardUpdateBehavior;
import net.evmodder.EvLib.EvPlugin;
import net.evmodder.EvLib.util.Pair;
import java.util.HashMap;
import java.util.Map.Entry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
*
* @author EvModder/EvDoc (evdoc at altcraft.net)
*/
public class ScoreboardUUID extends EvPlugin implements Listener{
	HashMap<String, ScoreboardUpdateBehavior> scoresToUpdate;
	boolean resetOldScores;

	@Override public void onEvEnable(){
		if(!getConfig().isConfigurationSection("uuid-based-scores")){
			getLogger().warning("No uuid-based scores found in config! Disabling plugin");
			this.onDisable();
			return;
		}
		resetOldScores = getConfig().getBoolean("reset-old-scores", true);

		ConfigurationSection scoreListSection = getConfig().getConfigurationSection("uuid-based-scores");
		for(String key : scoreListSection.getKeys(false)){
			String strUpdateBehavior = getConfig().getString("scoreboard-update-behavior", "OVERWRITE");
			ScoreboardUpdateBehavior updateBehavior;
			try{
				updateBehavior = ScoreboardUpdateBehavior.valueOf(strUpdateBehavior);
			}
			catch(IllegalArgumentException e){
				updateBehavior = ScoreboardUpdateBehavior.OVERWRITE;
				getLogger().warning("Invalid behavior type '" + strUpdateBehavior + "' for score '" + key + "' using " + updateBehavior.name());
			}
			scoresToUpdate.put(key, updateBehavior);
		}

		getServer().getPluginManager().registerEvents(this, this);
	}

	boolean updateScore(Objective obj, String username, int scoreValue, ScoreboardUpdateBehavior updateBehavior){
		Score newScoreObject = obj.getScore(username);

		int newScoreValue;
		switch(updateBehavior){
			case SAFE_MOVE:
				if(newScoreObject.isScoreSet()){
					getLogger().severe("Failed to update score '" + obj.getName() + "' for user '" + username +"' - entry already exists!");
					return false;
				}
			case OVERWRITE:
				newScoreValue = scoreValue;
				break;
			case ADD:
				newScoreValue = scoreValue + (newScoreObject.isScoreSet() ? newScoreObject.getScore() : 0);
				break;
			default:
				getLogger().severe("Encountered invalid behavior type " + updateBehavior + " while updating score: " + obj.getName());
				newScoreValue = -1;
				return false;
		}
		//if(newScoreObject.isScoreSet() && newScoreObject.getScore() == newScoreValue) return false; // no change occurred.
		newScoreObject.setScore(newScoreValue);
		return true;
	}

	boolean updateScores(String oldName, String newName){
		getLogger().info("Updating scoreboard of '" + oldName + "' to '" + newName + "'");

		final ScoreboardManager sm = getServer().getScoreboardManager();
		if(sm == null){
			throw new IllegalStateException("World has not loaded yet - this is a bug!");
		}

		final Scoreboard sb = sm.getMainScoreboard();
		final HashMap<Objective, Pair<Integer, ScoreboardUpdateBehavior>> scores = new HashMap<>();

		// collect scores for old username.
		for(Entry<String, ScoreboardUpdateBehavior> entry : scoresToUpdate.entrySet()){
			Objective obj = sb.getObjective(entry.getKey());
			if(obj == null){
				getLogger().warning("Scoreboard Objective " + entry.getKey() + " doesn't exist!");
				continue;
			}
			Score score = obj.getScore(oldName);
			if(!score.isScoreSet()) continue;
			scores.put(obj, new Pair<>(score.getScore(), entry.getValue()));
		}

		boolean moveSuccess = true;
		// transfer collected scores to new username.
		for(Entry<Objective, Pair<Integer, ScoreboardUpdateBehavior>> entry : scores.entrySet()){
			moveSuccess &= updateScore(entry.getKey(), newName, entry.getValue().a, entry.getValue().b);
		}

		// clear scores for old username.
		if(resetOldScores && moveSuccess){
			HashMap<Objective, Integer> scoresToKeep = new HashMap<>();
			for(Objective obj : sb.getObjectives()){
				if(scoresToUpdate.containsKey(obj.getName()) || !obj.getScore(oldName).isScoreSet()) continue;
				scoresToKeep.put(obj, obj.getScore(oldName).getScore());
			}
			sb.resetScores(oldName);
			for(Entry<Objective, Integer> entry : scoresToKeep.entrySet()){
				entry.getKey().getScore(oldName).setScore(entry.getValue());
			}
		}
		return moveSuccess;
	}

	String getPreviousName(Player player){
		for(String tag : player.getScoreboardTags()){
			if(tag.startsWith("prev_name_")) return tag.substring(10);
		}
		return player.getName();
	}

	private void onPlayerJoinSync(PlayerJoinEvent evt){
		final String currName = evt.getPlayer().getName();
		final String prevName = getPreviousName(evt.getPlayer());
		if(!prevName.equals(currName)){ // name changed.
			boolean success = false;
			try{
				success = updateScores(prevName, currName);
			}
			catch(IllegalStateException ex){
				success = false;
			}
			if(!success){
				getLogger().warning("Encountered error whilst updating scores for player '" + currName + "'!");
				return; // do not reset scoreboard tags if score update failed - attempt again later.
			}
		}
		// reset tags for user.
		evt.getPlayer().removeScoreboardTag("prev_name_" + prevName);
		evt.getPlayer().addScoreboardTag("prev_name_" + currName);
	}

	@EventHandler public void onPlayerJoinAsync(PlayerJoinEvent evt){
		getServer().getScheduler().scheduleSyncDelayedTask(this, () -> onPlayerJoinSync(evt), 1L);
	}
}