package net.evmodder.ScoreboardUUID;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import net.evmodder.EvLib.EvPlugin;

public class ScoreboardUUID extends EvPlugin implements Listener{
	List<String> scoresToUpdate;

	@Override public void onEvEnable(){
		scoresToUpdate = getConfig().getStringList("uuid-based-scores");
	}
	@Override public void onEvDisable(){}

	void updateScores(String oldName, String newName){
		getLogger().info("Updating scoreboard of '"+oldName+"' to '"+newName+"'");

		final Scoreboard sb = getServer().getScoreboardManager().getMainScoreboard();
		final HashMap<String, Integer> scores = new HashMap<String, Integer>();
		for(String scoreName : scoresToUpdate){
			Score score = sb.getObjective(scoreName).getScore(oldName);
			if(!score.isScoreSet()) continue;
			scores.put(scoreName, score.getScore());
		}
		sb.resetScores(oldName);
		for(Entry<String, Integer> entry : scores.entrySet()){
			sb.getObjective(entry.getKey()).getScore(newName).setScore(entry.getValue());
		}
	}

	String getPreviousName(Player player){
		for(String tag : player.getScoreboardTags()){
			if(tag.startsWith("prev_name_")) return tag.substring(10);
		}
		return player.getName();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		final String currName = evt.getPlayer().getName();
		final String prevName = getPreviousName(evt.getPlayer());
		if(!prevName.equals(currName)){
			updateScores(prevName, currName);
		}
		evt.getPlayer().removeScoreboardTag("prev_name_"+prevName);
		evt.getPlayer().addScoreboardTag("prev_name_"+currName);
	}
}