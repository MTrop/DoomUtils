/*******************************************************************************
 * Copyright (c) 2013-2016 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.texspy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import net.mtrop.doom.Wad;
import net.mtrop.doom.WadBuffer;
import net.mtrop.doom.WadFile;
import net.mtrop.doom.enums.MapFormat;
import net.mtrop.doom.exception.WadException;
import net.mtrop.doom.map.binary.DoomSector;
import net.mtrop.doom.map.binary.DoomSidedef;
import net.mtrop.doom.map.udmf.UDMFObject;
import net.mtrop.doom.map.udmf.UDMFReader;
import net.mtrop.doom.map.udmf.UDMFTable;
import net.mtrop.doom.map.udmf.attributes.UDMFCommonSectorAttributes;
import net.mtrop.doom.map.udmf.attributes.UDMFCommonSidedefAttributes;
import net.mtrop.doom.util.MapUtils;
import net.mtrop.doom.util.NameUtils;

import com.blackrook.commons.Common;
import com.blackrook.commons.list.List;
import com.blackrook.commons.list.SortedList;
import com.blackrook.commons.math.Pair;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * TexSpy - looks up and prints the names of used textures in a WAD.
 * @author Matthew Tropiano
 */
public class TextureSpy extends Utility<TextureSpy.TextureSpyContext>
{
	private static final Version VERSION = new Version(2,0,0,0);

	/** Output type setting key. */
	public static final String SETTING_OUTPUT_TEXTURE = "outputtex";
	/** Output type setting key. */
	public static final String SETTING_OUTPUT_FLAT = "outputflat";
	/** Suppress messages. */
	public static final String SETTING_NOMESSAGES = "nomessages";
	/** File path. */
	public static final String SETTING_FILES = "files";
	/** Script out for TExtract. */
	public static final String SETTING_TEXTRACT = "textract";
	/** Include skies. */
	public static final String SETTING_NOSKIES = "skies";

	/** Switch: normal output. */
	public static final String SWITCH_TEXTURES = "-t";
	/** Switch: long output. */
	public static final String SWITCH_FLATS = "-f";
	/** Switch: prepare for TEXtract. */
	public static final String SWITCH_TEXTRACT = "-textract";
	/** Switch: don't include skies. */
	public static final String SWITCH_NOSKIES = "-noskies";
	/** Switch: no messages. */
	public static final String SWITCH_NOMSG = "-nomsg";

	/** Regex pattern for Episode, Map. */
	private static final Pattern EPISODE_PATTERN = Pattern.compile("E[1-5]M[1-9]");
	/** Regex pattern for Map only. */
	private static final Pattern MAP_PATTERN = Pattern.compile("MAP[0-9][0-9]");

	/**
	 * Context.
	 */
	public static class TextureSpyContext implements Context
	{
		/** List of textures. */
		private SortedList<String> textureList; 
		/** List of flats. */
		private SortedList<String> flatList;
		/** Output textures. */
		private boolean outputTextures;
		/** Output flats. */
		private boolean outputFlats;
		/** Prep for TExtract? */
		private boolean noskies;
		/** Prep for TExtract? */
		private boolean textract;
		/** No messages? */
		private boolean nomessage;
		
		private TextureSpyContext()
		{
			textureList = new SortedList<String>(20);
			flatList = new SortedList<String>(20);
			outputTextures = false;
			outputFlats = false;
			noskies = false;
			textract = false;
			nomessage = false;
		}
		
		String tcomment() {return textract ? "#" : "";}
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

		boolean noswitch = true;
		
		for (String a : args)
		{
			if (a.equalsIgnoreCase(SWITCH_TEXTRACT))
			{
				out.put(SETTING_TEXTRACT, true);
			}
			else if (a.equalsIgnoreCase(SWITCH_NOSKIES))
			{
				out.put(SETTING_NOSKIES, true);
			}
			else if (a.equalsIgnoreCase(SWITCH_TEXTURES))
			{
				out.put(SETTING_OUTPUT_TEXTURE, true);
				noswitch = false;
			}
			else if (a.equalsIgnoreCase(SWITCH_FLATS))
			{
				out.put(SETTING_OUTPUT_FLAT, true);
				noswitch = false;
			}
			else if (a.equalsIgnoreCase(SWITCH_NOMSG))
			{
				out.put(SETTING_NOMESSAGES, true);
			}
			else
				files.add(a);
		}
				
		if (noswitch)
		{
			out.put(SETTING_OUTPUT_TEXTURE, true);
			out.put(SETTING_OUTPUT_FLAT, true);
		}
		
		String[] filePaths = new String[files.size()];
		files.toArray(filePaths);
		out.put(SETTING_FILES, filePaths);
		return out;
	}

	@Override
	public TextureSpyContext createNewContext()
	{
		return new TextureSpyContext();
	}

	// Process PK3/ZIP
	private void processPK3(TextureSpyContext context, String fileName, File f) throws ZipException, IOException
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
				File pk3 = File.createTempFile("texspy", "pk3tmp");
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
	private void processWAD(TextureSpyContext context, File f) throws WadException, IOException
	{
		WadFile wf = new WadFile(f);
		inspectWAD(context, wf);
		wf.close();
	}
	
	// Inspect WAD contents.
	private void inspectWAD(TextureSpyContext context, Wad wad) throws IOException
	{
		String[] mapHeaders = MapUtils.getAllMapHeaders(wad);
		for (String mapName : mapHeaders)
			inspectMap(context, wad, mapName);
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
	
	// Inspect a map in a WAD.
	private void inspectMap(TextureSpyContext context, Wad wad, String mapName) throws IOException
	{
		if (!context.nomessage)
			out.println(context.tcomment() + "    Opening map "+mapName+"...");
		
		MapFormat format = MapUtils.getMapFormat(wad, mapName);
		
		if (format == null)
		{
			out.println(context.tcomment() + "    ERROR: NOT A MAP!");
			return;
		}

		if (!context.nomessage)
			out.println(context.tcomment() + "    Format is "+format.name()+"...");

		// filled in if UDMF.
		UDMFTable udmf = null;
		
		if (format == MapFormat.UDMF)
		{
			InputStream in = wad.getInputStream("TEXTMAP", wad.getLastIndexOf(mapName));
			udmf = UDMFReader.readData(in);
			Common.close(in);
		}
				
		if (context.outputTextures)
		{
			if (!context.nomessage)
				out.println(context.tcomment() + "        Reading SIDEDEFS...");

			switch (format)
			{
				default:
				case DOOM:
				case HEXEN:
				case STRIFE:
				{
					byte[] in = wad.getData("SIDEDEFS", wad.getLastIndexOf(mapName));
					DoomSidedef[] sidedefs = DoomSidedef.create(in, in.length / DoomSidedef.LENGTH);
					inspectSidedefs(context, sidedefs);
				}
				break;

				case UDMF:
				{
					inspectSidedefs(context, udmf.getObjects("sidedef"));
				}
				break;
					
			}

		}

		if (context.outputFlats)
		{
			if (!context.nomessage)
				out.println(context.tcomment() + "        Reading SECTORS...");

			switch (format)
			{
				default:
				case DOOM:
				case HEXEN:
				case STRIFE:
				{
					byte[] in = wad.getData("SECTORS", wad.getLastIndexOf(mapName));
					DoomSector[] sectors = DoomSector.create(in, in.length / DoomSector.LENGTH);
					inspectSectors(context, sectors);
				}
				break;

				case UDMF:
				{
					inspectSectors(context, udmf.getObjects("sector"));
				}
				break;
					
			}
		}
		
		if (!context.noskies)
		{
			inspectMap(context, mapName);
		}
		
	}
	
	private void inspectMap(TextureSpyContext context, String mapName)
	{
		Pair p = new Pair();
		getEpisodeAndMap(mapName, p);
		if (p.x == 0)
		{
			if (p.y >= 21)
			{
				if (!context.textureList.contains("SKY3"))
					context.textureList.add("SKY3");
			}
			else if (p.y >= 12)
			{
				if (!context.textureList.contains("SKY2"))
					context.textureList.add("SKY2");
			}
			else
			{
				if (!context.textureList.contains("SKY1"))
					context.textureList.add("SKY1");
			}
		}
		else if (p.x == 1)
		{
			if (!context.textureList.contains("SKY1"))
				context.textureList.add("SKY1");
		}
		else if (p.x == 2)
		{
			if (!context.textureList.contains("SKY2"))
				context.textureList.add("SKY2");
		}
		else if (p.x == 3)
		{
			if (!context.textureList.contains("SKY3"))
				context.textureList.add("SKY3");
		}
		else if (p.x == 4)
		{
			if (!context.textureList.contains("SKY4"))
				context.textureList.add("SKY4");
			if (!context.textureList.contains("SKY1"))
				context.textureList.add("SKY1");
		}
		else if (p.x == 5)
		{
			if (!context.textureList.contains("SKY3"))
				context.textureList.add("SKY3");
		}
	}
	
	// Adds sidedef textures to the list.
	private void inspectSidedefs(TextureSpyContext context, DoomSidedef[] sidedefs)
	{
		for (DoomSidedef s : sidedefs)
		{
			addTexture(context, s.getTextureTop());
			addTexture(context, s.getTextureMiddle());
			addTexture(context, s.getTextureBottom());
		}
	}
	
	// Adds sidedef textures to the list.
	private void inspectSidedefs(TextureSpyContext context, UDMFObject[] sidedefs)
	{
		for (UDMFObject s : sidedefs)
		{
			addTexture(context, s.getString(UDMFCommonSidedefAttributes.ATTRIB_TEXTURE_TOP, NameUtils.EMPTY_TEXTURE_NAME));
			addTexture(context, s.getString(UDMFCommonSidedefAttributes.ATTRIB_TEXTURE_MIDDLE, NameUtils.EMPTY_TEXTURE_NAME));
			addTexture(context, s.getString(UDMFCommonSidedefAttributes.ATTRIB_TEXTURE_BOTTOM, NameUtils.EMPTY_TEXTURE_NAME));
		}
	}
	
	// Adds sector textures to the list.
	private void inspectSectors(TextureSpyContext context, DoomSector[] sectors)
	{
		for (DoomSector s : sectors)
		{
			addFlat(context, s.getFloorTexture());
			addFlat(context, s.getCeilingTexture());
		}
	}
	
	// Adds sector textures to the list.
	private void inspectSectors(TextureSpyContext context, UDMFObject[] sectors)
	{
		for (UDMFObject s : sectors)
		{
			addFlat(context, s.getString(UDMFCommonSectorAttributes.ATTRIB_TEXTURE_FLOOR));
			addFlat(context, s.getString(UDMFCommonSectorAttributes.ATTRIB_TEXTURE_CEILING));
		}
	}
	
	private void addTexture(TextureSpyContext context, String texture)
	{
		if (!context.textureList.contains(texture) && !Common.isEmpty(texture))
			context.textureList.add(texture);
	}

	private void addFlat(TextureSpyContext context, String texture)
	{
		if (!context.flatList.contains(texture) && !Common.isEmpty(texture))
			context.flatList.add(texture);
	}
	
	// Prints the usage message.
	private void printUsage()
	{
		out.printf("Texture Spy v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: texspy [file] [switches]");
		out.println("    [file]    :           A valid WAD/PK3/ZIP file. Accepts wildcards");
		out.println("                          for multiple files.");
		out.println("    [switches]: -t        If specified, output textures.");
		out.println("                -f        If specified, output flats.");
		out.println("                          Neither specified implies \"output both.\"");
		out.println("                -nomsg    Suppresses non-error messages during execution.");
		out.println("                -textract Prepares output so that it can be piped directly");
		out.println("                          into TEXtract.");
		out.println("                -noskies  If specified, this will skip adding map skies to");
		out.println("                          the output list.");
	}
	
	@Override
	public int execute(TextureSpyContext context, Settings settings)
	{
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No WAD(s)/PK3(s) specified.");
			printUsage();
			return 2;
		}
		
		context.outputTextures = settings.getBoolean(SETTING_OUTPUT_TEXTURE);
		context.outputFlats = settings.getBoolean(SETTING_OUTPUT_FLAT);
		context.textract = settings.getBoolean(SETTING_TEXTRACT);
		context.nomessage = settings.getBoolean(SETTING_NOMESSAGES);
		context.noskies = settings.getBoolean(SETTING_NOSKIES);

		boolean successfulOnce = false;
		
		for (String f : filePaths)
		{
			if (!context.nomessage)
				out.println(context.tcomment() + "Opening file "+f+"...");
			try {
				processPK3(context, f, new File(f));
				successfulOnce = true;
			} catch (ZipException e) {
				try {
					processWAD(context, new File(f));
					successfulOnce = true;
				} catch (WadException ex) {
					out.printf("%sERROR: Couldn't open %s: not a WAD or PK3.\n", context.tcomment(), f);
				} catch (IOException ex) {
					out.printf("%sERROR: Couldn't open %s. Read error encountered.\n", context.tcomment(), f);
				}
			} catch (IOException ex) {
				out.printf("%sERROR: Couldn't open %s. Read error encountered.\n", context.tcomment(), f);
			}
		}
		
		if (!successfulOnce)
			return 1;
		
		// Print texture list.
		if (context.textureList.size() > 0)
			out.println("-TEXTURE");
		for (String s : context.textureList)
		{
			if (!s.equalsIgnoreCase(NameUtils.EMPTY_TEXTURE_NAME) && s.length() > 0)
				out.println(s.toUpperCase());
		}

		// Print flat list.
		if (context.flatList.size() > 0)
			out.println("-FLAT");
		for (String s : context.flatList)
		{
			if (!s.equalsIgnoreCase(NameUtils.EMPTY_TEXTURE_NAME) && s.length() > 0)
				out.println(s.toUpperCase());
		}
		
		if (context.textract)
			out.println("-END");

		return 0;
	}
	
}
