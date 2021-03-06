/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.prosicraft.ultravision.commands;

import com.prosicraft.ultravision.base.PlayerIdent;
import com.prosicraft.ultravision.base.UltraVisionAPI;
import com.prosicraft.ultravision.ultravision;
import com.prosicraft.ultravision.util.MLog;
import com.prosicraft.ultravision.util.MResult;
import java.sql.Time;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 *
 * @author passi
 */
public class tempbanCommand extends extendedCommand
{

	public tempbanCommand( ultravision uv, String[] args )
	{
		super( uv, args );
	}

	@Override
	public commandResult run( Player p )
	{

		try
		{

			// /tmepban <player> <time> [reason]   --> localtempban
			if( this.numArgs() >= 2 )
			{
								
				this.ev( p );

				UltraVisionAPI api = ( ( ultravision ) this.getParent() ).getAPI();
				
				List<UltraVisionAPI.MatchUserResult> mayKick = api.matchUser(this.getArg( 0 ), false);


				/*if ( mayKick == null || mayKick.isEmpty() ) {

				 mayKick = new ArrayList<Player>();

				 } */

				if( mayKick != null && mayKick.size() > 1 )
				{
					p.sendMessage( ChatColor.DARK_AQUA + "There are some players matching '" + this.getArg( 0 ) + "'" );
					String plist = "";
					for( UltraVisionAPI.MatchUserResult toKick : mayKick )
					{
						String formattedName = toKick.name + ((toKick.isOnline) ? "" : " (off)");
						plist += ChatColor.GRAY + formattedName + ( ( mayKick.indexOf( toKick ) != ( mayKick.size() - 1 ) ) ? ChatColor.DARK_GRAY + ", " : "" );
					}
					p.sendMessage( plist );
					return suc();
				}
				else if( mayKick != null && mayKick.size() == 1 )
				{    // Got ONE player
					if( mayKick.get( 0 ).name.equalsIgnoreCase( "prosicraft" ) )					
						return err( p, "You can't ban such an important person!" );					
					
					String reason = "";
					for( int i = 2; i < this.numArgs(); i++ )
						reason += this.getArg( i ).trim() + " ";
					MResult res;

					String t = this.getArg( 1 );
					if( t.startsWith( "t:" ) )
						t = t.substring( 2, t.length() - 2 );

					long thetime = timeInterpreter.getTime( t );
					if( thetime <= 0 )
						return err( p, "Given Time is not valid!" );

					Time tt = new Time( thetime );

					if( ( res = api.banPlayerTemporarily( p, mayKick.get(0).pIdent, ( ( getArgs().length >= 2 ) ? reason.trim() : "No reason provided." ), tt, false ) ) == MResult.RES_SUCCESS )
					{
						int c = ( ( ultravision ) getParent() ).ownBroadcast( ChatColor.AQUA + mayKick.get( 0 ).name + ChatColor.DARK_AQUA + " banned by " + ChatColor.AQUA + p.getName() + ChatColor.DARK_AQUA + " for " + timeInterpreter.getText( thetime ) + " (local)." );
						( ( ultravision ) getParent() ).ownBroadcast( ChatColor.DARK_AQUA + "Reason: " + ChatColor.AQUA + ( ( numArgs() >= 2 ) ? reason.trim() : "No reason." ) );
					}
					else
					{
						return err( p, ChatColor.RED + "Can't ban player: " + res.toString() );
					}
					return suc( p, "Locally banned player. (temporary)" );
				}
				else if( mayKick == null || mayKick.isEmpty() )
				{
					if( getArg( 0 ).equalsIgnoreCase( "prosicraft" ) )
					{
						return err( p, "You can't ban such an important person!" );
					}
					String reason = "";
					for( int i = 2; i < this.numArgs(); i++ )
						reason += this.getArg( i ).trim() + " ";
					MResult res;

					String t = this.getArg( 1 );
					if( t.startsWith( "t:" ) )
						t = t.substring( 2, t.length() - 2 );

					long thetime = timeInterpreter.getTime( t );
					if( thetime <= 0 )
						return err( p, "Given Time is not valid!" );

					Time tt = new Time( thetime );					
					
					List<UltraVisionAPI.MatchUserResult> match = api.matchUser(getArg(0), true);					
					if (match.isEmpty())					
						return err(p, "Could not find this user!");																
					
					if( ( res = api.banPlayerTemporarily( p, match.get(0).pIdent, ( ( getArgs().length >= 2 ) ? reason.trim() : "No reason provided." ), tt, false ) ) == MResult.RES_SUCCESS )
					{
						int c = ( ( ultravision ) getParent() ).ownBroadcast( ChatColor.AQUA + getArg( 0 ) + ChatColor.DARK_AQUA + " banned by " + ChatColor.AQUA + p.getName() + ChatColor.DARK_AQUA + " for " + timeInterpreter.getText( thetime ) + " (local)." );
						( ( ultravision ) getParent() ).ownBroadcast( ChatColor.DARK_AQUA + "Reason: " + ChatColor.AQUA + ( ( numArgs() >= 2 ) ? reason.trim() : "No reason." ) );
					}
					else
					{
						return err( p, ChatColor.RED + "Can't ban player: " + res.toString() );
					}
					return suc( p, "Locally banned player. (temporary)" );
				}
				else
				{
					return err( p, "Player '" + getArg( 0 ) + "' not found." );
				}

			}
			else
			{
				return err( p, "Too few arguments." );
			}

		}
		catch( wrongParentException | wrongPlayerException ex )
		{
			MLog.e( "[TEMPBANCMD] " + ex.getMessage() );
			ex.printStackTrace(System.out);
			return err( p, "Failed to execute command." );
		}

	}
}
