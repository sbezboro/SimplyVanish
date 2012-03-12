package asofold.simplyvanish;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Example plugin for the vanish API as of CB 1914 !
 * Vanish + God mode + No Target + No pickup.
 * @author mc_dev
 *
 */
public class SimplyVanish extends JavaPlugin {
	
	static final SimplyVanishCore core = new SimplyVanishCore();
	
	Configuration defaults;
	
	/**
	 * Constructor: set some defualt configuration values.
	 */
	public SimplyVanish(){
		defaults = new MemoryConfiguration();
		defaults.set("pickup.exp.workaround.active", new Boolean(true));
		defaults.set("pickup.exp.workaround.distance.threshold", 3.0D);
		defaults.set("pickup.exp.workaround.distance.teleport", 1.0D);
		defaults.set("pickup.exp.workaround.distance.remove", 0.5D);
		defaults.set("pickup.exp.workaround.velocity", 0.3D);
//		defaults.set("persistance", new Boolean(false)); // TODO: load save vanished players.
	}
	
	@Override
	public void onDisable() {
		core.setEnabled(false);
		// TODO: maybe let all players see each other again?
		System.out.println("[SimplyVanish] Disabled.");
	}

	@Override
	public void onEnable() {
		// load settings
		loadSettings();
		// just in case quadratic time checking:
		for ( Player player : getServer().getOnlinePlayers()){
			core.updateVanishState(player);
		}
		// register events:
		getServer().getPluginManager().registerEvents(core, this);
		// finished enabling.
		core.setEnabled(true);
		System.out.println("[SimplyVanish] Enabled");
	}

	/**
	 * Force reloading the config.
	 */
	public void loadSettings() {
		reloadConfig();
		Configuration config = getConfig();
		Utils.forceDefaults(defaults, config);
		core.applyConfig(config);
		saveConfig();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		int length = args.length;
		boolean isPlayer = sender instanceof Player;
		if ( label.equalsIgnoreCase("vanish") && length==0 ){
			if ( !Utils.checkPlayer(sender)) return true;
			if ( !Utils.hasPermission(sender, "simplyvanish.vanish.self")) return true;
			// Make sure the player is vanished...
			core.onVanish((Player) sender);
			return true;
		} if ( label.equalsIgnoreCase("vanish") && length==1 ){
			if ( !Utils.hasPermission(sender, "simplyvanish.vanish.other")) return true;
			// Make sure the other player is vanished...
			String name = args[0].trim();
			setVanished(name, true);
			sender.sendMessage("Vanish player: "+name);
			return true;
		} else if (label.equalsIgnoreCase("reappear") && length==0 ){
			if ( !Utils.checkPlayer(sender)) return true;
			if ( !Utils.checkPerm(sender, "simplyvanish.vanish.self")) return true;
			// Let the player be seen...
			core.onReappear((Player) sender);
			return true;
		} if ( label.equalsIgnoreCase("reappear") && length==1 ){
			if ( !Utils.hasPermission(sender, "simplyvanish.vanish.other")) return true;
			// Make sure the other player is shown...
			String name = args[0].trim();
			setVanished(name, false);
			sender.sendMessage("Show player: "+name);
			return true;
		} else if (label.equalsIgnoreCase("vanished")){
			if ( !Utils.checkPerm(sender, "simplyvanish.vanish")) return true;
			List<String> vanished = core.getSortedVanished();
			StringBuilder builder = new StringBuilder();
			builder.append((isPlayer?ChatColor.GOLD.toString():"")+"[VANISHED]");
			Server server = getServer();
			String c = "";
			for ( String n : vanished){
				Player player = server.getPlayerExact(n);
				if ( player == null ){
					if (isPlayer) c = ChatColor.GRAY.toString();
					builder.append(" "+c+"("+n+")");
				}
				else{
					if ( isPlayer) c = ChatColor.GREEN.toString();
					builder.append(" "+c+player.getName());
				}
			}
			if (vanished.isEmpty()) builder.append(" "+((isPlayer?ChatColor.DARK_GRAY:"")+"<none>"));
			sender.sendMessage(builder.toString());
			return true;
		} if ( label.equalsIgnoreCase("simplyvanish")){
			if (length==1 && args[0].equalsIgnoreCase("reload")){
				if ( !Utils.checkPerm(sender, "simplyvanish.reload")) return true;
				loadSettings();
				sender.sendMessage("[SimplyVanish] Settings reloaded.");
				return true;
			}
			return true;
		}
		
		sender.sendMessage("[SimplyVanish] Unrecognized command or number of arguments.");
		return false;
	}
	
	/**
	 * @deprecated Use setVanished(player, true)
	 * @param player
	 */
	public void vanish(Player player){
		setVanished(player, true);
	}
	
	/**
	 * @deprecated Use setVanished(player, false)
	 * @param player
	 */
	public void reappear(Player player){
		setVanished(player, false);
	}
	
	/**
	 * API
	 * @param player
	 * @param vanished true=vanish, false=reappear
	 */
	public static void setVanished(Player player, boolean vanished){
		if (!core.isEnabled()) return;
		
		if (vanished) core.onVanish(player);
		else core.onReappear(player);
	}
	
	/**
	 * API
	 * @param playerName
	 * @param vanished
	 */
	public static void setVanished(String playerName, boolean vanished){
		if (!core.isEnabled()) return;
		
		Player player = Bukkit.getServer().getPlayerExact(playerName);
		if (player != null){
			// The simple part.
			setVanished(player, vanished);
			return;
		}
		// The less simple part.
		if (vanished) core.vanished.add(playerName.toLowerCase());
		else if (core.vanished.remove(playerName)) return;
		else{
			// Expensive part:
			String match = null;
			for (String n : core.vanished){
				if ( n.equalsIgnoreCase(playerName)){
					match = n;
					break;
				}
			}
			if ( match != null) core.vanished.remove(match);
		}
	}
	
	/**
	 * API
	 * @param playerName Exact player name.
	 * @return
	 */
	public static boolean isVanished(String playerName){
		if (!core.isEnabled()) return false;
		else return core.vanished.contains(playerName.toLowerCase());
	}
	
	/**
	 * API
	 * @param player 
	 * @return
	 */
	public static boolean isVanished(Player player){
		if (!core.isEnabled()) return false;
		else return core.vanished.contains(player.getName().toLowerCase());
	}
	
	/**
	 * API
	 * Get the Set containing the lower case names of Players to be vanished.
	 * These are not necessarily online.
	 * NOTE: It returns the internally used HashSet instance, do not manipulate it, do not iterate in an asynchronous task or thread.
	 * @return
	 */
	public static Set<String> getVanishedPlayers(){
		if (!core.isEnabled()) return new HashSet<String>();
		else return core.vanished;
	}

}
