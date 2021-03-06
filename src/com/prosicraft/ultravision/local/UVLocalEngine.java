/*  ============================================================================
 *
 *      U L T R A V I S I O N  ---  P l a y e r   S u p e r v i s i o n
 *
 *           L  O  C  A  L        B  A  N  -   E  N  G  I  N  E
 *
 *                              by prosicraft  ,   (c) 2014
 *
 *  ============================================================================
 */
package com.prosicraft.ultravision.local;

import com.prosicraft.ultravision.base.*;
import com.prosicraft.ultravision.commands.timeInterpreter;
import com.prosicraft.ultravision.ultravision;
import com.prosicraft.ultravision.util.*;
import java.io.*;
import java.sql.Time;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 *
 * Engine Main Class
 *
 * @author prosicraft
 *
 *
 */
public class UVLocalEngine implements UltraVisionAPI
{
	private MAuthorizer authorizer		= null;	// The authorizer
	private ultravision ultravisionPlugin	= null; // The UltraVision Plugin
	private List<UVLocalPlayer> players	= null;	// player memory
	private String pluginDirectory		= "";	// The path to the plugin Directory

	public List<String> debugPlayers	= new ArrayList<>(); // players registered for debug output

	/**********************************************************************/
	/**
	 * Constructor
	 * @param pluginDir
	 */
	public UVLocalEngine( String pluginDir, ultravision plugin )
	{
		this.players = new ArrayList<>();
		this.pluginDirectory = pluginDir;
		this.ultravisionPlugin = plugin;
	}

	/**********************************************************************/
	@Override
	public boolean shutdown()
	{
		boolean result = true;
		for( UVLocalPlayer player : players )
		{
			PlayerIdent pIdent = new PlayerIdent(player.craftPlayer.getUniqueId());
			MResult tempRes;
			if( (tempRes = savePlayer(pIdent)) != MResult.RES_SUCCESS )
			{
				MLog.d("Could save Player: Returned with error " + tempRes.toString());
				result = false;
			}
		}
		players.clear();

		return result;
	}

	/**********************************************************************/
	public void logDebug( Player p, String message )
	{
		if( p != null && MConst._DEBUG_ENABLED )
		{
			for( String debugPlayerNameIterator : debugPlayers )
			{
				if( p.getName().equalsIgnoreCase( debugPlayerNameIterator ) )
				{
					p.sendMessage( ChatColor.DARK_GRAY + "uvdbg: " + ChatColor.GRAY + message );
					return;
				}
			}
		}
	}

	/**********************************************************************/
	/**
	 * Search for a UVLocalPlayer that matches given name
	 *
	 * @param playerName players name
	 * @returns null if player cannot be loaded into memory,
	 *	e.g. when server cannot find a player instance
	 */
	public UVLocalPlayer getUVLocalPlayer( PlayerIdent uid, Player playerInstance )
	{
		for( UVLocalPlayer player : players )
		{
			if( uid.Equals(player.getCraftPlayer().getUniqueId()) )
			{
				return player;
			}
		}

		// If readPlayer cannot find the player in the bukkit system yet,
		// it will use the given playerInstance.
		// If the playerInstance is just null, then readPlayer will
		// only return the player information if there is one.
		readPlayer( uid, true, playerInstance );

		for( UVLocalPlayer player : players )
		{
			if( uid.Equals(player.getCraftPlayer().getUniqueId()) )
			{
				MLog.d( "Got player successfully." );
				return player;
			}
		}

		return null;
	}
	public UVLocalPlayer getUVLocalPlayer(PlayerIdent uid)
	{
		return getUVLocalPlayer(uid, null);
	}

	/**********************************************************************/
	@Override
	public Player getPlayer( PlayerIdent uid )
	{
		UVLocalPlayer localPlayer = getUVLocalPlayer( uid );
		if( localPlayer != null )
		{
			return (Player)localPlayer.getCraftPlayer();
		}
		return null;
	}

	/**********************************************************************/
	@Override
	public UVPlayerInfo getPlayerInfo( PlayerIdent uid )
	{
		UVLocalPlayer localPlayer = getUVLocalPlayer( uid );
		if( localPlayer != null )
		{
			return localPlayer.i;
		}
		return null;
	}


	/**********************************************************************/
	public MResult writePlayerInformation(DataOutputStream fod, UVPlayerInfo i)
	{
		try
		{
			fod.write( MAuthorizer.getCharArrayB( i.name, 16 ) );  // Write player name
			fod.write( i.isMute ? 1 : 0 ); // Write mute state
			try
			{
				// last online (last logout)
				if( i.lastOnline != null )
					fod.writeLong(i.lastOnline.getTime());
				else
					fod.writeLong(Calendar.getInstance().getTime().getTime());

				// last login
				if (i.lastLogin != null)
					fod.writeLong(i.lastLogin.getTime());
				else
					fod.writeLong(Calendar.getInstance().getTime().getTime());

				// total online time
				fod.writeLong(i.onlineTime.getTime());
			}
			catch( IOException ex )
			{
				MLog.e( "Can't write times to database!" );
			}

			fod.write( i.praise );   // Write praise

			//=== Write praisers
			if( !i.praiser.isEmpty() )
			{
				for( String praiser : i.praiser )
				{
					fod.write( MAuthorizer.getCharArrayB( "oprais", 6 ) );
					fod.write( MAuthorizer.getCharArrayB( praiser, 16 ) );
				}
			}
			else
			{
				fod.write( MAuthorizer.getCharArrayB( "nprais", 6 ) );
			}

			//=== Write bans
			fod.write( MAuthorizer.getCharArrayB( "theban", 6 ) );
			if( i.ban != null )
			{
				i.ban.write( fod );
			}
			else
			{
				UVBan.writeNull( fod );
			}

			if( !i.banHistory.isEmpty() )
			{
				for( UVBan b : i.banHistory )
				{
					fod.write( MAuthorizer.getCharArrayB( "oneban", 6 ) );
					b.write( fod );
				}
			}
			else
			{
				fod.write( MAuthorizer.getCharArrayB( "nooban", 6 ) );
			}

			//=== Write Warnings
			fod.write( MAuthorizer.getCharArrayB( "thwarn", 6 ) );
			if( i.warning != null )
			{
				i.warning.write( fod );
			}
			else
			{
				UVWarning.writeNull( fod );
			}

			if( !i.warnHistory.isEmpty() )
			{
				for( UVWarning b : i.warnHistory )
				{
					fod.write( MAuthorizer.getCharArrayB( "onwarn", 6 ) );
					b.write( fod );
				}
			}
			else
			{
				fod.write( MAuthorizer.getCharArrayB( "nowarn", 6 ) );
			}

			//=== Write Kick History
			if( !i.kickHistory.isEmpty() )
			{
				for( UVKick k : i.kickHistory )
				{
					fod.write( MAuthorizer.getCharArrayB( "onkick", 6 ) );
					k.write( fod );
				}
			}
			else
			{
				fod.write( MAuthorizer.getCharArrayB( "nokick", 6 ) );
			}

			//=== Write Friends
			if( !i.friends.isEmpty() )
			{
				for( String friend : i.friends )
				{
					fod.write( MAuthorizer.getCharArrayB( "friend", 6 ) );
					fod.write( MAuthorizer.getCharArrayB( friend, 16 ) );
				}
			}
			else
			{
				fod.write( MAuthorizer.getCharArrayB( "nofrie", 6 ) );
			}

			//=== Write notes
			if( !i.notes.isEmpty() )
			{
				for( String devil : i.notes.keySet() )
				{
					fod.write( MAuthorizer.getCharArrayB( "onnote", 6 ) );
					fod.write( MAuthorizer.getCharArrayB( devil, 16 ) );
					fod.write( MAuthorizer.getCharArrayB( i.notes.get( devil ), 60 ) );
				}
			}
			else
			{
				fod.write( MAuthorizer.getCharArrayB( "nonote", 6 ) );
			}

			return MResult.RES_SUCCESS;
		}
		catch( IOException ex )
		{
			return MResult.RES_ERROR;
		}
	}

	public MResult savePlayer(PlayerIdent uid, UVPlayerInfo customInformation)
	{
		if (uid == null)
		{
			MLog.d("Given uid in savePlayer(PlayerIdent, UVPlayerInfo) is null!");
			return MResult.RES_ERROR;
		}

		MLog.d("Now trying to save Player file of puuid " + uid.toString());

		try
		{
			// Get the Local Player and its information
			UVLocalPlayer player = null;
			UVPlayerInfo playerInfo = null;
			if (customInformation == null)
			{
				for( UVLocalPlayer playerIterator : players )
				{
					if( uid.Equals(playerIterator.getCraftPlayer().getUniqueId()) )
					{
						player = playerIterator;
						playerInfo = player.i;
						break;
					}
				}

				if( player == null )
				{
					MLog.e("Cannot save player: Not found in UV memory and no custom player information given");
					return MResult.RES_NOTGIVEN;
				}
			}
			else
			{
				playerInfo = customInformation;
			}

			// Create file if not there already
			File ud = new File( pluginDirectory + UltraVisionAPI.userDataDir, uid.toString() + ".usr" );
			if( !ud.exists() )
			{
				MLog.d( "File doesn't exist at " + MConfiguration.normalizePath( ud ) + ". Trying to create new one..." );
				try
				{
					File uf = new File( pluginDirectory + UltraVisionAPI.userDataDir );
					if( !uf.exists() )
					{
						uf.mkdirs();
					}

					ud.createNewFile();
					MLog.i( "Created new player data file at '" + MConfiguration.normalizePath( ud ) + "'" );
				}
				catch( IOException ioex )
				{
					MLog.e( "Can't create new file at " + MConfiguration.normalizePath( ud ) );
					ioex.printStackTrace(System.out);
					return MResult.RES_ERROR;
				}
			}

			DataOutputStream fod;
			try
			{
				fod = new DataOutputStream( new FileOutputStream( ud ) );
			}
			catch( FileNotFoundException fnfex )
			{
				MLog.e( "(flushUD) Can't get UserData file for save: File not found (user: '" + player.GetName() + "' (" + player.GetIdent().toString() + ")" );
				return null;
			}

			// write header
			fod.write( MAuthorizer.getCharArrayB( "ouvplr", 6 ) );
			fod.write( MAuthorizer.getCharArrayB( "uvinfo", 6 ) );
			fod.write( UVFileInformation.uVersion );  // The Version

			// write actual data
			writePlayerInformation(fod, playerInfo);

			// write end and close file
			fod.write( MAuthorizer.getCharArrayB( "theend", 6 ) );
			fod.flush();
			fod.close();
		}
		catch( IOException ex )
		{
			MLog.e( "An error occured while attempting to save player file (player uid: " + uid.toString() + ")" );
			ex.printStackTrace( System.out );
			return MResult.RES_ERROR;
		}

		return MResult.RES_SUCCESS;
	}

	@Override
	public MResult savePlayer( PlayerIdent uid )
	{
		return savePlayer(uid, null);
	}

	/**********************************************************************/
	/**
	 * Read the header of a chunk
	 * @param in
	 * @return
	 */
	private String readChunkHeader( DataInputStream in )
	{
		return readString( in, 6 );
	}

	/**
	 * read a String from stream
	 * @param in
	 * @param bytes
	 * @return
	 */
	private String readString( DataInputStream in, int bytes )
	{
		byte[] buf = new byte[ bytes ];
		try
		{
			in.read( buf );
			return new String( buf );
		}
		catch( IOException ioex )
		{
			MLog.e( "(fetchDB) Error while reading chars: " + ioex.getMessage() );
			ioex.printStackTrace( System.out );
			return "";
		}
	}

	/**
	 * Get an additional Chunk instance
	 * @param <T>
	 * @param c
	 * @return
	 */
	public <T extends UVPlayerInfoChunk> T getAdditionalChunkInstance( Class<T> c )
	{
		try
		{
			return c.newInstance();
		}
		catch( InstantiationException | IllegalAccessException ex )
		{
			Logger.getLogger( UVLocalEngine.class.getName() ).log( Level.SEVERE, null, ex );
		}
		return null;
	}


	@Override
	public UVPlayerInfo readPlayer( PlayerIdent uid, boolean forceNewFile, Player playerInstance )
	{
		MLog.d( "Start fetching Player Info from player with id " + uid.toString() + " ..." );

		UVPlayerInfo resultInformation;
                File ud = null;

                // first, check if there is an old file with the player name
                Player checkPlayer = ultravisionPlugin.getServer().getPlayer(uid.Get());
                if( checkPlayer != null )
                {
                    // check if the file for this online player is there.
                    // if so, convert it to a uid-file
                    ud = new File(pluginDirectory + UltraVisionAPI.userDataDir, checkPlayer.getName() + ".usr");
                    if (ud.exists())
                    {
                        MLog.i("Found old player information file. Converting it to a UID-file...");
                        ud.renameTo(new File(pluginDirectory + UltraVisionAPI.userDataDir, uid.toString() + ".usr"));

                        // If necessary do further conversion here...




                    }
                    else
                    {
                        ud = null;
                    }
                }

                // not already found, try with a uid file
                if( checkPlayer == null || ud == null )
                {
                    ud = new File( pluginDirectory + UltraVisionAPI.userDataDir, uid.toString() + ".usr" );
                }

		if( !ud.exists() )
		{
			if( !forceNewFile )
			{
				return null;
			}

			MLog.i( "File doesn't exist at " + MConfiguration.normalizePath( ud ) + ". Trying to create new one..." );
			try
			{
				File uf = new File( pluginDirectory + UltraVisionAPI.userDataDir );
				if( !uf.exists() )
				{
					uf.mkdirs();
				}

				ud.createNewFile();
				MLog.d( "Created new file at " + MConfiguration.normalizePath( ud ) );
			}
			catch( IOException ioex )
			{
				MLog.e( "Can't create new file at " + MConfiguration.normalizePath( ud ) );
				if( MConst._DEBUG_ENABLED )
				{
					ioex.printStackTrace( System.out );
				}

				return null;
			}
		}

		// Check if the file exists but it is empty (means we recently created a new one)
		if( ud.length() == 0 )
		{
			MLog.d( "File is empty." );

			// then add Default player information chunks
			resultInformation = new UVPlayerInfo();

			// check if somehow already have a player with this name
			boolean playerAlreadyLoaded = false;
			MResult savePlayerResult = MResult.RES_SUCCESS;
			for( UVLocalPlayer player : players )
			{
				if( uid.Equals(player.getCraftPlayer().getUniqueId()) )
				{
					// then set information to this data
					resultInformation = player.i;
					playerAlreadyLoaded = true;
					break;
				}
			}

			// otherwise add new player and save
                        Player bukkitPlayer = null; // storing bukkit player instance if found
			if( !playerAlreadyLoaded )
			{
				if( null == ( bukkitPlayer = ultravisionPlugin.getServer().getPlayer(uid.Get()) ) )
				{
					bukkitPlayer = ultravisionPlugin.getServer().getOfflinePlayer( uid.Get() ).getPlayer();
				}

				if( null == bukkitPlayer )
				{
					MLog.d( "Cannot create a new file for player with id " + uid.toString() + "' as he was never on the server." );
					return null;
				}

				UVLocalPlayer newLocalPlayer = new UVLocalPlayer( bukkitPlayer, pluginDirectory, resultInformation);
				players.add( newLocalPlayer );
				savePlayerResult = savePlayer( uid );
			}

			// Now print result
			if( bukkitPlayer != null && savePlayerResult == MResult.RES_SUCCESS )
			{
				MLog.i( "Created new Player Data File for player '" + bukkitPlayer.getName() + "' (" + uid.toString() + ")" );
			}
			else
			{


                            // TODO: maybe try to retrieve the name of the player anyway


                            MLog.e( "Could not properly create new player data file for player with id " + uid.toString() );


			}
			return resultInformation;
		}

		// Get the Datastream for inputting data
		DataInputStream fid;
		try
		{
			fid = new DataInputStream( new FileInputStream( ud ) );
		}
		catch( FileNotFoundException fnfex )
		{
			MLog.e( "(fetchUD) Can't load UserData file: File not found" );
			return null;
		}


		// Now read.
		try
		{
			UVFileInformation fi = new UVFileInformation( UVFileInformation.uVersion );
			resultInformation = new UVPlayerInfo();
			String ch;

			// Read the file type identifier chunk id
			ch = readChunkHeader( fid );
			if( !ch.equalsIgnoreCase( "ouvplr" ) )  // prosicraft, 20.6.2014: What does ouvplr mean?
			{
				MLog.w( "User Data File damaged at " + MConfiguration.normalizePath( ud ) + ". Backup..." );
				fid.close();
				ud.renameTo( new File( pluginDirectory + UltraVisionAPI.userDataDir, uid.toString() + ".dmg" ) );
				return resultInformation;
			}

			// Read the file information
			ch = readChunkHeader( fid );
			if( !ch.equalsIgnoreCase( "uvinfo" ) )
			{
				MLog.w( "User Data File damaged at " + MConfiguration.normalizePath( ud ) + ". Backup..." );
				fid.close();
				ud.renameTo( new File( pluginDirectory + UltraVisionAPI.userDataDir, uid.toString() + ".dmg" ) );
				return resultInformation;
			}
			else
			{
				fi.setVersion( fid.read() );
				MLog.d("File version is '" + fi.getVersion() + "' at " + MConfiguration.normalizePath(ud));
			}

			// Read the players name
			resultInformation.name = readString(fid, 16);

			// Read the general information
			int isMute = fid.read();
			resultInformation.isMute = ( ( isMute == 0 ) ? false : true );
			try
			{
				if (fi.getVersion() >= 3)
					resultInformation.lastOnline = new Time(fid.readLong());
				else
					resultInformation.lastOnline = new Time(Calendar.getInstance().getTime().getTime());

				if (fi.getVersion() >= 4)
					resultInformation.lastLogin = new Time(fid.readLong());
				else
					resultInformation.lastLogin = new Time(Calendar.getInstance().getTime().getTime());
			}
			catch( EOFException eofex )
			{
				MLog.e( "File critically damaged at " + MConfiguration.normalizePath( ud ) + ". Backup..." );
				fid.close();
				ud.renameTo( new File( pluginDirectory + UltraVisionAPI.userDataDir, uid.toString() + ".dmg" ) );
				return resultInformation;
			}
			catch( Exception ex )
			{
				MLog.e( "File damaged at " + MConfiguration.normalizePath( ud ) + "!" );
			}

			resultInformation.onlineTime = new Time( fid.readLong() );
			resultInformation.praise = fid.read();


			// Now read chunks
			boolean isPlayerChunk = true;
			while(isPlayerChunk)
			{
				if( ( ch = readChunkHeader( fid ) ).equalsIgnoreCase( "theend" ) )
				{
					break;
				}

				if( ch.equalsIgnoreCase( "oprais" ) )
				{
					resultInformation.praiser.add( readString( fid, 16 ) );
				}
				else if( ch.equalsIgnoreCase( "nprais" ) )
				{
					continue;
				}
				else if( ch.equalsIgnoreCase( "theban" ) )
				{
					resultInformation.ban = new UVBan();
					if( !resultInformation.ban.read( fid, fi ) )
					{
						resultInformation.ban = null;
					}
				}
				else if( ch.equalsIgnoreCase( "oneban" ) )
				{
					UVBan b = new UVBan();
					b.read( fid, fi );
					resultInformation.banHistory.add( b );
				}
				else if( ch.equalsIgnoreCase( "nooban" ) )
				{
					continue;
				}
				else if( ch.equalsIgnoreCase( "thwarn" ) )
				{
					resultInformation.warning = new UVWarning();
					if( !resultInformation.warning.read( fid, fi ) )
					{
						resultInformation.warning = null;
					}
				}
				else if( ch.equalsIgnoreCase( "onwarn" ) )
				{
					UVWarning w = new UVWarning();
					w.read( fid, fi );
					resultInformation.warnHistory.add( w );
				}
				else if( ch.equalsIgnoreCase( "nowarn" ) )
				{
					continue;
				}
				else if( ch.equalsIgnoreCase( "onkick" ) )
				{
					UVKick k = new UVKick();
					k.read( fid, fi );
					resultInformation.kickHistory.add( k );
				}
				else if( ch.equalsIgnoreCase( "nokick" ) )
				{
					continue;
				}
				else if( ch.equalsIgnoreCase( "frireq" ) )
				{
					resultInformation.friendRequests.add( MStream.readString( fid, 16 ) );
				}
				else if( ch.equalsIgnoreCase( "friend" ) )
				{
					resultInformation.friends.add( MStream.readString( fid, 16 ) );
				}
				else if( ch.equalsIgnoreCase( "nofrie" ) )
				{
					continue;
				}
				else if( ch.equalsIgnoreCase( "onnote" ) )
				{
					resultInformation.notes.put( MStream.readString( fid, 16 ), MStream.readString( fid, 60 ) );
				}
				else if( ch.equalsIgnoreCase( "nonote" ) )
				{
					continue;
				}
				else
				{
					isPlayerChunk = false;
				}
			}

			fid.close();
			MLog.d("Finished read file!");

			// Check if we already loaded this player
			// If so, we'll delete the old instance
			for( UVLocalPlayer player : players  )
			{
				if (player.playerIdent == null)
				{
					MLog.d("Found player registered in UV without UUID. Deleting...");
					players.remove(player);
					continue;
				}

				if (uid.Equals(player.playerIdent) || (playerInstance != null && uid.Equals(playerInstance.getUniqueId())))
				{
					players.remove(player);
					break;
				}
			}

			// If the player is already registered in the bukkit system,
			// then add him to our array immediately, otherwise we'll just return the player information
			Player bukkitPlayer = ultravisionPlugin.getServer().getPlayer(uid.Get());
			if( bukkitPlayer == null )
			{
				MLog.d("Could not find online player with uuid " + uid.toString() + ". Trying to search an offline player...");
				bukkitPlayer = ultravisionPlugin.getServer().getOfflinePlayer( uid.Get() ).getPlayer();
			}

			if (bukkitPlayer == null)
			{
				if (playerInstance == null)
				{
					MLog.d("Read player data file which could not be associated with any bukkit Player instance!");
					return resultInformation;
				}
				else
				{
					bukkitPlayer = playerInstance;
					MLog.d("Using given player Instance, not registered in bukkit system yet to load player into memory.");
				}
			}

			UVLocalPlayer newPlayer = new UVLocalPlayer( bukkitPlayer, pluginDirectory, resultInformation );

			MLog.d("Loaded lastonline: " + String.valueOf(newPlayer.i.lastOnline));

			// prevent NPE in JMessage
			if (newPlayer.i.lastOnline == null) newPlayer.i.lastOnline = new Time( Calendar.getInstance().getTime().getTime() );
			if (newPlayer.i.lastLogin == null) newPlayer.i.lastLogin = new Time( Calendar.getInstance().getTime().getTime() );

			players.add( newPlayer );
			MLog.i( "Loaded player named '" + bukkitPlayer.getName() + "' (" + uid.toString() + ") into memory." );
			MLog.d("Loaded players now: " + players.size());

			return resultInformation;
		}
		catch( IOException ioex )
		{
			MLog.e( "Can't read user data file (puuid: " + uid.toString() + "): " + ioex.getMessage() );
			ioex.printStackTrace( System.out );
			return null;
		}
	}

	@Override
	public UVPlayerInfo readPlayer(PlayerIdent uid, boolean forceNewFile)
	{
		return readPlayer(uid, forceNewFile, null);
	}

	/**********************************************************************/
	@Override
	public void onPlayerJoin( Player p )
	{
		MLog.d("Doing LocalEngine::onPlayerJoin");

		// check if userentry found
		PlayerIdent pIdent = new PlayerIdent(p.getUniqueId());

		// getUVLocalPlayer will try to read the player data file and create
		// a new one if it doesn't exist yet
		UVLocalPlayer localPlayer = getUVLocalPlayer(pIdent, p);
		if( localPlayer == null )
		{
			MLog.d("Could not add player in LocalEngine::getUVLocalPlayer, so we'll try to add it now.");

			// create the new player instance
			localPlayer = new UVLocalPlayer( p, pluginDirectory, new UVPlayerInfo() );
			localPlayer.i.lastOnline = new Time( Calendar.getInstance().getTime().getTime() );
			localPlayer.i.lastLogin = new Time( Calendar.getInstance().getTime().getTime() );

			players.add( localPlayer );
			savePlayer( localPlayer.GetIdent() );
			MLog.d( "Created PlayerInstance '" + p.getName() + "' (" + pIdent.toString() + ") to memory!" );
		}
	}

	/**********************************************************************/
	@Override
	public void onPlayerLogin( Player p )
	{
		MLog.d("Doing LocalEngine::onPlayerLogin");

		if( p == null )
		{
			MLog.e( "Parameter p is null in UVLocalEngine.onPlayerLogin()!" );
			return;
		}

		PlayerIdent pIdent = new PlayerIdent(p.getUniqueId());
		UVLocalPlayer localPlayer = getUVLocalPlayer(pIdent, p);
		if( localPlayer != null )
		{
			localPlayer.i.lastLogin = new Time( Calendar.getInstance().getTime().getTime() );
			localPlayer.i.offline = false;
		}
		else
		{
			MLog.e( "Could not find logging in user '" + p.getName() + "' (" + pIdent.toString() + ")." );
			MLog.e( "This seems to be a bug OR might be hacking!" );
		}
	}

	/**********************************************************************/
	@Override
	public void onPlayerLeave( Player p )
	{
		MLog.d("Doing LocalEngine::onPlayerLeave");

		if( p == null )
		{
			return;
		}

		PlayerIdent pIdent = new PlayerIdent(p.getUniqueId());
		UVLocalPlayer localPlayer = getUVLocalPlayer( pIdent );
		if( localPlayer != null )
		{
			Time t = new Time( Calendar.getInstance().getTime().getTime() );
			localPlayer.i.lastOnline = t;
			localPlayer.i.offline = true;
			MResult res;
			if( ( res = addPlayerOnlineTime( new Time( t.getTime() - localPlayer.i.lastLogin.getTime() ), pIdent ) ) == MResult.RES_SUCCESS )
			{
				if( p.getAddress() != null )
				{
					localPlayer.log( "** Left successfully. (ip " + p.getAddress().toString() + ")" );
				}
			}
			else
			{
				localPlayer.log( "[ERROR] ** Left with error: " + res.toString() );
				MLog.e("Player " + p.getName() + " left with error: " + res.toString());
			}

			localPlayer.quitlog();
			savePlayer( pIdent );
		}
		else
		{
			MLog.w( "Player left but not registered in UV: " + p.getName() + "(" + pIdent.toString() + ")" );
		}
	}

	/**********************************************************************/
	@Override
	public Map<String, String> getAllPlayerInformation( PlayerIdent uid )
	{
		throw new UnsupportedOperationException( "Not supported yet." );
	}

	/**********************************************************************/
	@Override
	public MResult setAuthorizer( MAuthorizer authorizer )
	{
		if( authorizer == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( this.authorizer != null && this.authorizer.equals( authorizer ) )
		{
			return MResult.RES_ALREADY;
		}

		this.authorizer = authorizer;
		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MAuthorizer getAuthorizer()
	{
		return this.authorizer;
	}

	/**********************************************************************/
	@Override
	public boolean isAuthorizerEnabled()
	{
		return ( this.authorizer != null );
	}

	/**********************************************************************/
	@Override
	public boolean isPlayerBanned( PlayerIdent uid )
	{
		UVPlayerInfo ui = getPlayerInfo( uid );
		if( ui != null && ui.ban != null )
		{
			MLog.d( "isBanned::getTimeRemain() = '" + ui.ban.getTimeRemain() + "'" );
			if( ui.ban.getTimeRemain() == null )
			{
				return true;
			}
			if( ui.ban.getTimeRemain().getTime() <= 0 )
			{
				MLog.d( "reset ban as time <= 0" );
				ui.ban = null;
				return false;
			}
			else
			{
				return true;
			}
		}
		else
		{
			return false;
		}
	}

	/**********************************************************************/
	@Override
	public MResult banPlayerLocally( CommandSender cs, PlayerIdent uid, String reason )
	{
		return banPlayer( cs, uid, reason, false );
	}

	/**********************************************************************/
	@Override
	public MResult banPlayer( CommandSender cs, PlayerIdent uid, String reason, boolean global )
	{
		return banPlayerTemporarily( cs, uid, reason, null, global );
	}

	/**********************************************************************/
	/*
	 * @param time: If player should be banned permanently, then set to null
	 */
	@Override
	public MResult banPlayerTemporarily( CommandSender cs, PlayerIdent uid, String reason, Time time, boolean global )
	{
		if( global )
		{
			MLog.w( "Can't global ban player with id " + uid.toString() + " in local mode. Set to local." );
			global = false;
		}

		// Check if command executor is given and accessable
		boolean bSenderIsConsole = false;
                if( cs == null )
                {
			MLog.e( "Cannot ban Player Temporarily: Command Executor parameter (cs) not set!" );
			return MResult.RES_NOTGIVEN;
                }
		else if( !(cs instanceof Player) && !(bSenderIsConsole = cs instanceof ConsoleCommandSender) )
		{
			MLog.e("Cannot ban Player temporarily: Command Executor is not a player or the console!");
			return MResult.RES_ERROR;
		}

		// try to get the player
		// If the player to be banned is offline, then getUVLocalPlayer does not return the player information tho
		// That's why we'll directly access the readPlayer function here.
		UVPlayerInfo localPlayerInformation = readPlayer(uid, true);
		if (localPlayerInformation == null)
		{
			MLog.w("Could not find player information for " + uid.toString() + " during ban process. Creating new one to ban though...");
			localPlayerInformation = new UVPlayerInfo();
			localPlayerInformation.name = "???";
			localPlayerInformation.lastLogin = new Time(0);
			localPlayerInformation.lastOnline = new Time(0);
			localPlayerInformation.onlineTime = new Time(0);
		}

		// extra security check for logged in status of command executor
		if( authorizer != null && ( authorizer.isRegistered( (Player)cs ) && !authorizer.loggedIn( (Player)cs ) ) )
		{
			return MResult.RES_NOACCESS;
		}

		// Check if this user is already banned
		if( localPlayerInformation.ban != null )
		{
			return MResult.RES_ALREADY;
		}

		// try to get the bukkit player instance
		List<MatchUserResult> playerMatch = matchUser(localPlayerInformation.name, true);
		OfflinePlayer kickedPlayer = null; // oflinePlayer because he could be offline...
		if (!playerMatch.isEmpty())
		{
			kickedPlayer = ultravisionPlugin.getServer().getOfflinePlayer(playerMatch.get(0).pIdent.Get());
		}

		// check if the player is online
		if (kickedPlayer != null && kickedPlayer.isOnline()
			&& (kickedPlayer instanceof Player)) // just to make sure that we can safely cast to Player
		{
			// format the ban message
			String banMessage = ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA +
				( ( time == null ) ? "B" : "Tempb" ) + "an" + ChatColor.DARK_GRAY + "] " +
				ChatColor.AQUA + reason + " (local" + ( ( time == null ) ? "" : ", for " + timeInterpreter.getText( time.getTime() ) ) + ")";

			// kick him
			Player kickedPlayerInstance = (Player)kickedPlayer;
			kickedPlayerInstance.kickPlayer(banMessage);

			MLog.i("Kicked player.");

			// try to put info about the ban into his logfile
			UVLocalPlayer localUVPlayer = getUVLocalPlayer(uid, kickedPlayerInstance);
			if (localUVPlayer != null)
			{
				localUVPlayer.log(banMessage + " BY " + cs.getName());
			}
		}
                else
                {
                    MLog.d("Did not kick player in perma ban execution! " +
                            "[kickedPlayer=" + kickedPlayer +
                            (kickedPlayer != null ? ", .isOnline()=" + kickedPlayer.isOnline() : "") +
                            ", instanceof Player:" + (kickedPlayer instanceof Player));
                }

		// Gather Ban Information
		if (bSenderIsConsole)
		{
			localPlayerInformation.ban = new UVBan(reason, null, global, time);
			localPlayerInformation.banHistory.add( localPlayerInformation.ban );
		}
		else
		{
			Player senderPlayer = (Player)cs;
			UVLocalPlayer uPBanner = getUVLocalPlayer( new PlayerIdent(senderPlayer) );

			// add the ban
			localPlayerInformation.ban = new UVBan( reason, uPBanner.craftPlayer, global, time );
			localPlayerInformation.banHistory.add( localPlayerInformation.ban );
		}

		// save player file
		if( savePlayer( uid, localPlayerInformation ) != MResult.RES_SUCCESS )
		{
			MLog.e( "Can't save userdata for player '" + localPlayerInformation.name + "' (" + uid.toString() + ")" );
			return MResult.RES_ERROR;
		}

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult pardonPlayer( CommandSender cs, PlayerIdent uid, String note )
	{
		boolean bSenderIsConsole = false;
		if (cs instanceof ConsoleCommandSender)
		{
			bSenderIsConsole = true;
		}
		else if(!(cs instanceof Player))
		{
			return MResult.RES_ERROR;
		}

		if( !bSenderIsConsole )
		{
			if( authorizer != null && ( authorizer.isRegistered( (Player) cs ) && !authorizer.loggedIn( (Player) cs ) ) )
			{
				return MResult.RES_NOACCESS;
			}
		}

		UVLocalPlayer localPlayer = getUVLocalPlayer( uid );
		if( localPlayer == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		UVPlayerInfo localPlayerInformation = localPlayer.i;

		// Check if player is banned at all
		if( localPlayerInformation.ban == null )
		{
			return MResult.RES_ALREADY;
		}

		// Prevent pardon from globalban (use pardonrequest instead)
		if( localPlayerInformation.ban.isGlobal() )
		{
			return MResult.RES_NOTGIVEN;
		}

		localPlayerInformation.ban = null;
		savePlayer( uid );

		String executorName = "Console";
		if (!bSenderIsConsole)
			executorName = cs.getName();

		MLog.i( "Player '" + localPlayer.GetName() + "' (" + uid + ") pardoned by " + executorName );

		return MResult.RES_SUCCESS;

	}

	/**********************************************************************/
	@Override
	public UVBan getPlayerBan( PlayerIdent uid, String serverName )
	{
		UVLocalPlayer localPlayer = getUVLocalPlayer( uid );
		if( localPlayer != null )
		{
			return localPlayer.i.ban;
		}

		return null;
	}

	/**********************************************************************/
	@Override
	public List<UVBan> getPlayerBans( PlayerIdent uid )
	{
		List<UVBan> res = new ArrayList<>();
		UVLocalPlayer localPlayer = getUVLocalPlayer( uid );

		if( localPlayer == null )
			return res;

		if( localPlayer.i.ban != null )
		{
			res.add( localPlayer.i.ban );
		}

		return res;
	}

	/**********************************************************************/
	@Override
	public List<UVBan> getPlayerBanHistory( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		return ( ( uP.i.banHistory != null ) ? uP.i.banHistory : new ArrayList<UVBan>() );
	}

	/**********************************************************************/
	@Override
	public MResult kickPlayer( CommandSender cs, PlayerIdent uid, String reason )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTINIT;  // or RES_ALREADY
		}

		boolean bSenderIsConsole = false;
		String kickerName = "";
		if (cs instanceof ConsoleCommandSender)
		{
			bSenderIsConsole = true;
			kickerName = "Console";
		}
		else if (cs == null || !(cs instanceof Player))
		{
			return MResult.RES_NOTINIT;
		}

		if (!bSenderIsConsole)
			kickerName = cs.getName();

		MLog.real( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "Kick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason + " BY " + kickerName );
		uP.log( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "Kick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason + " BY " + kickerName );

		/*( uP.getCraftPlayer() ).getHandle().playerConnection.player.extinguish();
		( uP.getCraftPlayer() ).getHandle().playerConnection.sendPacket( new Packet255KickDisconnect( MLog.real( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "Kick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason ) ) );
		( uP.getCraftPlayer() ).getHandle().playerConnection.networkManager.d();
		( (CraftServer)uP.getCraftPlayer().getServer() ).getHandle().disconnect( uP.getCraftPlayer().getHandle() );
		( uP.getCraftPlayer() ).getHandle().playerConnection.disconnected = true;*/
                uP.getCraftPlayer().getServer().getPlayer( uid.Get() ).kickPlayer( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "Kick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason );

		if( bSenderIsConsole )
		{
			return MResult.RES_SUCCESS;
		}

		uP.quitlog();
		uP.i.kickHistory.add( new UVKick( reason, ( Player ) cs, new Time( ( new Date() ).getTime() ) ) );
		if( savePlayer( uid ) != MResult.RES_SUCCESS )
		{
			MLog.e( "Can't save userdata for player '" + uP.craftPlayer.getName() + "'" );
		}

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult kickPlayerHard( PlayerIdent uid, String reason )
	{
		MLog.real( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "BackendKick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason );

		// Save Log quit
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) != null )
		{
			uP.quitlog();
		}

		/*( uP.getCraftPlayer() ).getHandle().playerConnection.player.extinguish();
		( uP.getCraftPlayer() ).getHandle().playerConnection.sendPacket( new Packet255KickDisconnect( MLog.real( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "Kick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason ) ) );
		( uP.getCraftPlayer() ).getHandle().playerConnection.networkManager.d();
		( ( CraftServer )uP.getCraftPlayer().getServer() ).getHandle().disconnect( uP.getCraftPlayer().getHandle() );
		( uP.getCraftPlayer() ).getHandle().playerConnection.disconnected = true;
		*/
		uP.getCraftPlayer().getServer().getPlayer( uid.Get() ).kickPlayer( ChatColor.DARK_GRAY + "[UltraVision " + ChatColor.DARK_AQUA + "Kick" + ChatColor.DARK_GRAY + "] " + ChatColor.AQUA + reason );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public List<UVKick> getPlayerKickHistory( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		return ( ( uP.i.kickHistory != null ) ? uP.i.kickHistory : new ArrayList<UVKick>() );
	}

	/**********************************************************************/
	@Override
	public MResult warnPlayer( CommandSender cs, PlayerIdent uid, String reason )
	{
		return warnPlayerTemporarily( cs, uid, reason, null );
	}

	/**********************************************************************/
	@Override
	public MResult warnPlayerTemporarily( CommandSender cs, PlayerIdent uid, String reason, Time time )
	{
		if (cs instanceof ConsoleCommandSender)
		{
			MLog.e("Sorry, you cannot warn a player from console - yet.");
			return MResult.RES_ERROR;
		}

		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTINIT;
		}

		if( uP.i.warning != null )
		{
			return MResult.RES_ALREADY;
		}

		uP.i.warning = new UVWarning( reason, ( Player ) cs, false, time );
		uP.log( "[UltraVision] warned by " + cs.getName() + ( time == null ? "" : "for" + timeInterpreter.getText( time.getTime() ) ) );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult unwarnPlayer( CommandSender cs, PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( uP.i.warning == null )
		{
			return MResult.RES_ALREADY;
		}

		uP.i.warning = null;

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public boolean isPlayerWarned( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return false;
		}

		if( uP.i.warning == null )
			return false;

		if( uP.i.warning.getRemainingWarnTime() == null ) // Perma warn
		{
			return true;
		}

		if( uP.i.warning.getRemainingWarnTime().getTime() <= 0 )
		{
			uP.i.warning = null;
			return false;
		}

		return true;
	}

	/**********************************************************************/
	@Override
	public String getPlayerWarnReason( PlayerIdent uid )
	{
		if( !isPlayerWarned( uid ) )
		{
			return "";
		}

		UVLocalPlayer uP = getUVLocalPlayer( uid );

		return uP.i.warning.getReason();
	}

	/**********************************************************************/
	@Override
	public UVWarning getPlayerWarning( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		return uP.i.warning;
	}

	/**********************************************************************/
	@Override
	public List<UVWarning> getPlayerWarnHistory( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		return ( ( uP.i.warnHistory != null ) ? uP.i.warnHistory : new ArrayList<UVWarning>() );
	}

	/**********************************************************************/
	@Override
	public MResult praisePlayer( CommandSender cs, PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( cs instanceof ConsoleCommandSender )
		{
			MLog.e("Sorry, a console cannot praise a player.");
			return MResult.RES_NOACCESS;
		}

		if( uP.i.praiser.contains( cs.getName() ) )
		{
			return MResult.RES_ALREADY;
		}

		uP.i.praise++;
		uP.i.praiser.add( cs.getName() );
		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult unpraisePlayer( CommandSender cs, PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( cs instanceof ConsoleCommandSender )
		{
			MLog.e("Sorry, a console cannot praise a player, so it cannot unpraise him too.");
			return MResult.RES_NOACCESS;
		}

		if( !uP.i.praiser.contains( cs.getName() ) )
		{
			return MResult.RES_ALREADY;
		}

		uP.i.praise--;
		uP.i.praiser.remove( cs.getName() );
		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public String tryGetPlayerNameByUID(PlayerIdent uid)
	{
		Player onlinePlayer = ultravisionPlugin.getServer().getPlayer(uid.Get());
		if (onlinePlayer != null)
		{
			return onlinePlayer.getName();
		}
		else
		{
			OfflinePlayer offlinePlayer = ultravisionPlugin.getServer().getOfflinePlayer(uid.Get());
			if (offlinePlayer != null)
			{
				return offlinePlayer.getName();
			}
		}

		return "";
	}

	/**********************************************************************/

	@Override
	public List<MatchUserResult> matchUser(String part, boolean needsFullMatch)
	{
		List<MatchUserResult> out = new ArrayList<>();
		List<OfflinePlayer> offlinePlayers = new ArrayList<>(Arrays.asList(ultravisionPlugin.getServer().getOfflinePlayers()));
		for (OfflinePlayer checkOfflinePlayer : offlinePlayers)
		{
			boolean bMatches;
			if (needsFullMatch)
				bMatches = checkOfflinePlayer.getName().equalsIgnoreCase(part);
			else
				bMatches = checkOfflinePlayer.getName().contains(part);

			if (bMatches)
			{
				MatchUserResult tempRes = new MatchUserResult(checkOfflinePlayer.getName(), false, new PlayerIdent(checkOfflinePlayer.getUniqueId()));
				tempRes.isOnline = checkOfflinePlayer.isOnline();
				out.add(tempRes);
			}
		}

		return out;
	}

	/**********************************************************************/
	@Override
	public boolean isPlayerPraisedBy( PlayerIdent uid, PlayerIdent otherUid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return false;
		}


		// TODO: following method is cruel.
		//	Try to switch praiser identification to UUIDs too. (that means to modify savefile)
		String possibleOtherPlayerName = tryGetPlayerNameByUID(otherUid);
		if (possibleOtherPlayerName.equals(""))
		{
			MLog.e("Cannot check whether player is praised by another player: cannot find other player with uid " + otherUid.toString() + ".");
			return false;
		}

		return uP.i.praiser.contains(possibleOtherPlayerName);
	}

	/**********************************************************************/
	@Override
	public int getPlayerPraiseCount( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return -1;
		}

		return uP.i.praise;
	}

	/**********************************************************************/
	@Override
	public MResult addPlayerNote( CommandSender cs, PlayerIdent uid, String note )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( cs instanceof ConsoleCommandSender )
		{
			MLog.e("Console cannot add notes to players!");
			return MResult.RES_NOACCESS;
		}

		uP.i.notes.put( cs.getName(), note );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult delPlayerNote( CommandSender cs, PlayerIdent uid, int id )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( cs instanceof ConsoleCommandSender )
		{
			MLog.e("Console cannot add notes to players, so it cannot delete notes aswell");
			return MResult.RES_NOACCESS;
		}

		if( uP.i.notes.size() <= id )
		{
			return MResult.RES_NOTINIT;
		}

		uP.i.notes.remove( String.valueOf( uP.i.notes.keySet().toArray()[id] ) );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public Map<String, String> getPlayerNotes( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		if( uP.i.notes == null || uP.i.notes.isEmpty() )
		{
			return new HashMap<>();
		}

		return uP.i.notes;
	}

	/**********************************************************************/
	@Override
	public MResult mutePlayer( CommandSender cs, PlayerIdent uid )
	{
		if( cs == null )
		{
			MLog.e( "Failed execute mutePlayer in local Engine: command sender parameter (cs) not given" );
			return MResult.RES_NOTGIVEN;
		}

		if( cs instanceof ConsoleCommandSender )
		{
			MLog.e( "MutePlayer() cannot be run on console yet!" );
			return MResult.RES_NOACCESS;
		}

		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			logDebug( (Player)cs, "Cannot retrieve UVLocalPlayer with UUID '" + uid.toString() + "' while trying to mute player." );
			return MResult.RES_NOTGIVEN;
		}

		if( uP.i.isMute )
		{
			return MResult.RES_ALREADY;
		}

		uP.i.isMute = true;

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public boolean isPlayerMuted( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return false;
		}

		return uP.i.isMute;
	}

	/**********************************************************************/
	@Override
	public MResult setPlayerOnlineTime( Time time, PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		uP.i.onlineTime = time;
		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult addPlayerOnlineTime( Time time, PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( uP.i.onlineTime == null )
		{
			return MResult.RES_NOTINIT;
		}

		uP.i.onlineTime.setTime( uP.i.onlineTime.getTime() + time.getTime() );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult subPlayerOnlineTime( Time time, PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( uP.i.onlineTime == null )
		{
			return MResult.RES_NOTINIT;
		}

		uP.i.onlineTime.setTime( uP.i.onlineTime.getTime() - time.getTime() );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public Time getPlayerOnlineTime( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		return uP.i.onlineTime;
	}

	/**********************************************************************/
	@Override
	public MResult addPlayerLogLine( PlayerIdent uid, String message )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		uP.log( message );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult clearPlayerLog( PlayerIdent uid )
	{
		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public List<String> getPlayerLog( PlayerIdent uid, Time timefrom, Time timeto )
	{
		throw new UnsupportedOperationException( "Not supported yet." );
	}

	/**********************************************************************/
	@Override
	public List<String> getPlayerLog( PlayerIdent uid, String pluginfilter )
	{
		throw new UnsupportedOperationException( "Not supported yet." );
	}

	/**********************************************************************/
	@Override
	public MResult rejectFriendship( PlayerIdent requestedUID, PlayerIdent performingUID )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( requestedUID ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		String possiblePerformingUserName = tryGetPlayerNameByUID(performingUID);
		if (possiblePerformingUserName.length() > 0)
		{
			if( !uP.i.friendRequests.contains( possiblePerformingUserName ) )
				return MResult.RES_ALREADY;
			else
				uP.i.friendRequests.remove( possiblePerformingUserName );
		}
		else
		{
			return MResult.RES_ALREADY;
		}

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult requestFriendship( PlayerIdent requestedUID, PlayerIdent performingUID )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( requestedUID ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		String possiblePerformingUserName = tryGetPlayerNameByUID(performingUID);
		if (possiblePerformingUserName.length() > 0)
		{
			if( uP.i.friendRequests.contains( possiblePerformingUserName ) )
				return MResult.RES_ALREADY;
			else
				uP.i.friendRequests.add( possiblePerformingUserName );
		}
		else
		{
			// performer user was not found..
			MLog.w("Could not find user for performing User ID - invalid command invoke!");
			return MResult.RES_NOACCESS;
		}

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult acceptFriendship( PlayerIdent requestedUID, PlayerIdent performingUID )
	{
		UVLocalPlayer performingPlayer;
		UVLocalPlayer requestedPlayer;
		if( ( performingPlayer = getUVLocalPlayer( performingUID ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( ( requestedPlayer = getUVLocalPlayer( requestedUID ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( performingPlayer.i.friends.contains( requestedPlayer.craftPlayer.getName() ) )
		{
			return MResult.RES_ALREADY;
		}

		performingPlayer.i.friends.add( requestedPlayer.craftPlayer.getName() );
		requestedPlayer.i.friends.add( performingPlayer.craftPlayer.getName() );

		performingPlayer.i.friendRequests.remove( requestedPlayer.GetName() );
		requestedPlayer.i.friendRequests.remove( performingPlayer.GetName() );

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public MResult delPlayerFriend( PlayerIdent requestedUID, PlayerIdent perfomingUID )
	{
		// Remove friendship on the side of the perfomring Player
		UVLocalPlayer performingPlayer;
		if( ( performingPlayer = getUVLocalPlayer( perfomingUID ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		String possibleRequestedUID = tryGetPlayerNameByUID(requestedUID);
		if (possibleRequestedUID.length() > 0)
		{
			if( performingPlayer.i.friends.contains( possibleRequestedUID ) )
				performingPlayer.i.friends.remove( possibleRequestedUID );
		}

		// remove friendship on the side of the requested Player
		UVLocalPlayer requestedPlayer;
		if( ( requestedPlayer = getUVLocalPlayer( requestedUID ) ) == null )
		{
			return MResult.RES_NOTGIVEN;
		}

		if( requestedPlayer.i.friends.contains( performingPlayer.GetName() ) )
		{
			requestedPlayer.i.friends.remove( performingPlayer.GetName() );
		}

		return MResult.RES_SUCCESS;
	}

	/**********************************************************************/
	@Override
	public List<String> getPlayerFriends( PlayerIdent uid )
	{
		UVLocalPlayer uP;
		if( ( uP = getUVLocalPlayer( uid ) ) == null )
		{
			return null;
		}

		return ( ( uP.i.friends == null ) ? new ArrayList<String>() : uP.i.friends );
	}

	/**********************************************************************/
	@Override
	public MResult setPlayerProperty( PlayerIdent uid, String prop )
	{
		return MResult.RES_SUCCESS;     // Shouldn't we better use notes for this?
	}

	/**********************************************************************/
	@Override
	public List<String> getPlayerProperties( PlayerIdent uid )
	{
		return new ArrayList<>(); // Shouldn't we better use notes for this?
	}
}