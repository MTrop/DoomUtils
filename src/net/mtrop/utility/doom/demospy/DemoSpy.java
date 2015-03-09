/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.demospy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import net.mtrop.doom.struct.Demo;

import com.blackrook.commons.Common;
import com.blackrook.commons.list.List;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;


/**
 * DemoSpy - a DEMO inspector for Doom DEMOs.
 * @author Matthew Tropiano
 */
public class DemoSpy extends Utility<DemoSpy.DemoSpyContext>
{
	private static final Version VERSION = new Version(1,0,0,0);

	private static final String[] SKILL_NAMES = new String[]{
		"ITYTD",
		"HNTR",
		"HMP",
		"UV",
		"NM",
	};
	
	private static final String[] SR_NAMES = new String[]{
		"",
		"SR40",
		"SR50",
		"STROLL",
	};	

	private static final int SR_BEHAVIOR_NONE = 	0;
	private static final int SR_BEHAVIOR_40 = 		1;
	private static final int SR_BEHAVIOR_50 = 		2;
	private static final int SR_BEHAVIOR_STROLL = 	3;
	
	/** Output type setting key. */
	public static final String SETTING_OUTPUT_TYPE = "output";
	/** Output type setting. */
	public static final int SETTING_OUTPUT_TYPE_INVALID = -1;
	/** Output type setting. */
	public static final int SETTING_OUTPUT_TYPE_NORMAL = 0;
	/** Output type setting. */
	public static final int SETTING_OUTPUT_TYPE_LONG = 1;
	/** File path. */
	public static final String SETTING_FILE_PATHS = "file";

	/** Switch: normal output. */
	public static final String SWITCH_NORMAL = "-n";
	/** Switch: long output. */
	public static final String SWITCH_LONG = "-l";
	
	
	public static class DemoSpyContext implements Context
	{
		// NOTHING.
	}

	@Override
	public Version getVersion()
	{
		return VERSION;
	}

	@Override
	public Settings getSettingsFromCMDLINE(String... args)
	{
		List<String> files = new List<String>();
		Settings out = new Settings();

		for (String a : args)
		{
			if (a.equalsIgnoreCase(SWITCH_NORMAL))
				out.put(SETTING_OUTPUT_TYPE, SETTING_OUTPUT_TYPE_NORMAL);
			else if (a.equalsIgnoreCase(SWITCH_LONG))
				out.put(SETTING_OUTPUT_TYPE, SETTING_OUTPUT_TYPE_LONG);
			else
				files.add(a);
		}
		
		String[] filePaths = new String[files.size()];
		files.toArray(filePaths);
		out.put(SETTING_FILE_PATHS, filePaths);
		
		return out;
	}

	@Override
	public DemoSpyContext createNewContext()
	{
		return new DemoSpyContext();
	}

	private void processFile(File f, int outputType)
	{
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
			processDemoData(fis, f.getPath(), outputType);
		} catch (IOException e) {
			out.printf("ERROR: Couldn't open %s for reading.\n", f.getPath());
		} finally {
			Common.close(fis);
		}
	}
	
	private void processZipFile(File f, int outputType) throws ZipException, IOException
	{
		ZipFile zf = new ZipFile(f);
		
		@SuppressWarnings("unchecked")
		Enumeration<ZipEntry> en = (Enumeration<ZipEntry>)zf.entries();
		while (en.hasMoreElements())
		{
			ZipEntry ze = en.nextElement();
			if (ze.isDirectory())
				continue;
			
			InputStream zin = zf.getInputStream(ze);
			processDemoData(zin, f.getPath()+":"+ze.getName(), outputType);
			Common.close(zin);
		}
		
		zf.close();
	}
	
	// Processes demo data.
	private void processDemoData(InputStream in, String name, int outputType)
	{
		Demo demo = null;
		try {
			demo = Demo.read(in);
		} catch (IOException e) {
			switch (outputType)
			{
				case SETTING_OUTPUT_TYPE_NORMAL:
					out.printf("%s: %s\n", name, e.getMessage());
					break;
				case SETTING_OUTPUT_TYPE_LONG:
					out.printf("%s (%s)\n", name, e.getMessage());
					break;
			}
			return;
		}
				
		switch (outputType)
		{
			case SETTING_OUTPUT_TYPE_NORMAL:
				printDemo(name, demo);
				break;
			case SETTING_OUTPUT_TYPE_LONG:
				printDemoLong(name, demo);
				break;
		}
	}

	// Detects special or interesting speedrun behavior.
	private int detectSpeedRunBehavior(Demo demo)
	{
		boolean stroll = true;
		int ticscan = 0;
		for (int i = 0; i < demo.getTicCount(); i++)
		{
			Demo.Tic tic = demo.getTic(i);
			int fwd = Math.abs(tic.getForwardMovement());
			int strf = Math.abs(tic.getRightStrafe());
			if (fwd == 50 && strf == 50)
			{
				ticscan++;
				if (ticscan == 5)
					return SR_BEHAVIOR_50;
			}
			else if (fwd == 50 && strf == 40)
			{
				ticscan++;
				if (ticscan == 5)
					return SR_BEHAVIOR_40;
			}
			if (strf != 0)
				stroll = false; 
		}
		
		if (stroll)
			return SR_BEHAVIOR_STROLL;
		
		return SR_BEHAVIOR_NONE;
	}
	
	// Detects relevant flags for boasting rights.
	private String detectRelevantFlags(Demo demo)
	{
		StringBuilder sb = new StringBuilder();
		if (demo.getFastMonsters())
			sb.append("fast ");
		if (demo.getMonsterRespawn())
			sb.append("respawn ");
		if (demo.getNoMonsters())
			sb.append("nomonsters ");
		
		return sb.toString();
	}
	
	private void printDemo(String name, Demo demo)
	{
		out.println(name);
		out.printf("\tVersion: %d\n", demo.getVersion());
		out.printf("\tPlayers: %d\n", demo.getPlayers());
		out.printf("\tE%dM%d %s\n", demo.getEpisode(), demo.getMap(), SKILL_NAMES[demo.getSkill()]);
		out.printf("\t%d Tics, %d:%02d.%03d\n", 
				demo.getTicCount(), 
				(int)(demo.getLength() / 60), 
				(int)(demo.getLength()) % 60,
				(int)((demo.getLength() % 1.0) * 1000)
				);
		out.printf("\t%s%s\n", detectRelevantFlags(demo), SR_NAMES[detectSpeedRunBehavior(demo)]);
	}
	
	private void printDemoLong(String name, Demo demo)
	{
		out.printf("%s %d %d %d %d %s %d %d:%02d.%03d %s%s\n", 
			name,
			demo.getVersion(),
			demo.getPlayers(),
			demo.getEpisode(), 
			demo.getMap(), 
			SKILL_NAMES[demo.getSkill()],
			demo.getTicCount(), 
			(int)(demo.getLength() / 60), 
			(int)(demo.getLength()) % 60,
			(int)((demo.getLength() % 1.0) * 1000),
			detectRelevantFlags(demo), 
			SR_NAMES[detectSpeedRunBehavior(demo)]
			);
	}
	
	// Prints the usage message.
	private void printUsage()
	{
		out.printf("Demo Spy v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: demospy [file] [outputswitch]");
		out.println("    [file]        :    A valid Doom/Boom/MBF demo file, or zip file.");
		out.println("                       Accepts wildcards.");
		out.println("    [outputswitch]: -n Normal out. Assumed if no switch specified.");
		out.println("                    -l Long out. Prints info on one line, for GREP-ing.");
	}
	
	@Override
	public int execute(DemoSpyContext context, Settings settings)
	{
		String[] filePaths = (String[])settings.get(SETTING_FILE_PATHS);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No file(s) specified.");
			printUsage();
			return 2;
		}
		
		int outputType = settings.getInteger(SETTING_OUTPUT_TYPE);
		
		for (String f : filePaths)
		{
			try {
				// try the ZipFile route.
				processZipFile(new File(f), outputType);
			} catch (ZipException e) {
				// try the regular file route.
				processFile(new File(f), outputType);
			} catch (IOException e) {
				out.printf("ERROR: Couldn't open %s for reading.\n", f);
			} 
		}

		return 0;
	}
	
}
