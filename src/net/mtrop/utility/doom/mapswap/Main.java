/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.mapswap;

/**
 * Utility for swapping map positions in a WAD file.
 * @author Matthew Tropiano
 */
public final class Main
{
	/**
	 * Entry point.
	 */
	public static void main(String[] args)
	{
		System.exit((new MapSwap()).go(args));
	}

}
