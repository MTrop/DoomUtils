/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.wadsort;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.regex.Pattern;

import com.blackrook.commons.Common;
import com.blackrook.commons.ObjectPair;
import com.blackrook.commons.hash.CaseInsensitiveHashMap;
import com.blackrook.commons.hash.Hash;
import com.blackrook.commons.hash.HashMap;
import com.blackrook.commons.list.List;
import com.blackrook.doom.DoomMap;
import com.blackrook.doom.DoomWadEntry;
import com.blackrook.doom.WadException;
import com.blackrook.doom.WadFile;
import com.blackrook.doom.util.DoomUtil;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * WadSort - Sorts entries in WAD files, so that they
 * appear in a predefined order.
 * 
 * Globals, text globals, Demos, playpal/colormap/tinttab, colormap namespace, 
 * ACS library namespace, Maps, textures/pnames set, sounds, music, global graphics, voice namespace, 
 * sprite namespace, patch namespace, flat namespace, texture namespace, hi-res namespace, voxel namespace.
 * 
 * @author Matthew Tropiano
 */
public class WadSorter extends Utility<WadSorter.SorterContext>
{
	private static final Version VERSION = new Version(0,9,0,0);

	/** List of files to sort. */
	public static final String SETTING_FILES = "files";
	/** Backup switch. */
	public static final String SETTING_BACKUP = "backup";

	/** Switch for backup. */
	public static final String SWITCH_BACKUP = "-backup";
	
	// Categories.
	private static enum Category
	{
		GLOBAL,
		GLOBAL_TEXT,
		ENDOOM,
		DEMO,
		PLAYPAL,
		COLORMAP,
		COLORMAP_OTHER,
		TINTTAB,
		COLORMAP_NAMESPACE,
		ACS_NAMESPACE,
		MAP,
		TEXTURE_LUMP,
		PNAMES_LUMP,
		SOUND,
		MUSIC,
		GRAPHICS,
		VOICE_NAMESPACE,
		SPRITE_NAMESPACE,
		PATCH_NAMESPACE,
		FLAT_NAMESPACE,
		TEXTURE_NAMESPACE,
		HIRES_NAMESPACE,
		VOXEL_NAMESPACE
	}
	
	private static final CaseInsensitiveHashMap<Category> 
	NAMESPACE_PREFIX_MAP = new CaseInsensitiveHashMap<Category>(){{
		put("a", Category.ACS_NAMESPACE);
		put("c", Category.COLORMAP_NAMESPACE);
		put("v", Category.VOICE_NAMESPACE);
		put("s", Category.SPRITE_NAMESPACE);
		put("p", Category.PATCH_NAMESPACE);
		put("f", Category.FLAT_NAMESPACE);
		put("tx", Category.TEXTURE_NAMESPACE);
		put("hi", Category.HIRES_NAMESPACE);
		put("vx", Category.VOXEL_NAMESPACE);
}};
	
	private static final CaseInsensitiveHashMap<Pattern> 
	IGNORE_NAMESPACE_PATTERN = new CaseInsensitiveHashMap<Pattern>(){{
		put("s", Pattern.compile("S[0-9]_(START|END)"));
		put("p", Pattern.compile("P[0-9]_(START|END)"));
		put("f", Pattern.compile("F[0-9]_(START|END)"));
}}; 
	
	/**
	 * Context.
	 */
	protected static class SorterContext implements Context
	{
		HashMap<Category, List<DoomWadEntry>> outMap;
		HashMap<String, DoomWadEntry[]> mapList;
		
		private SorterContext()
		{
			outMap = new HashMap<Category, List<DoomWadEntry>>();
			mapList = new HashMap<String, DoomWadEntry[]>();
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
		
		List<String> fileList = new List<String>();
		
		for (String s : args)
		{
			if (s.equalsIgnoreCase(SWITCH_BACKUP))
				out.put(SETTING_BACKUP, true);
			else
				fileList.add(s);
		}
		
		String[] files = new String[fileList.size()];
		fileList.toArray(files);
		
		out.put(SETTING_FILES, files);
		return out;
	}

	@Override
	public SorterContext createNewContext()
	{
		return new SorterContext();
	}

	// Prints the usage message.
	private void printUsage()
	{
		out.printf("WADSort v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: wadsort [file] [switches]");
		out.println("    [file]    :         A valid WAD file. Accepts wildcards for");
		out.println("                        multiple files.");
		out.println("    [switches]: -backup If specified, creates a backup of the wad file named");
		out.println("                        the same as the input WAD(s) plus \".bak\" will be");
		out.println("                        produced.");
	}

	/**
	 * Sorts the contents of the WAD file.
	 * @param wf the wad file to sort.
	 */
	private void sortWAD(SorterContext context, WadFile wf) throws IOException
	{
		out.println("    Scanning entries...");
		scanEntries(context, wf);
		out.println("    Sorting entries...");
		sortEntries(context, wf);
		out.println("    Writing entries...");
		exportEntries(context, wf);
		out.println("    Done!");
	}

	/**
	 * Categorizes an entry.
	 */
	private Category categorizeEntry(DoomWadEntry entry)
	{
		String entryName = entry.getName();
		
		if (DoomUtil.isEndoomLump(entryName))
			return Category.ENDOOM;
		else if (DoomUtil.isTextLump(entryName))
			return Category.GLOBAL_TEXT;
		else if (DoomUtil.isColormapLump(entryName))
			return Category.COLORMAP;
		else if (DoomUtil.isColormap(entryName))
			return Category.COLORMAP_OTHER;
		else if (entryName.startsWith("TINTTAB"))
			return Category.TINTTAB;
		else if (entryName.startsWith("DEMO"))
			return Category.DEMO;
		else if (DoomUtil.isPaletteLump(entryName))
			return Category.PLAYPAL;
		else if (DoomUtil.isTextureLump(entryName))
			return Category.TEXTURE_LUMP;
		else if (DoomUtil.isPatchNamesLump(entryName))
			return Category.PNAMES_LUMP;
		else if (DoomUtil.isSoundLump(entryName))
			return Category.SOUND;
		else if (DoomUtil.isMusicLump(entryName))
			return Category.MUSIC;
		else if (isGraphicLump(entryName))
			return Category.GRAPHICS;
		return Category.GLOBAL;
	}

	private boolean isGraphicLump(String name)
	{
		return DoomUtil.isDoomGraphicLump(name)
			|| DoomUtil.isDoom2GraphicLump(name)
			|| DoomUtil.isHereticGraphicLump(name)
			|| DoomUtil.isHexenGraphicLump(name)
			|| DoomUtil.isStrifeGraphicLump(name);
	}
	
	/**
	 * Checks if an entry is a namespace start.
	 * If so, returns a category.
	 */
	private Category isNamespaceStart(DoomWadEntry entry)
	{
		String entryName = entry.getName();
		if (entryName.endsWith("_START"))
			return NAMESPACE_PREFIX_MAP.get(entryName.substring(0, 1));
		
		return null;
	}

	/**
	 * Checks if an entry is a namespace end.
	 * If so, returns true.
	 */
	private boolean isNamespaceEnd(DoomWadEntry entry, String namespacePrefix)
	{
		String ename = entry.getName();
		return ename.startsWith(namespacePrefix) && ename.endsWith("_END");
	}

	/**
	 * Scans entries and returns a sortable list.
	 * @param wf the wad to scan.
	 */
	private void scanEntries(SorterContext context, WadFile wf)
	{
		int[] mapIndicies = DoomMap.getAllMapIndices(wf);
		Hash<Integer> mapIndexHash = new Hash<Integer>();
		for (int m : mapIndicies)
			mapIndexHash.put(m);

		Category currentNamespaceCategory = null; 
		String currentNamespacePrefix = null; 
		Pattern currentNamespaceIgnorePattern = null; 
		
		for (int i = 0; i < wf.getSize(); i++)
		{
			DoomWadEntry entry = wf.getEntry(i);

			if (currentNamespaceCategory != null)
			{
				if (isNamespaceEnd(entry, currentNamespacePrefix))
				{
					currentNamespaceCategory = null;
					currentNamespaceIgnorePattern = null;
					currentNamespacePrefix = null;
				}
				else if (currentNamespaceIgnorePattern == null || !currentNamespaceIgnorePattern.matcher(entry.getName()).matches())
					addToMap(currentNamespaceCategory, entry, context.outMap);
				// else ignore.
			}
			// check for map. entries in a map are not sorted individually.
			else if (mapIndexHash.contains(i))
			{
				String mapName = entry.getName();
				int len = DoomMap.getMapContentIndices(wf, mapName);
				
				DoomWadEntry[] mapentries = new DoomWadEntry[len];
				
				for (int n = 0; n < len; n++)
					mapentries[n] = wf.getEntry(n + i);

				i += len - 1;
				
				context.mapList.put(mapName, mapentries);
				addToMap(Category.MAP, entry, context.outMap);
			}
			else if ((currentNamespaceCategory = isNamespaceStart(entry)) != null)
			{
				String ename = entry.getName();
				currentNamespacePrefix = ename.substring(0, 1);
				currentNamespaceIgnorePattern = IGNORE_NAMESPACE_PATTERN.get(currentNamespacePrefix);
			}
			else
				addToMap(categorizeEntry(entry), entry, context.outMap);
		}

	}
	
	/**
	 * Sort entries.
	 * @param context
	 * @param wf
	 */
	private void sortEntries(SorterContext context, WadFile wf)
	{
		Comparator<DoomWadEntry> entryComparator = new Comparator<DoomWadEntry>()
		{
			public int compare(DoomWadEntry o1, DoomWadEntry o2)
			{
				return o1.getName().compareTo(o2.getName());
			};
		};
		
		for (ObjectPair<Category, List<DoomWadEntry>> pair : context.outMap)
			pair.getValue().sort(entryComparator);
	}

	/**
	 * Exports the sorted entries to the Wad file.
	 * @param entrySets the entry set list.
	 * @param wf the wad file.
	 */
	private void exportEntries(SorterContext context, WadFile wf) throws IOException
	{
		List<DoomWadEntry> outList = new List<DoomWadEntry>();
		
		for (Category c : Category.values())
		{
			List<DoomWadEntry> dumpList = context.outMap.get(c);
			if (dumpList != null) switch (c)
			{
				case GLOBAL: 
				case GLOBAL_TEXT:
				case ENDOOM:
				case DEMO:
				case PLAYPAL:
				case COLORMAP:
				case COLORMAP_OTHER:
				case TINTTAB:
				case SOUND:
				case MUSIC:
				case GRAPHICS:
				case TEXTURE_LUMP:
				case PNAMES_LUMP:
					for (DoomWadEntry e : dumpList)
						outList.add(e);
					break;
				case MAP:
					for (DoomWadEntry e : dumpList)
						for (DoomWadEntry me : context.mapList.get(e.getName()))
							outList.add(me);
					break;
				case COLORMAP_NAMESPACE:
					outList.add(wf.createMarker("C_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("C_END"));
					break;
				case ACS_NAMESPACE:
					outList.add(wf.createMarker("A_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("A_END"));
					break;
				case VOICE_NAMESPACE:
					outList.add(wf.createMarker("V_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("V_END"));
					break;
				case SPRITE_NAMESPACE:
					outList.add(wf.createMarker("SS_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("SS_END"));
					break;
				case PATCH_NAMESPACE:
					outList.add(wf.createMarker("PP_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("PP_END"));
					break;
				case FLAT_NAMESPACE:
					outList.add(wf.createMarker("FF_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("FF_END"));
					break;
				case TEXTURE_NAMESPACE:
					outList.add(wf.createMarker("TX_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("TX_END"));
					break;
				case HIRES_NAMESPACE:
					outList.add(wf.createMarker("HI_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("HI_END"));
					break;
				case VOXEL_NAMESPACE:
					outList.add(wf.createMarker("VX_START"));
					for (DoomWadEntry e : dumpList) outList.add(e);
					outList.add(wf.createMarker("VX_END"));
					break;
			}
		}
		
		DoomWadEntry[] out = new DoomWadEntry[outList.size()];
		outList.toArray(out);
		wf.setEntries(out);
	}
	
	/**
	 * Adds to map.
	 * @param category the category.
	 * @param entry the entry to add to the map.
	 * @param map the map to add it to.
	 */
	private void addToMap(Category category, DoomWadEntry entry, HashMap<Category, List<DoomWadEntry>> map)
	{
		List<DoomWadEntry> list = map.get(category);
		if (list == null)
		{
			list = new List<DoomWadEntry>();
			map.put(category, list);
		}
		list.add(entry);
	}
	
	@Override
	public int execute(SorterContext context, Settings settings)
	{
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		boolean backupEnabled = settings.getBoolean(SETTING_BACKUP);
		
		if (Common.isEmpty(filePaths))
		{
			out.println("ERROR: No WAD(s) specified.");
			printUsage();
			return 2;
		}

		boolean successfulOnce = false;
		
		for (String f : filePaths)
		{
			File wadfile = new File(f);
			
			if (!wadfile.exists())
			{
				out.printf("ERROR: Couldn't open %s: File does not exist.\n", f);
				continue;
			}
			
			out.println("Opening file "+f+"...");

			if (backupEnabled)
			{
				File backupFile = new File(wadfile.getPath()+".bak");

				out.println("    Backing up to "+(backupFile.getPath())+"...");
				FileInputStream fis = null;
				FileOutputStream fos = null;
				try {
					fis = new FileInputStream(wadfile);
					fos = new FileOutputStream(backupFile);
					Common.relay(fis, fos);
				} catch (IOException e) {
					out.printf("ERROR: Couldn't create a backup for %s: %s\n", f, e.getMessage());
				} finally {
					Common.close(fis);
					Common.close(fos);
				}
			}
			
			WadFile wf = null;
			try {
				wf = new WadFile(wadfile);
				sortWAD(context, wf);
				successfulOnce = true;
			} catch (WadException ex) {
				out.printf("ERROR: Couldn't open %s: not a WAD or PK3.\n", f);
			} catch (IOException ex) {
				out.printf("ERROR: Couldn't open %s. Read error encountered.\n", f);
			} finally {
				Common.close(wf);
			}
		}
		
		if (!successfulOnce)
			return 1;
		
		return 0;
	}
	
}
