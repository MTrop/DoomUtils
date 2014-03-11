/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.textract;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Pattern;

import com.blackrook.commons.Common;
import com.blackrook.commons.ObjectPair;
import com.blackrook.commons.comparators.CaseInsensitiveComparator;
import com.blackrook.commons.hash.CaseInsensitiveHash;
import com.blackrook.commons.hash.CaseInsensitiveHashMap;
import com.blackrook.commons.linkedlist.Queue;
import com.blackrook.commons.list.ComparatorList;
import com.blackrook.commons.list.List;
import com.blackrook.doom.WadException;
import com.blackrook.doom.WadFile;
import com.blackrook.doom.struct.Animated;
import com.blackrook.doom.struct.Switches;
import com.blackrook.doom.struct.Texture;
import com.blackrook.doom.struct.TextureLump;
import com.blackrook.doom.util.TextureSet;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * TEXtract - extracts textures and flats, including ANIMATED and SWITCHES data.
 * @author Matthew Tropiano
 */
public class TextureExtractor extends Utility<TextureExtractor.ExtractorContext>
{
	private static final Version VERSION = new Version(0,9,6,0,"BETA");

	private static final Pattern PATCH_MARKER = Pattern.compile("P[0-9]*_(START|END)");
	private static final Pattern FLAT_MARKER = Pattern.compile("F[0-9]*_(START|END)");
	
	/** File path. */
	public static final String SETTING_FILES = "files";
	/** IWAD path. */
	public static final String SETTING_BASE = "base";
	/** Output file. */
	public static final String SETTING_OUTFILE = "outfile";
	/** Don't add associated animated textures. */
	public static final String SETTING_NOANIMATED = "noanimated";
	/** Don't add associated switch textures. */
	public static final String SETTING_NOSWITCHES = "noswitches";
	/** Overwrite target WAD. */
	public static final String SETTING_OVERWRITE = "overwrite";
	
	/** Switch: Source IWAD. */
	public static final String SWITCH_BASE = "-base";
	/** Switch: Output file. */
	public static final String SWITCH_OUTPUT = "-o";
	/** Switch: Overwrite output file. */
	public static final String SWITCH_OVERWRITE = "-owrite";
	/** Switch: No animations. */
	public static final String SWITCH_NOANIMATED = "-noanim";
	/** Switch: No switches. */
	public static final String SWITCH_NOSWITCH = "-noswit";

	/**
	 * Context.
	 */
	public static class ExtractorContext implements Context
	{
		/** Path to output wad. */
		private String outWad;
		/** Path to base wad. */
		private String baseWad;
		/** List of texture names. */
		private CaseInsensitiveHash textureList; 
		/** List of flat names. */
		private CaseInsensitiveHash flatList; 

		/** Base Unit. */
		private WadUnit baseUnit;
		/** WAD priority queue. */
		private Queue<WadUnit> wadPriority;
		
		/** No animated. */
		private boolean noAnimated;
		/** No switches. */
		private boolean noSwitches;
		/** Overwrite output. */
		private boolean overwrite;

		private ExtractorContext()
		{
			outWad = null;
			baseWad = null;
			textureList = new CaseInsensitiveHash();
			flatList = new CaseInsensitiveHash();
			baseUnit = null;
			wadPriority = new Queue<WadUnit>();
			noAnimated = false;
			noSwitches = false;
			overwrite = false;
		}
	}
	
	private static class ExportSet
	{
		private CaseInsensitiveHash textureHash;
		private CaseInsensitiveHash patchHash;
		private CaseInsensitiveHash flatHash;
		
		private TextureSet textureSet;
		private List<EntryData> patchData;
		private List<EntryData> flatData;
		private List<EntryData> textureData;
		private Animated animatedData;
		private Switches switchesData;
		
		public ExportSet()
		{
			textureHash = new CaseInsensitiveHash();
			flatHash = new CaseInsensitiveHash();
			patchHash = new CaseInsensitiveHash();
			textureSet = null;
			patchData = new List<EntryData>();
			flatData = new List<EntryData>();
			textureData = new List<EntryData>();
			animatedData = new Animated();
			switchesData = new Switches();
		}
	}
	
	/** Pair for grouping WAD and entry index. */
	private static class EntryData extends ObjectPair<String, byte[]> implements Comparable<EntryData>
	{
		EntryData(String key, byte[] value)
		{
			super(key, value);
		}
		
		@Override
		public int compareTo(EntryData o)
		{
			return getKey().compareTo(o.getKey());
		}
		
	}
	
	/**
	 * A WAD-Texture unit that is stored in a queue
	 * for figuring out from where textures should be extracted.
	 */
	private static class WadUnit
	{
		/** WAD path. */
		WadFile wad; 

		/** Texture Set. */
		TextureSet textureSet;
		
		/** Patch ENTRY indices. */
		CaseInsensitiveHashMap<Integer> patchIndices; 		
		/** Flat ENTRY indices. */
		CaseInsensitiveHashMap<Integer> flatIndices;
		/** Namespace texture ENTRY indices. */
		CaseInsensitiveHashMap<Integer> texNamespaceIndices; 

		/** Animated texture map. */
		CaseInsensitiveHashMap<String[]> animatedTexture;
		/** Animated texture map. */
		CaseInsensitiveHashMap<String[]> animatedFlat;
		/** Switches map. */
		CaseInsensitiveHashMap<String> switchMap;

		/** Sorted texture names. */
		ComparatorList<String> textureList;
		/** Sorted flat names. */
		ComparatorList<String> flatList;
		
		Animated animated;
		Switches switches;
		
		private WadUnit(WadFile file)
		{
			this.wad = file;
			textureSet = new TextureSet();
			flatIndices = new CaseInsensitiveHashMap<Integer>();
			patchIndices = new CaseInsensitiveHashMap<Integer>();
			texNamespaceIndices = new CaseInsensitiveHashMap<Integer>();
			animatedTexture = new CaseInsensitiveHashMap<String[]>();
			animatedFlat = new CaseInsensitiveHashMap<String[]>();
			switchMap = new CaseInsensitiveHashMap<String>();
			textureList = new ComparatorList<String>(CaseInsensitiveComparator.getInstance());
			flatList = new ComparatorList<String>(CaseInsensitiveComparator.getInstance());
			animated = new Animated();
			switches = new Switches();
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

		final int STATE_INIT = 0;
		final int STATE_BASE = 1;
		final int STATE_OUT = 2;
		
		int state = STATE_INIT;
		for (String a : args)
		{
			if (a.equalsIgnoreCase(SWITCH_BASE))
			{
				state = STATE_BASE;
				continue;
			}
			else if (a.equalsIgnoreCase(SWITCH_OUTPUT))
			{
				state = STATE_OUT;
				continue;
			}
			else if (a.equalsIgnoreCase(SWITCH_NOANIMATED))
			{
				out.put(SETTING_NOANIMATED, true);
				state = STATE_INIT;
				continue;
			}
			else if (a.equalsIgnoreCase(SWITCH_NOSWITCH))
			{
				out.put(SETTING_NOSWITCHES, true);
				state = STATE_INIT;
				continue;
			}
			else if (a.equalsIgnoreCase(SWITCH_OVERWRITE))
			{
				out.put(SETTING_OVERWRITE, true);
				state = STATE_INIT;
				continue;
			}
			
			switch (state)
			{
				case STATE_BASE:
					out.put(SETTING_BASE, a);
					break;
				case STATE_OUT:
					out.put(SETTING_OUTFILE, a);
					break;
				default:
					files.add(a);
					break;
			}
		}
				
		String[] filePaths = new String[files.size()];
		files.toArray(filePaths);
		out.put(SETTING_FILES, filePaths);
		
		return out;
	}

	@Override
	public ExtractorContext createNewContext()
	{
		return new ExtractorContext();
	}

	// Scan WAD file.
	private boolean scanWAD(ExtractorContext context, String path, boolean isBase)
	{
		out.printf("Scanning %s...\n", path);
		File f = new File(path);
		WadFile wf = openWadFile(f, false);
		if (wf == null)
			return false;
		
		WadUnit unit = new WadUnit(wf);
		
		try {
			if (!scanTexturesAndPNames(context, unit, wf))
				return false;
		} catch (IOException e) {
			out.printf("ERROR: \"%s\" could not be read.\n", f.getPath());
			return false;
		}
		
		out.println("    Scanning patch entries...");
		if (!scanNamespace("p", PATCH_MARKER, unit, wf, unit.patchIndices))
			return false;
		if (!scanNamespace("pp", null, unit, wf, unit.patchIndices))
			return false;
		out.printf("        %d patches.\n", unit.patchIndices.size());
		out.println("    Scanning flat entries...");
		if (!scanNamespace("f", FLAT_MARKER, unit, wf, unit.flatIndices))
			return false;
		if (!scanNamespace("ff", null, unit, wf, unit.flatIndices))
			return false;
		out.printf("        %d flats.\n", unit.flatIndices.size());
		out.println("    Scanning texture namespace entries...");
		if (!scanNamespace("tx", null, unit, wf, unit.texNamespaceIndices))
			return false;
		out.printf("        %d namespace textures.\n", unit.texNamespaceIndices.size());
		
		Iterator<String> it = unit.flatIndices.keyIterator();
		while (it.hasNext())
			unit.flatList.add(it.next());
	
		Iterator<String> it2 = unit.texNamespaceIndices.keyIterator();
		while (it2.hasNext())
		{
			String s = it2.next();
			if (!unit.textureList.contains(s))
				unit.textureList.add(s);
		}

		for (TextureLump tl : unit.textureSet.getTextureLumps())
			for (Texture t : tl)
				if (!unit.textureList.contains(t.getName()))
					unit.textureList.add(t.getName());

		try {
			if (!scanAnimated(context, unit, wf))
				return false;
		} catch (IOException e) {
			out.printf("ERROR: \"%s\" could not be read: an ANIMATED or SWITCHES lump may be corrupt.\n", f.getPath());
			return false;
		}
		
		if (!isBase)
			context.wadPriority.enqueue(unit);
		else
			context.baseUnit = unit;
		
		return true;
	}

	// Scan for TEXTUREx and PNAMES.
	private boolean scanTexturesAndPNames(ExtractorContext context, WadUnit unit, WadFile wf) throws IOException
	{
		if (!wf.contains("texture1"))
			return true;
		out.println("    Scanning TEXTUREx/PNAMES...");
		try {
			unit.textureSet = new TextureSet(wf);
		} catch (WadException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		} catch (IOException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		}
		
		return true;
	}

	// Scan for ANIMATED. Add combinations of textures to animated mapping.
	private boolean scanAnimated(ExtractorContext context, WadUnit unit, WadFile wf) throws IOException
	{
		if (!context.noAnimated)
		{
			if (wf.contains("animated"))
			{
				out.println("    Scanning ANIMATED...");
				unit.animated.readDoomBytes(wf.getDataAsStream("animated"));
				processAnimated(unit, unit.animated);
			}
			
			processAnimated(unit, TextureTables.ALL_ANIMATED);
		}

		if (!context.noSwitches)
		{
			if (wf.contains("switches"))
			{
				out.println("    Scanning SWITCHES...");
				unit.switches.readDoomBytes(wf.getDataAsStream("switches"));
				
				for (Switches.Entry entry : unit.switches.getSwitchList())
				{
					unit.switchMap.put(entry.getOffName(), entry.getOnName());
					unit.switchMap.put(entry.getOnName(), entry.getOffName());
				}
			}
		}
		
		return true;
	}
	
	private void processAnimated(WadUnit unit, Animated animated)
	{
		for (Animated.Entry entry : animated.getTextureList())
		{
			String[] seq = getTextureSequence(unit, entry.getFirstName(), entry.getLastName());
			if (seq != null) for (String s : seq)
				unit.animatedTexture.put(s, seq);
		}
		
		for (Animated.Entry entry : animated.getFlatList())
		{
			String[] seq = getFlatSequence(unit, entry.getFirstName(), entry.getLastName());
			if (seq != null) for (String s : seq)
				unit.animatedFlat.put(s, seq);
		}
	}

	// Get animated texture sequence.
	private String[] getTextureSequence(WadUnit unit, String firstName, String lastName)
	{
		Queue<String> out = new Queue<String>();
		int index = unit.textureList.getIndexOf(firstName);
		if (index >= 0)
		{
			int index2 = unit.textureList.getIndexOf(lastName);
			if (index2 >= 0)
			{
				int min = Math.min(index, index2);
				int max = Math.max(index, index2);
				for (int i = min; i <= max; i++)
					out.add(unit.textureList.getByIndex(i));
			}
			else
				return null;
		}
		else
			return null;
		
		String[] outList = new String[out.size()];
		out.toArray(outList);
		return outList;
	}
	
	// Get animated flat sequence.
	private String[] getFlatSequence(WadUnit unit, String firstName, String lastName)
	{
		Queue<String> out = new Queue<String>();
		int index = unit.flatList.getIndexOf(firstName);
		if (index >= 0)
		{
			int index2 = unit.flatList.getIndexOf(lastName);
			if (index2 >= 0)
			{
				int min = Math.min(index, index2);
				int max = Math.max(index, index2);
				for (int i = min; i <= max; i++)
					out.add(unit.flatList.getByIndex(i));
			}
			else
				return null;
		}
		else
			return null;
		
		String[] outList = new String[out.size()];
		out.toArray(outList);
		return outList;
	}
	
	// Scans namespace entries.
	private boolean scanNamespace(String name, Pattern ignorePattern, WadUnit unit, WadFile wf, CaseInsensitiveHashMap<Integer> map)
	{
		// scan patch namespace
		int start = wf.getIndexOf(name+"_start");
		if (start >= 0)
		{
			int end = wf.getIndexOf(name+"_end");
			if (end >= 0)
			{
				for (int i = start + 1; i < end; i++)
				{
					String ename = wf.getEntry(i).getName();
					if (ignorePattern != null && ignorePattern.matcher(ename).matches())
						continue;
					map.put(ename, i);
				}
			}
			else
			{
				out.printf("ERROR: %s: %s_START without %s_END!\n", unit.wad, name.toUpperCase(), name.toUpperCase());
				return false;
			}
		}		
		
		return true;
	}
	
	private boolean readTexturesAndFlats(ExtractorContext context) throws IOException
	{
		final String TEXTURES = "-texture";
		final String FLATS = "-flat";
		final String END = "-end";
		
		final int STATE_NONE = 0;
		final int STATE_TEXTURES = 1;
		final int STATE_FLATS = 2;
		
		BufferedReader br = Common.openSystemIn();
		String line = null;
		int state = STATE_NONE;
		boolean keepGoing = true;
		
		while (keepGoing && ((line = br.readLine()) != null))
		{
			line = line.trim();
			// skip blank lines
			if (line.length() == 0)
				continue;
			// skip commented lines.
			if (line.charAt(0) == '#')
				continue;
			
			if (line.equalsIgnoreCase(TEXTURES))
			{
				state = STATE_TEXTURES;
				continue;
			}
			else if (line.equalsIgnoreCase(FLATS))
			{
				state = STATE_FLATS;
				continue;
			}
			else if (line.equalsIgnoreCase(END))
			{
				keepGoing = false;
				continue;
			}
			
			switch (state)
			{
				case STATE_NONE:
					out.println("ERROR: Name before '-texture' or '-flat'.");
					out.println("NOTE: Are you importing this from TEXSPY? If so, try running TEXSPY with the");
					out.println("'-textract' switch!");
					br.close();
					return false;
				case STATE_TEXTURES:
					readAndAddTextures(context, line);
					break;
				case STATE_FLATS:
					readAndAddFlats(context, line);
					break;
			}
		}
		
		br.close();
		return true;
	}
	
	private void readAndAddTextures(ExtractorContext context, String textureName)
	{
		context.textureList.put(textureName);
		
		for (WadUnit unit : context.wadPriority)
		{
			if (!context.noAnimated)
			{
				if (unit.animatedTexture.containsKey(textureName))
					for (String s : unit.animatedTexture.get(textureName))
						context.textureList.put(s);
			}
		
			if (!context.noSwitches)
			{
				if (unit.switchMap.containsKey(textureName))
				{
					context.textureList.put(textureName);
					context.textureList.put(unit.switchMap.get(textureName));
				}
				else if (TextureTables.SWITCH_TABLE.containsKey(textureName))
				{
					context.textureList.put(textureName);
					context.textureList.put(TextureTables.SWITCH_TABLE.get(textureName));
				}
			}
		}
		
		
	}

	private void readAndAddFlats(ExtractorContext context, String textureName)
	{
		context.flatList.put(textureName);
		
		if (!context.noAnimated)
		{
			for (WadUnit unit : context.wadPriority)
			{
				if (unit.animatedFlat.containsKey(textureName))
					for (String s : unit.animatedFlat.get(textureName))
						context.flatList.put(s);
			}
		}
	}

	/** Searches for the flat to extract. */
	private WadUnit searchForFlat(Queue<WadUnit> unitQueue, String flatName)
	{
		for (WadUnit unit : unitQueue)
		{
			if (unit.flatIndices.containsKey(flatName))
				return unit;
		}
		
		return null;
	}
	
	/** Searches for the texture to extract. */
	private WadUnit searchForTexture(Queue<WadUnit> unitQueue, String textureName)
	{
		for (WadUnit unit : unitQueue)
		{
			if (unit.textureSet.contains(textureName))
				return unit;
		}
		
		return null;
	}
	
	/** Searches for the texture to extract. */
	private WadUnit searchForNamespaceTexture(Queue<WadUnit> unitQueue, String textureName)
	{
		for (WadUnit unit : unitQueue)
		{
			if (unit.texNamespaceIndices.containsKey(textureName))
				return unit;
		}
		
		return null;
	}

	private boolean extractFlats(ExtractorContext context, ExportSet exportSet)
	{
		out.println("    Extracting flats...");
		for (String flat : context.flatList)
		{
			WadUnit unit = null;
			
			if ((unit = searchForFlat(context.wadPriority, flat)) != null)
			{
				// does a matching texture entry exist?
				if (unit.flatIndices.containsKey(flat))
				{
					Integer pidx = unit.flatIndices.get(flat);
					if (pidx != null)
					{
						try {
							out.printf("        Extracting flat %s...\n", flat);
							EntryData data = new EntryData(flat, unit.wad.getData(pidx));
							exportSet.flatData.add(data);
							exportSet.flatHash.put(flat);
						} catch (IOException e) {
							out.printf("ERROR: %s: Could not read entry %s.", unit.wad.getFilePath(), flat);
							return false;
						}
					}
				}
			}
		}
		return true;
	}
	
	private boolean extractTextures(ExtractorContext context, ExportSet exportSet)
	{
		out.println("    Extracting textures...");
		for (String texture : context.textureList)
		{
			WadUnit unit = null;
			
			// found texture.
			if ((unit = searchForTexture(context.wadPriority, texture)) != null)
			{
				// for figuring out if we've found a replaced/added patch.
				boolean foundPatches = false;
				
				TextureSet.Entry entry = unit.textureSet.getEntry(texture);
				
				for (int i = 0; i < entry.getPatchCount(); i++)
				{
					TextureSet.Entry.Patch p = entry.getPatch(i);
					String pname = p.getName();
					
					// does a matching patch exist?
					if (unit.patchIndices.containsKey(pname))
					{
						foundPatches = true;
						Integer pidx = unit.patchIndices.get(pname);
						if (pidx != null && !exportSet.patchHash.contains(pname))
						{
							try {
								out.printf("        Extracting patch %s...\n", pname);
								EntryData data = new EntryData(pname, unit.wad.getData(pidx));
								exportSet.patchData.add(data);
								exportSet.patchHash.put(pname);
							} catch (IOException e) {
								out.printf("ERROR: %s: Could not read entry %s.\n", unit.wad.getFilePath(), pname);
								return false;
							}
						}
					}
				}

				// if we've found patches or the texture is new, better extract the texture.
				if (foundPatches || !exportSet.textureSet.contains(texture))
				{
					out.printf("        Copying texture %s...\n", texture);
					exportSet.textureSet.addEntry(entry);
					exportSet.textureHash.put(texture);
				}
				
			}
			// unit not found
			else if ((unit = searchForNamespaceTexture(context.wadPriority, texture)) != null)
			{
				// does a matching texture entry exist?
				if (unit.texNamespaceIndices.containsKey(texture))
				{
					Integer pidx = unit.texNamespaceIndices.get(texture);
					if (pidx != null)
					{
						try {
							out.printf("        Extracting namespace texture %s...\n", texture);
							EntryData data = new EntryData(texture, unit.wad.getData(pidx));
							exportSet.textureData.add(data);
						} catch (IOException e) {
							out.printf("ERROR: %s: Could not read entry %s.\n", unit.wad.getFilePath(), texture);
							return false;
						}
					}
				}
			}
		}
		
		return true;
	}
	
	// Merges ANIMATED and SWITCHES from inputs.
	private boolean mergeAnimatedAndSwitches(ExtractorContext context, ExportSet exportSet)
	{
		if (!context.noAnimated)
		{
			out.println("    Merging ANIMATED...");
			for (WadUnit unit : context.wadPriority)
			{
				// did we pull any animated textures? if so, copy the entries.
				for (Animated.Entry e : unit.animated.getTextureList())
				{
					if (exportSet.textureSet.contains(e.getFirstName()))
						exportSet.animatedData.addTexture(e.getLastName(), e.getFirstName(), e.getTicks(), e.getAllowsDecals());
				}
				for (Animated.Entry e : unit.animated.getFlatList())
				{
					if (exportSet.flatHash.contains(e.getFirstName()))
						exportSet.animatedData.addFlat(e.getLastName(), e.getFirstName(), e.getTicks());
					else if (context.baseUnit.flatIndices.containsKey(e.getFirstName()))
						exportSet.animatedData.addFlat(e.getLastName(), e.getFirstName(), e.getTicks());
				}
			}
		}
		
		if (!context.noSwitches)
		{
			out.println("    Merging SWITCHES...");
			for (WadUnit unit : context.wadPriority)
			{
				// did we pull any animated textures? if so, copy the entries.
				for (Switches.Entry e : unit.switches.getSwitchList())
				{
					if (exportSet.textureSet.contains(e.getOffName()))
						exportSet.switchesData.addSwitch(e.getOffName(), e.getOnName(), e.getGame());
					else if (exportSet.textureSet.contains(e.getOnName()))
						exportSet.switchesData.addSwitch(e.getOffName(), e.getOnName(), e.getGame());
				}
			}
		}
		
		return true;
	}
	
	private boolean dumpToOutputWad(ExtractorContext context, ExportSet exportSet, WadFile wf) throws IOException
	{
		out.println("Sorting entries...");
		exportSet.textureSet.sort();
		exportSet.patchData.sort();
		exportSet.flatData.sort();
		exportSet.textureData.sort();
		
		out.println("Dumping entries...");
		List<TextureLump> tlist = exportSet.textureSet.getTextureLumps();
		
		for (int i = 0; i < tlist.size(); i++)
		{
			String tentry = String.format("texture%01d", i+1);
			int idx = wf.getIndexOf(tentry);
			if (idx >= 0)
				wf.replaceEntry(idx, tlist.getByIndex(i).getDoomBytes());
			else
				wf.add(tentry, tlist.getByIndex(i).getDoomBytes());
		}
		
		int idx = wf.getIndexOf("pnames");
		if (idx >= 0)
			wf.replaceEntry(idx, exportSet.textureSet.getPatchNames().getDoomBytes());
		else
			wf.add("pnames", exportSet.textureSet.getPatchNames().getDoomBytes());
		
		if (!context.noAnimated && (exportSet.animatedData.getFlatCount() > 0 || exportSet.animatedData.getTextureCount() > 0))
		{
			idx = wf.getIndexOf("animated");
			if (idx >= 0)
				wf.replaceEntry(idx, exportSet.animatedData.getDoomBytes());
			else
				wf.add("animated", exportSet.animatedData.getDoomBytes());
		}

		if (!context.noSwitches && exportSet.switchesData.getSwitchCount() > 0)
		{
			idx = wf.getIndexOf("switches");
			if (idx >= 0)
				wf.replaceEntry(idx, exportSet.switchesData.getDoomBytes());
			else
				wf.add("switches", exportSet.switchesData.getDoomBytes());
		}
		
		dumpListToOutputWad(exportSet.patchData, "pp", wf);
		dumpListToOutputWad(exportSet.flatData, "ff", wf);
		dumpListToOutputWad(exportSet.textureData, "tx", wf);
		
		return true;
	}
	
	private boolean dumpListToOutputWad(List<EntryData> entries, String namespace, WadFile wf) throws IOException
	{
		if (entries.size() == 0)
			return true;
		
		String[] names = new String[entries.size() + 2];
		byte[][] data = new byte[entries.size() + 2][];
		
		names[0] = namespace + "_start";
		data[0] = new byte[0];
		
		for (int i = 0; i < entries.size(); i++)
		{
			names[1 + i] = entries.getByIndex(i).getKey();
			data[1 + i] = entries.getByIndex(i).getValue();
		}

		names[names.length - 1] = namespace + "_end";
		data[data.length - 1] = new byte[0];
		
		wf.addAll(names, data);
		
		return true;
	}
	
	/** Extracts the necessary stuff for output. */
	private boolean extractToOutputWad(ExtractorContext context)
	{
		File outFile = new File(context.outWad);
		WadFile outWadFile = context.overwrite ? newWadFile(outFile) : openWadFile(outFile, true);
		if (outWadFile == null)
			return false;

		File baseFile = new File(context.baseWad);
		WadFile baseWadFile = openWadFile(baseFile, false);
		if (baseWadFile == null)
		{
			Common.close(outWadFile);
			return false;
		}

		ExportSet exportSet = new ExportSet();
		try {
			exportSet.textureSet = new TextureSet(baseWadFile);
			extractTextures(context, exportSet);
			extractFlats(context, exportSet);
			mergeAnimatedAndSwitches(context, exportSet);
			dumpToOutputWad(context, exportSet, outWadFile);
		} catch (WadException e) {
			out.printf("ERROR: %s: %s\n", baseWadFile.getFilePath(), e.getMessage());
			return false;
		} catch (IOException e) {
			out.printf("ERROR: %s: %s\n", baseWadFile.getFilePath(), e.getMessage());
			return false;
		} finally {
			Common.close(baseWadFile);
			Common.close(outWadFile);
		}
		
		return true;
	}
	
	// Attempts to make a new WAD file.
	private WadFile newWadFile(File f)
	{
		WadFile outWad = null;
		try {
			outWad = WadFile.createWadFile(f);
		} catch (SecurityException e) {
			out.printf("ERROR: \"%s\" could not be created. Access denied.\n", f.getPath());
			return null;
		} catch (IOException e) {
			out.printf("ERROR: \"%s\" could not be created.\n", f.getPath());
			return null;
		}
		
		return outWad;
	}
	
	// Attempts to open a WAD file.
	private WadFile openWadFile(File f, boolean create)
	{
		WadFile outWad = null;
		try {
			if (f.exists())
				outWad = new WadFile(f);
			else if (create)
				outWad = WadFile.createWadFile(f);
			else
				out.printf("ERROR: \"%s\" could not be opened.\n", f.getPath());
		} catch (SecurityException e) {
			out.printf("ERROR: \"%s\" could not be read. Access denied.\n", f.getPath());
			return null;
		} catch (WadException e) {
			out.printf("ERROR: \"%s\" is not a WAD file.\n", f.getPath());
			return null;
		} catch (IOException e) {
			out.printf("ERROR: \"%s\" could not be read.\n", f.getPath());
			return null;
		}
		
		return outWad;
	}

	// Prints the usage message.
	private void printUsage()
	{
		out.printf("TEXtract v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: textract [file] -base [base] -o [output] [switches]");
		out.println("    [file]    :         A valid WAD file (that contains the textures to ");
		out.println("                        extract). Accepts wildcards for multiple WAD files.");
		out.println();
		out.println("    [base]    :         The WAD file to use for reference for extraction.");
		out.println("                        Any texture resources found in this file are NOT");
		out.println("                        extracted, except for the TEXTUREx and PNAMES lumps");
		out.println("                        to use as a base. (Usually an IWAD)");
		out.println();
		out.println("    [output]  :         The output WAD file. If it exists, the target's");
		out.println("                        TEXTUREx and PNAMES lumps are overwritten, and the");
		out.println("                        extracted contents are APPENDED to it.");
		out.println();
		out.println("    [switches]: -noanim If specified, do not include other textures in");
		out.println("                        a texture's animation sequence, and ignore ANIMATED");
		out.println("                        lumps.");
		out.println();
		out.println("                -noswit If specified, do not include other textures in");
		out.println("                        a texture's switch sequence, and ignore SWITCHES");
		out.println("                        lumps.");
		out.println();
		out.println("                -owrite If specified, it will overwrite the contents of the");
		out.println("                        output WAD (by default, it appends them).");
	}
	
	@Override
	public int execute(ExtractorContext context, Settings settings)
	{
		/* STEP 0 : Get yo' shit together. */

		String[] filePaths = (String[])settings.get(SETTING_FILES);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No input WAD(s) specified.");
			printUsage();
			return 2;
		}

		context.baseWad = settings.getString(SETTING_BASE);

		if (Common.isEmpty(context.baseWad))
		{
			out.println("ERROR: No base WAD specified.");
			printUsage();
			return 2;
		}

		context.outWad = settings.getString(SETTING_OUTFILE);

		if (Common.isEmpty(context.outWad))
		{
			out.println("ERROR: No output WAD specified.");
			printUsage();
			return 2;
		}

		context.noAnimated = settings.getBoolean(SETTING_NOANIMATED);
		context.noSwitches = settings.getBoolean(SETTING_NOSWITCHES);
		context.overwrite = settings.getBoolean(SETTING_OVERWRITE);
		
		/* STEP 1 : Scan all incoming WADs so we know where crap is. */
		
		// scan base.
		if (!scanWAD(context, context.baseWad, true))
			return 1;
		
		// scan patches. 
		for (String f : filePaths)
			if (!scanWAD(context, f, false))
				return 1;

		/* STEP 2 : Read list of what we want. */

		out.println("Input texture/flat list:");
		try {
			if (!readTexturesAndFlats(context))
				return 1;
		} catch (IOException e) {
			// if we reach here, you got PROBLEMS, buddy.
			out.println("ERROR: Could not read from STDIN.");
			return 3;
		}
		
		/* STEP 3 : Extract the junk and put it in the output wad. */

		if (!extractToOutputWad(context))
			return 1;
		
		out.println("Done!");
		
		return 0;
	}
	
}