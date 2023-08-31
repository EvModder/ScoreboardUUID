package net.evmodder.scoreboarduuid;

import com.github.crashdemons.scoreboarduuid.ScoreboardUpdateBehavior;
import net.evmodder.EvLib.EvPlugin;
import net.evmodder.EvLib.extras.WebUtils;
import net.evmodder.EvLib.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

/**
*
* @author EvModder/EvDoc (evdoc at altcraft.net)
*/
public class ScoreboardUUID extends EvPlugin implements Listener{
	HashMap<String, ScoreboardUpdateBehavior> scoresToUpdate;//TODO: Support wildcards. E.g., in config.yml: "'pstats-*': NONE"
	HashMap<UUID, String> previousName;
//	HashMap<String, String> newName;
	ScoreboardUpdateBehavior defaultMode;
	boolean RESET_OLD_SCORES, UPDATE_TEAMS;

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
		UPDATE_TEAMS = getConfig().getBoolean("update-teams", true);
		RESET_OLD_SCORES = getConfig().getBoolean("reset-old-scores", true);
		defaultMode = parseUpdateBehavior(getConfig().getString("default-mode", "NONE"));

		ConfigurationSection scoreListSection = getConfig().getConfigurationSection("uuid-based-scores");
		scoresToUpdate = new HashMap<>();
		for(String key : scoreListSection.getKeys(false)){
			scoresToUpdate.put(key, parseUpdateBehavior(scoreListSection.getString(key)));
		}

		previousName = new HashMap<>();
		getServer().getPluginManager().registerEvents(this, this);
	}

	boolean setNewScore(Objective obj, String username, int scoreValue, ScoreboardUpdateBehavior updateBehavior){
		Score newScoreObject = obj.getScore(username);

		int newScoreValue;
		switch(updateBehavior){
			/*case CASCADE_MOVE:
				if(newScoreObject.isScoreSet()){
					final String newUsername = newName.get(username);
					if(setNewScore(obj, newUsername, newScoreObject.getScore(), updateBehavior)){
						newScoreObject.setScore(scoreValue);
						return true;
					}
				}*/
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

		// Collect scores for old username.
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

		// Transfer collected scores to new username.
		boolean totalSuccess = true;
		HashMap<Objective, Integer> scoresToKeep = new HashMap<>();
		for(Entry<Objective, Pair<Integer, ScoreboardUpdateBehavior>> entry : scores.entrySet()){
			boolean moveSuccess = setNewScore(entry.getKey(), newName, entry.getValue().a, entry.getValue().b);
			if(!moveSuccess){
				if(RESET_OLD_SCORES) scoresToKeep.put(entry.getKey(), entry.getValue().a);
				if(entry.getValue().b != ScoreboardUpdateBehavior.NONE) totalSuccess = false;
			}
		}

		// Clear scores for old username.
		if(RESET_OLD_SCORES){
			sb.resetScores(oldName);
			for(Entry<Objective, Integer> entry : scoresToKeep.entrySet()){
				entry.getKey().getScore(oldName).setScore(entry.getValue());
			}
		}
		return totalSuccess;
	}

	private void updateScoresOrPrintError(String oldName, String newName){
		boolean success = false;
		try{success = updateScores(oldName, newName);}
		catch(IllegalStateException ex){success = false;}
		if(!success) getLogger().warning("Encountered error whilst updating scores for player '" + oldName + "' -> '" + newName + "'!");
	}

	private void updateTeamEntry(String oldName, String newName){
		final Team team = getServer().getScoreboardManager().getMainScoreboard().getEntryTeam(oldName);
		if(team != null){
			team.removeEntry(oldName);
			team.addEntry(newName);
		}
	}

	private void onPlayerJoinSync(PlayerJoinEvent evt){
//		getLogger().info("evt.getPlayer().getName()4: "+evt.getPlayer().getName());
//		getLogger().info("offlinePlayer.getName()4: "+getServer().getOfflinePlayer(evt.getPlayer().getUniqueId()).getName());
//		getLogger().info("offlinePlayer.getUUID()4: "+getServer().getOfflinePlayer(evt.getPlayer().getName()).getUniqueId());
		final String currName = evt.getPlayer().getName();
		final String prevName = previousName.remove(evt.getPlayer().getUniqueId());
		if(prevName != null){
			if(!prevName.equals(currName)){  // Name got changed.
				updateScoresOrPrintError(prevName, currName);
				if(UPDATE_TEAMS) updateTeamEntry(prevName, currName);
			}
		}
	}

	@EventHandler public void onPlayerJoinAsync(PlayerJoinEvent evt){
		getServer().getScheduler().scheduleSyncDelayedTask(this, () -> onPlayerJoinSync(evt), 1L);
	}

	@SuppressWarnings("deprecation")
	@EventHandler public void onPlayerLogin(PlayerLoginEvent evt){
//		getLogger().info("evt.getPlayer().getName()2: "+evt.getPlayer().getName());
//		getLogger().info("offlinePlayer.getName()2: "+getServer().getOfflinePlayer(evt.getPlayer().getUniqueId()).getName());
//		getLogger().info("offlinePlayer.getUUID()2: "+getServer().getOfflinePlayer(evt.getPlayer().getName()).getUniqueId());
		previousName.put(evt.getPlayer().getUniqueId(), getServer().getOfflinePlayer(evt.getPlayer().getUniqueId()).getName());
		OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(evt.getPlayer().getName());
		UUID uuidOfNewPlayer = evt.getPlayer().getUniqueId();
		if(offlinePlayer.getName() != null && !offlinePlayer.getUniqueId().equals(uuidOfNewPlayer)){
			ArrayList<String> nameUpdates = new ArrayList<>();
			nameUpdates.add(evt.getPlayer().getName());
			while(offlinePlayer.getName() != null && !offlinePlayer.getUniqueId().equals(uuidOfNewPlayer)){
				final String newName = WebUtils.getGameProfile(""+offlinePlayer.getUniqueId(),
						/*fetchSkin=*/false, /*nullForSync=*/null).getName();
				nameUpdates.add(newName);
				uuidOfNewPlayer = offlinePlayer.getUniqueId();
				offlinePlayer = getServer().getOfflinePlayer(newName);
			}
			for(int i = nameUpdates.size()-1; i > 0; --i){
				updateScoresOrPrintError(nameUpdates.get(i-1), nameUpdates.get(i));
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerLoginLowest(PlayerLoginEvent evt){
//		getLogger().info("evt.getPlayer().getName()1: "+evt.getPlayer().getName());
//		getLogger().info("offlinePlayer.getName()1: "+getServer().getOfflinePlayer(evt.getPlayer().getUniqueId()).getName());
//		getLogger().info("offlinePlayer.getUUID()1: "+getServer().getOfflinePlayer(evt.getPlayer().getName()).getUniqueId());
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerLoginMonitor(PlayerLoginEvent evt){
//		getLogger().info("evt.getPlayer().getName()3: "+evt.getPlayer().getName());
//		getLogger().info("offlinePlayer.getName()3: "+getServer().getOfflinePlayer(evt.getPlayer().getUniqueId()).getName());
//		getLogger().info("offlinePlayer.getUUID()3: "+getServer().getOfflinePlayer(evt.getPlayer().getName()).getUniqueId());
	}
}