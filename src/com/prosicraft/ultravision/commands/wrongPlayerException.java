/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.prosicraft.ultravision.commands;

/**
 *
 * @author passi
 */
public class wrongPlayerException extends Exception
{
	public wrongPlayerException( String message )
	{
		super( message );
	}
}