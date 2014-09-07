package net.mtrop.utility.doom.palcnvrt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import com.blackrook.doom.struct.Flat;
import com.blackrook.doom.struct.Patch;
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
		
		// default mode
		settings.put(SETTING_GRAPHICMODE, SETTING_GRAPHICMODE_PATCHES);
		
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
			out.printf("ERROR: Must specify a palette %s (raw file or WAD/PK3).\n", srcstr);
			return 1;
		}
		else if (Common.isEmpty(settings.getString(source ? SETTING_COLORMAP_SOURCE : SETTING_COLORMAP_TARGET)))
		{
			out.printf("ERROR: Must specify a palette %s (raw file or WAD/PK3).\n", srcstr);
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
	
	/** Process the patch file. */
	private void processPatchFile(PSContext context, File f)
	{
		Patch inPatch = new Patch();
	
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
			inPatch.readDoomBytes(fis);
		} catch (IOException e) {
			out.printf("\rERROR: Trouble reading %s. %s: %s\n", f.getName(), e.getClass().getSimpleName(), e.getLocalizedMessage());
		} finally {
			Common.close(fis);
		}
		
		for (int w = 0; w < inPatch.getWidth(); w++)
			for (int h = 0; h < inPatch.getHeight(); h++)
			{
				int index = inPatch.getPixel(w, h);
				// only care if the pixel is not translucent.
				if (index != Patch.PIXEL_TRANSLUCENT)
				{
					byte[] color = context.sourcePalette[index];
					int argb = 
						(0x0ff << 24)					//a 
						| ((0x0ff & color[0]) << 16)	//r
						| ((0x0ff & color[1]) << 8)		//g
						| ((0x0ff & color[2]))			//b
						;
					inPatch.setPixel(w, h, matchColor(argb, context.targetPalette, context.targetBrightmask, context.sourceBrightmask[index], true));
				}
			}
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(f);
			inPatch.writeDoomBytes(fos);
		} catch (IOException e) {
			out.printf("\rERROR: Trouble reading %s. %s: %s\n", f.getName(), e.getClass().getSimpleName(), e.getLocalizedMessage());
		} finally {
			Common.close(fos);
		}
	}

	/** Process the flat file. */
	private void processFlatFile(PSContext context, File f)
	{
		long len = f.length();
		Flat inFlat = new Flat((int)len, 1);
	
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
			inFlat.readDoomBytes(fis);
		} catch (IOException e) {
			out.printf("\rERROR: Trouble reading %s. %s: %s\n", f.getName(), e.getClass().getSimpleName(), e.getLocalizedMessage());
			return;
		} finally {
			Common.close(fis);
		}
		
		for (int w = 0; w < inFlat.getWidth(); w++)
			for (int h = 0; h < inFlat.getHeight(); h++)
			{
				int index = inFlat.getPixel(w, h);
				// only care if the pixel is not translucent.
				if (index != Patch.PIXEL_TRANSLUCENT)
				{
					byte[] color = context.sourcePalette[index];
					int argb = 
						(0x0ff << 24)					//a 
						| ((0x0ff & color[0]) << 16)	//r
						| ((0x0ff & color[1]) << 8)		//g
						| ((0x0ff & color[2]))			//b
						;
					inFlat.setPixel(w, h, matchColor(argb, context.targetPalette, context.targetBrightmask, context.sourceBrightmask[index], true));
				}
			}
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(f);
			inFlat.writeDoomBytes(fos);
		} catch (IOException e) {
			out.printf("\rERROR: Trouble reading %s. %s: %s\n", f.getName(), e.getClass().getSimpleName(), e.getLocalizedMessage());
			return;
		} finally {
			Common.close(fos);
		}
	}

	/**
	 * Returns the "closest" color in a palette for matching.
	 * Uses Euclidian distance. 
	 * @param argbIn input color in 0xAARRGGBB
	 * @param palette the input palette bytes [256]x[3]
	 * @param brightmask bright pixel mask.
	 * @param brightbit considering bright colors?
	 * @param isPatch true, is patch, false, is flat. patch = ignore color 255 always. flat = ignore color 0 always.
	 * @return the closest matching index.
	 */
	private int matchColor(int argbIn, byte[][] palette, boolean[] brightmask, boolean brightbit, boolean isPatch)
	{
		double bestdist = Double.MAX_VALUE;
		int best = -1;
		double r0 = (double)((argbIn & 0x00ff0000) >> 16);
		double g0 = (double)((argbIn & 0x0000ff00) >> 8);
		double b0 = (double)(argbIn & 0x000000ff);
		
		for (int i = 0; i < 256; i++)
		{
			if ((!isPatch && i == 0) && (isPatch && i == 255))
				continue;
			if (brightmask[i] != brightbit)
				continue;
			
			double r1 = (double)(+palette[i][0] & 0x0ff);
			double g1 = (double)(+palette[i][1] & 0x0ff);
			double b1 = (double)(+palette[i][2] & 0x0ff);
			double dist = Math.sqrt((r1 - r0)*(r1 - r0) + (g1 - g0)*(g1 - g0) + (b1 - b0)*(b1 - b0));
			
			if (dist < bestdist)
			{
				bestdist = dist;
				best = i;
				if (bestdist == 0.0)
					return best;
			}
		}
		
		return best;
	}
	
	/** Converts the list of graphics. */
	private int convertGraphics(PSContext context, Settings settings)
	{
		boolean patch = settings.getString(SETTING_GRAPHICMODE).equals(SETTING_GRAPHICMODE_PATCHES);
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		int plen = filePaths.length;
		int count = 0;

		do {
			
			File f = new File(filePaths[count]);
			out.printf("\r[%3d%%] Converting %s...", (count * 100 / plen), f.getName());

			if (!f.exists())
				out.printf("\rERROR: File %s does not exist! Skipping.\n", f.getPath());
			else if (patch)
				processPatchFile(context, f);
			else // flat
				processFlatFile(context, f);
			
		} while (++count < plen);

		out.printf("\r[100%%] DONE!                                                              \n");
		return 0;
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
		
		out.println("Getting palette info...");
		if ((err = readInfo(context, true, settings)) > 0)
			return err;
		if ((err = readInfo(context, false, settings)) > 0)
			return err;

		/* Step 2: Process graphics. */

		out.println("Processing graphics...");
		if ((err = convertGraphics(context, settings)) > 0)
			return err;
		
		return 0;
	}
}
