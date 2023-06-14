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
	ScoreboardUpdateBehavior defaultMode;
	boolean resetOldScores;

	private ScoreboardUpdateBehavior parseUpdateBehavior(String strUpdateBehavior){
		try{return ScoreboardUpdateBehavior.valueOf(strUpdateBehavior.toUpperCase());}
		catch(IllegalArgumentException e){
			getLogger().warning("Invalid behavior type '" + strUpdateBehavior + "'");
			return defaultMode;
		}
	}

	@Override public void onEvEnable(){
		if(!getConfig().isConfigurationSection("uuid-based-scores")){
			getLogger().warning("No uuid-based scores found in config! Disabling plugin");
			this.onDisable();
			return;
		}
		resetOldScores = getConfig().getBoolean("reset-old-scores", true);
		defaultMode = parseUpdateBehavior(getConfig().getString("default-mode", "NONE"));

		ConfigurationSection scoreListSection = getConfig().getConfigurationSection("uuid-based-scores");
		scoresToUpdate = new HashMap<>();
		for(String key : scoreListSection.getKeys(false)){
			scoresToUpdate.put(key, parseUpdateBehavior(scoreListSection.getString(key)));
		}

		getServer().getPluginManager().registerEvents(this, this);
	}

	boolean setNewScore(Objective obj, String username, int scoreValue, ScoreboardUpdateBehavior updateBehavior){
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
			case NONE:
				return false;
			default:
				getLogger().severe("Encountered invalid behavior type " + updateBehavior + " while updating score: " + obj.getName());
				return false;
		}
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

		// transfer collected scores to new username.
		boolean totalSuccess = true;
		HashMap<Objective, Integer> scoresToKeep = new HashMap<>();
		for(Entry<Objective, Pair<Integer, ScoreboardUpdateBehavior>> entry : scores.entrySet()){
			boolean moveSuccess = setNewScore(entry.getKey(), newName, entry.getValue().a, entry.getValue().b);
			if(!moveSuccess){
				scoresToKeep.put(entry.getKey(), entry.getValue().a);
				if(entry.getValue().b != ScoreboardUpdateBehavior.NONE) totalSuccess = false;
			}
		}

		// clear scores for old username.
		if(resetOldScores){
			sb.resetScores(oldName);
			for(Entry<Objective, Integer> entry : scoresToKeep.entrySet()){
				entry.getKey().getScore(oldName).setScore(entry.getValue());
			}
		}
		return totalSuccess;
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