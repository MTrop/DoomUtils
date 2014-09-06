package net.mtrop.utility.doom.paletteconvert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import com.blackrook.commons.Common;
import com.blackrook.commons.list.List;
import com.blackrook.doom.DoomPK3;
import com.blackrook.doom.DoomWad;
import com.blackrook.doom.WadException;
import com.blackrook.doom.WadFile;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

public class PaletteConvert extends Utility<PaletteConvert.PSContext>
{
	private static final Version VERSION = new Version(0,9,0,0);
	
	/** Palette source file. */
	public static final String SETTING_PALETTE_SOURCE = "palettesrc";
	/** Palette destination file. */
	public static final String SETTING_PALETTE_TARGET = "palettetarget";
	/** Colormap source file. */
	public static final String SETTING_COLORMAP_SOURCE = "colormapsrc";
	/** Colormap destination file. */
	public static final String SETTING_COLORMAP_TARGET = "colormaptarget";
	/** Files to convert. */
	public static final String SETTING_FILES = "files";
	/** Patches. */
	public static final String SETTING_GRAPHICMODE = "mode";

	/** Patches. */
	public static final String SETTING_GRAPHICMODE_PATCHES = "patches";
	/** Flats. */
	public static final String SETTING_GRAPHICMODE_FLATS = "flats";
	
	/** Switch: Palette source file. */
	public static final String SWITCH_PALETTE_SRC_FILE = "-srcpal";
	/** Switch: Palette target file. */
	public static final String SWITCH_PALETTE_TRG_FILE = "-trgpal";
	/** Switch: Colormap source file. */
	public static final String SWITCH_COLORMAP_SRC_FILE = "-srcmap";
	/** Switch: Colormap target file. */
	public static final String SWITCH_COLORMAP_TRG_FILE = "-trgmap";
	/** Switch: WAD/PK3 source file (for both). */
	public static final String SWITCH_WAD_SRC_FILE = "-srcwad";
	/** Switch: WAD/PK3 target file (for both). */
	public static final String SWITCH_WAD_TRG_FILE = "-trgwad";
	/** Switch: Read patches. */
	public static final String SWITCH_PATCHES = "-patch";
	/** Switch: Read flats. */
	public static final String SWITCH_FLATS = "-flat";
	
	/**
	 * Converter context. 
	 */
	public static class PSContext implements Context
	{
		boolean[] sourceBrightmask;
		byte[][] sourcePalette;
		boolean[] targetBrightmask;
		byte[][] targetPalette;
	}

	@Override
	public Version getVersion()
	{
		return VERSION;
	}

	@Override
	public Settings getSettingsFromCMDLINE(String... args)
	{
		Settings settings = new Settings();
		
		final int STATE_FILES = 0;
		final int STATE_PALETTE_SRC_FILE = 1;
		final int STATE_PALETTE_TRG_FILE = 2;
		final int STATE_COLORMAP_SRC_FILE = 3;
		final int STATE_COLORMAP_TRG_FILE = 4;
		final int STATE_WAD_SRC_FILE = 5;
		final int STATE_WAD_TRG_FILE = 6;
		
		int state = 0;
		
		List<String> inputFileList = new List<String>(25);
		
		for (String arg : args)
		{
			switch (state)
			{
				case STATE_FILES:
				{
					if (arg.equalsIgnoreCase(SWITCH_PALETTE_SRC_FILE))
						state = STATE_PALETTE_SRC_FILE;
					else if (arg.equalsIgnoreCase(SWITCH_PALETTE_TRG_FILE))
						state = STATE_PALETTE_TRG_FILE;
					else if (arg.equalsIgnoreCase(SWITCH_COLORMAP_SRC_FILE))
						state = STATE_COLORMAP_SRC_FILE;
					else if (arg.equalsIgnoreCase(SWITCH_COLORMAP_TRG_FILE))
						state = STATE_COLORMAP_TRG_FILE;
					else if (arg.equalsIgnoreCase(SWITCH_WAD_SRC_FILE))
						state = STATE_WAD_SRC_FILE;
					else if (arg.equalsIgnoreCase(SWITCH_WAD_TRG_FILE))
						state = STATE_WAD_TRG_FILE;
					else if (arg.equalsIgnoreCase(SWITCH_FLATS))
						settings.put(SETTING_GRAPHICMODE, SETTING_GRAPHICMODE_FLATS);
					else if (arg.equalsIgnoreCase(SWITCH_PATCHES))
						settings.put(SETTING_GRAPHICMODE, SETTING_GRAPHICMODE_PATCHES);
					else
						inputFileList.add(arg);
				}
					break;
				
				case STATE_PALETTE_SRC_FILE:
					settings.put(SETTING_PALETTE_SOURCE, arg);
					state = STATE_FILES;
					break;
				case STATE_PALETTE_TRG_FILE:
					settings.put(SETTING_PALETTE_TARGET, arg);
					state = STATE_FILES;
					break;
				case STATE_COLORMAP_SRC_FILE:
					settings.put(SETTING_COLORMAP_SOURCE, arg);
					state = STATE_FILES;
					break;
				case STATE_COLORMAP_TRG_FILE:
					settings.put(SETTING_COLORMAP_TARGET, arg);
					state = STATE_FILES;
					break;
				case STATE_WAD_SRC_FILE:
					settings.put(SETTING_PALETTE_SOURCE, arg);
					settings.put(SETTING_COLORMAP_SOURCE, arg);
					state = STATE_FILES;
					break;
				case STATE_WAD_TRG_FILE:
					settings.put(SETTING_PALETTE_TARGET, arg);
					settings.put(SETTING_COLORMAP_TARGET, arg);
					state = STATE_FILES;
					break;
			}
		}
		
		String[] inputFiles = new String[inputFileList.size()];
		inputFileList.toArray(inputFiles);
		settings.put(SETTING_FILES, inputFiles);
		
		return settings;
	}


	@Override
	public PSContext createNewContext()
	{
		return new PSContext();
	}

	/** Reads raw files for palette. */
	private boolean readPaletteFromRawFile(PSContext context, boolean source, InputStream paletteFile) throws IOException
	{
		byte[][] fill = null;
		if (source)
			fill = context.sourcePalette = new byte[256][];
		else
			fill = context.targetPalette = new byte[256][];
		
		for (int i = 0; i < 256; i++)
		{
			byte[] b = new byte[3];
			paletteFile.read(b);
			fill[i] = b;
		}
		
		return true;
	}

	/** Reads a wad file for a palette. */
	private boolean readPaletteFromWAD(PSContext context, boolean source, DoomWad wadFile) throws IOException
	{
		InputStream in = null;
		try {
			in = wadFile.getDataAsStream("playpal");
			return readPaletteFromRawFile(context, source, in);
		} finally {
			Common.close(in);
		}
	}

	/** Reads a pk3 file for a palette. */
	private boolean readPaletteFromPK3(PSContext context, boolean source, DoomPK3 pk3File) throws IOException
	{
		for (ZipEntry ze : pk3File.getGlobals())
		{
			if (ze.getName().toLowerCase().contains("playpal."))
			{
				InputStream in = null;
				try {
					in = pk3File.getDataAsStream(ze);
					return readPaletteFromRawFile(context, source, in);
				} finally {
					Common.close(in);
				}
			}
		}
		return false;
	}

	/** Reads raw files for colormap brightmask. */
	private boolean readBrightmaskFromRawFile(PSContext context, boolean source, InputStream colormapFile) throws IOException
	{
		boolean[] mask = null;
		if (source)
			mask = context.sourceBrightmask = new boolean[256];
		else
			mask = context.targetBrightmask = new boolean[256];

		byte[][] map = new byte[32][];
		
		for (int i = 0; i < 32; i++)
		{
			map[i] = new byte[256];
			colormapFile.read(map[i]);
		}

		// scan for brightmaps.
		for (int i = 0; i < 256; i++)
		{
			boolean same = true;
			for (int c = 1; c < 32 && same; c++)
				same = map[c - 1][i] == map[c][i];
			mask[i] = same;
		}
		
		return true;
	}
	
	/** Reads a wad file for colormap brightmask. */
	private boolean readBrightmaskFromWAD(PSContext context, boolean source, DoomWad wadFile) throws IOException
	{
		InputStream in = null;
		try {
			in = wadFile.getDataAsStream("colormap");
			return readBrightmaskFromRawFile(context, source, in);
		} finally {
			Common.close(in);
		}
	}
	
	/** Reads a pk3 file for colormap brightmask. */
	private boolean readBrightmaskFromPK3(PSContext context, boolean source, DoomPK3 pk3File) throws IOException
	{
		for (ZipEntry ze : pk3File.getGlobals())
		{
			if (ze.getName().toLowerCase().contains("colormap."))
			{
				InputStream in = null;
				try {
					in = pk3File.getDataAsStream(ze);
					return readBrightmaskFromRawFile(context, source, in);
				} finally {
					Common.close(in);
				}
			}
		}
		return false;
	}

	/** Reads the palette info files. */
	private int readInfo(PSContext context, boolean source, Settings settings)
	{
		String srcstr = source ? "source" : "target";
		
		if (Common.isEmpty(settings.getString(source ? SETTING_PALETTE_SOURCE : SETTING_PALETTE_TARGET)))
		{
			out.println("ERROR: Must specify a palette source (raw file or WAD/PK3).");
			return 1;
		}
		else
		{
			File palfile = new File(settings.getString(source ? SETTING_PALETTE_SOURCE : SETTING_PALETTE_TARGET));
			File cmapfile = new File(settings.getString(source ? SETTING_COLORMAP_SOURCE : SETTING_COLORMAP_TARGET));

			if (!palfile.exists())
			{
				out.printf("ERROR: File %s not found!\n", palfile.getPath());
				return 2;
			}

			if (!cmapfile.exists())
			{
				out.printf("ERROR: File %s not found!\n", cmapfile.getPath());
				return 2;
			}
			
			if (palfile.equals(cmapfile))
			{
				// wad file
				WadFile wadFile = null;
				try {
					wadFile = new WadFile(palfile);
					
					out.printf("Reading %s palette from %s...\n", srcstr, palfile.getPath());
					
					if (!readPaletteFromWAD(context, source, wadFile))
					{
						out.printf("ERROR: File %s : Palette not found!\n", palfile.getPath());
						return 5;
					}
					
					out.printf("Reading %s brightmask/colormap from %s...\n", srcstr, palfile.getPath());
					if (!readBrightmaskFromWAD(context, source, wadFile))
					{
						out.printf("ERROR: File %s : Colormap not found!\n", palfile.getPath());
						return 5;
					}
					
					return 0;
				} catch (WadException e) {
					// not a wad.
				} catch (IOException e) {
					out.printf("ERROR: File %s : %s\n", palfile.getPath(), e.getLocalizedMessage());
					return 3;
				} finally {
					Common.close(wadFile);
				}
				
				// pk3 file
				DoomPK3 pk3 = null;
				try {
					pk3 = new DoomPK3(palfile);
					out.printf("Reading %s palette from %s...\n", srcstr, palfile.getPath());
					if (!readPaletteFromPK3(context, source, pk3))
					{
						out.printf("ERROR: File %s : Palette not found!\n", palfile.getPath());
						return 5;
					}
					out.printf("Reading %s brightmask/colormap from %s...\n", srcstr, palfile.getPath());
					if (!readBrightmaskFromPK3(context, source, pk3))
					{
						out.printf("ERROR: File %s : Colormap not found!\n", palfile.getPath());
						return 5;
					}
					return 0;
				} catch (ZipException e) {
					// not a zip.
				} catch (IOException e) {
					out.printf("ERROR: File %s : %s\n", palfile.getPath(), e.getLocalizedMessage());
					return 3;
				} finally {
					Common.close(pk3);
				}
				
				out.printf("ERROR: File %s is not a viable %s.\n", palfile.getPath(), srcstr);
				return 3;
			}
			else
			{
				// raw file
				FileInputStream rawFile = null;
				
				try {
					rawFile = new FileInputStream(palfile);
					out.printf("Reading %s palette from %s...\n", srcstr, palfile.getPath());
					if (!readPaletteFromRawFile(context, source, rawFile))
					{
						out.printf("ERROR: File %s : Palette not found!\n", palfile.getPath());
						return 5;
					}
				} catch (IOException e) {
					out.printf("ERROR: File %s : %s\n", palfile.getPath(), e.getLocalizedMessage());
					return 3;
				} finally {
					Common.close(rawFile);
				}
				
				try {
					rawFile = new FileInputStream(cmapfile);
					out.printf("Reading %s brightmask/colormap from %s...\n", srcstr, cmapfile.getPath());
					if (!readBrightmaskFromRawFile(context, source, rawFile))
					{
						out.printf("ERROR: File %s : Colormap not found!\n", palfile.getPath());
						return 5;
					}
				} catch (IOException e) {
					out.printf("ERROR: File %s : %s\n", palfile.getPath(), e.getLocalizedMessage());
					return 3;
				} finally {
					Common.close(rawFile);
				}
				
				return 0;
			}
		}
	}
	
	/** Prints the usage blurb. */
	private void printUsage()
	{
		out.printf("Palette Convert v%s by Matt Tropiano\n", getVersion());
		out.println("Usage: palcnvrt [files] [type] [srcargs] [trgargs] ");
		out.println("    [files]  :         Valid Doom graphic files. Accepts wildcards");
		out.println("                       for multiple files.");
		out.println();
		out.println("    [type]   : -patch  If specified, all input files are graphic/patch format.");
		out.println();
		out.println("               -flat   If specified, all input files are flat format.");
		out.println();
		out.println("                       If neither are specified, assumes patch.");
		out.println();
		out.println("    [srcargs]: -srcpal If specified, next argument is palette source");
		out.println("                       raw file.");
		out.println();
		out.println("               -srcmap If specified, next argument is colormap source");
		out.println("                       raw file.");
		out.println();
		out.println("               -srcwad If specified, next argument is palette and colormap");
		out.println("                       source WAD/PK3.");
		out.println();
		out.println("    [trgargs]: -trgpal If specified, next argument is palette target");
		out.println("                       raw file.");
		out.println();
		out.println("               -trgmap If specified, next argument is colormap target");
		out.println("                       raw file.");
		out.println();
		out.println("               -trgwad If specified, next argument is palette and colormap");
		out.println("                       target WAD/PK3.");
	}
	
	@Override
	public int execute(PSContext context, Settings settings)
	{
		int err = 0;
		
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No graphic files specified.");
			printUsage();
			return 4;
		}

		/* Step 1: Read palette info files. */
		
		if ((err = readInfo(context, true, settings)) > 0)
			return err;
		if ((err = readInfo(context, false, settings)) > 0)
			return err;
		
		
		return 0;
	}
}
