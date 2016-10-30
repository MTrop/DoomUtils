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
import java.util.Comparator;
import java.util.Iterator;
import java.util.regex.Pattern;

import net.mtrop.doom.WadFile;
import net.mtrop.doom.exception.TextureException;
import net.mtrop.doom.exception.WadException;
import net.mtrop.doom.texture.Animated;
import net.mtrop.doom.texture.CommonTexture;
import net.mtrop.doom.texture.CommonTextureList;
import net.mtrop.doom.texture.DoomTextureList;
import net.mtrop.doom.texture.PatchNames;
import net.mtrop.doom.texture.StrifeTextureList;
import net.mtrop.doom.texture.Switches;
import net.mtrop.doom.texture.TextureSet;
import net.mtrop.doom.texture.TextureSet.Texture;
import net.mtrop.doom.util.GraphicUtils;
import net.mtrop.doom.util.NameUtils;
import net.mtrop.doom.util.WadUtils;

import com.blackrook.commons.AbstractSet;
import com.blackrook.commons.Common;
import com.blackrook.commons.ObjectPair;
import com.blackrook.commons.comparators.CaseInsensitiveComparator;
import com.blackrook.commons.hash.CaseInsensitiveHash;
import com.blackrook.commons.hash.CaseInsensitiveHashMap;
import com.blackrook.commons.hash.Hash;
import com.blackrook.commons.linkedlist.Queue;
import com.blackrook.commons.list.ComparatorList;
import com.blackrook.commons.list.List;
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
	private static final Version VERSION = new Version(1,0,0,0);

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
	/** Null texture name. */
	public static final String SETTING_NULLTEXTURE = "nulltex";
	
	/** Switch: Source IWAD. */
	public static final String SWITCH_BASE = "-base";
	/** Switch: Output file. */
	public static final String SWITCH_OUTPUT = "-o";
	/** Switch: Overwrite output file. */
	public static final String SWITCH_OVERWRITE = "-owrite";
	/** Switch: Null texture name. */
	public static final String SWITCH_NULLTEX = "-nulltex";
	/** Switch: No animations. */
	public static final String SWITCH_NOANIMATED = "-noanim";
	/** Switch: No switches. */
	public static final String SWITCH_NOSWITCH = "-noswit";

	/**
	 * Comparator class for Null Texture name. 
	 */
	public static class NullComparator implements Comparator<Texture>
	{
		/** Null texture set. */
		private static final CaseInsensitiveHash NULL_NAMES = new CaseInsensitiveHash(){{
			put("AASTINKY");
			put("AASHITTY");
			put("BADPATCH");
			put("ABADONE");
		}};
		
		private String nullName;
		
		private NullComparator(String nullName)
		{
			this.nullName = nullName;
		}
		
		@Override
		public int compare(Texture o1, Texture o2)
		{
			if (nullName == null)
			{
				return 
					NULL_NAMES.contains(o1.getName()) ? -1 :
					NULL_NAMES.contains(o2.getName()) ? 1 :
					o1.getName().compareTo(o2.getName());
			}
			else return 
					o1.getName().equalsIgnoreCase(nullName) ? -1 :
					o2.getName().equalsIgnoreCase(nullName) ? 1 :
					o1.getName().compareTo(o2.getName());
		}
		
	}
	
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

		/** Null comparator. */
		private NullComparator nullComparator;
		
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
			nullComparator = new NullComparator(null);
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

		/** Names in TEXTURE1. */
		Hash<String> tex1names;
		/** Texture Set. */
		TextureSet textureSet;
		/** Texture 2 */
		boolean tex2exists;
		/** Is Strife-formatted list? */
		boolean strife;
		
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
			textureSet = null;
			tex1names = null;
			tex2exists = false;
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
		final int STATE_NULLTEX = 3;
		
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
			else if (a.equalsIgnoreCase(SWITCH_NULLTEX))
			{
				state = STATE_NULLTEX;
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
				case STATE_NULLTEX:
					out.put(SETTING_NULLTEXTURE, a);
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
		if (!scanNamespace("P", "PP", PATCH_MARKER, unit, wf, unit.patchIndices))
			return false;
		if (!scanNamespace("PP", "P", null, unit, wf, unit.patchIndices))
			return false;
		out.printf("        %d patches.\n", unit.patchIndices.size());
		out.println("    Scanning flat entries...");
		if (!scanNamespace("F", "FF", FLAT_MARKER, unit, wf, unit.flatIndices))
			return false;
		if (!scanNamespace("FF", "F", null, unit, wf, unit.flatIndices))
			return false;
		out.printf("        %d flats.\n", unit.flatIndices.size());
		out.println("    Scanning texture namespace entries...");
		if (!scanNamespace("TX", null, unit, wf, unit.texNamespaceIndices))
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

		for (TextureSet.Texture tex : unit.textureSet)
			if (!unit.textureList.contains(tex.getName()))
				unit.textureList.add(tex.getName());

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
		if (!wf.contains("TEXTURE1"))
			return true;
		out.println("    Scanning TEXTUREx/PNAMES...");
		
		
		PatchNames patchNames = null;
		CommonTextureList<?> textureList1 = null;
		CommonTextureList<?> textureList2 = null;
		byte[] textureData = null;
		
		try {
			textureData = wf.getData("TEXTURE1");
		} catch (WadException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		} catch (IOException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		}

		// figure out if Strife or Doom Texture Lump.
		if (WadUtils.isStrifeTextureData(textureData))
		{
			textureList1 = StrifeTextureList.create(textureData);
			unit.strife = true;
		}
		else
		{
			textureList1 = DoomTextureList.create(textureData);
			unit.strife = false;
		}

		unit.tex1names = new Hash<String>(textureList1.size());
		for (CommonTexture<?> ct : textureList1)
			unit.tex1names.put(ct.getName());

		out.printf("        %d entries in TEXTURE1.\n", textureList1.size());

		try {
			textureData = wf.getData("TEXTURE2");
		} catch (WadException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		} catch (IOException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		}

		if (textureData != null)
		{
			// figure out if Strife or Doom Texture Lump.
			if (WadUtils.isStrifeTextureData(textureData))
				textureList2 = StrifeTextureList.create(textureData);
			else
				textureList2 = DoomTextureList.create(textureData);
			
			out.printf("        %d entries in TEXTURE2.\n", textureList2.size());
			unit.tex2exists = true;
		}
		
		try {
			textureData = wf.getData("PNAMES");
			if (textureData == null)
			{
				out.printf("ERROR: %s: TEXTUREx without PNAMES!\n", wf.getFilePath());
				return false;
			}
			patchNames = PatchNames.create(textureData);
		} catch (WadException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		} catch (IOException e) {
			out.printf("ERROR: %s: %s\n", wf.getFilePath(), e.getMessage());
			return false;
		}
		
		out.printf("        %d entries in PNAMES.\n", patchNames.size());

		if (textureList2 != null)
			unit.textureSet = new TextureSet(patchNames, textureList1, textureList2);
		else
			unit.textureSet = new TextureSet(patchNames, textureList1);
		
		return true;
	}

	// Scan for ANIMATED. Add combinations of textures to animated mapping.
	private boolean scanAnimated(ExtractorContext context, WadUnit unit, WadFile wf) throws IOException
	{
		if (!context.noAnimated)
		{
			if (wf.contains("ANIMATED"))
			{
				out.println("    Scanning ANIMATED...");
				unit.animated.readBytes(wf.getInputStream("ANIMATED"));
				processAnimated(unit, unit.animated);
			}
			
			processAnimated(unit, TextureTables.ALL_ANIMATED);
		}

		if (!context.noSwitches)
		{
			if (wf.contains("SWITCHES"))
			{
				out.println("    Scanning SWITCHES...");
				unit.switches.readBytes(wf.getInputStream("SWITCHES"));
				
				for (Switches.Entry entry : unit.switches)
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
		for (Animated.Entry entry : animated)
		{
			if (entry.isTexture())
			{
				String[] seq = getTextureSequence(unit, entry.getFirstName(), entry.getLastName());
				if (seq != null) for (String s : seq)
					unit.animatedTexture.put(s, seq);
			}
			else
			{
				String[] seq = getFlatSequence(unit, entry.getFirstName(), entry.getLastName());
				if (seq != null) for (String s : seq)
					unit.animatedFlat.put(s, seq);
			}
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
		return scanNamespace(name, null, ignorePattern, unit, wf, map);
	}
	
	// Scans namespace entries.
	private boolean scanNamespace(String name, String equivName, Pattern ignorePattern, WadUnit unit, WadFile wf, CaseInsensitiveHashMap<Integer> map)
	{
		// scan patch namespace
		int start = wf.getIndexOf(name+"_START");
		if (start < 0)
			start = wf.getIndexOf(equivName+"_START");
		
		if (start >= 0)
		{
			int end = wf.getIndexOf(name+"_END");
			if (end < 0)
				end = wf.getIndexOf(equivName+"_END");
			
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
					readAndAddTextures(context, line.toUpperCase());
					break;
				case STATE_FLATS:
					readAndAddFlats(context, line.toUpperCase());
					break;
			}
		}
		
		br.close();
		return true;
	}
	
	private void readAndAddTextures(ExtractorContext context, String textureName)
	{
		if (!NameUtils.isValidTextureName(textureName))
		{
			out.println("ERROR: Texture \""+textureName+"\" has an invalid name. Skipping.");
			return;
		}
		
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
		for (String textureName : context.textureList)
		{
			WadUnit unit = null;
			
			// found texture.
			if ((unit = searchForTexture(context.wadPriority, textureName)) != null)
			{
				// for figuring out if we've found a replaced/added patch.
				boolean foundPatches = false;
				
				TextureSet.Texture entry = unit.textureSet.getTextureByName(textureName);
				
				for (int i = 0; i < entry.getPatchCount(); i++)
				{
					TextureSet.Patch p = entry.getPatch(i);
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
				if (foundPatches || !exportSet.textureSet.contains(textureName))
				{
					out.printf("        Copying texture %s...\n", textureName);
					
					// check if potential overwrite.
					if (exportSet.textureSet.contains(textureName))
						exportSet.textureSet.removeTextureByName(textureName);
					
					TextureSet.Texture newtex = exportSet.textureSet.createTexture(textureName);
					newtex.setHeight(entry.getHeight());
					newtex.setWidth(entry.getWidth());
					for (TextureSet.Patch p : entry)
					{
						TextureSet.Patch newpatch = newtex.createPatch(p.getName());
						newpatch.setOriginX(p.getOriginX());
						newpatch.setOriginY(p.getOriginY());
					}
					
					exportSet.textureHash.put(textureName);
				}
				
			}
			// unit not found
			else if ((unit = searchForNamespaceTexture(context.wadPriority, textureName)) != null)
			{
				// does a matching texture entry exist?
				if (unit.texNamespaceIndices.containsKey(textureName))
				{
					Integer pidx = unit.texNamespaceIndices.get(textureName);
					if (pidx != null)
					{
						try {
							out.printf("        Extracting namespace texture %s...\n", textureName);
							EntryData data = new EntryData(textureName, unit.wad.getData(pidx));
							exportSet.textureData.add(data);
						} catch (IOException e) {
							out.printf("ERROR: %s: Could not read entry %s.\n", unit.wad.getFilePath(), textureName);
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
				
				for (Animated.Entry entry : unit.animated)
				{
					if (entry.isTexture())
					{
						if (exportSet.textureSet.contains(entry.getFirstName()))
							exportSet.animatedData.addEntry(Animated.texture(entry.getLastName(), entry.getFirstName(), entry.getTicks(), entry.getAllowsDecals()));
					}
					else
					{
						if (exportSet.flatHash.contains(entry.getFirstName()))
							exportSet.animatedData.addEntry(Animated.flat(entry.getLastName(), entry.getFirstName(), entry.getTicks()));
						else if (context.baseUnit.flatIndices.containsKey(entry.getFirstName()))
							exportSet.animatedData.addEntry(Animated.flat(entry.getLastName(), entry.getFirstName(), entry.getTicks()));
					}
				}
			}
		}
		
		if (!context.noSwitches)
		{
			out.println("    Merging SWITCHES...");
			for (WadUnit unit : context.wadPriority)
			{
				// did we pull any animated textures? if so, copy the entries.
				for (Switches.Entry e : unit.switches)
				{
					if (exportSet.textureSet.contains(e.getOffName()))
						exportSet.switchesData.addEntry(e.getOffName(), e.getOnName(), e.getGame());
					else if (exportSet.textureSet.contains(e.getOnName()))
						exportSet.switchesData.addEntry(e.getOffName(), e.getOnName(), e.getGame());
				}
			}
		}
		
		return true;
	}
	
	private boolean dumpToOutputWad(ExtractorContext context, ExportSet exportSet, WadFile wf) throws IOException
	{
		out.println("Sorting entries...");
		exportSet.textureSet.sort(context.nullComparator);
		exportSet.patchData.sort();
		exportSet.flatData.sort();
		exportSet.textureData.sort();
		
		out.println("Dumping entries...");

		List<CommonTextureList<?>> tlist = new List<>();
		PatchNames pnames;
		
		// if Strife-formatted source, export to Strife.
		if (context.baseUnit.strife)
		{
			pnames = new PatchNames();
			StrifeTextureList tex1 = new StrifeTextureList();
			StrifeTextureList tex2 = context.baseUnit.tex2exists ? new StrifeTextureList() : null;
			AbstractSet<String> tex1names = context.baseUnit.tex2exists ? context.baseUnit.tex1names : null;
			GraphicUtils.exportTextureSet(exportSet.textureSet, pnames, tex1, tex2, tex1names);
			tlist.add(tex1);
			if (tex2 != null)
				tlist.add(tex2);
		}
		// if not, Doom format.
		else
		{
			pnames = new PatchNames();
			DoomTextureList tex1 = new DoomTextureList();
			DoomTextureList tex2 = context.baseUnit.tex2exists ? new DoomTextureList() : null;
			AbstractSet<String> tex1names = context.baseUnit.tex2exists ? context.baseUnit.tex1names : null;
			GraphicUtils.exportTextureSet(exportSet.textureSet, pnames, tex1, tex2, tex1names);
			tlist.add(tex1);
			if (tex2 != null)
				tlist.add(tex2);
		}
		
		for (int i = 0; i < tlist.size(); i++)
		{
			String tentry = String.format("TEXTURE%01d", i+1);
			int idx = wf.getIndexOf(tentry);
			if (idx >= 0)
				wf.replaceEntry(idx, tlist.getByIndex(i).toBytes());
			else
				wf.addData(tentry, tlist.getByIndex(i).toBytes());
		}
		
		int idx = wf.getIndexOf("PNAMES");
		if (idx >= 0)
			wf.replaceEntry(idx, pnames.toBytes());
		else
			wf.addData("PNAMES", pnames.toBytes());
		
		if (!context.noAnimated && !exportSet.animatedData.isEmpty())
		{
			idx = wf.getIndexOf("ANIMATED");
			if (idx >= 0)
				wf.replaceEntry(idx, exportSet.animatedData.toBytes());
			else
				wf.addData("ANIMATED", exportSet.animatedData.toBytes());
		}

		if (!context.noSwitches && exportSet.switchesData.getEntryCount() > 0)
		{
			idx = wf.getIndexOf("SWITCHES");
			if (idx >= 0)
				wf.replaceEntry(idx, exportSet.switchesData.toBytes());
			else
				wf.addData("SWITCHES", exportSet.switchesData.toBytes());
		}
		
		dumpListToOutputWad(exportSet.patchData, "PP", wf);
		dumpListToOutputWad(exportSet.flatData, "FF", wf);
		dumpListToOutputWad(exportSet.textureData, "TX", wf);
		
		return true;
	}
	
	private boolean dumpListToOutputWad(List<EntryData> entries, String namespace, WadFile wf) throws IOException
	{
		if (entries.size() == 0)
			return true;
		
		String[] names = new String[entries.size() + 2];
		byte[][] data = new byte[entries.size() + 2][];
		
		names[0] = namespace + "_START";
		data[0] = new byte[0];
		
		for (int i = 0; i < entries.size(); i++)
		{
			names[1 + i] = entries.getByIndex(i).getKey();
			data[1 + i] = entries.getByIndex(i).getValue();
		}

		names[names.length - 1] = namespace + "_END";
		data[data.length - 1] = new byte[0];
		
		wf.addAllData(names, data);
		
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
			exportSet.textureSet = GraphicUtils.importTextureSet(baseWadFile);
			extractTextures(context, exportSet);
			extractFlats(context, exportSet);
			mergeAnimatedAndSwitches(context, exportSet);
			dumpToOutputWad(context, exportSet, outWadFile);
		} catch (TextureException | IOException e) {
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
		out.println("    [file]    :          A valid WAD file (that contains the textures to ");
		out.println("                         extract). Accepts wildcards for multiple WAD files.");
		out.println();
		out.println("    [base]    :          The WAD file to use for reference for extraction.");
		out.println("                         Any texture resources found in this file are NOT");
		out.println("                         extracted, except for the TEXTUREx and PNAMES lumps");
		out.println("                         to use as a base. (Usually an IWAD)");
		out.println();
		out.println("    [output]  :          The output WAD file. If it exists, the target's");
		out.println("                         TEXTUREx and PNAMES lumps are overwritten, and the");
		out.println("                         extracted contents are APPENDED to it.");
		out.println();
		out.println("    [switches]: -noanim  If specified, do not include other textures in");
		out.println("                         a texture's animation sequence, and ignore ANIMATED");
		out.println("                         lumps.");
		out.println();
		out.println("                -noswit  If specified, do not include other textures in");
		out.println("                         a texture's switch sequence, and ignore SWITCHES");
		out.println("                         lumps.");
		out.println();
		out.println("                -owrite  If specified, it will overwrite the contents of the");
		out.println("                         output WAD (by default, it appends them).");
		out.println();
		out.println("                -nulltex If specified, the next argument is the null");
		out.println("                         texture that is always sorted first.");
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
		context.nullComparator = new NullComparator(settings.getString(SETTING_NULLTEXTURE));
		
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

		if (context.nullComparator.nullName != null)
			out.println("Using "+ context.nullComparator.nullName.toUpperCase() + " as the null texture in TEXTURE1...");
		
		if (!extractToOutputWad(context))
			return 1;
		
		out.println("Done!");
		
		return 0;
	}
	
}
