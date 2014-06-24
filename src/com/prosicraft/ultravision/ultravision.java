/*  ============================================================================
 *
 *      U L T R A V I S I O N  ---  P l a y e r   S u p e r v i s i o n
 *
 *       This Bukkit Plugin provides functionality for every security,
 *              as well as broadcasting and logging purposes on your MC-Server.
 *
 *                              by prosicraft  ,   (c) 2014
 *
 *          Update 20.6.2014
 *
 *  ============================================================================
 */
package com.prosicraft.ultravision;

import com.prosicraft.ultravision.dummy.DebugDummy;
import com.prosicraft.ultravision.JMessage.JMessage;
import com.prosicraft.ultravision.base.PlayerIdent;
import com.prosicraft.ultravision.base.UVChatListener;
import com.prosicraft.ultravision.base.UVClickAuth;
import com.prosicraft.ultravision.base.UltraVisionAPI;
import com.prosicraft.ultravision.chat.MCChatListener;
import com.prosicraft.ultravision.chat.UVServer;
import com.prosicraft.ultravision.commands.*;
import com.prosicraft.ultravision.global.globalEngine;
import com.prosicraft.ultravision.local.UVLocalEngine;
import com.prosicraft.ultravision.util.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.server.v1_7_R3.EntityInsentient;
import net.minecraft.server.v1_7_R3.GenericAttributes;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_7_R3.entity.CraftLivingEntity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

//**********************************************************************************************
/**
 * UltraVision Main Class
 *
 * @author prosicraft
 */
public class ultravision extends JavaPlugin
{

	private String buildVersion		= "??";
	private MConfiguration config		= null;     // Ultravision Configuration
	private PluginDescriptionFile fPDesc	= null;     // Plugin description file
	private uvPlayerListener playerListener	= null;     // The Main Listener
	private uvblocklistener blockListener	= null;     // Block listener for auth
	public uvCoreListener coreListener	= null;     // Listener for UVBridges
	private UVServer uvserver		= null;     // The Mineconnect server
	private MCChatListener uvchatlistener	= null;     // Mineconnect listener
	private JMessage jmsg			= null;     // JMessage Template
	private MAuthorizer auth		= null;     // Authorizer Template
	private UVClickAuth clickauth		= null;     // ClickAuth Template
	private UltraVisionAPI api		= null;     // Ultravision API Instance
	private boolean global			= false;    // use global api ?
	private boolean useMineconnect		= false;    // Mineconnect Template
	private boolean useJMessage		= false;    // Join Message Template
	private boolean useAuthorizer		= true;     // Authorizer / Login Template
	private boolean useClickAuthorizer	= false;    // Clickauthorizer / Optional login template
	private boolean useCommandLog		= true;     // Command logging
	public boolean useUltraChat		= true;     // Use UltraChat Plugin if found
	private boolean showNotRegWarning	= true;     // Show "NotRegistered" Warning on join
	public boolean allowNotRegActions	= true;     // Allow Player Interaction when not registered
	public boolean showWelcomeMessage	= true;     // Show powered by Message on login
	public boolean showMessagesNotLoggedIn	= true;     // Show messages, if not logged in
	public boolean disableIngameOp		= true;	    // Disable ingame op command
	public UVBRIDGE[] bridges		= new UVBRIDGE[ 5 ];     // Bridge to UltraBox Plugins
	public List<String> debugPlayers	= new ArrayList<>();

	private boolean useDebugDummy		= false;
	private DebugDummy debugDummy		= null;

	//**********************************************************************************************
	/**
	 * OnEnable will start the plugin and everything else
	 */
	@Override
	public void onEnable()
	{
		// Try to load the configuration file and print startup info
		try
		{
			printStartupInfo();
			initConfig();
		}
		catch( Exception ex )
		{
			MLog.e( "FATAL: " + ex.toString() );
			ex.printStackTrace( System.out );
			return;
		}

		// Load and initialize used Templates
		loadTemplateSelection();
		initAuthorizer();
		initClickAuthorizer();
		initJMessage();

		// Now start the Engine
		MLog.d( "Starting engine..." );
		if( global )
		{
			api = new globalEngine();
			MLog.i( "Using global Engine. Version: " + UltraVisionAPI.version );
			MLog.w( "Global Engine isn't supported yet." );
		}
		else
		{
			api = new UVLocalEngine( this.getDataFolder().getAbsolutePath(), this );
			MLog.i( "Using Local Engine. Version: " + UltraVisionAPI.version );
		}

		// Check if everything went well
		if( api == null )
		{
			MLog.e( "Could not load API! (Unknown Error). Disabling..." );
			getServer().getPluginManager().disablePlugin( this );
			return;
		}

		// Hook Authorizer into Engine
		if( useAuthorizer )
		{
			final MResult tr;
			if( ( tr = api.setAuthorizer( auth ) ) == MResult.RES_SUCCESS )
			{
				MLog.i( "Authorizer hooked into Engine." );
			}
			else
			{
				MLog.e( "Authorizer can't hook into Engine: " + tr.toString() );
			}
		}

		// Apply API to Jmessage and other templates
		if( jmsg != null )
		{
			jmsg.setAPI( api );
		}

		// Hook the several Events into our listeners
		initEvents();

		// Start the Mineconnect Chat Server
		if( !useMineconnect )
			MLog.i( "Mineconnect is disabled in configuration." );
		else
		{
			MLog.d( "Starting Mineconnect server..." );

			uvchatlistener = new MCChatListener()
			{
				@Override
				public void onMessage( String msg )
				{
					getServer().broadcastMessage( "MConnect (test): " + msg );
				}

				@Override
				public void onLogin( String username )
				{
					getServer().broadcastMessage( username + " joined the UV Server!" );
				}

				@Override
				public void onLeave( String username )
				{
					getServer().broadcastMessage( username + " left the UV Server!" );
				}
			};
			uvserver = new UVServer( getServer().getIp(), auth, 10 );
			uvserver.registerListener( uvchatlistener );
			uvserver.start();

			MLog.i( "Started Mineconnect server." );
		}

		// In case of Reload: Do Rejoin everybody
		// This also calls api.playerJoin()
		rejoin();

	}

	//**********************************************************************************************
	/**
	 * onDisable disables the plugin and shuts down everything
	 */
	@Override
	public void onDisable()
	{
		MLog.i( "Ultravision is being shutdown." );

		try
		{
			// Save authorizer datas
			if( auth != null )
				auth.save();

			if( clickauth != null )
				clickauth.saveToFile();

			// Save Jmessage config
			if( jmsg != null )
				jmsg.save( config );


			// unload the dummy player
			if (useDebugDummy && debugDummy != null && debugDummy.isLiving)
				debugDummy.despawn();

			// Shut down Engine
			if( api != null )
			{
				for( Player p : getServer().getOnlinePlayers() )
				{
					api.onPlayerLeave( p );
				}

				if( api.shutdown() )
				{
					MLog.i( "Shut down engine (" + ( ( global ) ? "global" : "local" ) + ")" );
				}
				else
				{
					MLog.e( "Can't shut down engine (" + ( ( global ) ? "global" : "local" ) + ")" );
				}
			}

			playerListener = null;
			fPDesc = null;
			config = null;

			// Stop the Mineconnect server
			if( useMineconnect && uvserver != null && uvserver.isAlive() )
			{
				uvserver.shutdown();
				uvserver = null;
				MLog.i( "Mineconnect Server stopped successfullly." );
			}
			else
			{
				MLog.i( "Mineconnect Server already shut down or disabled." );
			}
		}
		catch (NoClassDefFoundError ncdfe)
		{
			MLog.w("A NoClassDefFoundError occured while disabling UV. You probably have overwritten the plugin file without unloading it.");
		}
	}

	//**********************************************************************************************
	/**
	 * Prints Startup / Boot information about the plugin into the console
	 */
	private void printStartupInfo ()
	{
		// Startup info
		fPDesc = this.getDescription();
		try
		{
			Properties buildProperties = new Properties();
			try
			{
				InputStream resourceStream = ultravision.class.getResourceAsStream("version.properties");
				if( resourceStream == null )
				{
					MLog.i( "Cannot retrieve UltraVision Build number. This may occur when reloading UV in runtime." );
					buildVersion = "???";
					return;
				}

				buildProperties.load( resourceStream );
				buildVersion = "#b" + MCrypt.prependZeros(buildProperties.getProperty( "BUILD" ));

				MLog.i( "Ultravision is starting (Version " + fPDesc.getVersion() + " " + buildVersion + ") ..." );
			}
			catch( IOException e )
			{
				MLog.e( "This build seems like a damaged one" );
				e.printStackTrace( System.out );
			}
		}
		catch( java.util.MissingResourceException ex )
		{
			buildVersion = "???";
			MLog.i( "Ultravision is starting (Version " + fPDesc.getVersion() + " #b???) ..." );
			MLog.d( "Cannot retrieve Build version due to resource not found." );
		}
	}

	//**********************************************************************************************
	/**
	 * Load the template selection from the configuration
	 */
	public void loadTemplateSelection()
	{
		global = config.getBoolean( "useGlobalAPI", false );
		useAuthorizer = config.getBoolean( "general.useAuthorizer", true );
		useClickAuthorizer = config.getBoolean( "general.useClickAuthorizer", false );
		useJMessage = config.getBoolean( "general.useJMessage", false );
		useCommandLog = config.getBoolean( "general.useCommandLog", true );
		useUltraChat = config.getBoolean( "general.useUltraChat", true );
		showWelcomeMessage = config.getBoolean( "general.showWelcomeMessage", true );
		MConst._LIMIT_A = config.getInt( "general.logFileLimitKByte", MConst._LIMIT_A );
		showNotRegWarning = config.getBoolean( "general.showNotRegWarning", true );
		showMessagesNotLoggedIn = config.getBoolean( "general.showMessagesNotLoggedIn", true );
		MConst._DEBUG_ENABLED = config.getBoolean( "general.debug", false );
		useDebugDummy = config.getBoolean( "general.debugDummy", useDebugDummy );
		debugPlayers = config.getStringList( "general.debugPlayers" , new ArrayList<String>() );
		allowNotRegActions = config.getBoolean( "general.allowNotRegActions", true );
		disableIngameOp = config.getBoolean( "general.disableIngameOp", true );

		config.setDefault( "auth.showMessagesNotLoggedIn", true );
		config.setDefault( "ultravision.showWarnedMessages", true );
		config.set( "general.savestats", config.getBoolean( "general.savestats", true ) );

		// Initialize Bridges
		if( useUltraChat )
		{
			addBridge( new UVBRIDGE( this, "UltraChat" ) );
		}

		// save config file
		config.save();
	}

	//**********************************************************************************************
	/**
	 * Add a new UVBRIDGE, e.g. from an extern Plugin
	 * @param bridge the bridge instance to be added
	 * @return true if everything went well, false if not
	 */
	public boolean addBridge( UVBRIDGE bridge )
	{
		boolean assigned = false;
		for( int n = 0; n < bridges.length; n++ )
		{
			if( bridges[n] == null )
			{
				bridges[n] = bridge;
				assigned = true;
			}
		}
		return assigned;
	}

	//**********************************************************************************************
	/**
	 * Initializes the dummy player
	 */
	private void loadDummyPlayer()
	{
		if (!useDebugDummy)
			return;

		String uuidStr = config.getString("debugDummy.uuid", UUID.randomUUID().toString());

		Location zeroWorldLocation = getServer().getWorlds().get(0).getSpawnLocation();
		String worldName = config.getString("debugDummy.world", zeroWorldLocation.getWorld().getName());
		double locX = config.getDouble("debugDummy.x", zeroWorldLocation.getX());
		double locY = config.getDouble("debugDummy.y", zeroWorldLocation.getY());
		double locZ = config.getDouble("debugDummy.z", zeroWorldLocation.getZ());
		config.save();

		World dummyWorld = getServer().getWorld(worldName);
		Location dummyLocation = new Location(dummyWorld, locX, locY, locZ);

		debugDummy = new DebugDummy(this);
		debugDummy.spawn(uuidStr, dummyLocation);

		MLog.i("Loaded Dummy Player in world '" + worldName + "' with id " + uuidStr + "!");
	}

	//**********************************************************************************************
	/**
	 * Initialize MAuthorizer if used
	 */
	private void initAuthorizer()
	{
		if( !useAuthorizer )
		{
			MLog.i( "Authorizer not used!" );
			return;
		}

		MLog.d( "Loading Authorizer now..." );

		File authFile = new File( this.getDataFolder(), "authdb.dat" );
		if( !authFile.exists() )
		{
			try
			{
				authFile.createNewFile();
				MLog.i( "Created unexisting authentication file at " + authFile.getAbsolutePath() );
			}
			catch( IOException ioex )
			{
				MLog.e( "Can't create unexisting authentication file at " + authFile.getAbsolutePath() );
				ioex.printStackTrace( System.out );
			}
		}

		// Initialize Authorizer
		( auth = new MAuthorizer( authFile.getAbsolutePath() ) ).a();
	}

	//**********************************************************************************************
	/**
	 * Initialize ClickAuthorizer if used
	 */
	private void initClickAuthorizer()
	{
		if( !useClickAuthorizer )
		{
			MLog.i( "Clickauthorizer is not used." );
			return;
		}

		clickauth = new UVClickAuth( api, this, showMessagesNotLoggedIn );
		clickauth.init();
	}

	//**********************************************************************************************
	/**
	 * Initialize JMessage if used
	 */
	private void initJMessage()
	{
		if( jmsg != null || !useJMessage )
		{
			MLog.i( "JMessage is disabled in configuration file." );
			return;
		}

		( jmsg = new JMessage( config, api )
		{
			@Override
			public void broadcast( String txt )
			{
				ownBroadcast( txt );
			}
		} ).init( this, auth, clickauth );
		jmsg.save( config );
	}

	//**********************************************************************************************
	/**
	 * Register Events and assign templates
	 */
	private void initEvents()
	{
		playerListener	= new uvPlayerListener( this );
		blockListener	= new uvblocklistener( this );
		coreListener	= new uvCoreListener( this );

		PluginManager pm = this.getServer().getPluginManager();

		pm.registerEvents( playerListener, this );
		pm.registerEvents( blockListener, this );
		pm.registerEvents( coreListener, this );
		pm.registerEvents( new UVChatListener( this ), this );
	}

	//**********************************************************************************************
	/**
	 * Initialize Main-Configuration file of ultravision plugin
	 */
	public void initConfig()
	{
		// Create Plugin Folder if not existing
		if( !this.getDataFolder().exists() && !getDataFolder().mkdirs() )
		{
			MLog.e( "Can't create missing configuration Folder for UltraVision" );
		}

		// Load config file or create if not exist
		File cf = new File( this.getDataFolder(), "config.yml" );
		if( !cf.exists() )
		{
			try
			{
				MLog.w( "Configuration File doesn't exist. Trying to recreate it..." );
				if( !cf.createNewFile() || !cf.exists() )
				{
					MLog.e( "Placement of Plugin might be wrong or has no Permissions to access configuration file." );
				}
			}
			catch( IOException iex )
			{
				MLog.e( "Can't create unexisting configuration file" );
			}
		}

		config = new MConfiguration( YamlConfiguration.loadConfiguration( cf ), cf );

		// Initialize DataTable
		Map<String, MConfiguration.DataType> dt = new HashMap<>();

		dt.put( "general.useGlobalAPI", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.useAuthorizer", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.useClickAuthorizer", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.useJMessage", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.useCommandLog", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.useCHack", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.useUltraChat", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.showWelcomeMessage", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.logFileLimitKByte", MConfiguration.DataType.DATATYPE_INTEGER );
		dt.put( "general.showNotRegWarning", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.allowNotRegActions", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.showMesssagesNotLoggedIn", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.debug", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.savestats", MConfiguration.DataType.DATATYPE_BOOLEAN );
		dt.put( "general.debugDummy", MConfiguration.DataType.DATATYPE_BOOLEAN );

		config.setDataTypeTable( dt );

		config.load();
	}

	//**********************************************************************************************
	/**
	 * Clears whole configuration filea and creates new one
	 */
	public boolean clearConfig()
	{
		try
		{
			config.clear();
			config = null;

			File cfg = new File( this.getDataFolder(), "config.yml" );

			if( cfg.exists() && cfg.isFile() )
			{
				cfg.delete();
			}

			if( !cfg.createNewFile() )
			{
				throw new Exception( "Couldn't create new configuration File." );
			}

			if( !cfg.exists() )
			{
				throw new Exception( "Didn't create configuration File properly." );
			}

			config = new MConfiguration( YamlConfiguration.loadConfiguration( cfg ), cfg );
			config.load();
			config.set( "general.savestats", true );
			config.save();

			return true;
		}
		catch( Exception ex )
		{
			MLog.e( "Failed to reload Ultravision: " + ex.getMessage() );
			return false;
		}
	}

	//**********************************************************************************************
	/**
	 * When a players tries to join the game...
	 *
	 * @param p Player who joins
	 */
	public boolean playerJoin( Player p )
	{
		if( p == null )
			return true;    // not false, otherwise it would crash

		api.onPlayerLogin( p );
		if( api.isPlayerBanned( new PlayerIdent(p) ) )
			return false;

		config.save();
		MLog.d( "Player joined successfully: " + p.getName() );

		if( uvserver != null )
			uvserver.sendMessage( p.getName() + " joined the server via Minecraft." );

		return true;
	}

	//**********************************************************************************************
	/**
	 * When a player says goodbye
	 *
	 * @param p Player who leaves
	 */
	public boolean playerLeave( Player p )
	{
		if( p != null )
		{
			api.onPlayerLeave( p );

			if( useAuthorizer && auth.loggedIn( p ) )
				auth.logout( p );

			if( uvserver != null )
				uvserver.sendMessage( p.getName() + " left the channel via Minecraft." );
		}
		return false;
	}

	//**********************************************************************************************
	/**
	 * Join all players after reload
	 */
	private boolean rejoin()
	{
		// Get all Players and hook into count thread
		//    ONLY IF SOME STUPID KIDS JOINED BEFORE SERVER IS READY >:|
		Player[] oPlayer = getServer().getOnlinePlayers();
		if( oPlayer.length > 0 )
		{
			for( int i = 0; i < oPlayer.length; i++ )
			{
				api.onPlayerJoin( oPlayer[i] );
				playerJoin( oPlayer[i] );
			}
		}

		return true;
	}

	//**********************************************************************************************
	/**
	 * Handle ultravision Login Command (called by PreCommandListener)
	 *
	 * @param player the Player who issued the login command
	 * @param password the password given by the player
	 * @return true if password was correct or player is not registered or auth is not initialized
	 */
	public boolean doLoginCommand( Player player, String password )
	{
		if( auth == null )
			return true;

		if( player != null && password.length() > 0 )
		{
			if( !auth.isRegistered( player ) )
			{
				player.sendMessage( ChatColor.GOLD + "You're not registered in the login system." );
				return true;
			}
			if( auth.loggedIn( player ) )
			{
				player.sendMessage( ChatColor.RED + "You're already logged in." );
				return true;
			}

			if( password.contains( " " ) )
			{
				player.kickPlayer( MLog.real( ChatColor.DARK_GRAY + "[UltraVision] " + ChatColor.RED + "Wrong password!" ) );
				return true;
			}
			if( auth.login( player, password ) == MResult.RES_NOACCESS )
			{
				player.kickPlayer( MLog.real( ChatColor.DARK_GRAY + "[UltraVision] " + ChatColor.RED + "Wrong password!" ) );
				return true;
			}
			else
			{
				player.sendMessage( ChatColor.GREEN + "Logged in successfully." );
				return true;
			}
		}
		return false;
	}

	public boolean doUUIDOFCommand(String[] args, Player p)
	{
		if (args.length == 0)
		{
			p.sendMessage(ChatColor.RED + "Wrong argument count!");
			return true;
		}

		List<UltraVisionAPI.MatchUserResult> match = api.matchUser(args[0], true);
		if (match.isEmpty())
		{
			p.sendMessage(ChatColor.RED + "Cannot find this user!");
			return true;
		}

		// take first match here
		p.sendMessage(match.get(0).pIdent.toString());
		return true;
	}

	public void printDummyCommandHelp(Player p)
	{
		p.sendMessage(ChatColor.GOLD + "UV Dummy commands:");
		p.sendMessage("/dummy create");
		p.sendMessage("/dummy delete");
		p.sendMessage("/dummy tpto");
		p.sendMessage("/dummy tphere");
	}

	public boolean doDummyCommand(String[] args, Player p)
	{
		if (!useDebugDummy)
		{
			p.sendMessage(ChatColor.RED + "The DebugDummy Function is turned off on this server!");
			return true;
		}

		if (args.length == 0)
		{
			printDummyCommandHelp(p);
			return true;
		}

		boolean debugDummyIsLiving = debugDummy != null && debugDummy.isLiving;

		// create
		if (args[0].equalsIgnoreCase("create"))
		{
			if (debugDummyIsLiving)
			{
				p.sendMessage(ChatColor.RED + "The DebugDummy is already living!");
				return true;
			}

			if (debugDummy == null)
				debugDummy = new DebugDummy(this);

			loadDummyPlayer();
			p.sendMessage(ChatColor.GREEN + "DebugDummy spawned. His name is 'uvdummy'!");
			return true;
		}

		// delete
		if (args[0].equalsIgnoreCase("delete"))
		{
			if (!debugDummyIsLiving)
			{
				p.sendMessage(ChatColor.RED + "The debugdummy is not living!");
				return true;
			}

			p.sendMessage(ChatColor.RED + "Please use " + ChatColor.AQUA + "/kick uvdummy" + ChatColor.RED + " for that operation!");
			return true;
		}

		// tpto
		if (args[0].equalsIgnoreCase("tpto"))
		{
			if (!debugDummyIsLiving)
			{
				p.sendMessage(ChatColor.RED + "The debugdummy is not living!");
				return true;
			}

			p.teleport(debugDummy.getLocation());
			return true;
		}

		// tphere
		if (args[0].equalsIgnoreCase("tphere"))
		{
			if (!debugDummyIsLiving)
			{
				p.sendMessage(ChatColor.RED + "The debugdummy is not living!");
				return true;
			}

			debugDummy.teleportTo(p.getLocation());
			p.sendMessage(ChatColor.GRAY + "Note: It might take a while until you see the player - move around a bit!");
			return true;
		}

		printDummyCommandHelp(p);
		return true;
	}

	//**********************************************************************************************
	/**
	 * Handle ultravision Commands
	 *
	 * @param sender
	 * @param cmd
	 * @param commandLabel
	 * @param args
	 */
	@Override
	public boolean onCommand( CommandSender sender, Command cmd, String commandLabel, String[] args )
	{
		// Handle Player Commands
		if( sender instanceof Player )
		{
			Player p = (Player)sender;
			Class commandClass = null;

			// Faked Ban also handles /ban -commands
			if( cmd.getName().equalsIgnoreCase( "uvfban" ) )
			{
				p.sendMessage( ChatColor.GREEN + "Ban." );
				return true;
			}

			// UUID OF command
			if (cmd.getName().equalsIgnoreCase("uvuuidof"))
			{
				return doUUIDOFCommand(args, p);
			}


			if (cmd.getName().equalsIgnoreCase("uvadminhorse"))
			{
				Horse h = p.getWorld().spawn(p.getLocation(), Horse.class);
				h.setJumpStrength(2.0);
				h.setVariant(Horse.Variant.SKELETON_HORSE);
				h.setAdult();
				h.setTamed(true);
				h.setOwner(p);
				((EntityInsentient)((CraftLivingEntity)h).getHandle()).getAttributeInstance(GenericAttributes.d).setValue(2.5);

				p.sendMessage("Spawned adminhorse!");
				return true;
			}

			// UVDUMMY command
			if (cmd.getName().equalsIgnoreCase("uvdummy"))
			{
				return doDummyCommand(args, p);
			}


			// all other commands

			if( cmd.getName().equalsIgnoreCase( "ultravision" ) ) commandClass = ultravisionCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvkick" ) ) commandClass = kickCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvconfig" ) ) commandClass = configCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvbackendkick" ) ) commandClass = backendkickCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvwarn" ) ) commandClass = warnCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvtempwarn" ) ) commandClass = tempwarnCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvunwarn" ) ) commandClass = unwarnCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvpraise" ) ) commandClass = praiseCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvunpraise" ) ) commandClass = unpraiseCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvban" ) ) commandClass = banCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvunban" ) ) commandClass = unbanCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvtempban" ) ) commandClass = tempbanCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvgc" ) ) commandClass = gcCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvnote" ) ) commandClass = noteCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvdelnote" ) ) commandClass = delnoteCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvaddfriend" ) ) commandClass = addfriendCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvacceptfriend" ) ) commandClass = accfriendCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvdelfriend" ) ) commandClass = delfriendCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvstat" ) ) commandClass = statCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvingamelog" ) ) commandClass = ingamelogCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvsay" ) ) commandClass = sayCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvspamsay" ) ) commandClass = spamsayCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvclickregister") ) commandClass = clickRegisterCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvclickunregister" ) ) commandClass = clickUnregisterCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvregister" ) ) commandClass = registerCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvaunregister" ) ) commandClass = unregisterCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvclearconfig" ) ) commandClass = clearConfigCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvfake" ) ) commandClass = fakeCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "jmessage" ) ) commandClass = jmessageCommand.class;

			// Now run command if not null
			// otherwise we don't handle this command!
			if( commandClass != null )
			{
				this.runCommand( args, p, commandClass );
				return true;
			}
		}
		else
		{    // Running command from console

			ConsoleCommandSender console = (ConsoleCommandSender)sender;
			Class commandClass = null;

			if( cmd.getName().equalsIgnoreCase( "ultravision" ) )
			{
				MLog.i( "Running UltraVision " + fPDesc.getVersion() + " " + buildVersion + "." );
			}

			if( cmd.getName().equalsIgnoreCase( "uvkick" ) ) commandClass = kickCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvbackendkick" ) ) commandClass = backendkickCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvsay" ) ) commandClass = sayCommand.class;

			else if( cmd.getName().equalsIgnoreCase( "uvspamsay" ) ) commandClass = spamsayCommand.class;

			// Now run the command if not null
			if( commandClass != null )
			{
				this.runConsoleCommand( args, console, commandClass );
				return true;
			}

		}
		return false;
	}

	/**
	 * Runs a command by specified class
	 *
	 * @param <T>
	 * @return
	 */
	public <T extends extendedCommand> boolean runCommand( String[] args, Player p, Class<T> clazz )
	{
		try
		{
			Constructor commandClassConstructor = clazz.getConstructor( ultravision.class, String[].class );
			T newInstance = (T)commandClassConstructor.newInstance( this, args );
			return ( newInstance.run( p ) == commandResult.RES_SUCCESS );
		}
		catch( Exception ex )
		{
			MLog.e( "Cannot find class for command or something went wrong in this command: " + clazz.getName() );
			ex.printStackTrace( System.out );
			return false;
		}
	}

	/**
	 * Runs a command by specified class on console
	 *
	 * @param <T>
	 * @return
	 */
	public <T extends extendedCommand> boolean runConsoleCommand( String[] args, ConsoleCommandSender console, Class<T> clazz )
	{
		try
		{
			Constructor commandClassConstructor = clazz.getConstructor( ultravision.class, String[].class );
			T newInstance = (T)commandClassConstructor.newInstance( this, args );
			return ( newInstance.consoleRun(console) == commandResult.RES_SUCCESS );
		}
		catch( Exception ex )
		{
			MLog.e( "Cannot find class for consolecommand or something went wrong in this console command: " + clazz.getName() );
			ex.printStackTrace( System.out );
			return false;
		}
	}

	//**********************************************************************************************
	//                      G E T / S E T   P R I V A T E    I N S T A N C E S

	/**
	 * Gets the authorizer
	 * @return null if authorizer isnt used, otherwise the instance
	 */
	public MAuthorizer getAuthorizer()
	{
		if( !useAuthorizer ) return null;
		return auth;
	}

	/**
	 * Gets the Click Authorizer
	 * @return null if clickauthorizer isnt used, otherwise the instance
	 */
	public UVClickAuth getClickAuth()
	{
		if( !useClickAuthorizer ) return null;
		return clickauth;
	}

	/**
	 * Gets the API Instance
	 */
	public UltraVisionAPI getAPI()
	{
		return api;
	}

	/**
	 * Gets the Messager instance
	 * @return
	 */
	public JMessage getMessager()
	{
		return jmsg;
	}

	/**
	 * Gets configuration
	 * @return
	 */
	public MConfiguration getMConfig()
	{
		return config;
	}

	/**
	 * Set the configuration
	 * @param config
	 */
	public void setMConfig( MConfiguration config )
	{
		this.config = config;
	}

	/**
	 * Get the Plugin description File
	 * @return
	 */
	public PluginDescriptionFile getfPDesc()
	{
		return fPDesc;
	}

	//**********************************************************************************************
	/**
	 * Better Broadcast, that won't crash or show the message twice
	 *
	 * @param message The Message to be broadcasted
	 */
	public int ownBroadcast( String message )
	{
		Player[] ps = getServer().getOnlinePlayers();
		int cnt = 0;
		for( Player p : ps )
		{
			p.sendMessage( message );
			cnt++;
		}

		// Send message to console as well
		MLog.i( message );

		return cnt + 1;
	}

	//**********************************************************************************************
	/**
	 * Get state whether to use Authorizer or not
	 */
	public boolean IsUsingAuthorizer()
	{
		return useAuthorizer;
	}

	/**
	 * Get state whether to show warning when player is not registered or not
	 * @return
	 */
	public boolean showNotRegWarning()
	{
		return showNotRegWarning;
	}

	/**
	 * Get state whether to use commandlogging or not
	 * @return
	 */
	public boolean useCommandLog()
	{
		return useCommandLog;
	}
}
