/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.mapcount;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.blackrook.commons.Common;
import com.blackrook.commons.hash.CountMap;
import com.blackrook.commons.list.List;
import com.blackrook.doom.DoomMap;
import com.blackrook.doom.DoomWad;
import com.blackrook.doom.WadException;
import com.blackrook.doom.WadMap;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * MapCount - counts the maps in WADs and PK3s.
 * @author Matthew Tropiano
 */
public class MapCount extends Utility<MapCount.MapCountContext>
{
	private static final Version VERSION = new Version(1,0,0,0);

	/** File path. */
	public static final String SETTING_FILES = "files";

	/**
	 * Context.
	 */
	public static class MapCountContext implements Context
	{
		/** File count. */
		private int fileCount;
		/** WAD count. */
		private int wadCount;

		/** WAD map count. */
		private int wadMapCount;
		/** File map count. */
		private int fileMapCount;
		/** Total map count. */
		private int totalMapCount;
		/** Count map. */
		private CountMap<String> mapCount;

		private MapCountContext()
		{
			wadCount = 0;
			fileCount = 0;
			wadMapCount = 0;
			fileMapCount = 0;
			totalMapCount = 0;
			mapCount = new CountMap<String>();
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
		Settings out = new Settings();

		for (String a : args)
		{
			files.add(a);
		}
				
		String[] filePaths = new String[files.size()];
		files.toArray(filePaths);
		out.put(SETTING_FILES, filePaths);
		
		return out;
	}

	@Override
	public MapCountContext createNewContext()
	{
		return new MapCountContext();
	}

	// Process PK3/ZIP
	private void processPK3(MapCountContext context, String fileName, File f) throws ZipException, IOException
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
				WadMap wm = null;
				try {
					wm = new WadMap(zin);
					inspectWAD(context, fileName+File.separator+ze.getName(), wm);
				} catch (IOException e) {
					out.println("ERROR: Could not read entry "+ze.getName()+".");
				}
				Common.close(zin);
			}
			else if (ze.getName().toLowerCase().endsWith(".pk3"))
			{
				File pk3 = File.createTempFile("mapcount", "pk3tmp");
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
	private void processWAD(MapCountContext context, File f) throws WadException, IOException
	{
		inspectWAD(context, f.getPath(), new WadMap(f));
	}
	
	// Inspect WAD contents.
	private void inspectWAD(MapCountContext context, String fileName, DoomWad wad) throws IOException
	{
		context.wadCount++;
		String[] mapHeaders = DoomMap.getAllMapEntries(wad);
		for (String mapName : mapHeaders)
		{
			context.mapCount.give(mapName);
			context.wadMapCount++;
			context.fileMapCount++;
			context.totalMapCount++;
		}
		out.printf("[WAD] %s: %d maps.\n", fileName, context.wadMapCount);
		context.wadMapCount = 0;
	}

	// Prints the usage message.
	private void printUsage()
	{
		out.printf("MapCount v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: mapcount [files]");
		out.println("    [files]: A valid WAD/PK3/ZIP file. Accepts wildcards");
		out.println("             for multiple files.");
	}
	
	@Override
	public int execute(MapCountContext context, Settings settings)
	{
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No WAD(s)/PK3(s) specified.");
			printUsage();
			return 2;
		}

		boolean successfulOnce = false;
		
		for (String f : filePaths)
		{
			try {
				processPK3(context, f, new File(f));
				successfulOnce = true;
				out.printf("[FILE] %s: %d maps.\n", f, context.fileMapCount);
				context.fileMapCount = 0;
				context.fileCount++;
			} catch (ZipException e) {
				try {
					processWAD(context, new File(f));
					successfulOnce = true;
					out.printf("[FILE] %s: %d maps.\n", f, context.fileMapCount);
					context.fileMapCount = 0;
					context.fileCount++;
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

		out.printf("[TOTAL] %d files, %d WADs, %d maps.\n", context.fileCount, context.wadCount, context.totalMapCount);
		return 0;
	}
	
}
