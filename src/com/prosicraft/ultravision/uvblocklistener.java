/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.prosicraft.ultravision;

import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 *
 * @author passi
 */
public class uvblocklistener implements Listener
{

	public ultravision uv;

	public uvblocklistener( ultravision handle )
	{
		uv = handle;
	}

	/**
	 * Checks if player is permitted to do something
	 * @param p the player
	 * @return true if event has to be cancelled
	 */
	public boolean validateAuthorizer( Player p )
	{
		if( uv.getAuthorizer() == null )
			return false;
		
		if( !uv.getAuthorizer().isRegistered( p.getName() ) )
		{
			if( uv.allowNotRegActions )
				return false;
			else
			{
				p.sendMessage( ChatColor.RED + "Please register on this server." );
				return true;
			}
		}
		else
		{
			if( uv.getAuthorizer().loggedIn( p ) )
				return false;			
		}
		
		// by default cancel event
		return true;
	}

	/**
	 * Check if player is permitted to do something
	 * @param p the player
	 * @param a the action (if given)
	 * @return true if event has to be cancelled
	 */
	public boolean validateClickAuth( Player p, Action a )
	{
		// Check if ClickAuth is used
		if( uv.getClickAuth() == null )
			return false;

		// Check if player is registered at all
		if( !uv.getClickAuth().isRegistered( p.getName() ) )
		{
			// Check if players are allowed to do anything without being registered
			if( uv.allowNotRegActions )
				return false;
			else
			{
				p.sendMessage( ChatColor.RED + "Please register on this server." );
				return true;
			}
		}
		else
		{
			// check if player is logged in already
			if( uv.getClickAuth().isLoggedIn( p.getName() ) )
				return false;

			// Check if user is currently logging in
			if( a == Action.RIGHT_CLICK_BLOCK )
				return false;
		}

		return true;
	}

	@EventHandler( priority = EventPriority.LOW )
	public void onPlayerInteract( PlayerInteractEvent event )
	{
		if( event == null )
			return;
		if( event.getPlayer() == null )
			event.setCancelled( true );

		if( validateAuthorizer( event.getPlayer() ) )
			event.setCancelled( true );
		else if( validateClickAuth( event.getPlayer(), event.getAction() ) )
			event.setCancelled( true );

	}

	@EventHandler( priority = EventPriority.LOW )
	public void onPlayerDropItem( PlayerDropItemEvent event )
	{
		if( event == null )
			return;
		if( event.getPlayer() == null )
			event.setCancelled( true );

		if( validateAuthorizer( event.getPlayer() ) )
			event.setCancelled( true );
		else if( validateClickAuth( event.getPlayer(), null ) )
			event.setCancelled( true );
	}

	@EventHandler( priority = EventPriority.LOW )
	public void onPlayerPickupItem( PlayerPickupItemEvent event )
	{
		if( event == null )
			return;
		if( event.getPlayer() == null )
			event.setCancelled( true );

		if( validateAuthorizer( event.getPlayer() ) )
			event.setCancelled( true );
		else if( validateClickAuth( event.getPlayer(), null ) )
			event.setCancelled( true );
	}

	@EventHandler( priority = EventPriority.LOW )
	public void onBlockBreak( BlockBreakEvent event )
	{
		if( event == null )
			return;
		if( event.getPlayer() == null )
			event.setCancelled( true );

		if( validateAuthorizer( event.getPlayer() ) )
		{

			String[] l = null;
			if( event.getBlock() instanceof Sign )
				l = ( ( Sign ) event.getBlock() ).getLines();

			event.setCancelled( true );

			if( l == null )
				return;

			if( event.getBlock() instanceof Sign )
				for( int i = 0; i < l.length; i++ )
					( ( Sign ) event.getBlock() ).setLine( i, l[i] );

		}
		else if( validateClickAuth( event.getPlayer(), null ) )
			event.setCancelled( true );
	}

	@EventHandler( priority = EventPriority.LOW )
	public void onBlockPlace( BlockPlaceEvent event )
	{
		if( event == null )
			return;
		if( event.getPlayer() == null )
			event.setCancelled( true );

		if( validateAuthorizer( event.getPlayer() ) )
			event.setCancelled( true );
		else if( validateClickAuth( event.getPlayer(), null ) )
			event.setCancelled( true );
	}

	@EventHandler( priority = EventPriority.LOW )
	public void onBlockDamage( BlockDamageEvent event )
	{
		if( event == null )
			return;
		if( event.getPlayer() == null )
			event.setCancelled( true );

		if( validateAuthorizer( event.getPlayer() ) )
			event.setCancelled( true );
		else if( validateClickAuth( event.getPlayer(), null ) )
			event.setCancelled( true );
	}
}
