/*******************************************************************************
 * Copyright (c) 2013-2016 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.mapswap;

import java.io.IOException;
import java.util.regex.Pattern;

import net.mtrop.doom.WadFile;
import net.mtrop.doom.exception.WadException;
import net.mtrop.doom.util.MapUtils;

import com.blackrook.commons.Common;
import com.blackrook.commons.hash.CaseInsensitiveHash;
import com.blackrook.commons.math.Pair;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * MapSwap - swaps the position of two maps in a WAD file,
 * along with other map-specific info, if any.
 * @author Matthew Tropiano
 */
public class MapSwap extends Utility<MapSwap.MapSwapContext>
{
	private static final Version VERSION = new Version(2,0,0,0);

	/** File path. */
	public static final String SETTING_FILE = "file";
	/** First map. */
	public static final String SETTING_MAP1 = "map1";
	/** Second map. */
	public static final String SETTING_MAP2 = "map2";
	/** No graphics setting. */
	public static final String SETTING_NOGFX = "nographics";
	/** No music setting. */
	public static final String SETTING_NOMUS = "nomusic";
	
	/** Switch: Don't swap graphics lumps. */
	public static final String SWITCH_NO_GRAPHICS = "-nogfx";
	/** Switch: Don't swap music lumps. */
	public static final String SWITCH_NO_MUSIC = "-nomus";

	/** Regex pattern for Episode, Map. */
	private static final Pattern EPISODE_PATTERN = Pattern.compile("E[1-5]M[1-9]");
	/** Regex pattern for Map only. */
	private static final Pattern MAP_PATTERN = Pattern.compile("MAP[0-9][0-9]");
	
	private static final String[] DOOM2_MUSIC = new String[]{ 
		"D_RUNNIN",
		"D_STALKS",
		"D_COUNTD",
		"D_BETWEE",
		"D_DOOM",
		"D_THE_DA",
		"D_SHAWN",
		"D_DDTBLU",
		"D_IN_CIT",
		"D_DEAD",
		"D_STLKS2",
		"D_THEDA2",
		"D_DOOM2",
		"D_DDTBL2",
		"D_RUNNI2",
		"D_DEAD2",
		"D_STLKS3",
		"D_ROMERO",
		"D_SHAWN2",
		"D_MESSAG",
		"D_COUNT2",
		"D_DDTBL3",
		"D_AMPIE",
		"D_THEDA3",
		"D_ADRIAN",
		"D_MESSG2",
		"D_ROMER2",
		"D_TENSE",
		"D_SHAWN3",
		"D_OPENIN",
		"D_EVIL",
		"D_ULTIMA",
	};
	
	/**
	 * Context.
	 */
	public static class MapSwapContext implements Context
	{
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

		for (String a : args)
		{
			if (a.equalsIgnoreCase(SWITCH_NO_GRAPHICS))
			{
				out.put(SETTING_NOGFX, true);
			}
			else if (a.equalsIgnoreCase(SWITCH_NO_MUSIC))
			{
				out.put(SETTING_NOMUS, true);
			}
			else
			{
				if (!out.containsKey(SETTING_FILE))
					out.put(SETTING_FILE, a);
				else if (!out.containsKey(SETTING_MAP1))
					out.put(SETTING_MAP1, a);
				else if (!out.containsKey(SETTING_MAP2))
					out.put(SETTING_MAP2, a);
			}
		}
				
		return out;
	}

	@Override
	public MapSwapContext createNewContext()
	{
		return new MapSwapContext();
	}

	/**
	 * Returns the episode and map as (x,y) in the provided pair.
	 * If p.x and p.y = -1, the episode and map was not detected.
	 * Map only lumps have p.x = 0.
	 * @param mapName the map lump
	 * @param p the output Pair.
	 */
	private void getEpisodeAndMap(String mapName, Pair p)
	{
		if (EPISODE_PATTERN.matcher(mapName).matches())
		{
			p.x = Integer.parseInt(mapName.substring(1,2));;
			p.y = Integer.parseInt(mapName.substring(3));			
		}
		else if (MAP_PATTERN.matcher(mapName).matches())
		{
			p.x = 0;
			p.y = Integer.parseInt(mapName.substring(3));
		}
	}
	
	/**
	 * Returns the music lump name for the graphic that represents a level's title.
	 * @param episode the episode, or 0 for no episode.
	 * @param map the map number.
	 */
	private String getMusicLump(int episode, int map)
	{
		if (episode == 0 && map >= 1 && map <= 32)
		{
			return DOOM2_MUSIC[map - 1];
		}
		else if (episode > 0 && map >= 1 && map <= 9)
		{
			return String.format("D_E%dM%d", episode, map);
		}
		return null;
	}

	/**
	 * Returns the "WILV" lump name for the graphic that represents
	 * a level's title.
	 * @param episode the episode, or 0 for no episode.
	 * @param map the map number.
	 */
	private String getWILVLump(int episode, int map)
	{
		if (episode == 0 && map >= 1 && map <= 99)
		{
			return String.format("CWILV%02d", map - 1);
		}
		else if (episode > 0 && map >= 1 && map <= 9)
		{
			return String.format("WILV%d%d", episode - 1, map - 1);
		}
		return null;
	}
	
	private void swapMaps(WadFile wf, String sourceMap, String targetMap, boolean noGfx, boolean noMus) throws IOException
	{
		Pair src = new Pair();
		Pair trg = new Pair();
		
		getEpisodeAndMap(sourceMap, src);
		getEpisodeAndMap(targetMap, trg);
		
		int sourceHeaderIndex = wf.getIndexOf(sourceMap); 
		int targetHeaderIndex = wf.getIndexOf(targetMap);

		String sourceMusicLump = !noMus ? getMusicLump(src.x, src.y) : null; 
		String targetMusicLump = !noMus ? getMusicLump(trg.x, trg.y) : null; 
		
		int sourceMusicIndex = sourceMusicLump != null ? wf.getIndexOf(sourceMusicLump) : -1; 
		int targetMusicIndex = targetMusicLump != null ? wf.getIndexOf(targetMusicLump) : -1;

		String sourceWILV = !noGfx ? getWILVLump(src.x, src.y) : null; 
		String targetWILV = !noGfx ? getWILVLump(trg.x, trg.y) : null;
		
		int sourceWILVIndex = sourceWILV != null ? wf.getIndexOf(sourceWILV) : -1; 
		int targetWILVIndex = targetWILV != null ? wf.getIndexOf(targetWILV) : -1;

		// swap maps
		out.printf("Swap %s --> %s...\n", sourceMap, targetMap);
		wf.renameEntry(sourceHeaderIndex, targetMap);
		if (targetHeaderIndex >= 0)
		{
			out.printf("Swap %s --> %s...\n", targetMap, sourceMap);
			wf.renameEntry(targetHeaderIndex, sourceMap);
		}
		
		// swap music
		if (!noMus && sourceMusicIndex >= 0)
		{
			out.printf("Swap %s --> %s...\n", sourceMusicLump, targetMusicLump);
			wf.renameEntry(sourceMusicIndex, targetMusicLump);
			if (targetMusicIndex >= 0)
			{
				out.printf("Swap %s --> %s...\n", targetMusicLump, sourceMusicLump);
				wf.renameEntry(targetMusicIndex, sourceMusicLump);
			}
		}
			
		// swap graphics
		if (!noGfx && sourceWILVIndex >= 0)
		{
			out.printf("Swap %s --> %s...\n", sourceWILV, targetWILV);
			wf.renameEntry(sourceWILVIndex, targetWILV);
			if (targetWILVIndex >= 0)
			{
				out.printf("Swap %s --> %s...\n", targetWILV, sourceWILV);
				wf.renameEntry(targetWILVIndex, sourceWILV);
			}
		}
		
	}

	// Prints the usage message.
	private void printUsage()
	{
		out.println("Usage: mapswap [file] [map1] [map2] [switches]");
		out.println("    [file]    :         A valid WAD file.");
		out.println("    [map1]    :         The source map lump.");
		out.println("    [map2]    :         The target map lump.");
		out.println("    [switches]: -nogfx  If specified, will not swap relevant graphics");
		out.println("                        lumps.");
		out.println("                -nomus  If specified, will not swap relevant music");
		out.println("                        lumps.");
	}
	
	@Override
	public int execute(MapSwapContext context, Settings settings)
	{
		out.printf("MapSwap v%s by Matt Tropiano\n", getVersion());
		String filePath = settings.getString(SETTING_FILE);
		
		if (Common.isEmpty(filePath))
		{
			out.println("ERROR: No WAD specified.");
			printUsage();
			return 2;
		}

		String sourceMap = settings.getString(SETTING_MAP1);
		String targetMap = settings.getString(SETTING_MAP2);
		
		if (Common.isEmpty(sourceMap))
		{
			out.println("ERROR: Source map name not specified.");
			printUsage();
			return 2;
		}
		
		if (Common.isEmpty(targetMap))
		{
			out.println("ERROR: Target map name not specified.");
			printUsage();
			return 2;
		}
		
		boolean noGfx = settings.getBoolean(SETTING_NOGFX);
		boolean noMus = settings.getBoolean(SETTING_NOMUS);
		
		WadFile wf = null;
		try {
			
			out.printf("Opening %s...\n", filePath);
			wf = new WadFile(filePath);

			CaseInsensitiveHash mapnames = new CaseInsensitiveHash();
			for (String m : MapUtils.getAllMapHeaders(wf))
				mapnames.put(m);
			
			if (Common.isEmpty(mapnames))
			{
				out.println("ERROR: WAD file contains no maps; nothing to do.");
				return 1;
			}
			else if (!mapnames.contains(sourceMap))
			{
				out.printf("ERROR: Map header %s not found; nothing to do.\n", sourceMap);
				return 1;
			}

			swapMaps(wf, sourceMap, targetMap, noGfx, noMus);
			out.println("Done!");
			
		} catch (WadException e) {
			out.printf("ERROR: %s is not a WAD file.\n", filePath);
			return 1;
		} catch (SecurityException e) {
			out.printf("ERROR: %s could not be read/written to. Access was denied.\n", filePath);
			return 1;
		} catch (IOException e) {
			out.printf("ERROR: %s could not be read/written to.\n", filePath);
			return 1;
		} finally {
			Common.close(wf);
		}
		
		return 0;
	}
	
}
