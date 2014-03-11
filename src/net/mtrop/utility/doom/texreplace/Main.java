/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.texreplace;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.blackrook.commons.hash.HashMap;
import com.blackrook.doom.BufferedWad;
import com.blackrook.doom.DoomMap;
import com.blackrook.doom.DoomWad;
import com.blackrook.doom.WadException;
import com.blackrook.doom.WadFile;
import com.blackrook.doom.WadIO;
import com.blackrook.doom.enums.DataFormat;
import com.blackrook.doom.struct.Sector;
import com.blackrook.doom.struct.Sidedef;
import com.blackrook.io.files.ConfigReader;

/**
 * Main entry point for the replacer utility.
 * @author Matthew Tropiano
 */
public final class Main
{
	private static final String VERSION = "1.1";
	private static final String EXE_NAME = "texreplace";
	private static final String OUT_WAD = "out.wad";
	
	/**
	 * Entry point from command line.
	 */
	public static void main(String[] args)
	{
		printSplash();
		if (args.length == 0)
		{
			printHelp();
			System.exit(0);
		}
		
		File inFile = new File(args[0]);
		if (!inFile.exists())
		{
			System.out.println(args[0]+" does not exist!");
			System.exit(1);
		}
		
		int ret = 0;
		
		WadFile wf = null;
		File out = null;
		
		try {
			wf = new WadFile(inFile);
			out = args.length < 2 ? new File(OUT_WAD) : new File(args[1]);
			ret = doReplace(wf, out, (args.length < 3 ? null : args[2]), System.in);
			wf.close();
		} catch (WadException exception) {
			System.out.println("Not a wad file!");
			System.exit(2);
		} catch (SecurityException exception) {
			System.out.println("Could not open wad file.\nYou may not have read/write permissions!");
			System.exit(2);
		} catch (IOException exception) {
			System.out.println("Could not open wad file! "+exception.getLocalizedMessage());
			System.exit(2);
		}

		System.exit(ret);
	}
	
	private static void printSplash()
	{
		System.out.printf("Texture Replacer v%s by Matt Tropiano\n", VERSION);
	}

	private static void printHelp()
	{
		System.out.printf("USAGE: %s [inputwad] [outputwad] [lumpname]\n", EXE_NAME);
		System.out.println();
		System.out.println("This utility replaces sidedef textures and sector texures in a WAD.");
		System.out.println("This will read commands from standard in which dictate the");
		System.out.println("replacement rules. A text file can be redirected to the input");
		System.out.println("for easier execution.");
		System.out.println();
		System.out.println("[inputwad]:  The wad to open.");
		System.out.println("[outputwad]: (optional) The wad to put the resultant wad");
		System.out.println("             information. If this is not specified, it is");
		System.out.println("             \"out.wad\".");
		System.out.println("[lumpname]:  (optional) If the input wad has many maps, you");
		System.out.println("             can specify the lump name of the map to read.");
	}
	
	public static int doReplace(WadFile wf, File outFile, String mapname, InputStream in)
	{
		String[] mapNames = DoomMap.getAllMapEntries(wf);
		
		if (mapNames.length == 0)
		{
			System.out.println("Error: No maps in the wad!");
			return 3;
		}

		if (mapname == null)
			mapname = mapNames[0];

		
		System.out.println("Reading input....");
		
		HashMap<String, String> textureMap = new HashMap<String, String>();
		HashMap<String, String> flatMap = new HashMap<String, String>();

		try {
			readInput(in, textureMap, flatMap);
		} catch (Exception e) {
			System.out.println("Could not read input: "+e.getLocalizedMessage());
			return 3;
		}

		System.out.println("Texture entries: "+textureMap.size());
		System.out.println("Flat entries: "+flatMap.size());

		if (textureMap.size() == 0 && flatMap.size() == 0)
		{
			System.out.println("Nothing to write! No output wad written!");
			return 0;
		}
		
		System.out.println("Opening map \""+mapname+"\"...");

		DoomMap dm = null;
		
		try {
			dm = new DoomMap(wf, mapname);
		} catch (WadException exception) {
			System.out.println("Could not read map: "+exception.getLocalizedMessage());
			return 3;
		} catch (IOException exception) {
			System.out.println("Could not read map: "+exception.getLocalizedMessage());
			return 3;
		}
		
		if (textureMap.size() > 0)
		{
			int x = 0;
			System.out.printf("Performing texture replace on %d sidedefs....\n", dm.getSidedefCount());
			
			for (Sidedef side : dm.getSidedefList())
			{
				if (textureMap.containsKey(side.getUpperTexture()))
				{
					side.setUpperTexture(textureMap.get(side.getUpperTexture()));
					x++;
				}
				if (textureMap.containsKey(side.getMiddleTexture()))
				{
					side.setMiddleTexture(textureMap.get(side.getMiddleTexture()));
					x++;
				}
				if (textureMap.containsKey(side.getLowerTexture()))
				{
					side.setLowerTexture(textureMap.get(side.getLowerTexture()));
					x++;
				}
			}
			System.out.printf("%d textures replaced.\n", x);
		}
		else
			System.out.printf("No textures to replace, skipping step....\n");
			
		
		if (flatMap.size() > 0)
		{
			int x = 0;
			System.out.printf("Performing texture replace on %d flats....\n", dm.getSectorCount() * 2);

			for (Sector sector : dm.getSectorList())
			{
				if (flatMap.containsKey(sector.getCeilingTexture()))
				{
					sector.setCeilingTexture(flatMap.get(sector.getCeilingTexture()));
					x++;
				}
				if (flatMap.containsKey(sector.getFloorTexture()))
				{
					sector.setFloorTexture(flatMap.get(sector.getFloorTexture()));
					x++;
				}
			}
			System.out.printf("%d flats replaced.\n", x);
		}
		else
			System.out.printf("No flats to replace, skipping step....\n");


		System.out.println("Writing output wad...");

		BufferedWad bw = new BufferedWad();
		bw.addMap(mapname, dm);

		try {
			WadIO.writeWad(bw, outFile, DataFormat.DOOM, dm.getOriginalFormat(), DoomWad.Type.PWAD);
		} catch (IOException exception) {
			System.out.println("Could not write map: "+exception.getLocalizedMessage());
			return 3;
		}
		System.out.println("Wrote to file: "+outFile.getPath());
		
		return 0;
	}
	
	@SuppressWarnings("resource")
	private static void readInput(InputStream in, 
			HashMap<String, String> textureMap, HashMap<String, String> flatMap) throws Exception
	{
		HashMap<String, String> currentMap = null;
		ConfigReader br = new ConfigReader(new BufferedReader(new InputStreamReader(in)), "#");
		String line = null;
		int lineNum = 1;
		
		while ((line = br.readLine()) != null)
		{
			line = line.trim();
			
			String[] commands = line.split(" ");

			// singleton command
			if (commands.length == 1)
			{
				if (commands[0].equalsIgnoreCase("end"))
					break;
				else if (commands[0].equalsIgnoreCase("textures"))
					currentMap = textureMap;
				else if (commands[0].equalsIgnoreCase("flats"))
					currentMap = flatMap;
				else
					System.out.println("Bad command on line "+lineNum+": "+line+"\nIgnored.");
			}
			else
			{
				if (currentMap == null)
				{
					System.out.println("Error: Must specify \"textures\" or \"flats\" before entries.");
					return;
				}
				else
					currentMap.put(commands[0].toUpperCase(), commands[1].toUpperCase());
			}
			lineNum++;
		}
	}
	
	
}
