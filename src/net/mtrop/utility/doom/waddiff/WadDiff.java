/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.waddiff;

import java.io.File;

import com.blackrook.commons.Common;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * WadDiff - outputs differences between WAD files.
 * @author Matthew Tropiano
 */
public class WadDiff extends Utility<WadDiff.DiffContext>
{
	private static final Version VERSION = new Version(0,9,0,0);

	/** File to inspect. */
	public static final String SETTING_FILE = "file";
	/** Base file for comparison. */
	public static final String SETTING_BASE_FILE = "basefile";

	/** Base file for comparison. */
	public static final String SWITCH_BASE = "-base";

	/**
	 * Context.
	 */
	protected static class DiffContext implements Context
	{
		private DiffContext()
		{
		}
	}

	@Override
	public Version getVersion()
	{
		return VERSION;
	}

	@Override
	public Settings getSettingsFromCMDLINE(String... args)
	{
		Settings out = new Settings();
		
		final int STATE_INIT = 0;
		final int STATE_BASE = 1;
		int state = STATE_INIT;
		
		for (String s : args)
		{
			if (s.equalsIgnoreCase(SWITCH_BASE))
			{
				state = STATE_BASE;
			}
			else switch (state)
			{
				case STATE_INIT:
					out.put(SETTING_FILE, s);
					break;
				case STATE_BASE:
					out.put(SETTING_BASE_FILE, s);
					break;
			}
		}
		
		return out;
	}

	@Override
	public DiffContext createNewContext()
	{
		return new DiffContext();
	}

	// Prints the usage message.
	private void printUsage()
	{
		out.printf("WADDiff v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: waddiff [targetfile] -base [basefile]");
		out.println("    [targetfile] : A valid WAD file to inspect.");
		out.println("    [basefile]   : The base WAD file to compare with (usually an IWAD).");
	}

	@Override
	public int execute(DiffContext context, Settings settings)
	{
		String file = settings.getString(SETTING_FILE);
		String basefile = settings.getString(SETTING_BASE_FILE);
		
		if (Common.isEmpty(file))
		{
			out.println("ERROR: No target file specified.");
			printUsage();
			return 2;
		}

		if (Common.isEmpty(basefile))
		{
			out.println("ERROR: No base file specified.");
			printUsage();
			return 2;
		}

		File targetFile = new File(file);
		File sourceFile = new File(basefile);
		
		if (!targetFile.exists())
		{
			out.println("ERROR: Target file does not exist.");
			return 1;
		}
		if (!sourceFile.exists())
		{
			out.println("ERROR: Base file does not exist.");
			return 1;
		}

		// TODO: Do compare!
		
		return 0;
	}
	
}
