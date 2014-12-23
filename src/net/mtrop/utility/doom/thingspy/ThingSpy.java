/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.thingspy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.blackrook.commons.Common;
import com.blackrook.commons.ObjectPair;
import com.blackrook.commons.hash.Hash;
import com.blackrook.commons.list.List;
import com.blackrook.commons.list.SortedList;
import com.blackrook.commons.list.SortedMap;
import com.blackrook.doom.DoomMap;
import com.blackrook.doom.DoomWad;
import com.blackrook.doom.WadBuffer;
import com.blackrook.doom.WadException;
import com.blackrook.doom.WadFile;
import com.blackrook.doom.DoomMap.Format;
import com.blackrook.doom.struct.Thing;
import com.blackrook.doom.udmf.UDMFTable;
import com.blackrook.doom.udmf.namespace.UDMFNamespace;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * ThingSpy - looks up and prints the editor numbers of things used
 * in a set of maps, or prints maps that use certain things.
 * @author Matthew Tropiano
 */
public class ThingSpy extends Utility<ThingSpy.ThingSpyContext>
{
	private static final Version VERSION = new Version(1,1,2,0);

	/** File path. */
	public static final String SETTING_FILES = "files";
	/** Thing list. */
	public static final String SETTING_THINGS = "things";
	/** Search?. */
	public static final String SETTING_SEARCH = "search";
	/** Search all?. */
	public static final String SETTING_SEARCH_ALL = "all";
	/** Suppress messages. */
	public static final String SETTING_NOMESSAGES = "nomessages";
	
	/** Switch: search mode, input thing numbers. */
	public static final String SWITCH_SEARCH = "-s";
	/** Switch: if search mode, match ALL. */
	public static final String SWITCH_SEARCH_ALL = "-a";
	/** Switch: no messages. */
	public static final String SWITCH_NOMSG = "-nomsg";
	
	/**
	 * Context.
	 */
	public static class ThingSpyContext implements Context
	{
		/** List of lumps to thing numbers. */
		private SortedMap<String, SortedList<Integer>> thingList; 
		/** Set of thing numbers to search for (if search). */
		private Hash<Integer> searchList; 
		/** Search, not list?. */
		private boolean search;
		/** Search all, not one. */
		private boolean allFlag;
		/** No messages? */
		private boolean nomessage;
		
		private ThingSpyContext()
		{
			thingList = new SortedMap<String, SortedList<Integer>>(20);
			searchList = new Hash<Integer>(4);
			search = false;
			allFlag = false;
			nomessage = false;
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
		List<String> files = new List<String>();
		List<String> things = new List<String>();
		Settings out = new Settings();

		boolean searchState = false;
		
		for (String a : args)
		{
			if (a.equalsIgnoreCase(SWITCH_SEARCH))
			{
				out.put(SETTING_SEARCH, true);
				searchState = true;
			}
			else if (a.equalsIgnoreCase(SWITCH_SEARCH_ALL))
			{
				out.put(SETTING_SEARCH_ALL, true);
				searchState = false;
			}
			else if (a.equalsIgnoreCase(SWITCH_NOMSG))
			{
				out.put(SETTING_NOMESSAGES, true);
			}
			else if (searchState)
			{
				things.add(a);
			}
			else
			{
				files.add(a);
			}
		}
				
		String[] filePaths = new String[files.size()];
		files.toArray(filePaths);
		out.put(SETTING_FILES, filePaths);
		
		String[] thingNumbers = new String[things.size()];
		things.toArray(thingNumbers);
		out.put(SETTING_THINGS, thingNumbers);
		
		return out;
	}

	@Override
	public ThingSpyContext createNewContext()
	{
		return new ThingSpyContext();
	}

	// Process PK3/ZIP
	private void processPK3(ThingSpyContext context, String fileName, File f) throws ZipException, IOException
	{
		ZipFile zf = new ZipFile(f);
		
		@SuppressWarnings("unchecked")
		Enumeration<ZipEntry> en = (Enumeration<ZipEntry>)zf.entries();
		while (en.hasMoreElements())
		{
			ZipEntry ze = en.nextElement();
			if (ze.isDirectory())
				continue;
			if (ze.getName().toLowerCase().endsWith(".wad"))
			{
				InputStream zin = zf.getInputStream(ze);
				WadBuffer wm = null;
				try {
					wm = new WadBuffer(zin);
					inspectWAD(context, wm);
				} catch (IOException e) {
					out.println("ERROR: Could not read entry "+ze.getName()+".");
				}
				Common.close(zin);
			}
			else if (ze.getName().toLowerCase().endsWith(".pk3"))
			{
				File pk3 = File.createTempFile("thingspy", "pk3tmp");
				InputStream zin = zf.getInputStream(ze);
				FileOutputStream fos = new FileOutputStream(pk3);
				try {
					Common.relay(zin, fos);
					Common.close(fos);
					processPK3(context, fileName+File.separator+ze.getName(), pk3);
				} catch (IOException e) {
					out.println("ERROR: Could not read entry "+ze.getName()+".");
				} finally {
					Common.close(fos);
					Common.close(zin);
				}
				pk3.deleteOnExit();
			}
		}
		
		zf.close();
	}

	// Process WAD
	private void processWAD(ThingSpyContext context, File f) throws WadException, IOException
	{
		WadFile wf = new WadFile(f);
		inspectWAD(context, wf);
		wf.close();
	}
	
	// Inspect WAD contents.
	private void inspectWAD(ThingSpyContext context, DoomWad wad) throws IOException
	{
		String[] mapHeaders = DoomMap.getAllMapEntries(wad);
		for (String mapName : mapHeaders)
			inpectMap(context, wad, mapName);
	}

	// Inspect a map in a WAD.
	private void inpectMap(ThingSpyContext context, DoomWad wad, String mapName) throws IOException
	{
		if (!context.nomessage)
			out.println("    Opening map "+mapName+"...");
		
		DoomMap.Format type = DoomMap.detectFormat(wad, mapName);

		if (!context.nomessage)
			out.println("    Format is "+type.name()+"...");

		UDMFTable table = null;
		UDMFNamespace namespace = null;

		if (type == Format.UDMF)
		{
			table = DoomMap.readUDMFTable(wad.getData("textmap", wad.getIndexOf(mapName)));
			namespace = DoomMap.readUDMFNamespace(table);
		}
			
		if (!context.nomessage)
			out.println("        Reading THINGS...");

		List<Thing> things = null;
		switch (type)
		{
			case DOOM:
			default:
				things = DoomMap.readDoomThingLump(wad.getData("things", wad.getIndexOf(mapName)));
				break;
			case HEXEN:
				things = DoomMap.readHexenThingLump(wad.getData("things", wad.getIndexOf(mapName)));
				break;
			case STRIFE:
				things = DoomMap.readStrifeThingLump(wad.getData("things", wad.getIndexOf(mapName)));
				break;
			case UDMF:
				things = DoomMap.readUDMFThings(namespace, table);
				break;
		}

		if (context.search && !searchThings(context, mapName, things))
			context.thingList.remove(mapName);
		else
			readThings(context, mapName, things);
	}
	
	// Adds things to the list.
	private void readThings(ThingSpyContext context, String mapName, List<Thing> things)
	{
		for (Thing t : things)
		{
			addThing(context, mapName, t.getType());
		}
	}

	// Searches things. Returns true if the map lump should be included.
	private boolean searchThings(ThingSpyContext context, String mapName, List<Thing> things)
	{
		for (Thing t : things)
		{
			int type = t.getType();
			if (context.searchList.contains(type))
			{
				int n = addThing(context, mapName, type);
				if (!context.allFlag)
					return true;
				else
				{
					if (context.searchList.size() == n)
						return true;
					// else, continue.
				}
			}
		}
		
		return false;
	}
	
	private int addThing(ThingSpyContext context, String mapName, int thingNum)
	{
		SortedList<Integer> list = context.thingList.get(mapName);
		if (list == null)
		{
			list = new SortedList<Integer>();
			context.thingList.add(mapName, list);
		}
		
		if (!list.contains(thingNum))
			list.add(thingNum);
		
		return list.size();
	}

	// Prints the usage message.
	private void printUsage()
	{
		out.printf("Thing Spy v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: thingspy [file] [switches]");
		out.println("    [file]    :        A valid WAD/PK3/ZIP file. Accepts wildcards");
		out.println("                       for multiple files.");
		out.println("    [switches]: -s     If specified, switches to SEARCH mode.");
		out.println("                       Arguments after -s are thing numbers, and");
		out.println("                       prints maps that contain them.");
		out.println("                -a     If SEARCH mode, must match ALL things provided");
		out.println("                       instead of just one.");
		out.println("                -nomsg Suppresses non-error messages during execution.");
	}
	
	@Override
	public int execute(ThingSpyContext context, Settings settings)
	{
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No WAD(s)/PK3(s) specified.");
			printUsage();
			return 2;
		}

		context.search = settings.getBoolean(SETTING_SEARCH);
		context.allFlag = settings.getBoolean(SETTING_SEARCH_ALL);

		for (String s : (String[])settings.get(SETTING_THINGS))
		{
			int thingnum = -1;
			try {
				thingnum = Integer.parseInt(s);
				context.searchList.put(thingnum);
			} catch (NumberFormatException e) {
				out.printf("ERROR: %s is not a number! Expected a thing number.\n", s);
				printUsage();
				return 1;
			}
		}
		
		context.nomessage = settings.getBoolean(SETTING_NOMESSAGES);
		
		boolean successfulOnce = false;
		
		for (String f : filePaths)
		{
			if (!context.nomessage)
				out.println("Opening file "+f+"...");
			try {
				processPK3(context, f, new File(f));
				successfulOnce = true;
			} catch (ZipException e) {
				try {
					processWAD(context, new File(f));
					successfulOnce = true;
				} catch (WadException ex) {
					out.printf("ERROR: Couldn't open %s: not a WAD or PK3.\n", f);
				} catch (IOException ex) {
					out.printf("ERROR: Couldn't open %s. Read error encountered.\n", f);
				}
			} catch (IOException ex) {
				out.printf("ERROR: Couldn't open %s. Read error encountered.\n", f);
			}
		}
		
		if (!successfulOnce)
			return 1;
		
		if (context.thingList.size() == 0)
		{
			if (!context.nomessage)
				out.println("No Maps.");
		}
		else for (ObjectPair<String, SortedList<Integer>> pair : context.thingList)
		{
			String mapName = pair.getKey();
			out.println(mapName);
			if (!context.search)
			{
				for (int i : pair.getValue())
					out.println(i);
				out.println();
			}
		}
		
		return 0;
	}
	
}
